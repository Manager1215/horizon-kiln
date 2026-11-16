// What Kiln reads of the game itself, where the hook points' helpers say nothing: the
// pause, the world's game time and the player's advancements; and, on the integrated server, the acting player's
// attributes, health and hands, which a Java mod's code changes (`use_item`, `player_tick`, `player_respawned`), and
// the blocks a mod's block code reads and sets (`right_click_block`, `random_tick`, `queue_step`, `tick_block`,
// `neighbor_changed`, `state_for_placement`, `place_block`, `destroy_block`), and the mods' block entities (`block_entity`,
// `block_entity_tick`) with a level's table of the ones that tick (`level_tick`), and the world's cooking recipes and
// fuels their code looks for (`ask_tables`).
// This module is a child of `crate::game`, so it sees `Game`'s fields. A rename in a new Horizon breaks the build
// here, and only here.
// It names the game's types, so it isn't part of the mod's Cargo library: `native.py check` builds it.
//
// Advancements come from the single-player server's own `Game` when there is one (its thread publishes them,
// the window's reads them): Horizon's client view of them loses most of them after a trip between dimensions.
// Without a server in the process (playing on someone's server), the client view is all there is.

use std::cell::{Cell, RefCell};
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::Mutex;

use horizon_client::containers::{BlockEntity, ModBlockEntity, ModContainer};
use horizon_client::save::{decode_items, encode_items, item_from_nbt, item_to_nbt};
use horizon_client::first_person::Hand;
use horizon_client::items::ItemStack;
use horizon_client::text::Text;
use horizon_core::position::BlockPos;
use horizon_world::attribute_map::ValueKind;
use horizon_world::level::{Dimension as LevelDimension, LevelAccess, PlacementContext, Ticks};
use horizon_world::nbt::{Compound, Limits, Tag, parse_snbt, read_network, write_network};
use horizon_world::registry::{BlockRegistry, BlockStateId};
use horizon_world::snbt_text::to_snbt;

use crate::game::kiln as m;
use crate::game::kiln_jvm::Play;

/// The attributes a server call carries: the ones Kiln's Java mods read so far.
const ATTRIBUTES: [&str; 1] = ["minecraft:max_health"];

thread_local! {
    /// The advancement tree's size and the criteria granted, as last read on this thread: a change means
    /// the made advancements may have changed.
    static ADVANCEMENTS: Cell<Option<(usize, usize)>> = const { Cell::new(None) };
    /// The server's publication the window last took.
    static SERVER_SEEN: Cell<u64> = const { Cell::new(0) };
    /// The window's player's name: Horizon's options read it from a file the first time.
    static WINDOW_NAME: std::cell::OnceCell<String> = const { std::cell::OnceCell::new() };
    /// The server's thread: the level each player was last in, as Java mods heard (`join`).
    static LOADED: RefCell<HashMap<u128, String>> = RefCell::new(HashMap::new());
    /// The server's thread: each player as they were at their last tick, for a respawn's old player.
    static LAST: RefCell<HashMap<u128, m::PlayerSnapshot>> = RefCell::new(HashMap::new());
    /// The server's thread: each player's attributes Java mods changed, by id: the base they set last, and the base
    /// before their first change (a new body's).
    static KEPT: RefCell<HashMap<u128, HashMap<String, (f64, f64)>>> = RefCell::new(HashMap::new());
    /// The server's thread: the server's players as its tick began, and the server's view distance then, for Kiln's
    /// Java's `players` (Fabric's `PlayerLookup`): Horizon lists them only where no player's state is swapped in.
    static ONLINE: RefCell<(Vec<m::Online>, i32)> = const { RefCell::new((Vec::new(), 10)) };
    /// The server's thread: the players whose connection the Java mods were told of (Fabric's INIT and JOIN), each as
    /// they were at their last tick, for DISCONNECT.
    static CONNECTED: RefCell<HashMap<u128, m::PlayerSnapshot>> = RefCell::new(HashMap::new());
    /// The server's thread: the open world's folder, for calls from hooks that don't know it (a random tick).
    static WORLD: RefCell<Option<PathBuf>> = const { RefCell::new(None) };
    /// The server's thread: for each level, the announcement of the Java mods' blocks its block entity ticking table
    /// was built after, and whether that table was just dropped (`level_tick`).
    static TICKING: RefCell<HashMap<String, (u64, bool)>> = RefCell::new(HashMap::new());
    /// The server's thread: the game time each player's click a mod's block took (`right_click_block`). The window
    /// can't tell a mod's block takes a click, so it sends the item's use after it, as for a block with no use: Kiln
    /// takes that too (`use_item`), or a bucket held while opening a machine would pour beside it.
    static CLICK_TAKEN: RefCell<HashMap<u128, u64>> = RefCell::new(HashMap::new());
    /// The window's thread: how many messages its chat had when Kiln last looked, or last wrote its own lines there.
    static CHAT_SEEN: Cell<Option<u64>> = const { Cell::new(None) };
    /// The window's thread: the system toasts the Java mods showed, as Horizon's own toasts, each with its token.
    static TOASTS: RefCell<Vec<(String, horizon_client::toasts::SystemToast)>> = const { RefCell::new(Vec::new()) };
    /// The server's thread: each player as they were at their last tick, by their entity's id, with the game time
    /// then, for the hooks that name an entity and have no game at hand (Kiln's Mixin bridge, `incoming_damage`).
    static BODIES: RefCell<HashMap<i32, (m::PlayerSnapshot, u64)>> = RefCell::new(HashMap::new());
    /// The server's thread: each player as at their last tick, by uuid, for the hooks that name only that (a death).
    static PLAYERS: RefCell<HashMap<u128, (m::PlayerSnapshot, u64)>> = RefCell::new(HashMap::new());
    /// The server's thread: the mobs each level ticked in its tick so far, by dimension, with their kinds, for the
    /// bridge's batch after it (`entity_ticks`, D-145).
    static TICKED: RefCell<HashMap<String, Vec<(u32, String)>>> = RefCell::new(HashMap::new());
}

/// What the mods' code did to a player from a hook with no game at hand (a hit, through Kiln's Mixin bridge): done at
/// that player's next tick, by their uuid.
static PLAYER_PENDING: Mutex<Vec<(u128, m::Effect)>> = Mutex::new(Vec::new());

/// Players standing on a mod's block, as Horizon's step hook heard them (it has no game to call Java from):
/// (dimension, entity, state, position), taken at that player's tick. At most a tick's worth for a few players.
static STEPS: Mutex<Vec<(String, i32, BlockStateId, [i32; 3])>> = Mutex::new(Vec::new());
/// What a mod's code did from a hook that can't change the world (a neighbour change, a placement's state): done at
/// the server's next tick, with the dimension it was in.
static PENDING: Mutex<Vec<(String, m::Effect)>> = Mutex::new(Vec::new());
/// Players' messages on Kiln's channel for mods (`net_from_client`), for the Java mods' server receivers at each
/// sender's next tick, where Kiln has the player to hand them (`payloads`). Horizon lets a player send 32 a tick.
static NET_IN: Mutex<Vec<(u128, Vec<u8>)>> = Mutex::new(Vec::new());
/// The most messages waiting for their senders' ticks; past it, a message is dropped (logged).
const NET_IN_MAX: usize = 1024;

/// The game's state for Kiln's Java mods, as of now (the window's game).
pub(super) fn snapshot(game: &mut crate::game::Game<'_>) -> m::Snapshot {
    // The player list first: the server's comes with skins Horizon may still be fetching.
    let players = if game.native_player().is_some() { listed(game) } else { Vec::new() };
    let game = &*game;
    // Taken in a world or not: what came while Java had no world isn't news in the next one.
    let messages = new_messages(game);
    // On the title screen `title` is set; travelling between dimensions, Horizon has no player but no title.
    let connected = game.title.is_none();
    if !connected {
        m::forget_server_advancements();
        SERVER_SEEN.set(0);
    }
    known_maps(game);
    let Some(player) = game.native_player() else {
        ADVANCEMENTS.set(None);
        return m::Snapshot {
            connected,
            paused: game.pause,
            ..m::Snapshot::default()
        };
    };
    let held = |s: &Option<ItemStack>| s.as_ref().map(|s| (s.item.to_string(), s.count));
    let window_player = game.player.as_ref().map(|p| m::PlayerSnapshot {
        uuid: own_uuid(game),
        name: WINDOW_NAME.with(|n| n.get_or_init(|| game.options.player_name()).clone()),
        dimension: player.dimension.to_owned(),
        position: [player.x, player.y, player.z],
        game_mode: game.mode_id() as u8,
        health: p.health.health(),
        main: held(game.inventory.get(game.selected).unwrap_or(&None)),
        off: held(&game.offhand),
        attributes: Vec::new(),
        sneaking: game.keys.contains(&super::KeyCode::ShiftLeft),
        experience: (game.experience.level, game.experience.progress, game.experience.total),
        main_stack: stack_text(game.inventory.get(game.selected).unwrap_or(&None)),
        off_stack: stack_text(&game.offhand),
    });
    m::Snapshot {
        connected,
        in_world: true,
        paused: game.pause,
        game_time: game.world.game_time.max(0) as u64,
        dimension: player.dimension.to_owned(),
        advancements: window_advancements(game),
        player: window_player,
        equipment: equipment(game),
        hud_hidden: game.ui.hide_gui,
        debug_shown: game.debug,
        state: m::WindowState {
            players,
            messages,
            ..window_state(game)
        },
        keys: mod_bindings(game),
    }
}

/// The maps the window has the data of, as Java last heard them: id to `scale locked`.
static MAPS_TOLD: Mutex<Option<std::collections::BTreeMap<i32, (u8, bool)>>> = Mutex::new(None);

/// The maps the window knows, told to Java when they changed (`maps <id>:<scale>:<locked>,...`): a mod's tooltip of a
/// filled map reads "ID #n" and "Locked" for a map the game has, "Unknown Map" for one it hasn't, as the game's
/// (`MapId.addToTooltip` asks the client's map data, D-177).
fn known_maps(game: &crate::game::Game<'_>) {
    let now: std::collections::BTreeMap<i32, (u8, bool)> =
        game.maps.data.iter().map(|(id, m)| (*id, (m.scale, m.locked))).collect();
    let mut told = MAPS_TOLD.lock().unwrap_or_else(|e| e.into_inner());
    if told.as_ref() == Some(&now) {
        return;
    }
    let list: Vec<String> = now
        .iter()
        .map(|(id, (scale, locked))| format!("{id}:{scale}:{}", u8::from(*locked)))
        .collect();
    m::mod_screen(|s| s.tell_java("maps", vec![list.join(",")]));
    *told = Some(now);
}

/// The key of each Java mod's key Kiln knows: the one the player chose on the mod's page in Options > Mods (the
/// game's `key.kiln_<mod id>.<name>` lines in `horizon-mods.txt`), else the mod's own.
fn mod_bindings(game: &crate::game::Game<'_>) -> std::collections::BTreeMap<String, String> {
    let kiln_dir = m::kiln_dir(&game.native_data_dir(m::ID));
    m::mod_keys()
        .into_iter()
        .map(|(row, name, default)| {
            // A mapping that keeps modifiers itself gets its combination (D-178): what the page shows read back.
            let key = m::combo_chosen(&kiln_dir, &game.native_mod_key(&row, &name, &default), &default);
            (name, key)
        })
        .collect()
}

/// How the game names a key (`key.keyboard.o` is `O`), as its Key Binds rows show it, for a combination's label.
fn key_label(key: &str) -> String {
    horizon_client::keybinds::display_name(key).plain()
}

/// A key bound on a mod's page while modifiers are held (the player holds Ctrl and presses O), for a mapping that keeps
/// modifiers itself (JEI's), is that combination (D-178): the game's page binds the key alone, so the frame after,
/// with the modifiers still down, Kiln keeps it as `CTRL + O`. A modifier key bound alone stays itself (JEI's own
/// Controls bind one so too). Every frame, as the page is open while the game is paused.
pub(super) fn combine_mod_keys(game: &mut crate::game::Game<'_>) {
    let held: Vec<&str> = [
        ("control", game.modifiers.control_key()),
        ("shift", game.modifiers.shift_key()),
        ("alt", game.modifiers.alt_key()),
        ("command", game.modifiers.super_key()),
    ]
    .into_iter()
    .filter_map(|(name, down)| down.then_some(name))
    .collect();
    let mut changed = false;
    let mut kiln_dir = None;
    for (row, name, default) in m::mod_keys() {
        if m::split_combo(&default).is_none() {
            continue;
        }
        let value = game.settings.mods.keys.get(&row).and_then(|k| k.get(&name)).cloned();
        if !m::fresh_choice(&row, &name, value.as_deref()) || held.is_empty() {
            continue;
        }
        let Some(value) = value.filter(|v| v.starts_with("key.") && !m::is_modifier_key(v)) else {
            continue;
        };
        let dir = kiln_dir.get_or_insert_with(|| m::kiln_dir(&game.native_data_dir(m::ID)));
        let shown = m::combo_shown(dir, &m::combo(&held, &value), &key_label);
        game.settings.mods.keys.entry(row.clone()).or_default().insert(name.clone(), shown.clone());
        // Seen as it is now, so that it isn't taken for another choice.
        m::fresh_choice(&row, &name, Some(&shown));
        changed = true;
    }
    if changed {
        game.settings.save(&crate::game::Game::options_path());
    }
}

/// The Java mods' keys Java told of since the game made Options > Mods' pages (it makes them when it starts, from the
/// keys Kiln kept): onto the mods' pages as Key Binds rows, so that the game that first runs a mod can bind its keys.
pub(super) fn show_mod_keys(game: &mut crate::game::Game<'_>) {
    keep_chosen_keys(game);
    let Some(rows) = m::take_changed_keys() else {
        return;
    };
    let kiln_dir = m::kiln_dir(&game.native_data_dir(m::ID));
    for row in &mut game.settings.mods.installed {
        if let (Some(keys), Some(page)) = (rows.get(&row.id), row.native.as_mut()) {
            // A combination as the page shows it (`CTRL + O`, D-178); a key's name as it is.
            page.keys = keys
                .iter()
                .map(|[name, label, key]| [name.clone(), label.clone(), m::combo_shown(&kiln_dir, key, &key_label)])
                .collect();
        }
    }
}

/// The keys the mods set themselves (`KeyMapping.setKey`: their own key screens, a library's config field), kept as
/// the player's choice on the mods' pages, as the page's own Key Binds rows keep it (`key.kiln_<mod id>.<name>` in
/// `horizon-mods.txt`, written at once, as Minecraft's `Options.save` after a mod's change): the page shows it, the
/// next game has it, and Java hears it back (`bind`) as any choice. The mod's own key again: the choice goes.
fn keep_chosen_keys(game: &mut crate::game::Game<'_>) {
    let chosen = m::take_chosen_keys();
    if chosen.is_empty() {
        return;
    }
    let kiln_dir = m::kiln_dir(&game.native_data_dir(m::ID));
    for (row, name, key) in chosen {
        let keys = game.settings.mods.keys.entry(row.clone()).or_default();
        match key {
            Some(key) => {
                // A combination kept as the page shows it (D-178).
                let shown = m::combo_shown(&kiln_dir, &key, &key_label);
                m::fresh_choice(&row, &name, Some(&shown));
                keys.insert(name, shown);
            }
            None => {
                keys.remove(&name);
                if keys.is_empty() {
                    game.settings.mods.keys.remove(&row);
                }
            }
        }
    }
    game.settings.save(&crate::game::Game::options_path());
}

/// The messages that came to the window's chat since the last call, oldest first, as plain text: a player's (the
/// game makes theirs a `chat.type.*` translation) or the game's. None the first time: those were there before.
fn new_messages(game: &crate::game::Game<'_>) -> Vec<(bool, String)> {
    let chat = &game.ui.chat;
    let Some(seen) = CHAT_SEEN.replace(Some(chat.received)) else {
        return Vec::new();
    };
    // The chat keeps the last hundred.
    let n = chat.received.saturating_sub(seen).min(100) as usize;
    let player = |c: &serde_json::Value| {
        c.get("translate")
            .and_then(|t| t.as_str())
            .is_some_and(|t| t.starts_with("chat.type."))
    };
    let mut messages: Vec<(bool, String)> = chat
        .recent_components(n)
        .iter()
        .zip(chat.recent_lines(n))
        .map(|(c, text)| (player(c), text))
        .collect();
    messages.reverse();
    messages
}

/// After Kiln wrote its own lines to the window's chat, which had `before` messages then: they aren't messages that
/// came to it. Only right after Kiln looked: a message that came since stays news.
pub(super) fn chat_written(game: &crate::game::Game<'_>, before: u64) {
    if CHAT_SEEN.get() == Some(before) {
        CHAT_SEEN.set(Some(game.ui.chat.received));
    }
}

/// How many messages the window's chat has had, before Kiln writes its own lines there (`chat_written`).
pub(super) fn chat_count(game: &crate::game::Game<'_>) -> u64 {
    game.ui.chat.received
}

/// The window: an item's tooltip (`native_tooltip`, each frame it shows), with the lines the Java mods' code gives it
/// past its name (D-145), and the name when their code changed it. Java makes them a tick late: the first frames show
/// Horizon's lines alone, and the window asks again while the tooltip shows.
pub(super) fn tooltip(stack: &ItemStack, lines: &mut Vec<(Text, u32)>) {
    let key = to_snbt(&item_to_nbt(stack));
    let (made, ask) = m::tooltip(&key);
    if ask {
        m::hook("tooltip", &[key]);
    }
    let Some(made) = made else {
        return;
    };
    let text = |json: &serde_json::Value| horizon_client::component::to_text(json, &mut |s| Text::literal(s));
    if let Some(name) = &made.name
        && let Some(first) = lines.first_mut()
    {
        first.0 = text(name);
    }
    lines.extend(made.lines.iter().map(|line| (text(line), u32::MAX)));
}

/// A mod's line in the window's chat with its style, from vanilla's JSON (`m::take_chat_components`): as
/// `native_chat` adds a client line, which takes plain text only, so a colour set with `withStyle` would be lost.
/// The JSON is decoded as the game decodes a component (`component::to_text`, selectors left as their text): the
/// spans are what the chat draws, `with_component` only what it sends on. Legacy `§` codes in the text still
/// restyle it, as the chat resolves them when it draws.
pub(super) fn chat_component(game: &mut crate::game::Game<'_>, component: serde_json::Value) {
    let focused = game.screen == crate::game::Screen::Chat;
    let line = horizon_client::component::to_text(&component, &mut |s| Text::literal(s))
        .with_component(component);
    game.ui.chat.add(
        &game.gui.atlas,
        line,
        None,
        horizon_client::chat::ChatSource::Client,
        game.ticks,
        focused,
    );
}

/// What the Java mods played for the window's player: a sound (in the level, or the player's own), a particle (one of
/// the game's simple ones, near the player), a system toast (drawn by `draw_toasts`).
pub(super) fn play(game: &mut crate::game::Game<'_>, played: Vec<Play>) {
    use horizon_client::particles::{ParticleOptions, ParticleType};
    for play in played {
        match play {
            Play::Sound {
                id,
                at: Some(at),
                volume,
                pitch,
            } => {
                if !game.native_play_local_sound(&id, at, volume, pitch, 0) {
                    log::info!("Kiln: a Java mod's sound {id} isn't one the game plays");
                }
            }
            Play::Sound {
                id,
                at: None,
                volume,
                pitch,
            } => {
                if volume.is_finite() && pitch.is_finite() {
                    game.audio.play(&id, None, volume.max(0.0), pitch.max(0.0));
                }
            }
            Play::Particle { id, at, velocity } => {
                let Some(ty) = ParticleType::from_name(&id) else {
                    log::info!("Kiln: a Java mod's particle {id} isn't one Horizon draws");
                    continue;
                };
                // As vanilla's particle engine: none more than 32 blocks from the camera.
                let near = game.native_player().is_some_and(|p| {
                    (at[0] - p.x).powi(2) + (at[1] - p.y).powi(2) + (at[2] - p.z).powi(2) <= 1024.0
                });
                if near && velocity.iter().all(|v| v.is_finite()) {
                    game.particles.add(&ParticleOptions::simple(ty), at, velocity);
                }
            }
            Play::Toast {
                title,
                message,
                millis,
                token,
                replace,
            } => {
                let message = (!message.is_empty()).then_some(message);
                let millis = if millis.is_finite() { millis.clamp(0.0, 600_000.0) } else { 5000.0 };
                let toast = horizon_client::toasts::SystemToast::new(title, message).with_display_time(millis);
                // `SystemToast.addOrUpdate`: one of the token still shown is reset, else a new one.
                TOASTS.with(|t| {
                    let mut toasts = t.borrow_mut();
                    match toasts.iter_mut().find(|(t, _)| replace && *t == token) {
                        Some((_, shown)) => shown.reset_from(toast),
                        None => toasts.push((token, toast)),
                    }
                });
            }
        }
    }
}

/// The Java mods' system toasts, each in a free 32-pixel slot of the game's toasts, sliding in and out with the
/// game's sounds (`hidden`, F1, keeps them timing out undrawn).
pub(super) fn draw_toasts(game: &mut crate::game::Game<'_>, batch: &mut horizon_client::hud::HudBatch, hidden: bool) {
    let multiplier = f64::from(game.settings.notification_display_time);
    TOASTS.with(|t| {
        let mut toasts = t.borrow_mut();
        let mut used = toasts
            .iter()
            .filter_map(|(_, t)| t.slot)
            .fold(game.toast_slots_used(), |used, slot| used | 1 << slot);
        toasts.retain_mut(|(_, toast)| {
            if toast.slot.is_none() {
                let Some(slot) = (0..crate::game::Game::TOAST_SLOTS).find(|s| used & (1 << s) == 0) else {
                    return true;
                };
                used |= 1 << slot;
                toast.slot = Some(slot);
            }
            let (sound, finished) = toast.update(multiplier);
            if let Some(show) = sound {
                let event = if show { "minecraft:ui.toast.in" } else { "minecraft:ui.toast.out" };
                game.audio.play(event, None, 1.0, 1.0);
            }
            if finished {
                return false;
            }
            if let (false, Some(slot)) = (hidden, toast.slot) {
                toast.draw(&game.gui.atlas, batch, 32.0 * slot as f32);
            }
            true
        });
    });
}

/// The players the window's connection lists, as its player list (Tab) has them: the server's list, or in a world
/// of this computer's own with no connection, the window's player and the LAN guests in the world.
fn listed(game: &mut crate::game::Game<'_>) -> Vec<m::Listed> {
    let players = match game.remote_tab_players() {
        Some((players, _, _)) => players,
        None => {
            let name = WINDOW_NAME.with(|n| n.get_or_init(|| game.options.player_name()).clone());
            let mut me = horizon_client::tab_list::TabPlayer::new(own_uuid(game), &name);
            me.game_mode = game.mode_id();
            let guests = game.lan.as_ref().map_or(&[][..], |h| &h.hub.guests);
            super::scoreboard::local_tab_players(me, guests)
        }
    };
    players
        .into_iter()
        .map(|p| m::Listed {
            uuid: p.uuid,
            name: p.name,
            game_mode: p.game_mode,
            latency: p.latency,
        })
        .collect()
}

/// The window's world and player for client mods: what `WindowState` lists, from Horizon's game.
fn window_state(game: &crate::game::Game<'_>) -> m::WindowState {
    let items = match &game.world_tables {
        Some(t) => &t.items,
        None => game.res.items(),
    };
    let mut state = m::WindowState {
        single_player: game.has_singleplayer_server(),
        // A server's address as the player joined it (D-178): JEI keeps bookmarks per server by it. Not single
        // player's own, nor a LAN world of this computer's.
        server: if game.integrated.is_none() && game.remote.is_some() {
            game.server_address().unwrap_or_default().to_owned()
        } else {
            String::new()
        },
        day_time: game.native_day_time(),
        weather: game.native_weather(),
        height: game.native_world_height(),
        rotation: (game.yaw, game.pitch),
        selected: game.selected,
        fov: game.settings.fov,
        render_distance: game.settings.render_distance,
        gui_scale: game.settings.gui_scale,
        show_subtitles: game.settings.show_subtitles,
        left_handed: game.settings.main_hand_left,
        language: game.settings.language.clone(),
        held: m::held_keys(),
        difficulty: horizon_client::title::DIFFICULTIES[usize::from(game.ui.difficulty) & 3].to_owned(),
        cursor: game.native_cursor(),
        screen: game.screen_label().to_owned(),
        chunks: game.world.chunks.keys().map(|&(x, z)| [x, z]).collect(),
        effects: game
            .potions
            .effects
            .list
            .iter()
            .map(|e| m::ActiveEffect {
                id: e.id.to_owned(),
                duration: e.duration,
                amplifier: e.amplifier,
                ambient: e.ambient,
                visible: e.visible,
            })
            .collect(),
        mouse: [
            game.physical_buttons.contains(&super::MouseButton::Left),
            game.physical_buttons.contains(&super::MouseButton::Middle),
            game.physical_buttons.contains(&super::MouseButton::Right),
            game.captured,
        ],
        ..m::WindowState::default()
    };
    // The window's pixels and the GUI scale, as Horizon works the HUD's out (vanilla's Window.calculateScale).
    if let Some(r) = &game.renderer {
        let size = [r.config.width, r.config.height];
        state.window = [size[0], size[1], horizon_client::hud::HudBatch::new(size).scale as u32];
    }
    // The camera (F5), and the debug screen's entries that show (F3+B's hitboxes, F3+G's chunk borders...).
    state.camera = match game.camera_type {
        crate::game::perspective::CameraType::FirstPerson => 0,
        crate::game::perspective::CameraType::ThirdPersonBack => 1,
        crate::game::perspective::CameraType::ThirdPersonFront => 2,
    };
    state.debug = horizon_client::debug_entries::search("")
        .into_iter()
        .filter_map(|e| match game.debug_entries.status(e.path) {
            horizon_client::debug_entries::Status::Never => None,
            status => Some(format!("{}:{}", e.path, status.name())),
        })
        .collect();
    state.debug_on = horizon_client::debug_entries::search("")
        .into_iter()
        .filter(|e| game.debug_entry(e.path))
        .map(|e| e.path.to_owned())
        .collect();
    // What the crosshair is on, as `Minecraft.hitResult` (GameRenderer.pick): an entity before the block is none
    // for Kiln's Java, which knows no entity but the player.
    if game.player.is_some() && game.pick_entity_hit().is_none() {
        state.hit = game.pick().map(|hit| (hit.pos, hit.face, hit.point));
    }
    state.hook = game.hook.as_ref().map(|h| {
        let (at, was) = (h.position, h.previous);
        [at.x, at.y, at.z, at.x - was.x, at.y - was.y, at.z - was.z]
    });
    let Some(p) = game.player.as_ref() else {
        return state;
    };
    let at = [
        p.position.x.floor() as i32,
        p.position.y.floor() as i32,
        p.position.z.floor() as i32,
    ];
    state.light = game.native_light(at);
    state.biome = game.biome_name_at(p.position).unwrap_or_default();
    // Vanilla's armour value: the `minecraft:armor` modifiers (added values) of the armour worn, in its slot.
    let slots = ["head", "chest", "legs", "feet"];
    state.armor = game
        .armor
        .iter()
        .zip(slots)
        .filter_map(|(stack, slot)| Some((items.get(&stack.as_ref()?.item)?, slot)))
        .flat_map(|(info, slot)| {
            info.modifiers.iter().filter(move |(attribute, _, _, operation, group)| {
                attribute == "minecraft:armor"
                    && operation == "add_value"
                    && (group == slot || group == "armor" || group == "any")
            })
        })
        .map(|(_, _, amount, _, _)| amount)
        .sum();
    let mut blocks = Vec::with_capacity(27);
    for dx in -1..=1 {
        for dy in -1..=1 {
            for dz in -1..=1 {
                blocks.push(game.native_block([at[0] + dx, at[1] + dy, at[2] + dz]));
            }
        }
    }
    state.blocks = blocks;
    state.food = (p.health.food.food.max(0) as u32, p.health.food.saturation);
    state.flying = p.flying;
    state.may_fly = p.may_fly;
    state.flying_speed = p.flying_speed;
    state.horizontal_collision = p.collision.horizontal;
    state.minor_horizontal_collision = p.collision.minor;
    state.invulnerable = p.invulnerable;
    state.on_ground = p.ground.on_ground;
    state.in_water = p.fluids.was_touching_water;
    state.sprinting = p.sprinting;
    state.fall_flying = p.fall_flying;
    state.velocity = [p.velocity.x, p.velocity.y, p.velocity.z];
    state.inventory = game
        .inventory
        .iter()
        .take(36)
        .enumerate()
        .filter_map(|(slot, stack)| {
            let stack = stack.as_ref()?;
            Some((
                slot,
                m::Worn {
                    slot: "inventory",
                    item: tick_item(stack),
                    count: stack.count,
                    damage: stack.damage,
                    max_damage: items.get(&stack.item).map_or(0, |i| i.max_damage),
                },
            ))
        })
        .collect();
    // The crafting grid of the open inventory or table, as the game numbers its cells (100 and up, D-166).
    if let Some(side) = open_grid(game) {
        state.inventory.extend(game.craft_grid.iter().take(side * side).enumerate().filter_map(|(cell, stack)| {
            let stack = stack.as_ref()?;
            Some((
                100 + cell,
                m::Worn {
                    slot: "inventory",
                    item: tick_item(stack),
                    count: stack.count,
                    damage: stack.damage,
                    max_damage: 0,
                },
            ))
        }));
    }
    // A station's own slots (a furnace's, a brewing stand's, a crafter's, a smithing table's, an anvil's), numbered as
    // its menu's from 100 (D-176): a recipe viewer on the client sees what they hold, as on the game's client, so its
    // "+" counts them as ingredients and as stacks to put back first.
    else if let Some(cells) = open_station_cells(game) {
        state.inventory.extend(cells.iter().take(9).enumerate().filter_map(|(cell, stack)| {
            let stack = stack.as_ref()?;
            Some((
                100 + cell,
                m::Worn {
                    slot: "inventory",
                    item: tick_item(stack),
                    count: stack.count,
                    damage: stack.damage,
                    max_damage: items.get(&stack.item).map_or(0, |i| i.max_damage),
                },
            ))
        }));
    }
    // Single player's world folder, by name, as the integrated server last said it (D-167).
    if state.single_player {
        state.level = game
            .native_world_dir()
            .and_then(|p| p.file_name().map(|n| n.to_string_lossy().into_owned()))
            .unwrap_or_else(|| LEVEL_ID.lock().unwrap_or_else(|e| e.into_inner()).clone());
    }
    state
}

/// An inventory slot's item for the tick (D-169): its id, and when the stack has components (a water bottle's potion,
/// an enchanted book's enchantments, a name) `|` and the whole stack's SNBT, %-escaped so it holds no space, `;` or `|`:
/// a recipe viewer on the client tells a water bottle from a plain one by them.
fn tick_item(stack: &ItemStack) -> String {
    let id = stack.item.to_string();
    let Tag::Compound(nbt) = item_to_nbt(stack) else {
        return id;
    };
    if !matches!(nbt.get(&"components".into()), Some(Tag::Compound(c)) if !c.is_empty()) {
        return id;
    }
    let snbt = to_snbt(&Tag::Compound(nbt)).replace('%', "%25").replace(' ', "%20").replace(';', "%3B").replace('|', "%7C");
    format!("{id}|{snbt}")
}

/// The side of the crafting grid the window's player has open (2 in the inventory, 3 at a crafting table), when the
/// game's screen is only that: no chest, station, mount or another server menu beside it.
fn open_grid(game: &crate::game::Game<'_>) -> Option<usize> {
    let plain = game.screen == crate::game::Screen::Inventory
        && game.container.is_none()
        && game.station_slots().is_none()
        && game.mount_slots().is_none()
        && matches!(game.remote_menu_type(), None | Some("") | Some("minecraft:crafting"));
    (plain && matches!(game.craft_size, 2 | 3)).then_some(game.craft_size)
}

/// The own slots of the station the window's player has open, in its menu's order (D-176): an anvil's or a smithing
/// table's inputs, a furnace's, a brewing stand's or a crafter's block slots. None for any other screen.
fn open_station_cells(game: &crate::game::Game<'_>) -> Option<Vec<Option<ItemStack>>> {
    if let Some(station) = game.station.as_ref() {
        return matches!(station.kind, crate::game::stations::Kind::Anvil | crate::game::stations::Kind::Smithing)
            .then(|| station.inputs.clone());
    }
    let key = game.container?;
    match game.world.block_entities.get(&key)? {
        entity @ (BlockEntity::Furnace(_) | BlockEntity::Brewing(_) | BlockEntity::Crafter(_)) => Some(entity.slots().to_vec()),
        _ => None,
    }
}

/// The integrated server's world folder's name, for the window's tick (`level`, D-167).
static LEVEL_ID: Mutex<String> = Mutex::new(String::new());

thread_local! {
    /// The menu each guest had open at the end of their last turn on the integrated server (D-166, D-167). A Java
    /// mod's code reads it with `player_menu`; the menu isn't in the game while a player's turn runs (it stays in the
    /// hub's guest).
    static MENUS: RefCell<HashMap<u128, SeenMenu>> = RefCell::new(HashMap::new());
    /// The `slots` effects of a guest's turn, for the end of it (`guest_turn`).
    static SLOT_MOVES: RefCell<Vec<SlotMoves>> = RefCell::new(Vec::new());
    /// A station's own slots as the hub's menu held them when `station_inputs` last ran (`station-slots`, D-169).
    static STATION_SLOTS: RefCell<Option<Vec<Option<ItemStack>>>> = const { RefCell::new(None) };
}

/// The `station-slots` injection's report (D-169): an anvil's or smithing table's own slots, in the menu's order.
pub(super) fn station_slots(slots: Vec<Option<ItemStack>>) {
    STATION_SLOTS.with(|s| *s.borrow_mut() = Some(slots));
}

/// A guest's open menu as the mods see it: its kind (`2` the inventory's crafting grid, `3` a crafting table's, a
/// station's or a machine's name: `furnace`, `smoker`, `blast_furnace`, `brewing_stand`, `crafter`, `smithing`,
/// `anvil`; `0` any other, whose own slots the mods don't get), its own slots (the grid's cells, a furnace's input,
/// fuel and result...) and the stack on the cursor.
#[derive(Clone, Default)]
struct SeenMenu {
    kind: String,
    cells: Vec<Option<ItemStack>>,
    carried: Option<ItemStack>,
}

/// A `slots` effect: the player, the menu's kind Java read, each slot it changed (0 to 35 the inventory, then the
/// menu's own) with what it held and what it holds now, and the cursor's stack before and after when it changed.
type SlotMoves = (u128, String, Vec<(usize, Option<ItemStack>, Option<ItemStack>)>, Option<(Option<ItemStack>, Option<ItemStack>)>);

/// How many slots of its own a menu of `kind` has before the player's inventory, as the game numbers its window.
fn menu_cells(kind: &str) -> usize {
    match kind {
        "2" => 4,
        "3" | "crafter" => 9,
        "furnace" | "smoker" | "blast_furnace" | "anvil" => 3,
        "smithing" => 4,
        "brewing_stand" => 5,
        _ => 0,
    }
}

/// The player's menu for a payload's code (`player_menu`, D-166, D-167): `<kind> <selected slot> <SNBT>`, the menu's
/// kind, the hotbar slot in hand, and `Items` (the 36 inventory slots and then the menu's own) with `Carried`, the
/// cursor's stack; empty when the acting player has no menu on record.
fn ask_player_menu(game: &crate::game::Game<'_>) -> String {
    let Some(uuid) = game.native_acting_player().map(|p| p.uuid) else {
        return String::new();
    };
    let Some(seen) = MENUS.with(|g| g.borrow().get(&uuid).cloned()) else {
        return String::new();
    };
    let cells = menu_cells(&seen.kind);
    let mut slots: Vec<Option<ItemStack>> = game.inventory.iter().take(36).cloned().collect();
    slots.resize(36, None);
    slots.extend(seen.cells.into_iter().take(cells));
    slots.resize(36 + cells, None);
    let mut nbt = Compound::new();
    nbt.insert("Items".into(), encode_items(&slots));
    if let Some(carried) = &seen.carried {
        nbt.insert("Carried".into(), item_to_nbt(carried));
    }
    format!("{} {} {}", seen.kind, game.selected, to_snbt(&Tag::Compound(nbt)))
}

/// What a payload's code moved among the acting player's slots (`Effect::PlayerSlots`, D-166): it waits for the end of
/// their turn (`guest_turn`), where the menu is in hand too, so a move is done whole or not at all.
fn put_player_slots(game: &mut crate::game::Game<'_>, data: &str) {
    let Some(mut nbt) = snbt_compound(data) else {
        return;
    };
    let Some(uuid) = game.native_acting_player().map(|p| p.uuid) else {
        return;
    };
    let Some(seen) = MENUS.with(|g| g.borrow().get(&uuid).cloned()) else {
        log::warn!("Kiln: a mod moved the player's slots, which the game gave no menu for");
        return;
    };
    let kind = nbt.get(&"Kind".into()).and_then(|t| match t {
        Tag::String(s) => s.to_string().ok(),
        _ => None,
    });
    let kind = kind.unwrap_or(seen.kind);
    let size = 36 + menu_cells(&kind);
    let before = decode_items(nbt.get(&"Before".into()), size);
    let after = decode_items(nbt.remove(&"Items".into()).as_ref(), size);
    let moves: Vec<(usize, Option<ItemStack>, Option<ItemStack>)> = before
        .into_iter()
        .zip(after)
        .enumerate()
        .filter(|(_, (was, now))| was != now)
        .map(|(slot, (was, now))| (slot, was, now))
        .collect();
    let stack = |key: &str| nbt.get(&key.into()).and_then(item_from_nbt);
    let carried = nbt
        .contains_key(&"CarriedBefore".into())
        .then(|| (stack("CarriedBefore"), stack("Carried")))
        .filter(|(was, now)| was != now);
    if !moves.is_empty() || carried.is_some() {
        SLOT_MOVES.with(|m| m.borrow_mut().push((uuid, kind, moves, carried)));
    }
}

/// The guest's open menu as the mods see it (`SeenMenu`), read from the hub at the end of their turn.
fn seen_menu(game: &crate::game::Game<'_>, host: &crate::game::lan::LanHost, conn: u32, gi: usize) -> SeenMenu {
    use horizon_client::lan::menu::Slots;
    let g = &host.hub.guests[gi];
    let carried = g.carried.clone();
    let play = host.play.get(&conn);
    if let Some((_, _, menu, _)) = play.and_then(|p| p.crafting.table.as_ref()) {
        return SeenMenu { kind: "3".into(), cells: (0..9).map(|k| menu.slot(1 + k).cloned()).collect(), carried };
    }
    if let Some(station) = play.and_then(|p| p.station.as_ref()) {
        // A station's slots are in its private menu: `station_inputs` reads them for Kiln (`station-slots`, D-169).
        let kind = match station.kind {
            crate::game::stations::Kind::Smithing => "smithing",
            crate::game::stations::Kind::Anvil => "anvil",
            _ => return SeenMenu { kind: "0".into(), cells: Vec::new(), carried },
        };
        STATION_SLOTS.with(|s| s.borrow_mut().take());
        let _ = crate::game::lan_stations::station_inputs(station);
        let Some(cells) = STATION_SLOTS.with(|s| s.borrow_mut().take()) else {
            return SeenMenu { kind: "0".into(), cells: Vec::new(), carried };
        };
        return SeenMenu { kind: kind.into(), cells: cells.into_iter().take(menu_cells(kind)).collect(), carried };
    }
    if let Some(Slots::Block(pos)) = g.menu.as_ref().map(|m| m.slots) {
        let kind = match game.world.block_entities.get(&pos) {
            Some(BlockEntity::Furnace(f)) => match f.kind.recipe_type() {
                "minecraft:blasting" => "blast_furnace",
                "minecraft:smoking" => "smoker",
                _ => "furnace",
            },
            Some(BlockEntity::Brewing(_)) => "brewing_stand",
            Some(BlockEntity::Crafter(_)) => "crafter",
            _ => "0",
        };
        let cells = g.menu.as_ref().and_then(|m| m.slots.read(&game.world, g)).unwrap_or_default();
        return SeenMenu { kind: kind.into(), cells: cells.into_iter().take(menu_cells(kind)).collect(), carried };
    }
    if g.menu.is_some() {
        return SeenMenu { kind: "0".into(), cells: Vec::new(), carried };
    }
    SeenMenu { kind: "2".into(), cells: g.craft_grid.iter().take(4).cloned().collect(), carried }
}

/// The same item with the same components, whatever the count.
fn same_kind(a: &ItemStack, b: &ItemStack) -> bool {
    a.item == b.item && a.damage == b.damage && a.extra == b.extra
}

/// The end of a guest's turn on the integrated server (an injection into `guest_dig_feedback`, which runs right after
/// it, with the hub in hand; D-166). Each `slots` effect of the turn is done whole, or not at all when a slot it changed
/// no longer holds what Java read (said in the log): so nothing is made twice or lost. The inventory is the hub guest's
/// (out of the game again), the grid the guest's 2x2 or the open table's menu; the result follows, the client is told,
/// and the menu as it is now is kept for the mods' next turn. A furnace's, a brewing stand's, a crafter's, a smithing
/// table's or an anvil's own slots are moved by the window's clicks, as the player's own hand would (D-167).
pub(super) fn guest_turn(game: &mut crate::game::Game<'_>, host: &mut crate::game::lan::LanHost, conn: u32) {
    use horizon_client::menu::{Kind, Menu};
    use horizon_protocol::packets::play::{self, Clientbound as C};
    if !game.native_on(m::ID) || !m::hears("payload") {
        return;
    }
    let Some(gi) = host.hub.guests.iter().position(|g| g.conn == conn) else {
        return;
    };
    let uuid = host.hub.guests[gi].uuid;
    let pending: Vec<SlotMoves> = SLOT_MOVES.with(|moves| {
        let (mine, rest) = std::mem::take(&mut *moves.borrow_mut()).into_iter().partition(|(p, ..)| *p == uuid);
        *moves.borrow_mut() = rest;
        mine
    });
    let mut changed = false;
    for (_, kind, moves, carried) in pending {
        let open = seen_menu(game, host, conn, gi);
        let g = &host.hub.guests[gi];
        let cells_hold = kind == open.kind
            && moves.iter().filter(|(slot, ..)| *slot >= 36).all(|(slot, was, _)| open.cells.get(slot - 36).cloned().flatten() == *was);
        let holds = cells_hold
            && moves.iter().filter(|(slot, ..)| *slot < 36).all(|(slot, was, _)| g.inventory.get(*slot).cloned().flatten() == *was)
            && carried.as_ref().is_none_or(|(was, _)| g.carried == *was);
        if !holds {
            log::warn!("Kiln: the player's slots changed under a mod's code; its move is left undone");
            continue;
        }
        if kind != "2" && kind != "3" && moves.iter().any(|(slot, ..)| *slot >= 36) {
            // The menu's own slots: by the window's clicks.
            if !replay(game, host, conn, gi, &kind, &moves) {
                log::warn!("Kiln: a mod's move in the player's {kind} menu could not be done whole");
            }
            // The window's slots and cursor again at the end: the client saw each click's step.
            changed = true;
            continue;
        }
        let mut table = host.play.get_mut(&conn).and_then(|p| p.crafting.table.take());
        let g = &mut host.hub.guests[gi];
        for (slot, _, now) in &moves {
            if *slot < 36 {
                g.inventory[*slot] = now.clone();
            } else if kind == "2" {
                g.craft_grid[slot - 36] = now.clone();
            }
        }
        if let Some((_, now)) = &carried {
            g.carried = now.clone();
        }
        let grid_moved = moves.iter().any(|(slot, ..)| *slot >= 36);
        if let Some((_, _, menu, _)) = table.as_mut().filter(|_| grid_moved) {
            game.guest_menu_op(host, gi, menu, |menu, env| {
                for (slot, _, now) in &moves {
                    if *slot >= 36 {
                        menu.set_slot(env, 1 + slot - 36, now.clone());
                    }
                }
            });
        } else if grid_moved {
            // The result follows the grid, as `slotsChanged` makes it.
            let mut menu = Box::new(Menu::new(Kind::Inventory));
            let grid = host.hub.guests[gi].craft_grid.clone();
            let result = game.guest_menu_op(host, gi, &mut menu, |menu, env| {
                for (k, s) in grid.into_iter().enumerate() {
                    menu.set_slot(env, 1 + k, s);
                }
                menu.slot(0).cloned()
            });
            host.hub.guests[gi].craft_result = result;
        }
        if let Some(table) = table {
            host.play.entry(conn).or_default().crafting.table = Some(table);
        }
        changed = true;
    }
    if changed {
        let window = host.hub.guests[gi].menu.as_ref().map(|m| m.window);
        match host.play.get_mut(&conn).and_then(|p| p.crafting.table.as_mut()) {
            Some((window, state, menu, _)) => {
                // `sendAllDataToRemote`: the table's slots, then the player's inventory, as the game sends them.
                *state = (*state + 1) & 0x7FFF;
                let (window, state) = (*window, *state);
                let mut items: Vec<Option<ItemStack>> = (0..10).map(|s| menu.slot(s).cloned()).collect();
                let g = &host.hub.guests[gi];
                items.extend((9..36).chain(0..9).map(|i| g.inventory[i].clone()));
                let carried = g.carried.clone();
                let items = items.iter().map(|s| host.hub.stack(s)).collect();
                let carried = host.hub.stack(&carried);
                host.hub.send_to(
                    conn,
                    &C::ContainerSetContent(play::ContainerSetContent { container: window, state, items, carried }),
                );
                host.hub.guests[gi].inventory_told();
            }
            // A block's window (a chest, a furnace): its slots and the inventory again.
            None if window.is_some_and(|w| w != 0) => host.hub.resend_menu(conn, window.unwrap_or(0), &game.world),
            None => host.hub.send_inventory(gi),
        }
    }
    // The menu as it is now, for the mods' next turn.
    let now = seen_menu(game, host, conn, gi);
    MENUS.with(|g| g.borrow_mut().insert(uuid, now));
}

/// A mod's move in the guest's open menu (a furnace, a smithing table...) done by the window's clicks, as the player's
/// hand would (D-167): first each of the menu's slots it changed is emptied into the inventory (a shift-click), then
/// each is filled from the inventory's stacks of its item (picked up, then put down whole or one by one, the rest put
/// back). The game moves the items and tells the client; where in the inventory an emptied slot's items go is the
/// game's choice, the menu's slots end as the mod's code left them. False when it couldn't be done whole (an item
/// missing, the cursor not empty).
fn replay(
    game: &mut crate::game::Game<'_>,
    host: &mut crate::game::lan::LanHost,
    conn: u32,
    gi: usize,
    kind: &str,
    moves: &[(usize, Option<ItemStack>, Option<ItemStack>)],
) -> bool {
    use horizon_protocol::packets::play::{ContainerClick, Serverbound as S};
    let window = match host.play.get(&conn).and_then(|p| p.station.as_ref()) {
        Some(station) => station.window,
        None => match host.hub.guests[gi].menu.as_ref() {
            Some(menu) => menu.window,
            None => return false,
        },
    };
    if host.hub.guests[gi].carried.is_some() {
        return false;
    }
    let n = menu_cells(kind);
    // The window's numbering: the menu's own slots, the main inventory (9 to 35), the hotbar (0 to 8).
    let slot_of = |i: usize| (if i < 9 { n + 27 + i } else { n + i - 9 }) as i16;
    let click = |game: &mut crate::game::Game<'_>, host: &mut crate::game::lan::LanHost, slot: i16, button: i8, input: i32| {
        let state = host.hub.guests[gi].menu.as_ref().map_or(0, |m| m.state);
        game.lan_guest_packet(
            host,
            conn,
            S::ContainerClick(ContainerClick { container: window, state, slot, button, input, changed: Vec::new(), carried: None }),
        );
    };
    let cells: Vec<(usize, Option<ItemStack>, Option<ItemStack>)> =
        moves.iter().filter(|(slot, ..)| *slot >= 36).map(|(slot, was, now)| (slot - 36, was.clone(), now.clone())).collect();
    for (cell, was, _) in &cells {
        if was.is_some() {
            click(game, host, *cell as i16, 0, 1);
        }
    }
    // What a slot holds after the shift-clicks: a stack the inventory had no room for stays (D-176).
    let held = |game: &crate::game::Game<'_>, host: &crate::game::lan::LanHost, cell: usize| {
        seen_menu(game, host, conn, gi).cells.get(cell).cloned().flatten()
    };
    // The game's drop key over a slot, its whole stack (`ClickType.THROW`, button 1): the mod's code dropped what fit
    // nowhere at the player's feet (JEI's `stowItems`), and Kiln's Java left that to this click (D-176).
    let throw = |game: &mut crate::game::Game<'_>, host: &mut crate::game::lan::LanHost, cell: usize| {
        let state = host.hub.guests[gi].menu.as_ref().map_or(0, |m| m.state);
        game.lan_guest_packet(
            host,
            conn,
            S::ContainerClick(ContainerClick { container: window, state, slot: cell as i16, button: 1, input: 4, changed: Vec::new(), carried: None }),
        );
    };
    let mut whole = true;
    for (cell, _, now) in &cells {
        let left = held(game, host, *cell);
        let Some(want) = now else {
            if left.is_some() {
                throw(game, host, *cell);
            }
            continue;
        };
        let mut need = want.count;
        if let Some(left) = left.as_ref().filter(|l| same_kind(l, want)) {
            // The same item stayed: only the rest goes in.
            need = need.saturating_sub(left.count);
        }
        while need > 0 {
            let inventory = &host.hub.guests[gi].inventory;
            let Some((from, count)) = inventory
                .iter()
                .take(36)
                .enumerate()
                .find_map(|(i, s)| s.as_ref().filter(|s| same_kind(s, want)).map(|s| (i, s.count)))
            else {
                whole = false;
                break;
            };
            if count > need && held(game, host, *cell).is_some_and(|h| !same_kind(&h, want)) {
                // Another item stayed in the slot and taking part of a stack frees no room for it: it is dropped, as
                // the mod's code dropped it.
                throw(game, host, *cell);
            }
            click(game, host, slot_of(from), 0, 0);
            if count <= need {
                click(game, host, *cell as i16, 0, 0);
                need -= count;
            } else {
                for _ in 0..need {
                    click(game, host, *cell as i16, 1, 0);
                }
                need = 0;
                click(game, host, slot_of(from), 0, 0);
            }
            if let Some(carried) = host.hub.guests[gi].carried.clone() {
                // The slot took fewer than offered (its limit), or gave back what stayed in it (another item, D-176):
                // that goes where the ingredients came from, and the slot is filled on.
                let swapped = !same_kind(&carried, want);
                click(game, host, slot_of(from), 0, 0);
                if host.hub.guests[gi].carried.is_some() {
                    return false;
                }
                if swapped {
                    continue;
                }
                break;
            }
        }
    }
    whole
}

/// What the window's player wears and holds, with each item's damage and durability (the item's own).
fn equipment(game: &crate::game::Game<'_>) -> Vec<m::Worn> {
    let items = match &game.world_tables {
        Some(t) => &t.items,
        None => game.res.items(),
    };
    let held = game.inventory.get(game.selected).unwrap_or(&None);
    let [head, chest, legs, feet] = &game.armor;
    [
        ("mainhand", held),
        ("offhand", &game.offhand),
        ("feet", feet),
        ("legs", legs),
        ("chest", chest),
        ("head", head),
    ]
    .into_iter()
    .filter_map(|(slot, stack)| {
        let stack = stack.as_ref()?;
        Some(m::Worn {
            slot,
            item: stack.item.to_string(),
            count: stack.count,
            damage: stack.damage,
            max_damage: items.get(&stack.item).map_or(0, |i| i.max_damage),
        })
    })
    .collect()
}

/// The window's player's UUID, as servers know it (Horizon's `own_uuid`).
fn own_uuid(game: &crate::game::Game<'_>) -> u128 {
    match &game.remote {
        Some(r) => r.profile(),
        None => game.level.player_uuid.map_or(0, |ints| {
            ints.iter()
                .fold(0u128, |acc, &i| acc << 32 | u128::from(i as u32))
        }),
    }
}

/// The window's list of made advancements, when it may have changed: the server's when it publishes one.
fn window_advancements(game: &crate::game::Game<'_>) -> Option<Vec<String>> {
    let mut seen = SERVER_SEEN.get();
    let from_server = m::server_advancements(&mut seen);
    SERVER_SEEN.set(seen);
    if seen > 0 {
        return from_server;
    }
    made_advancements(&game.adv.tree, &game.adv.progress)
}

/// The single-player server's tick: it publishes the player's made advancements when they change. The server
/// keeps each connected player's progress with the player (`lan_actions.rs` swaps it into `adv` only while
/// acting for them); single player has one. With several players, it publishes nothing.
pub(super) fn publish_server_advancements(game: &crate::game::Game<'_>) {
    let Some(host) = game.lan.as_ref() else {
        return;
    };
    let [player] = host.hub.guests.as_slice() else {
        return;
    };
    if let Some(made) = made_advancements(&game.adv.tree, &player.advancements) {
        m::publish_server_advancements(made);
    }
}

/// Every advancement of `tree` made in `progress` (recipes left out), when that may have changed since this
/// thread's last call.
fn made_advancements(
    tree: &horizon_client::advancements::Tree,
    progress: &horizon_client::advancements::Progress,
) -> Option<Vec<String>> {
    let granted = progress.criteria.values().map(|c| c.len()).sum();
    let key = (tree.list.len(), granted);
    if ADVANCEMENTS.get() == Some(key) {
        return None;
    }
    ADVANCEMENTS.set(Some(key));
    Some(
        tree.list
            .iter()
            .filter(|a| !a.id.starts_with("minecraft:recipes/") && progress.done(a))
            .map(|a| a.id.clone())
            .collect(),
    )
}

/// The game font's width of each character up to U+04FF (Latin and Cyrillic), in GUI pixels; 0 for control
/// characters.
pub(super) fn font_widths(game: &crate::game::Game<'_>) -> Vec<u32> {
    (0..0x500u32)
        .map(|code| match char::from_u32(code).filter(|c| !c.is_control()) {
            Some(c) => game.native_text_width(c.encode_utf8(&mut [0; 4])).round() as u32,
            None => 0,
        })
        .collect()
}

/// The integrated server's acting player (whose state is swapped in), as a call carries them; the world's folder; the
/// game time. None outside a player's hook.
fn acting(game: &mut crate::game::Game<'_>) -> Option<(m::PlayerSnapshot, PathBuf, u64)> {
    let (acting, world) = (game.native_acting_player()?, game.native_world_dir()?);
    let held = |s: &Option<ItemStack>| s.as_ref().map(|s| (s.item.to_string(), s.count));
    // Reading an attribute creates its instance, as vanilla's getAttribute.
    let attributes = game.player.as_mut().map_or_else(Vec::new, |p| {
        ATTRIBUTES
            .iter()
            .filter_map(|&id| {
                let key = p.attributes.key(id)?;
                let base = p
                    .attributes
                    .value(Some(key.clone()), ValueKind::Base)
                    .ok()?;
                let value = p.attributes.value(Some(key), ValueKind::Computed).ok()?;
                Some((id.to_owned(), base, value))
            })
            .collect()
    });
    let player = m::PlayerSnapshot {
        uuid: acting.uuid,
        name: acting.name,
        dimension: acting.dimension,
        position: acting.position,
        game_mode: acting.game_mode as u8,
        health: acting.health,
        main: held(game.inventory.get(game.selected).unwrap_or(&None)),
        off: held(&game.offhand),
        attributes,
        sneaking: game.keys.contains(&super::KeyCode::ShiftLeft),
        experience: (game.experience.level, game.experience.progress, game.experience.total),
        main_stack: stack_text(game.inventory.get(game.selected).unwrap_or(&None)),
        off_stack: stack_text(&game.offhand),
    };
    WORLD.with(|w| {
        if w.borrow().as_deref() != Some(world.as_path()) {
            *w.borrow_mut() = Some(world.clone());
        }
    });
    Some((player, world, game.world.game_time.max(0) as u64))
}

/// A stack whole, as vanilla saves an item (SNBT: id, count, components); empty for nothing.
fn stack_text(stack: &Option<ItemStack>) -> String {
    stack
        .as_ref()
        .map(|s| to_snbt(&item_to_nbt(s)))
        .unwrap_or_default()
}

/// A stack a mod's code gave back (SNBT); None for nothing or for what isn't one, logged.
fn stack_from_text(text: &str) -> Option<ItemStack> {
    match parse_snbt(text) {
        Ok((tag, _)) => item_from_nbt(&tag),
        Err(_) => {
            log::warn!("Kiln: a mod gave back {text}, which isn't an item");
            None
        }
    }
}

/// A block state as Kiln's Java reads it: `mymod:lamp[lit=true]`.
fn state_text(registry: &BlockRegistry, id: BlockStateId) -> String {
    let Some(state) = registry.state(id) else {
        return "minecraft:air".to_owned();
    };
    if state.properties.is_empty() {
        return state.name.clone();
    }
    let properties: Vec<String> = state
        .properties
        .iter()
        .map(|(k, v)| format!("{k}={v}"))
        .collect();
    format!("{}[{}]", state.name, properties.join(","))
}

/// The state `text` names, where the block is `current` now: properties it leaves out keep the current block's
/// when it is the same block, else that block's defaults. A property Horizon's block hasn't is left out: a block Kiln
/// carried (D-055) has only those its models name, and a mod's block may declare more (Tech Reborn's solar panels
/// face a way no model shows). None for a block or value Horizon doesn't have.
fn resolve_state(
    registry: &BlockRegistry,
    current: BlockStateId,
    text: &str,
) -> Option<BlockStateId> {
    let (name, properties) = match text.split_once('[') {
        Some((name, rest)) => (name, rest.strip_suffix(']')?),
        None => (text, ""),
    };
    let base = registry
        .state(current)
        .filter(|s| s.name == name)
        .map(|_| current)
        .or_else(|| registry.default_state(name))?;
    let mut state = registry.state(base)?.clone();
    for pair in properties.split(',').filter(|p| !p.is_empty()) {
        let (key, value) = pair.split_once('=')?;
        if let Some(have) = state.properties.get_mut(key) {
            *have = value.to_owned();
        }
    }
    registry.id(&state)
}

/// What Kiln's Java asks of the world during a call (`block <x> <y> <z>`), answered from `block`, the world's block
/// at a position.
fn answer<'w>(
    registry: &BlockRegistry,
    block: impl Fn([i32; 3]) -> BlockStateId,
    entity: impl Fn([i32; 3]) -> Option<&'w ModBlockEntity>,
    slots: impl Fn([i32; 3]) -> Option<&'w [Option<ItemStack>]>,
    loaded: impl Fn(i32, i32) -> bool,
    what: &[String],
) -> String {
    let at = |x: &String, y: &String, z: &String| Some([x.parse().ok()?, y.parse().ok()?, z.parse().ok()?]);
    match what {
        // Whether the chunk with block x and z is loaded (`LevelReader.hasChunk`).
        [kind, x, z] if kind == "loaded" => match (x.parse(), z.parse()) {
            (Ok(x), Ok(z)) => loaded(x, z).to_string(),
            _ => String::new(),
        },
        [kind, x, y, z] if kind == "block" => at(x, y, z)
            .map(|pos| state_text(registry, block(pos)))
            .unwrap_or_default(),
        // A block entity being ticked has an empty id (it is out of the world): Java has that one already.
        [kind, x, y, z] if kind == "block_entity" => at(x, y, z)
            .and_then(entity)
            .filter(|e| !e.id.is_empty())
            .map(|e| format!("{} {}", e.id, m::nbt_field(&saved(e))))
            .unwrap_or_default(),
        // The game's own container (D-144): how many slots it has, and them as vanilla saves them (`{Items:[...]}`).
        [kind, x, y, z] if kind == "container" => at(x, y, z)
            .and_then(slots)
            .map(|items| {
                let mut nbt = Compound::new();
                nbt.insert("Items".into(), encode_items(items));
                format!("{} {}", items.len(), to_snbt(&Tag::Compound(nbt)))
            })
            .unwrap_or_default(),
        _ => String::new(),
    }
}

/// A mod block entity's saved compound, as the network's binary NBT (D-140), with its container's slots as vanilla
/// saves them (`Items`).
fn saved(entity: &ModBlockEntity) -> Vec<u8> {
    let mut nbt = entity.nbt.clone();
    if !entity.items.is_empty() {
        nbt.insert("Items".into(), encode_items(&entity.items));
    }
    write_network(&Tag::Compound(nbt), Limits::saved()).unwrap_or_else(|e| {
        log::warn!("Kiln: a block entity's compound can't be written ({}): Java gets an empty one", e.reason);
        vec![10, 0]
    })
}

/// A compound a mod's code saved, into its block entity: `Items` into the container's `slots`, when it has some.
fn put_compound(entity: &mut ModBlockEntity, mut nbt: Compound, slots: usize) {
    if slots > 0 {
        if entity.items.len() < slots {
            entity.items.resize(slots, None);
        }
        let items = nbt.remove(&"Items".into());
        entity.items = decode_items(items.as_ref(), entity.items.len());
    }
    entity.nbt = nbt;
}

/// How many item slots the block entity at `pos` in the level in place has (`native_block_entity_container`).
fn slots_at(game: &crate::game::Game<'_>, pos: [i32; 3]) -> usize {
    let state = game.world.block(pos[0], pos[1], pos[2]);
    game.world
        .block_entities
        .mod_container(state.0)
        .map_or(0, |c| c.slots)
}

/// The slots of the game's own container at `pos` in `world` (D-144): a chest's, a barrel's, a hopper's, a dispenser's,
/// a shulker box's, which Horizon keeps as `BlockEntity::Chest`. None for any other block entity, and for one Horizon
/// hasn't sized yet (no slots), which its own hoppers leave alone too.
fn game_slots(world: &horizon_client::world::World, [x, y, z]: [i32; 3]) -> Option<&[Option<ItemStack>]> {
    match world.block_entities.get(&(x, y, z)) {
        Some(BlockEntity::Chest(items)) if !items.is_empty() => Some(items),
        _ => None,
    }
}

/// What a mod's code moved in and out of the game's container at `pos` (`Effect::Container`, D-144): each slot it
/// changed, where the slot still holds what Java read. A slot the game changed meanwhile (a hopper, a player, between
/// a level's call and the server's next tick) keeps the game's, said in the log: so nothing is made twice or lost
/// without a word.
fn put_container(game: &mut crate::game::Game<'_>, pos: [i32; 3], data: &str) {
    let Some(mut nbt) = snbt_compound(data) else {
        return;
    };
    let key = (pos[0], pos[1], pos[2]);
    let Some(BlockEntity::Chest(items)) = game.world.block_entities.get_mut(&key) else {
        log::warn!("Kiln: a mod changed the container at {pos:?}, which is gone");
        return;
    };
    let before = decode_items(nbt.get(&"Before".into()), items.len());
    let after = decode_items(nbt.remove(&"Items".into()).as_ref(), items.len());
    let mut changed = false;
    for (slot, (was, now)) in before.into_iter().zip(after).enumerate() {
        if was == now {
            continue;
        }
        if items[slot] == was {
            items[slot] = now;
            changed = true;
        } else {
            log::warn!("Kiln: slot {slot} of the container at {pos:?} changed under a mod's code; the game's stays");
        }
    }
    if changed {
        game.container_changed(key);
    }
}

/// The mod block entity at `pos` in `world`.
fn mod_entity(world: &horizon_client::world::World, [x, y, z]: [i32; 3]) -> Option<&ModBlockEntity> {
    match world.block_entities.get(&(x, y, z)) {
        Some(BlockEntity::Mod(m)) => Some(m),
        _ => None,
    }
}

/// The integrated server's game answering Kiln's Java.
fn ask_game(game: &crate::game::Game<'_>, what: &[String]) -> String {
    if what.len() == 1 && what[0] == "players" {
        let now = acting_now(game);
        return ONLINE.with(|o| {
            let (listed, view) = &*o.borrow();
            m::players_answer(listed, now.as_ref(), *view)
        });
    }
    if what.len() == 1 && what[0] == "player_menu" {
        return ask_player_menu(game);
    }
    if what.len() == 1 && what[0] == "permission" {
        // The acting player's operator level (`ServerPlayer.permissions`, D-167).
        return game.native_acting_player().map_or(0, |p| p.permission).to_string();
    }
    if let Some(answer) = ask_tables(game, what) {
        return answer;
    }
    if let Some(answer) = ask_stat(game, what) {
        return answer;
    }
    if let Some(answer) = ask_sky(game, what) {
        return answer;
    }
    answer(
        game.pack.assets.registry,
        |[x, y, z]| game.world.block(x, y, z),
        |pos| mod_entity(&game.world, pos),
        |pos| game_slots(&game.world, pos),
        |x, z| game.world.loaded(x, z),
        what,
    )
}

/// A player's statistic, for a mod's code (`ServerStatsCounter.getValue`): `stat <uuid> <type> <key>`
/// (`minecraft:custom minecraft:play_time`), as Horizon counts it for the player the call acts for, whose state is in
/// place; empty for another player. None for other questions.
fn ask_stat(game: &crate::game::Game<'_>, what: &[String]) -> Option<String> {
    let [kind, uuid, ty, key] = what else {
        return None;
    };
    if kind != "stat" {
        return None;
    }
    let acting = game.native_acting_player().map(|p| p.uuid);
    Some(if acting.is_some() && acting == m::parse_uuid(uuid) {
        // Horizon keys a stat type without its namespace, what it counts with it.
        game.stats.get(ty.strip_prefix("minecraft:").unwrap_or(ty), key).to_string()
    } else {
        String::new()
    })
}

/// What the world's tables say (the game's data, the world's data packs, the content mods'), for a mod's code that
/// looks for a recipe or a fuel: `recipe <type> <item>`, a cooking recipe of that type (`minecraft:smelting`...) whose
/// ingredient takes the item, as `<id> <experience> <time> <result> <count> <ingredient>,...`; `fuel <item>`, as
/// `<burn ticks> <speed>` in a furnace (0 for what doesn't burn); `tag <tag> <item>`, whether an item is in an item
/// tag. None for other questions, and for a recipe the world hasn't.
fn ask_tables(game: &crate::game::Game<'_>, what: &[String]) -> Option<String> {
    let (recipes, items) = match &game.world_tables {
        Some(t) => (&t.recipes, &t.items),
        None => (game.res.recipes(), game.res.items()),
    };
    match what {
        [kind, ty, item] if kind == "recipe" => {
            // Horizon's cooking table knows the four cooking types by their full ids.
            let recipe = recipes.cook(ty, &ItemStack::new(item, 1))?;
            let id = if recipe.id.contains(':') {
                recipe.id.clone()
            } else {
                format!("minecraft:{}", recipe.id)
            };
            Some(format!(
                "{id} {} {} {} {} {}",
                recipe.experience,
                recipe.time,
                recipe.result.item,
                recipe.result.count,
                recipe.ingredient.order.join(",")
            ))
        }
        // `EnchantmentHelper.has`: whether one of the stack's enchantments has the effect component.
        [kind, component, stack] if kind == "enchantment_has" => {
            let stack = stack_from_text(stack)?;
            let has = world_stations(game)
                .enchantments
                .has_component(&stack, component);
            Some(if has { "1" } else { "0" }.to_owned())
        }
        // `EnchantmentHelper.modifyDurabilityToRepairFromXp`: the durability `xp` points repair on the stack, by
        // its enchantments' `minecraft:repair_with_xp` (mending's: two a point).
        [kind, xp, stack] if kind == "repair_from_xp" => {
            let stack = stack_from_text(stack)?;
            let xp: f32 = xp.parse().ok()?;
            let ctx = horizon_items::enchant::EffectContext {
                items: Some(items),
                ..Default::default()
            };
            let mut random = horizon_core::random::LegacyRandom::new(0);
            let repaired = world_stations(game).enchantments.modify_value(
                Some(&stack),
                "minecraft:repair_with_xp",
                &ctx,
                &mut random,
                xp,
            );
            Some((repaired.max(0.0) as i64).to_string())
        }
        // A game item's own: its durability and how many stack (`max_damage max_stack_size`).
        [kind, item] if kind == "item" => items
            .get(item.as_str())
            .map(|i| format!("{} {}", i.max_damage, i.max_stack)),
        // `tag <tag> <item>`: whether the item is in the item tag, by the server's merged tags.
        [kind, tag, item] if kind == "tag" => game
            .native_server_item_in_tag(&ItemStack::new(item, 1), tag)
            .ok()
            .map(|in_tag| in_tag.to_string()),
        // `block_tag <tag> <block>`: whether the block is in the block tag, by the world's tags.
        [kind, tag, block] if kind == "block_tag" => {
            Some(items.block_in_tag(tag.trim_start_matches('#'), block).to_string())
        }
        [kind, item] if kind == "fuel" => {
            let stack = ItemStack::new(item, 1);
            let providers = game.res.providers();
            Some(format!(
                "{} {}",
                providers.burn_time(items, &stack, false),
                providers.speed(items, &stack, false)
            ))
        }
        _ => None,
    }
}

/// What the sky over the level in place says, for a mod's code that reads it (a solar panel): `sky <x> <y> <z>`,
/// whether the sky is seen from there (`Level.canSeeSky`: full sky light, none in the Nether); `weather`, how much it
/// rains and thunders (`<rain> <thunder>`, 0 to 1, as `getRainLevel` and `getThunderLevel`); `sun_angle <x> <y> <z>`,
/// the sun's angle in degrees from the dimension's environment at this time of day (0 overhead). None for other
/// questions.
fn ask_sky(game: &crate::game::Game<'_>, what: &[String]) -> Option<String> {
    let dimension = game.dim.id();
    match what {
        [kind, x, y, z] if kind == "sky" => {
            let [x, y, z] = [x.parse().ok()?, y.parse().ok()?, z.parse().ok()?];
            let seen = dimension != "minecraft:the_nether" && game.world.light(x, y, z) >> 4 == 15;
            Some(seen.to_string())
        }
        [kind] if kind == "weather" => {
            let (rain, thunder) = game.world.weather.levels(1.0);
            Some(format!("{rain} {thunder}"))
        }
        [kind, _, _, _] if kind == "sun_angle" => {
            let environment = match dimension {
                "minecraft:the_nether" => game.res.nether_environment(),
                "minecraft:the_end" => game.res.end_environment(),
                _ => game.res.environment(),
            };
            Some(environment.sample(game.world.day_time, None, None).sun_angle.to_string())
        }
        _ => None,
    }
}

/// The world's enchantments, villager trades and other station data (its data packs' over the game's).
fn world_stations<'a>(game: &'a crate::game::Game<'_>) -> &'a horizon_client::stations::StationData {
    match &game.world_tables {
        Some(t) => &t.stations,
        None => game.res.stations(),
    }
}

/// A server level answering Kiln's Java, from a level's hook (a random tick).
fn ask_level(level: &crate::game::GameLevel<'_>, what: &[String]) -> String {
    let at = |x: &String, y: &String, z: &String| -> Option<BlockPos> {
        Some(BlockPos::new(x.parse().ok()?, y.parse().ok()?, z.parse().ok()?))
    };
    // The sky as `ask_sky` answers it, from what a level's hook knows: whether it rains and thunders, not how much.
    match what {
        [kind, x, y, z] if kind == "sky" => {
            return at(x, y, z)
                .map(|pos| (level.dimension() != LevelDimension::Nether && level.sky_light(pos) == 15).to_string())
                .unwrap_or_default();
        }
        [kind] if kind == "weather" => {
            let level_of = |on: bool| if on { 1.0 } else { 0.0 };
            return format!("{} {}", level_of(level.is_raining()), level_of(level.is_thundering()));
        }
        [kind, x, y, z] if kind == "sun_angle" => {
            return at(x, y, z).map(|pos| level.sun_angle(pos).to_string()).unwrap_or_default();
        }
        _ => {}
    }
    answer(
        level.registry,
        |[x, y, z]| level.block_state(BlockPos::new(x, y, z)),
        |pos| mod_entity(level.world, pos),
        |pos| game_slots(level.world, pos),
        |x, z| level.world.loaded(x, z),
        what,
    )
}

/// A face as a step (`[0, 1, 0]`) and as Kiln's Java names it (`up`).
fn face_name(face: [i32; 3]) -> &'static str {
    match face {
        [0, -1, 0] => "down",
        [0, 0, -1] => "north",
        [0, 0, 1] => "south",
        [-1, 0, 0] => "west",
        [1, 0, 0] => "east",
        _ => "up",
    }
}

/// A block a mod might own: Horizon's own blocks are in the `minecraft` namespace, and are skipped without a lock.
fn modded(registry: &BlockRegistry, state: BlockStateId) -> Option<&str> {
    let name = registry.state(state)?.name.as_str();
    (!name.starts_with("minecraft:")).then_some(name)
}

/// The integrated server: a player right clicks the block at `pos` (`native_right_click_block`, before the block's own
/// use and the item's). When a Java mod hears every block's use (Fabric's `UseBlockCallback`), or it is a Java mod's
/// block with a use of its own, Kiln asks Java (the event, then the block's `useItemOn` and `useWithoutItem`), does
/// what the mods' code did, and takes the click unless they passed.
pub(super) fn right_click_block(
    game: &mut crate::game::Game<'_>,
    pos: [i32; 3],
    face: [i32; 3],
    canceled: &mut bool,
) {
    let registry = game.pack.assets.registry;
    let state = game.world.block(pos[0], pos[1], pos[2]);
    if *canceled
        || !(modded(registry, state).is_some_and(|id| m::block_does(id, "use")) || m::hears("use_block"))
    {
        return;
    }
    let text = state_text(registry, state);
    let Some((player, world, game_time)) = acting(game) else {
        return;
    };
    let hand = match game.hand {
        Hand::Main => m::Hand::Main,
        Hand::Off => m::Hand::Off,
    };
    let face = face_name(face);
    let Some(outcome) = m::block_used(
        &world,
        game_time,
        pos,
        &text,
        face,
        hand,
        &player,
        &mut |what| ask_game(game, what),
    ) else {
        return;
    };
    // The mod's code opened its menu (`Player.openMenu`): the game opens it, in the grid that holds the block's
    // container, laid out as the mod's code laid it out (`screen`). The first time, the containers' table is built
    // again with that menu. A block Kiln carried takes the spawner's behaviour, which opens nothing: the menu is
    // opened here, as a container block's use does (`open_menu`), and handed to the player who clicked.
    let block = modded(registry, state).map(str::to_owned);
    let (menus, effects): (Vec<_>, Vec<_>) = outcome
        .effects
        .into_iter()
        .partition(|effect| matches!(effect, m::Effect::Menu(_)));
    let outcome = m::Outcome { effects, ..outcome };
    let opened = !menus.is_empty();
    for menu in menus {
        if let (m::Effect::Menu(layout), Some(id)) = (menu, block.as_deref())
            && m::set_block_menu(id, layout)
        {
            let containers = game.native_containers();
            game.world.block_entities.set_mod_containers(containers);
        }
    }
    if opened && game.mod_menu_at((pos[0], pos[1], pos[2])).is_some() {
        game.open_menu(pos);
        *canceled = true;
        CLICK_TAKEN.with(|t| t.borrow_mut().insert(player.uuid, game_time));
        game.swing_arm();
        apply_all(game, &player, outcome);
        return;
    }
    if outcome.result.takes_use() {
        *canceled = true;
        CLICK_TAKEN.with(|t| t.borrow_mut().insert(player.uuid, game_time));
        if matches!(
            outcome.result,
            m::UseResult::Success | m::UseResult::SuccessServer
        ) {
            game.swing_arm();
        }
    }
    apply_all(game, &player, outcome);
}

/// The integrated server: a block ticks at random (`native_random_tick`). When it is a Java mod's block with a random
/// tick of its own, Kiln runs it, does what it set, and the block's template's own random tick doesn't run.
pub(super) fn random_tick(
    level: &mut crate::game::GameLevel<'_>,
    ticks: &mut Ticks,
    dimension: LevelDimension,
    pos: BlockPos,
    state: BlockStateId,
    handled: &mut bool,
) {
    if *handled || !modded(level.registry, state).is_some_and(|id| m::block_does(id, "random")) {
        return;
    }
    let Some(world) = WORLD.with(|w| w.borrow().clone()) else {
        return;
    };
    let dimension = dimension_name(dimension);
    let text = state_text(level.registry, state);
    let game_time = level.game_time().max(0) as u64;
    let outcome = {
        let level = &*level;
        m::random_tick(
            &world,
            game_time,
            dimension,
            [pos.x, pos.y, pos.z],
            &text,
            &mut |what| ask_level(level, what),
        )
    };
    if let Some(outcome) = outcome {
        *handled = true;
        apply_in_level(level, ticks, outcome);
    }
}

/// What a mod's code did, from a level's hook that may change it (a random or scheduled tick).
fn apply_in_level(level: &mut crate::game::GameLevel<'_>, ticks: &mut Ticks, outcome: m::Outcome) {
    for effect in outcome.effects {
        match effect {
            m::Effect::Block { pos, flags, state } => {
                let at = BlockPos::new(pos[0], pos[1], pos[2]);
                match resolve_state(level.registry, level.block_state(at), &state) {
                    Some(id) => {
                        let behaviours = level.behaviours;
                        behaviours.set_block(level, ticks, at, id, flags);
                    }
                    None => log::warn!("Kiln: a mod set {state}, which Horizon doesn't have"),
                }
            }
            m::Effect::Sound {
                id,
                source,
                at,
                volume,
                pitch,
            } => {
                level.native_play_sound(&id, at, source, volume, pitch);
            }
            m::Effect::Schedule { pos, block, delay } => {
                level.native_schedule_block_tick(ticks, pos, &block, delay);
            }
            m::Effect::Destroy { pos, drop } => {
                let at = BlockPos::new(pos[0], pos[1], pos[2]);
                let behaviours = level.behaviours;
                behaviours.destroy_block(level, ticks, at, drop);
            }
            m::Effect::Drops { pos, state } => {
                let at = BlockPos::new(pos[0], pos[1], pos[2]);
                match resolve_state(level.registry, level.block_state(at), &state) {
                    Some(id) => level.drop_resources_now(at, id),
                    None => log::warn!("Kiln: a mod dropped what {state} drops, which Horizon doesn't have"),
                }
            }
            // A block entity's compound and a container's slots are the game's to change, and a payload the game's
            // to send: at the server's next tick.
            effect @ (m::Effect::BlockEntity { .. }
            | m::Effect::Container { .. }
            | m::Effect::Data { .. }
            | m::Effect::Net { .. }) => {
                pend(dimension_name(level.dimension), vec![effect])
            }
            other => log::info!("Kiln: {other:?} isn't done in a block's tick yet"),
        }
    }
}

/// The window's tick: what the mods changed of the game's state Horizon owns (D-105, kiln/java/kiln/Back.java), set in
/// Horizon as the game's own options screen or abilities would: the field of view, the render distance and the
/// subtitles (the options file keeps them when Horizon next writes it), the window's player's flying speed.
pub(super) fn apply_back(game: &mut crate::game::Game<'_>) {
    let mut settings = false;
    for (key, value) in m::take_back() {
        match key.as_str() {
            "fov" => {
                if let Ok(fov) = value.parse::<f32>() {
                    game.settings.fov = fov.clamp(30.0, 110.0);
                    settings = true;
                }
            }
            "render_distance" => {
                if let Ok(chunks) = value.parse::<i32>() {
                    game.settings.render_distance =
                        chunks.clamp(2, horizon_client::options_file::MAX_RENDER_DISTANCE);
                    settings = true;
                }
            }
            "show_subtitles" => {
                game.settings.show_subtitles = value == "1";
                settings = true;
            }
            "flying_speed" => {
                if let (Ok(speed), Some(p)) = (value.parse::<f32>(), game.player.as_mut())
                    && speed.is_finite()
                {
                    p.flying_speed = speed.clamp(0.0, 10.0);
                }
            }
            "camera" => {
                use crate::game::perspective::CameraType;
                let to = match value.as_str() {
                    "1" => CameraType::ThirdPersonBack,
                    "2" => CameraType::ThirdPersonFront,
                    _ => CameraType::FirstPerson,
                };
                // As the game's F5 (Minecraft.handleKeybinds): the post effect follows the first-person view.
                let before = game.camera_type;
                game.camera_type = to;
                if before.first_person() != to.first_person() {
                    game.check_entity_post_effect(to.first_person());
                }
            }
            // What a mod did once, in order (kiln.Back.act): a debug entry's status, the sprint, an effect removed,
            // the title, sounds stopped, the item in hand used.
            "debug" => {
                use horizon_client::debug_entries::{Status, entry};
                let (path, status) = value.split_once(' ').unwrap_or((value.as_str(), ""));
                let status = match status {
                    "alwaysOn" => Some(Status::AlwaysOn),
                    "inOverlay" => Some(Status::InOverlay),
                    "never" => Some(Status::Never),
                    _ => None,
                };
                match (entry(path), status) {
                    (Some(_), Some(status)) => game.debug_entries.set_status(path, status),
                    _ => log::info!("Kiln: a mod set the debug entry {value:?}, which the game hasn't"),
                }
            }
            "sprinting" => {
                if let Some(p) = game.player.as_mut() {
                    p.sprinting = value == "1";
                }
            }
            "remove_effect" => {
                let id = value.strip_prefix("minecraft:").unwrap_or(&value);
                game.potions.effects.list.retain(|e| e.id != id);
            }
            "title" | "subtitle" => match serde_json::from_str::<serde_json::Value>(&value) {
                Ok(component) => {
                    let text = horizon_client::component::to_text(&component, &mut |s| Text::literal(s));
                    if key == "title" {
                        game.titles.set_title(text);
                    } else {
                        game.titles.set_subtitle(text);
                    }
                }
                Err(_) => log::info!("Kiln: a mod's title didn't read: {value}"),
            },
            "title_times" => {
                let t: Vec<i32> = value.split(' ').filter_map(|n| n.parse().ok()).collect();
                if let [fade_in, stay, fade_out] = t[..] {
                    game.titles.set_times(fade_in, stay, fade_out);
                }
            }
            "clear_titles" => game.titles.clear(),
            "reset_title_times" => game.titles.reset_times(),
            "stop_sound" => {
                if value.is_empty() {
                    game.audio.stop_all();
                } else {
                    game.audio.stop_sounds(Some(&value), None);
                }
            }
            "use_item" => {
                if let Err(e) = game.use_item() {
                    log::warn!("Kiln: a mod's use of the item in hand failed: {e}");
                }
            }
            other => log::warn!("Kiln: a mod changed {other}, which Kiln can't set in Horizon"),
        }
    }
    if settings {
        game.apply_settings();
    }
}

/// A dimension as Kiln's Java names it.
fn dimension_name(dimension: LevelDimension) -> &'static str {
    match dimension {
        LevelDimension::Nether => "minecraft:the_nether",
        LevelDimension::End => "minecraft:the_end",
        LevelDimension::Overworld => "minecraft:overworld",
    }
}

/// The integrated server: a block's scheduled tick (`native_tick_block`). A Java mod's block with a tick of its own
/// runs it in place of its template's.
pub(super) fn tick_block(
    level: &mut crate::game::GameLevel<'_>,
    ticks: &mut Ticks,
    dimension: LevelDimension,
    pos: BlockPos,
    state: BlockStateId,
    handled: &mut bool,
) {
    if *handled || !modded(level.registry, state).is_some_and(|id| m::block_does(id, "tick")) {
        return;
    }
    let Some(world) = WORLD.with(|w| w.borrow().clone()) else {
        return;
    };
    let text = state_text(level.registry, state);
    let game_time = level.game_time().max(0) as u64;
    let outcome = {
        let level = &*level;
        m::scheduled_tick(
            &world,
            game_time,
            dimension_name(dimension),
            [pos.x, pos.y, pos.z],
            &text,
            &mut |what| ask_level(level, what),
        )
    };
    if let Some(outcome) = outcome {
        *handled = true;
        apply_in_level(level, ticks, outcome);
    }
}

/// The integrated server: a neighbour of a block changed (`native_neighbor_changed`, which only hears). A Java mod's
/// block hears it now; what its code did is done at the server's next tick.
pub(super) fn neighbor_changed(
    level: &crate::game::GameLevel<'_>,
    dimension: LevelDimension,
    pos: BlockPos,
    state: BlockStateId,
    moved: bool,
) {
    if !modded(level.registry, state).is_some_and(|id| m::block_does(id, "neighbor")) {
        return;
    }
    let Some(world) = WORLD.with(|w| w.borrow().clone()) else {
        return;
    };
    let text = state_text(level.registry, state);
    let dimension = dimension_name(dimension);
    let game_time = level.game_time().max(0) as u64;
    if let Some(outcome) = m::neighbor_changed(
        &world,
        game_time,
        dimension,
        [pos.x, pos.y, pos.z],
        &text,
        moved,
        &mut |what| ask_level(level, what),
    ) {
        pend(dimension, outcome.effects);
    }
}

/// The integrated server: the state a block takes where it is placed (`native_state_for_placement`). A Java mod's
/// block with its own `getStateForPlacement` answers it; null refuses the placement.
pub(super) fn state_for_placement(
    level: &crate::game::GameLevel<'_>,
    dimension: LevelDimension,
    state: BlockStateId,
    context: &PlacementContext,
    answer: &mut Option<Option<BlockStateId>>,
) {
    if answer.is_some()
        || !modded(level.registry, state).is_some_and(|id| m::block_does(id, "placement"))
    {
        return;
    }
    let Some(world) = WORLD.with(|w| w.borrow().clone()) else {
        return;
    };
    use horizon_core::geometry::Direction;
    let face = match context.face {
        Direction::Down => "down",
        Direction::Up => "up",
        Direction::North => "north",
        Direction::South => "south",
        Direction::West => "west",
        Direction::East => "east",
    };
    let placement = m::Placement {
        pos: [context.pos.x, context.pos.y, context.pos.z],
        state: state_text(level.registry, state),
        face: face.to_owned(),
        hit: [context.hit.x, context.hit.y, context.hit.z],
        clicked: [context.hit_pos.x, context.hit_pos.y, context.hit_pos.z],
        yaw: context.yaw,
        pitch: context.pitch,
        sneaking: context.sneaking,
        item: context.item.clone(),
        replacing: context.replace_clicked,
    };
    let dimension = dimension_name(dimension);
    let game_time = level.game_time().max(0) as u64;
    let Some(outcome) = m::placement(&world, game_time, dimension, &placement, &mut |what| {
        ask_level(level, what)
    }) else {
        return;
    };
    let mut others = Vec::new();
    for effect in outcome.effects {
        match effect {
            m::Effect::Placement(None) => *answer = Some(None),
            m::Effect::Placement(Some(text)) => match resolve_state(level.registry, state, &text) {
                Some(id) => *answer = Some(Some(id)),
                None => log::warn!("Kiln: a mod placed {text}, which Horizon doesn't have"),
            },
            other => others.push(other),
        }
    }
    pend(dimension, others);
}

/// Effects to do at the server's next tick.
fn pend(dimension: &str, effects: Vec<m::Effect>) {
    if effects.is_empty() {
        return;
    }
    if let Ok(mut pending) = PENDING.lock() {
        pending.extend(effects.into_iter().map(|e| (dimension.to_owned(), e)));
    }
}

/// The server's tick: the effects hooks that couldn't change the world left (`pend`).
pub(super) fn apply_pending(game: &mut crate::game::Game<'_>) {
    let pending = match PENDING.lock() {
        Ok(mut pending) if !pending.is_empty() => std::mem::take(&mut *pending),
        _ => return,
    };
    for (dimension, effect) in pending {
        match effect {
            m::Effect::Block { .. }
            | m::Effect::Drop { .. }
            | m::Effect::Destroy { .. }
            | m::Effect::Drops { .. }
            | m::Effect::Schedule { .. }
            | m::Effect::Sound { .. }
            | m::Effect::BlockEntity { .. }
            | m::Effect::Container { .. }
            | m::Effect::Data { .. }
            | m::Effect::Net { .. } => apply(game, 0, &dimension, effect),
            other => log::info!("Kiln: {other:?} has no player to go to here"),
        }
    }
}

/// The integrated server: a player placed a block (`native_place_block`). A Java mod's block with its own
/// `setPlacedBy` hears it, with the player and the stack.
pub(super) fn place_block(
    game: &mut crate::game::Game<'_>,
    stack: &ItemStack,
    pos: [i32; 3],
    state: BlockStateId,
) {
    changed_near(game, pos);
    let registry = game.pack.assets.registry;
    if !modded(registry, state).is_some_and(|id| m::block_does(id, "placed")) {
        return;
    }
    let text = state_text(registry, state);
    let Some((player, world, game_time)) = acting(game) else {
        return;
    };
    if let Some(outcome) = m::placed(
        &world,
        game_time,
        pos,
        &text,
        &stack.item,
        stack.count,
        &player,
        &mut |what| ask_game(game, what),
    ) {
        apply_all(game, &player, outcome);
    }
}

/// The integrated server: a player breaks a block (`native_destroy_block`). A Java mod's block with its own
/// `playerWillDestroy` hears it first; the block still breaks.
pub(super) fn destroy_block(
    game: &mut crate::game::Game<'_>,
    pos: [i32; 3],
    state: BlockStateId,
    canceled: &mut bool,
) {
    if !*canceled {
        changed_near(game, pos);
    }
    let registry = game.pack.assets.registry;
    if *canceled || !modded(registry, state).is_some_and(|id| m::block_does(id, "destroy")) {
        return;
    }
    let text = state_text(registry, state);
    let Some((player, world, game_time)) = acting(game) else {
        return;
    };
    if let Some(outcome) = m::destroyed(&world, game_time, pos, &text, &player, &mut |what| {
        ask_game(game, what)
    }) {
        apply_all(game, &player, outcome);
    }
}

/// A player placed or broke a block at `pos`: if a mod's block entity is within 16 blocks, Java loads it again when
/// next met (`blocks_changed`), so a multiblock machine sees the casings built around it.
fn changed_near(game: &crate::game::Game<'_>, [x, y, z]: [i32; 3]) {
    let near = game.world.block_entities.iter().any(|(&(bx, by, bz), entity)| {
        matches!(entity, BlockEntity::Mod(_))
            && (bx - x).abs() <= 16
            && (by - y).abs() <= 16
            && (bz - z).abs() <= 16
    });
    let Some(world) = WORLD.with(|w| w.borrow().clone()).filter(|_| near) else {
        return;
    };
    let game_time = game.world.game_time.max(0) as u64;
    m::blocks_changed(&world, game_time, game.dim.id(), [x, y, z]);
}

/// Horizon's step hook (`native_block_step_on`, no game at hand): a player standing on a block is kept for their tick.
pub(super) fn queue_step(
    dimension: &str,
    entity: i32,
    kind: &str,
    state: BlockStateId,
    pos: [i32; 3],
) {
    if kind != "minecraft:player" {
        return;
    }
    if let Ok(mut steps) = STEPS.lock()
        && steps.len() < 64
    {
        steps.push((dimension.to_owned(), entity, state, pos));
    }
}

/// At a player's tick (Kiln's Mixin bridge, D-102): what the mods' code did to them in a hit since their last tick,
/// then `ServerPlayer.tick` when a mod's mixin is in it (or in `Player`'s, `LivingEntity`'s or `Entity`'s), with the
/// player as they are now; and the player kept by their entity's id for the hooks that name only that.
fn bridged_tick(game: &mut crate::game::Game<'_>) {
    let Some(uuid) = game.native_acting_player().map(|a| a.uuid) else {
        return;
    };
    let mine: Vec<m::Effect> = match PLAYER_PENDING.lock() {
        Ok(mut pending) if !pending.is_empty() => {
            let (mine, others): (Vec<_>, Vec<_>) =
                std::mem::take(&mut *pending).into_iter().partition(|(p, _)| *p == uuid);
            *pending = others;
            mine.into_iter().map(|(_, e)| e).collect()
        }
        _ => Vec::new(),
    };
    let ticking = m::bridged("player_tick");
    if mine.is_empty() && !ticking && !m::bridged("living_incoming_damage") && !m::bridged("player_died") {
        return;
    }
    let Some((player, world, game_time)) = acting(game) else {
        return;
    };
    if let Some(entity) = game.player.as_ref().map(|p| p.entity_id) {
        BODIES.with(|b| {
            let mut bodies = b.borrow_mut();
            bodies.retain(|_, (p, _)| p.uuid != player.uuid);
            bodies.insert(entity, (player.clone(), game_time));
        });
    }
    PLAYERS.with(|p| p.borrow_mut().insert(player.uuid, (player.clone(), game_time)));
    if !mine.is_empty() {
        apply_all(game, &player, m::Outcome { result: m::UseResult::Pass, effects: mine });
    }
    if ticking
        && let Some(outcome) =
            m::bridge(&world, game_time, "player_tick", &[], Some(&player), &mut |what| ask_game(game, what))
    {
        apply_all(game, &player, outcome);
    }
}

/// Horizon's hit (`native_living_incoming_damage`, on the server's thread, no game at hand): `LivingEntity.hurtServer`
/// for the mods' mixins in it (Kiln's Mixin bridge, D-102). One that cancels it at its head cancels the hit; one that
/// changes the amount changes the damage. A player hit is the player as at their last tick; what the mods' code does to
/// them is done at their next, and what it does to the world at the server's next tick.
pub(super) fn incoming_damage(
    entity: i32,
    kind: &str,
    damage_type: &str,
    causing: i32,
    direct: i32,
    amount: &mut f32,
    canceled: &mut bool,
) {
    if *canceled || !m::bridged("living_incoming_damage") {
        return;
    }
    let Some(world) = WORLD.with(|w| w.borrow().clone()) else {
        return;
    };
    let body = if kind == "minecraft:player" {
        // A player not ticked yet since the mods' mixins needed it: Kiln has no player to hand them.
        let Some(body) = BODIES.with(|b| b.borrow().get(&entity).cloned()) else {
            return;
        };
        Some(body)
    } else {
        None
    };
    let game_time = body
        .as_ref()
        .map(|(_, t)| *t)
        .or_else(|| BODIES.with(|b| b.borrow().values().map(|(_, t)| *t).max()))
        .unwrap_or(0);
    let fields = [
        entity.to_string(),
        kind.to_owned(),
        damage_type.to_owned(),
        causing.to_string(),
        direct.to_string(),
        amount.to_string(),
    ];
    let player = body.as_ref().map(|(p, _)| p);
    let Some(outcome) = m::bridge(&world, game_time, "living_incoming_damage", &fields, player, &mut |_| {
        String::new()
    }) else {
        return;
    };
    let mut others = Vec::new();
    for effect in outcome.effects {
        match effect {
            m::Effect::Out { name, value } if name == "canceled" => *canceled |= value == "1",
            m::Effect::Out { name, value } if name == "amount" => {
                if let Ok(v) = value.parse::<f32>()
                    && v.is_finite()
                {
                    *amount = v.max(0.0);
                }
            }
            m::Effect::Out { .. } => {}
            effect @ (m::Effect::Block { .. }
            | m::Effect::Drop { .. }
            | m::Effect::Destroy { .. }
            | m::Effect::Drops { .. }
            | m::Effect::Schedule { .. }
            | m::Effect::Sound { .. }
            | m::Effect::BlockEntity { .. }
            | m::Effect::Container { .. }
            | m::Effect::Data { .. }
            | m::Effect::Net { .. }) => others.push(effect),
            effect => {
                if let Some((p, _)) = &body
                    && let Ok(mut pending) = PLAYER_PENDING.lock()
                    && pending.len() < 256
                {
                    pending.push((p.uuid, effect));
                }
            }
        }
    }
    let dimension = body.map_or_else(|| "minecraft:overworld".to_owned(), |(p, _)| p.dimension);
    pend(&dimension, others);
}

/// The integrated server: the acting player attacks entity `entity` of `kind` (`native_attack_entity`):
/// `Player.attack`, for the mods' mixins in it (Kiln's Mixin bridge, D-106); one that cancels it cancels the blow.
pub(super) fn attack_entity(game: &mut crate::game::Game<'_>, entity: i32, kind: &str, canceled: &mut bool) {
    if *canceled || !m::bridged("attack_entity") {
        return;
    }
    let Some((player, world, game_time)) = acting(game) else {
        return;
    };
    let fields = [entity.to_string(), kind.to_owned()];
    if let Some(outcome) =
        m::bridge(&world, game_time, "attack_entity", &fields, Some(&player), &mut |what| ask_game(game, what))
    {
        *canceled |= outcome.effects.iter().any(|e| matches!(e, m::Effect::Out { name, value } if name == "canceled" && value == "1"));
        apply_all(game, &player, outcome);
    }
}

/// The integrated server: the acting player's blow killed mob `entity` of `kind` (`native_mob_killed`):
/// `LivingEntity.die`, for the mods' mixins in it (D-106), the player's attack its source.
pub(super) fn mob_killed(game: &mut crate::game::Game<'_>, entity: u32, kind: &str) {
    if !m::bridged("mob_killed") {
        return;
    }
    let Some((player, world, game_time)) = acting(game) else {
        return;
    };
    let fields = [m::uuid_text(player.uuid), entity.to_string(), kind.to_owned()];
    if let Some(outcome) =
        m::bridge(&world, game_time, "mob_killed", &fields, Some(&player), &mut |what| ask_game(game, what))
    {
        apply_all(game, &player, outcome);
    }
}

/// The integrated server: `player` died (`native_player_died`), `cause` the death message's key: `ServerPlayer.die`,
/// for the mods' mixins in it (D-106), with the player as at their last tick; what the mods' code does to them is done
/// at their next tick (after they respawn), what it does to the world at the server's next.
pub(super) fn player_died(game: &mut crate::game::Game<'_>, uuid: u128, cause: Option<&str>) {
    if !m::bridged("player_died") {
        return;
    }
    let Some((player, game_time)) = PLAYERS.with(|p| p.borrow().get(&uuid).cloned()) else {
        return;
    };
    let Some(world) = WORLD.with(|w| w.borrow().clone()) else {
        return;
    };
    let fields = [m::uuid_text(uuid), cause.unwrap_or_default().to_owned()];
    let Some(outcome) =
        m::bridge(&world, game_time, "player_died", &fields, Some(&player), &mut |what| ask_game(game, what))
    else {
        return;
    };
    let mut others = Vec::new();
    for effect in outcome.effects {
        match effect {
            m::Effect::Out { .. } => {}
            effect @ (m::Effect::Block { .. }
            | m::Effect::Drop { .. }
            | m::Effect::Destroy { .. }
            | m::Effect::Drops { .. }
            | m::Effect::Schedule { .. }
            | m::Effect::Sound { .. }
            | m::Effect::BlockEntity { .. }
            | m::Effect::Container { .. }
            | m::Effect::Data { .. }
            | m::Effect::Net { .. }) => others.push(effect),
            effect => {
                if let Ok(mut pending) = PLAYER_PENDING.lock()
                    && pending.len() < 256
                {
                    pending.push((uuid, effect));
                }
            }
        }
    }
    pend(&player.dimension, others);
}

/// At a player's tick: the mod blocks they stood on since the last one, once each (`stepOn`).
fn steps(game: &mut crate::game::Game<'_>) {
    let Some(entity) = game.player.as_ref().map(|p| p.entity_id) else {
        return;
    };
    let mine: Vec<(BlockStateId, [i32; 3])> = match STEPS.lock() {
        Ok(mut steps) => {
            let (mine, others): (Vec<_>, Vec<_>) = std::mem::take(&mut *steps)
                .into_iter()
                .partition(|s| s.1 == entity);
            *steps = others;
            let mut mine: Vec<(BlockStateId, [i32; 3])> =
                mine.into_iter().map(|s| (s.2, s.3)).collect();
            mine.dedup_by_key(|s| s.1);
            mine
        }
        Err(_) => return,
    };
    let registry = game.pack.assets.registry;
    for (state, pos) in mine {
        if !modded(registry, state).is_some_and(|id| m::block_does(id, "step")) {
            continue;
        }
        let text = state_text(registry, state);
        let Some((player, world, game_time)) = acting(game) else {
            return;
        };
        if let Some(outcome) = m::stepped_on(&world, game_time, pos, &text, &player, &mut |what| {
            ask_game(game, what)
        }) {
            apply_all(game, &player, outcome);
        }
    }
}

/// Does each effect, in order, to the acting player `player`.
fn apply_all(game: &mut crate::game::Game<'_>, player: &m::PlayerSnapshot, outcome: m::Outcome) {
    for effect in outcome.effects {
        apply(game, player.uuid, &player.dimension, effect);
    }
}

/// The integrated server: a player uses `stack` with `game.hand` (`native_use_item`). When a Java mod gave the item a
/// use of its own, or hears every use (Fabric's `UseItemCallback`), Kiln asks it what using it does, does that, and
/// takes the use unless the mod passed.
pub(super) fn use_item(game: &mut crate::game::Game<'_>, stack: &ItemStack, claimed: &mut bool) {
    // The use the window sends after a click a mod's block took, this tick or the last: the block's.
    if !*claimed
        && CLICK_TAKEN.with(|t| !t.borrow().is_empty())
        && let Some((player, _, game_time)) = acting(game)
        && let Some(taken) = CLICK_TAKEN.with(|t| t.borrow_mut().remove(&player.uuid))
    {
        if game_time.saturating_sub(taken) <= 1 {
            *claimed = true;
            return;
        }
    }
    // A mod's item with a use of its own, or any item while a mod hears Fabric's UseItemCallback.
    if *claimed || !(m::has_use(&stack.item) || m::hears("use_item")) {
        return;
    }
    let Some((player, world, game_time)) = acting(game) else {
        return;
    };
    let hand = match game.hand {
        Hand::Main => m::Hand::Main,
        Hand::Off => m::Hand::Off,
    };
    let Some(outcome) = m::use_item(&world, game_time, &stack.item, hand, &player, &mut |what| {
        ask_game(game, what)
    }) else {
        return;
    };
    *claimed = outcome.result.takes_use();
    apply_all(game, &player, outcome);
}

/// The integrated server: a living player's tick ends (`native_player_tick`). Vanilla adds a player to a level when
/// they join the game or change dimension, and Fabric's ENTITY_LOAD hears it; Kiln tells the Java mods the first
/// tick a player is in a level it hasn't told them of. Java started late (it starts with the window's first tick in
/// a world) is told at the first tick after it hears.
pub(super) fn player_tick(game: &mut crate::game::Game<'_>) {
    // The world's folder, for hooks that don't know it (a block's tick, a placement).
    if let Some(world) = game.native_world_dir()
        && WORLD.with(|w| w.borrow().as_deref() != Some(world.as_path()))
    {
        if let Some(name) = world.file_name() {
            // For the window's tick too, whose game has no world folder of its own (D-167).
            *LEVEL_ID.lock().unwrap_or_else(|e| e.into_inner()) = name.to_string_lossy().into_owned();
        }
        WORLD.with(|w| *w.borrow_mut() = Some(world));
    }
    keep_attributes(game);
    steps(game);
    connection(game);
    payloads(game);
    bridged_tick(game);
    let (join, respawn) = (m::hears("join"), m::hears("respawn"));
    if !join && !respawn {
        return;
    }
    let Some((player, world, game_time)) = acting(game) else {
        return;
    };
    if respawn {
        LAST.with(|l| l.borrow_mut().insert(player.uuid, player.clone()));
    }
    let new_level = LOADED.with(|l| l.borrow().get(&player.uuid) != Some(&player.dimension));
    if !join || !new_level {
        return;
    }
    LOADED.with(|l| l.borrow_mut().insert(player.uuid, player.dimension.clone()));
    if let Some(outcome) =
        m::player_joined(&world, game_time, &player, &mut |what| ask_game(game, what))
    {
        apply_all(game, &player, outcome);
    }
}

/// A player's attributes as Java mods last set them, on a new body. Vanilla keeps a player's attribute base values on
/// their entity, through portals and respawns (Heart Crystals counts on it); Horizon gives a player a new body when it
/// moves them to another level or to the world spawn after a respawn, at the attributes' defaults. So each tick, an
/// attribute back at the base it had before the mods' first change gets the mods' base again. A change by anything
/// else (`/attribute`) to another value stays.
fn keep_attributes(game: &mut crate::game::Game<'_>) {
    let Some(uuid) = game.native_acting_player().map(|a| a.uuid) else {
        return;
    };
    let Some(kept) = KEPT.with(|k| k.borrow().get(&uuid).cloned()) else {
        return;
    };
    let Some(p) = game.player.as_mut() else {
        return;
    };
    for (id, (base, before)) in kept {
        let Some(key) = p.attributes.key(&id) else {
            continue;
        };
        let now = p.attributes.value(Some(key.clone()), ValueKind::Base).ok();
        if now == Some(before)
            && before != base
            && let Ok(Some(handle)) = p.attributes.get_instance(Some(key))
        {
            let _ = p.attributes.edit(&handle, |value, observer| {
                value.set_base_value(base, observer)
            });
        }
    }
}

/// The integrated server: a player respawned after dying (`native_player_respawned`, their state swapped in): the
/// Java mods hear COPY_FROM with the player as they were at their last tick, ENTITY_LOAD and AFTER_RESPAWN.
pub(super) fn player_respawned(game: &mut crate::game::Game<'_>) {
    if !m::hears("respawn") {
        return;
    }
    let Some((player, world, game_time)) = acting(game) else {
        return;
    };
    let mut old = LAST
        .with(|l| l.borrow_mut().remove(&player.uuid))
        .unwrap_or_else(|| player.clone());
    old.health = 0.0;
    LOADED.with(|l| l.borrow_mut().insert(player.uuid, player.dimension.clone()));
    if let Some(outcome) =
        m::player_respawned(&world, game_time, false, &old, &player, &mut |what| {
            ask_game(game, what)
        })
    {
        apply_all(game, &player, outcome);
    }
}

/// At a player's tick: the first one the Java mods hear of is their connection made and ready (Fabric's INIT and
/// JOIN, which vanilla fires as the player is placed; Horizon tells native mods of a new player where none's state is
/// swapped in, so Kiln waits for their first tick, with the player whole). Java started late is told at the first tick
/// after it hears. Each tick keeps where the player is, for DISCONNECT.
fn connection(game: &mut crate::game::Game<'_>) {
    let (connect, disconnect) = (m::hears("connect"), m::hears("disconnect"));
    if !connect && !disconnect {
        return;
    }
    let Some(now) = acting_now(game) else {
        return;
    };
    let known = CONNECTED.with(|c| match c.borrow_mut().get_mut(&now.uuid) {
        Some(last) => {
            last.dimension = now.dimension.clone();
            last.position = now.position;
            last.game_mode = now.game_mode;
            last.health = now.health;
            true
        }
        None => false,
    });
    if known {
        return;
    }
    let Some((player, world, game_time)) = acting(game) else {
        return;
    };
    CONNECTED.with(|c| c.borrow_mut().insert(player.uuid, player.clone()));
    if connect
        && let Some(outcome) =
            m::player_connected(&world, game_time, &player, &mut |what| ask_game(game, what))
    {
        apply_all(game, &player, outcome);
    }
}

/// The integrated server: a player left; the Java mods hear their connection closed (Fabric's DISCONNECT), with them
/// as they were at their last tick, still on the server's list as in vanilla. What their code does to the world and
/// sends the other players is done at the server's next tick. When they come back, the Java mods hear them come into a
/// level again.
pub(super) fn player_logged_out(game: &mut crate::game::Game<'_>, uuid: u128) {
    let left = CONNECTED.with(|c| c.borrow_mut().remove(&uuid));
    if let Some(player) = left
        && m::hears("disconnect")
        && let Some(world) = WORLD.with(|w| w.borrow().clone())
    {
        let game_time = game.world.game_time.max(0) as u64;
        let outcome = m::player_disconnected(&world, game_time, &player, &mut |what| {
            if what.len() == 1 && what[0] == "players" {
                ONLINE.with(|o| {
                    let (listed, view) = &*o.borrow();
                    m::players_answer(listed, Some(&player), *view)
                })
            } else {
                ask_game(game, what)
            }
        });
        if let Some(outcome) = outcome {
            // Nothing reaches the one who left.
            let effects = outcome
                .effects
                .into_iter()
                .filter(|e| !matches!(e, m::Effect::Net { player, .. } if *player == uuid))
                .collect();
            pend(&player.dimension, effects);
        }
    }
    ONLINE.with(|o| o.borrow_mut().0.retain(|p| p.uuid != uuid));
    if let Ok(mut waiting) = NET_IN.lock() {
        waiting.retain(|(player, _)| *player != uuid);
    }
    LOADED.with(|l| l.borrow_mut().remove(&uuid));
    LAST.with(|l| l.borrow_mut().remove(&uuid));
    KEPT.with(|k| k.borrow_mut().remove(&uuid));
}

/// Does what a Java mod's code did to the acting player and around them, as the game's own code would.
fn apply(game: &mut crate::game::Game<'_>, uuid: u128, dimension: &str, effect: m::Effect) {
    match effect {
        // As `/attribute ... base set`; kept on the player's next bodies (`keep_attributes`).
        m::Effect::Attribute { id, base } => {
            if let Some(p) = game.player.as_mut()
                && let Some(key) = p.attributes.key(&id)
                && let Ok(Some(handle)) = p.attributes.get_instance(Some(key.clone()))
            {
                let before = p.attributes.value(Some(key), ValueKind::Base).ok();
                let _ = p.attributes.edit(&handle, |value, observer| {
                    value.set_base_value(base, observer)
                });
                KEPT.with(|k| {
                    let mut k = k.borrow_mut();
                    let kept = k.entry(uuid).or_default();
                    match kept.get_mut(&id) {
                        Some(entry) => entry.0 = base,
                        None => {
                            kept.insert(id, (base, before.unwrap_or(base)));
                        }
                    }
                });
            }
        }
        m::Effect::Health(health) => {
            if let Some(p) = game.player.as_mut() {
                let _ = p.set_health(health, false);
            }
        }
        m::Effect::Hand { hand, item, count } => {
            let selected = game.selected;
            let slot = match hand {
                m::Hand::Main => game.inventory.get_mut(selected),
                m::Hand::Off => Some(&mut game.offhand),
            };
            if let Some(slot) = slot {
                *slot = match slot.take() {
                    _ if count == 0 => None,
                    Some(mut s) if *s.item == *item => {
                        s.count = count;
                        Some(s)
                    }
                    _ => Some(ItemStack::new(&item, count)),
                };
            }
        }
        m::Effect::PlayerSlots { data } => put_player_slots(game, &data),
        m::Effect::Held { hand, stack } => {
            let stack = stack_from_text(&stack);
            match hand {
                m::Hand::Main => {
                    let selected = game.selected;
                    if let Some(slot) = game.inventory.get_mut(selected) {
                        *slot = stack;
                    }
                }
                m::Hand::Off => game.offhand = stack,
            }
        }
        m::Effect::Experience {
            level,
            progress,
            total,
        } => {
            game.experience.level = level.max(0);
            game.experience.progress = progress.clamp(0.0, 1.0);
            game.experience.total = total.max(0);
        }
        m::Effect::Sound {
            id,
            source,
            at,
            volume,
            pitch,
        } => {
            let dimension = match dimension {
                "minecraft:the_nether" => "minecraft:the_nether",
                "minecraft:the_end" => "minecraft:the_end",
                _ => "minecraft:overworld",
            };
            game.creatures
                .lan_sounds
                .push(super::creatures::NativeSound {
                    dimension,
                    sound: id,
                    at,
                    source: source as i32,
                    volume,
                    pitch,
                });
        }
        m::Effect::Overlay(component) => {
            game.native_action_bar(uuid, Text::literal("").with_component(component));
        }
        m::Effect::Tell(component) => {
            game.native_tell(uuid, Text::literal("").with_component(component));
        }
        m::Effect::Block { pos, flags, state } => {
            let registry = game.pack.assets.registry;
            let current = game.world.block(pos[0], pos[1], pos[2]);
            match resolve_state(registry, current, &state) {
                Some(id) => {
                    game.native_set_block(dimension, pos, id, flags);
                }
                None => log::warn!("Kiln: a mod set {state}, which Horizon doesn't have"),
            }
        }
        m::Effect::Drop { pos, item, count } => {
            game.pop_resource(pos, ItemStack::new(&item, count))
        }
        // `Level.destroyBlock`: the game breaks the block as its own code does (its drops when asked, sound and
        // particles, then the fluid there), through the behaviour library, as `/setblock ... destroy` does.
        m::Effect::Destroy { pos, drop } => {
            if dimension == game.dim.id() {
                let at = BlockPos::new(pos[0], pos[1], pos[2]);
                game.with_level(|b, level, ticks| b.destroy_block(level, ticks, at, drop));
                if let Err(e) = game.process_level_events() {
                    log::warn!("Kiln: a mod broke the block at {pos:?}: {e}");
                }
            }
        }
        // `Block.dropResources`: what the state drops by its loot table, popped at once.
        m::Effect::Drops { pos, state } => {
            if dimension == game.dim.id() {
                let registry = game.pack.assets.registry;
                let current = game.world.block(pos[0], pos[1], pos[2]);
                match resolve_state(registry, current, &state) {
                    Some(id) => {
                        let at = BlockPos::new(pos[0], pos[1], pos[2]);
                        game.with_level(|_, level, _| level.drop_resources_now(at, id));
                    }
                    None => log::warn!("Kiln: a mod dropped what {state} drops, which Horizon doesn't have"),
                }
            }
        }
        m::Effect::Schedule { pos, block, delay } => {
            game.with_level(|_, level, ticks| {
                level.native_schedule_block_tick(ticks, pos, &block, delay)
            });
        }
        m::Effect::BlockEntity { pos, data } => {
            let kept = if dimension == game.dim.id()
                && let Some(nbt) = compound(&data)
            {
                let slots = slots_at(game, pos);
                game.native_with_mod_block_entity(pos, |entity| {
                    put_compound(entity, nbt, slots);
                    saved(entity)
                })
            } else {
                None
            };
            // What the game keeps now, for the block entity's next tick to send only its token (D-140); one the game
            // didn't change gets its compound sent whole, as Java's object is ahead of it.
            match kept {
                Some(saved) => m::block_entity_kept(dimension, pos, &saved),
                None => m::block_entity_forget(dimension, pos),
            }
        }
        m::Effect::Container { pos, data } => {
            if dimension == game.dim.id() {
                put_container(game, pos, &data);
            }
        }
        m::Effect::Data { pos, values } => {
            if dimension == game.dim.id() {
                game.native_with_mod_block_entity(pos, |entity| entity.data = values);
            }
        }
        // A server command's answer: in the player's chat after the command, as its feedback is.
        m::Effect::Feedback(component) => {
            game.native_feedback(Text::literal("").with_component(component));
        }
        // A menu is opened where the click is answered (`right_click_block`); a bridge hook's outs where it is; the
        // commands' usages go to Horizon's tree (`register_commands`).
        m::Effect::Placement(_) | m::Effect::Menu(_) | m::Effect::Out { .. } | m::Effect::Usage { .. } => {}
        // To whichever player the mod named; it leaves at the end of the server's tick.
        m::Effect::Net { player, data } => {
            if let Err(why) = game.native_net_send_to_player(player, m::NET_CHANNEL, &data) {
                log::warn!("Kiln: a mod's payload for {} wasn't sent: {why}", m::uuid_text(player));
            }
        }
    }
}

/// The integrated server, any player's message on a channel for mods (`native_net_from_client`): one on Kiln's waits
/// for its sender's next tick, when a Java mod receives payloads.
pub(super) fn net_from_client(player: u128, channel: &str, data: &[u8]) {
    if channel != m::NET_CHANNEL || !m::hears("payload") {
        return;
    }
    if let Ok(mut waiting) = NET_IN.lock() {
        if waiting.len() >= NET_IN_MAX {
            log::warn!("Kiln: a payload from {} dropped: {NET_IN_MAX} wait already", m::uuid_text(player));
            return;
        }
        waiting.push((player, data.to_vec()));
    }
}

/// At a player's tick: what the mods did with their earlier payloads whose answers came late, then their messages on
/// Kiln's channel since the last tick, in order, each a call to the mods' server receivers with the player as they are
/// now.
fn payloads(game: &mut crate::game::Game<'_>) {
    let Some(uuid) = game.native_acting_player().map(|a| a.uuid) else {
        return;
    };
    for outcome in m::late_payloads(uuid) {
        if let Some((player, _, _)) = acting(game) {
            apply_all(game, &player, outcome);
        }
    }
    let mine: Vec<Vec<u8>> = match NET_IN.lock() {
        Ok(mut waiting) if !waiting.is_empty() => {
            let (mine, others): (Vec<_>, Vec<_>) =
                std::mem::take(&mut *waiting).into_iter().partition(|(p, _)| *p == uuid);
            *waiting = others;
            mine.into_iter().map(|(_, data)| data).collect()
        }
        _ => return,
    };
    for data in mine {
        let Some((player, world, game_time)) = acting(game) else {
            return;
        };
        if let Some(outcome) = m::from_client(&world, game_time, &data, &player, &mut |what| ask_game(game, what)) {
            apply_all(game, &player, outcome);
        }
    }
}

/// The window, a message from its server on a channel for mods (`native_net_from_server`): one on Kiln's goes to the
/// Java mods' client receivers.
pub(super) fn net_from_server(channel: &str, data: &[u8]) {
    if channel == m::NET_CHANNEL {
        m::from_server(data);
    }
}

/// A block entity's compound as its mod's code saved it (the network's binary NBT); None, logged, for anything else.
fn compound(data: &[u8]) -> Option<Compound> {
    match read_network(data, Limits::saved()) {
        Ok((Tag::Compound(c), _)) => Some(c),
        _ => {
            log::warn!("Kiln: a block entity saved {} bytes that aren't a compound", data.len());
            None
        }
    }
}

/// A container's compound as a mod's code left it (`Effect::Container`: SNBT, D-144); None, logged, for anything else.
fn snbt_compound(data: &str) -> Option<Compound> {
    match parse_snbt(data) {
        Ok((Tag::Compound(c), _)) => Some(c),
        _ => {
            log::warn!("Kiln: a mod left the container's slots as {data}, which isn't a compound");
            None
        }
    }
}

/// Either game: the block entity a block gets (`native_block_entity`). A Java mod's block gets the type its code
/// makes, with an empty compound until that code saves one.
pub(super) fn block_entity(block: &str, entity: &mut Option<(String, Compound)>) {
    if entity.is_some() || block.starts_with("minecraft:") {
        return;
    }
    if let Some(id) = m::block_entity_type(block) {
        *entity = Some((id, Compound::new()));
    }
}

/// The item slots of a block's block entity (`native_block_entity_container`): a Java mod's, when it is a container.
pub(super) fn block_entity_container(block: &str, container: &mut Option<ModContainer>) {
    if container.is_some() || block.starts_with("minecraft:") {
        return;
    }
    if let Some(slots) = m::block_slots(block) {
        // Once a player opened the block's menu, its container opens in the smallest grid menu that holds it.
        let grid = m::block_menu(block).and_then(|_| m::grid_menu(slots.count));
        *container = Some(ModContainer {
            slots: grid.map_or(slots.count, |(_, size)| size),
            faces: slots.faces,
            inputs: slots.inputs,
            outputs: slots.outputs,
            menu: grid.map(|(menu, _)| menu.to_owned()),
        });
    }
}

/// Either game: the screen of block `block`'s menu (`native_screen`), as the mod's code laid it out: the menu's
/// slots of its container where its code put them, the rest hidden; the player's inventory below.
pub(super) fn screen(block: &str, screen: &mut Option<crate::game::native_hooks::NativeScreen>) {
    if screen.is_some() || block.starts_with("minecraft:") {
        return;
    }
    let Some(layout) = m::block_menu(block) else {
        return;
    };
    let Some((_, size)) = m::block_slots(block).and_then(|s| m::grid_menu(s.count)) else {
        return;
    };
    let [width, height] = layout.size();
    *screen = Some(crate::game::native_hooks::NativeScreen {
        width,
        height,
        slots: layout.slot_places(size),
        inventory_y: layout.inventory[1] as f32,
        inventory_x: layout.inventory[0] as f32,
        // Kiln draws the title and the inventory's label (`screen_draw`): the label only where no slot is.
        own_labels: true,
        // Horizon draws the panel and the slots' frames: Kiln has no textures of the mod's own for them yet.
        own_background: false,
    });
}

/// The window: what a machine's screen shows over its slots (`native_screen_draw`), from its data (`MachineData`):
/// the energy bar left of the slots, filling up in red, and the progress arrow between input and output, in white.
pub(super) fn screen_draw(
    game: &crate::game::Game<'_>,
    block: &str,
    data: &[i32],
    batch: &mut horizon_client::hud::HudBatch,
    origin: [f32; 2],
) {
    let Some(layout) = m::block_menu(block) else {
        return;
    };
    let grey = 0x40_4040;
    if let Some(title) = game.native_screen_title() {
        game.native_text(batch, &title, [origin[0] + 8.0, origin[1] + 6.0], grey, false);
    }
    if let Some(y) = layout.inventory_label() {
        let label = game.translate("container.inventory", "Inventory");
        game.native_text(batch, &label, [origin[0] + 8.0, origin[1] + y], grey, false);
    }
    if data.is_empty() {
        return;
    }
    let (share, progress, _, capacity) = m::machine_data(data);
    let at = |[x, y, w, h]: [f32; 4]| [origin[0] + x, origin[1] + y, w, h];
    if capacity > 0
        && let Some([x, y, w, h]) = layout.energy_bar().map(at)
    {
        game.native_fill(batch, [x, y, w, h], 0xFF37_3737);
        game.native_fill(batch, [x + 1.0, y + 1.0, w - 2.0, h - 2.0], 0xFF8B_8B8B);
        let full = ((h - 2.0) * share).round();
        game.native_fill(batch, [x + 1.0, y + h - 1.0 - full, w - 2.0, full], 0xFFC8_2828);
    }
    if let (Some(progress), Some([x, y, w, h])) = (progress, layout.progress_arrow().map(at)) {
        game.native_fill(batch, [x, y + 2.0, w, h - 4.0], 0xFF8B_8B8B);
        game.native_fill(batch, [x, y + 2.0, (w * progress).round(), h - 4.0], 0xFFFF_FFFF);
    }
    // The tank: its fluid's colour from the bottom, as full as it is, and a mark at each quarter.
    if let (Some((full, colour, _, _)), Some([x, y, w, h])) = (m::machine_tank(data), layout.tank_gauge().map(at)) {
        game.native_fill(batch, [x, y, w, h], 0xFF37_3737);
        game.native_fill(batch, [x + 1.0, y + 1.0, w - 2.0, h - 2.0], 0xFF8B_8B8B);
        let level = ((h - 2.0) * full).round();
        game.native_fill(batch, [x + 1.0, y + h - 1.0 - level, w - 2.0, level], 0xFF00_0000 | colour);
        for quarter in 1..4 {
            let mark = y + 1.0 + ((h - 2.0) * quarter as f32 / 4.0).round();
            game.native_fill(batch, [x + w - 6.0, mark, 5.0, 1.0], 0xFF37_3737);
        }
    }
}

/// The window: the tooltip over a machine's energy bar or tank (`native_screen_tooltip`): its energy and capacity, or
/// its fluid's amount and the tank's capacity in millibuckets.
pub(super) fn screen_tooltip(block: &str, data: &[i32], at: [f32; 2], lines: &mut Vec<String>) {
    let Some(layout) = m::block_menu(block) else {
        return;
    };
    let over = |[x, y, w, h]: [f32; 4]| at[0] >= x && at[0] < x + w && at[1] >= y && at[1] < y + h;
    let (_, _, energy, capacity) = m::machine_data(data);
    if capacity > 0 && layout.energy_bar().is_some_and(over) {
        lines.push(format!("{energy} / {capacity} E"));
    }
    if let Some((_, _, amount, most)) = m::machine_tank(data)
        && layout.tank_gauge().is_some_and(over)
    {
        lines.push(format!("{amount} / {most} mB"));
    }
}

/// Whether a block's block entity ticks (`native_block_entity_ticks`): a Java mod's does when its block has a ticker
/// on the server.
pub(super) fn block_entity_ticks(block: &str, ticks: &mut bool) {
    if !*ticks && !block.starts_with("minecraft:") && m::block_does(block, "ticks") {
        *ticks = true;
    }
}

/// The integrated server: a server tick of the block entity at `pos` (`native_block_entity_tick`), out of the world
/// meanwhile. A Java mod's runs its block's ticker, and keeps the compound its code saves.
pub(super) fn block_entity_tick(
    game: &mut crate::game::Game<'_>,
    pos: [i32; 3],
    entity: &mut ModBlockEntity,
) {
    let registry = game.pack.assets.registry;
    let state = game.world.block(pos[0], pos[1], pos[2]);
    if !modded(registry, state).is_some_and(|id| m::block_does(id, "ticks")) {
        return;
    }
    let Some(world) = WORLD.with(|w| w.borrow().clone()) else {
        return;
    };
    let text = state_text(registry, state);
    let dimension = game.dim.id();
    let game_time = game.world.game_time.max(0) as u64;
    let Some(outcome) = m::block_entity_tick(
        &world,
        game_time,
        dimension,
        pos,
        &text,
        &entity.id,
        &saved(entity),
        &mut |what| ask_game(game, what),
    ) else {
        return;
    };
    for effect in outcome.effects {
        match effect {
            m::Effect::BlockEntity { pos: at, data } if at == pos => {
                if let Some(nbt) = compound(&data) {
                    let slots = slots_at(game, pos);
                    put_compound(entity, nbt, slots);
                    // The game keeps its slots in its own form: the next tick compares with what it keeps.
                    if slots > 0 {
                        m::block_entity_kept(dimension, pos, &saved(entity));
                    }
                }
            }
            m::Effect::Data { pos: at, values } if at == pos => entity.data = values,
            other => apply(game, 0, dimension, other),
        }
    }
}

/// The integrated server, before a level's tick, with that level in place (`native_level_tick_pre`). Horizon builds
/// a level's table of the block entities that tick at its first tick, which can come before Kiln's Java has
/// announced its mods' blocks. Then Kiln drops the table, Horizon builds it again in this tick, and at the next one
/// the Java mods' block entities already in the level get the tickers they missed.
pub(super) fn level_tick(game: &mut crate::game::Game<'_>, dimension: &str) {
    tick_table(game, dimension);
    tick_event(game, m::TickPhase::LevelStart, dimension);
}

/// The integrated server, after a level's tick, with that level in place (`native_level_tick_post`).
pub(super) fn level_tick_post(game: &mut crate::game::Game<'_>, dimension: &str) {
    entity_ticks(game, dimension);
    tick_event(game, m::TickPhase::LevelEnd, dimension);
}

/// The most mobs of a level one batch carries; past it, a tick's others wait for the next (D-145).
const TICKED_MAX: usize = 4096;

/// Whether an entity of Horizon's kind is a living one, whose tick is `LivingEntity.tick`: not an item, a minecart or
/// a boat, the other kinds `native_entity_tick_pre` tells.
fn ticks_living(kind: &str) -> bool {
    kind != "minecraft:item" && !kind.contains("minecart") && !kind.ends_with("boat") && !kind.ends_with("raft")
}

/// The integrated server: a mob of `kind` is about to tick in `dimension` (`native_entity_tick_pre`), kept for the
/// call of `LivingEntity.tick` after the level's tick (Kiln's Mixin bridge, D-145). Horizon ticks it as it does: the
/// mods' mixins run on Kiln's living entity of its id, and their cancel of it isn't Horizon's.
pub(super) fn entity_tick_pre(dimension: &str, entity: u32, kind: &str) {
    if !m::bridged("entity_tick_pre") || !ticks_living(kind) {
        return;
    }
    TICKED.with(|t| {
        let mut t = t.borrow_mut();
        let level = t.entry(dimension.to_owned()).or_default();
        if level.len() < TICKED_MAX {
            level.push((entity, kind.to_owned()));
        }
    });
}

/// After a level's tick: one bridge call for the mobs it ticked, `LivingEntity.tick` for each in Java (D-145); what
/// the mods' code did to the world is done, as at a level's tick event.
fn entity_ticks(game: &mut crate::game::Game<'_>, dimension: &str) {
    let ticked = TICKED.with(|t| t.borrow_mut().remove(dimension)).unwrap_or_default();
    if ticked.is_empty() {
        return;
    }
    let Some(world) = WORLD.with(|w| w.borrow().clone()) else {
        return;
    };
    let game_time = game.world.game_time.max(0) as u64;
    let mut fields = Vec::with_capacity(1 + 2 * ticked.len());
    fields.push(dimension.to_owned());
    for (entity, kind) in ticked {
        fields.push(entity.to_string());
        fields.push(kind);
    }
    let Some(outcome) =
        m::bridge(&world, game_time, "entity_tick_pre", &fields, None, &mut |what| ask_game(game, what))
    else {
        return;
    };
    let dimension = game.dim.id();
    for effect in outcome.effects {
        if !matches!(effect, m::Effect::Out { .. }) {
            apply(game, 0, dimension, effect);
        }
    }
}

/// The integrated server, as its tick starts and ends (`native_server_tick`).
pub(super) fn server_tick(game: &mut crate::game::Game<'_>, start: bool) {
    if start {
        list_players(game);
        commands_again(game);
        // The world folder's name for the window's tick, from the server's first tick on (D-167): a mod's client code
        // names its files per world by it as the player joins, before the player's first tick here.
        if let Some(name) = game.native_world_dir().and_then(|p| p.file_name().map(|n| n.to_string_lossy().into_owned())) {
            let mut level = LEVEL_ID.lock().unwrap_or_else(|e| e.into_inner());
            if *level != name {
                *level = name;
            }
        }
    }
    let phase = if start { m::TickPhase::Start } else { m::TickPhase::End };
    tick_event(game, phase, "");
}

/// The integrated server, as Horizon asks its mods for their commands (`native_register_commands`, once a world as its
/// server starts, and again when Kiln says so): the Java mods' server commands, as usages for every player's tree
/// (D-107).
pub(super) fn register_commands(
    game: &mut crate::game::Game<'_>,
    commands: &mut Vec<crate::mod_api::NativeCommand>,
) {
    let Some(world) = game.native_world_dir() else {
        return;
    };
    let game_time = game.world.game_time.max(0) as u64;
    let Some(usages) = m::server_commands(&world, game_time, &mut |what| ask_game(game, what)) else {
        return;
    };
    for (usage, level) in usages {
        commands.push(crate::mod_api::NativeCommand::op(&usage, level));
    }
}

/// As the server's tick starts: Horizon asked for the mods' commands before Java could tell them all (it starts with
/// the window's first tick in a world; its mods load after), so it is made to ask again, at its next tick. Its tree
/// then goes again to every player whose tree changed.
fn commands_again(game: &mut crate::game::Game<'_>) {
    if m::server_commands_stale()
        && let Some(host) = game.lan.as_mut()
    {
        host.native_commands = None;
    }
}

/// The integrated server: a player's command (`native_perform_command`, without its slash) for a command a mod
/// registered, the player's state swapped in. One of the Java mods' runs in Java as that player at their level: its
/// answer is the command's feedback, and what it did to them is done.
pub(super) fn perform_command(
    game: &mut crate::game::Game<'_>,
    player: &crate::mod_api::NativeServerPlayer,
    command: &str,
    ran: &mut bool,
) {
    if *ran || !m::is_server_command(command) {
        return;
    }
    let Some((snapshot, world, game_time)) = acting(game) else {
        return;
    };
    let Some(outcome) = m::server_command(&world, game_time, player.permission, command, &snapshot, &mut |what| {
        ask_game(game, what)
    }) else {
        return;
    };
    *ran = true;
    apply_all(game, &snapshot, outcome);
}

/// The server's players as its tick starts (`native_server_players`), each with the radius of chunks they are sent,
/// and the server's view distance, a newcomer's until Horizon lists them: what Kiln's Java gets for `players`.
fn list_players(game: &crate::game::Game<'_>) {
    let Some(host) = game.lan.as_ref() else {
        return;
    };
    let view = host.hub.config.view_distance;
    let listed = game
        .native_server_players()
        .into_iter()
        .map(|p| m::Online {
            uuid: p.uuid,
            view_distance: host
                .hub
                .guests
                .iter()
                .find(|g| g.uuid == p.uuid)
                .map_or(view, |g| g.view_distance),
            name: p.name,
            dimension: p.dimension,
            position: p.position,
            game_mode: p.game_mode as u8,
            health: p.health,
        })
        .collect();
    ONLINE.with(|o| *o.borrow_mut() = (listed, view));
}

/// The acting player as they are now, for the `players` answer: where and how they are, without their hands.
fn acting_now(game: &crate::game::Game<'_>) -> Option<m::PlayerSnapshot> {
    let p = game.native_acting_player()?;
    Some(m::PlayerSnapshot {
        uuid: p.uuid,
        name: p.name,
        dimension: p.dimension,
        position: p.position,
        game_mode: p.game_mode as u8,
        health: p.health,
        ..Default::default()
    })
}

/// Fabric's tick event of `phase` for the Java mods that hear it (`ServerTickEvents`): the level `dimension`'s, or
/// the server's when it is empty. What their code asks of the game is answered from the level in place, and what it
/// does happens there: for the server's events Horizon leaves which level that is open, and Kiln's `MinecraftServer`
/// has no levels of its own for a mod to reach yet.
fn tick_event(game: &mut crate::game::Game<'_>, phase: m::TickPhase, dimension: &str) {
    let Some(world) = WORLD.with(|w| w.borrow().clone()) else {
        return;
    };
    let game_time = game.world.game_time.max(0) as u64;
    let Some(outcome) = m::server_tick(&world, game_time, phase, dimension, &mut |what| {
        ask_game(game, what)
    }) else {
        return;
    };
    let dimension = game.dim.id();
    for effect in outcome.effects {
        apply(game, 0, dimension, effect);
    }
}

/// A level's table of the block entities that tick, built again once the Java mods' blocks are announced.
fn tick_table(game: &mut crate::game::Game<'_>, dimension: &str) {
    let announced = m::blocks_announced();
    if announced == 0 {
        return;
    }
    let entities = &mut game.world.block_entities;
    let missed = TICKING.with(|t| {
        let mut t = t.borrow_mut();
        let (seen, dropped) = t.entry(dimension.to_owned()).or_insert((0, false));
        let missed = *dropped && entities.has_states();
        if missed {
            *dropped = false;
        }
        if *seen != announced {
            *seen = announced;
            if entities.has_states() {
                entities.set_states(Vec::new());
                *dropped = true;
            }
        }
        missed
    });
    if !missed {
        return;
    }
    let registry = game.pack.assets.registry;
    let ours: Vec<_> = game
        .world
        .block_entities
        .iter_mut()
        .filter(|(_, e)| matches!(e, BlockEntity::Mod(m) if !m.id.is_empty()))
        .map(|(pos, _)| *pos)
        .collect();
    for pos in ours {
        let state = game.world.block(pos.0, pos.1, pos.2);
        if modded(registry, state).is_some_and(|id| m::block_does(id, "ticks")) {
            // As a block entity's block becoming one that ticks (`LevelChunk.setBlockState`); state 0 is air.
            game.world.block_entities.state_changed(pos, 0, state.0);
        }
    }
}

/// The blocks and items Kiln declares in code for the Java mods' content mods (`carry_blocks`, D-078): Horizon asks for
/// them once at start (`native_blocks`, `native_items`, API 145), right after Kiln made them, checks them as a content
/// mod's files and adds them to that content mod.
type Declared = (Vec<horizon_mods::content::Block>, Vec<horizon_mods::content::Item>);
static DECLARED: Mutex<Declared> = Mutex::new((Vec::new(), Vec::new()));

/// Horizon asks for the blocks native mods declare in code: Kiln's.
pub(super) fn declared_blocks(blocks: &mut Vec<horizon_mods::content::Block>) {
    let declared = DECLARED.lock().unwrap_or_else(|e| e.into_inner());
    blocks.extend(declared.0.iter().cloned());
}

/// Horizon asks for the items native mods declare in code: Kiln's.
pub(super) fn declared_items(items: &mut Vec<horizon_mods::content::Item>) {
    let declared = DECLARED.lock().unwrap_or_else(|e| e.into_inner());
    items.extend(declared.1.iter().cloned());
}

/// A block declared in code from what its file would hold (carry.rs makes it in the files' format).
fn declared_block(
    id: &str,
    file: &serde_json::Value,
) -> Result<horizon_mods::content::Block, String> {
    let field = |name: &str| file.get(name).cloned().unwrap_or_else(|| serde_json::json!({}));
    let wrong = |e: serde_json::Error| format!("{id}: {e}");
    let template = file
        .get("template")
        .and_then(serde_json::Value::as_str)
        .ok_or(format!("{id}: no template"))?;
    Ok(horizon_mods::content::Block {
        id: id.to_owned(),
        template: template.to_owned(),
        properties: serde_json::from_value(field("properties")).map_err(wrong)?,
        state_properties: serde_json::from_value(field("state_properties")).map_err(wrong)?,
        default_state: serde_json::from_value(field("default_state")).map_err(wrong)?,
        // As a block file: its item comes with it.
        item: file
            .get("item")
            .and_then(serde_json::Value::as_bool)
            .unwrap_or(true),
        components: serde_json::from_value(field("components")).map_err(wrong)?,
        ..Default::default()
    })
}

/// An item declared in code from what its file would hold: a copy of the import's item for its model, or the stick.
fn declared_item(id: &str, file: &serde_json::Value) -> Result<horizon_mods::content::Item, String> {
    let wrong = |e: serde_json::Error| format!("{id}: {e}");
    let template = file
        .get("template")
        .and_then(serde_json::Value::as_str)
        .ok_or(format!("{id}: no template"))?;
    let rarity = match file.get("rarity") {
        Some(r) => Some(serde_json::from_value(r.clone()).map_err(wrong)?),
        None => None,
    };
    let components = file
        .get("components")
        .cloned()
        .unwrap_or_else(|| serde_json::json!({}));
    Ok(horizon_mods::content::Item {
        id: id.to_owned(),
        template: template.to_owned(),
        max_stack_size: file
            .get("max_stack_size")
            .and_then(serde_json::Value::as_u64)
            .and_then(|n| u8::try_from(n).ok()),
        rarity,
        components: serde_json::from_value(components).map_err(wrong)?,
        crafting_remainder: file
            .get("crafting_remainder")
            .and_then(serde_json::Value::as_str)
            .map(str::to_owned),
    })
}

/// Before Horizon reads the content mods, once at start (`mods::content_root`): the content mod of each Java mod in
/// `kiln/mods` that is on gets the blocks Horizon's import left out of it (carry.rs, D-055), its items under the ids
/// its code names them by (D-074), and their recipes.
pub(super) fn carry_blocks(enabled: &std::collections::BTreeSet<String>) {
    *DECLARED.lock().unwrap_or_else(|e| e.into_inner()) = (Vec::new(), Vec::new());
    let here = std::env::current_dir().unwrap_or_default();
    // The game reads its switches from this file before it starts (Options > Mods keeps them there): the Java mods
    // turned off are left out from here on, as if their jars weren't in kiln/mods.
    let list = std::fs::read_to_string(here.join(horizon_client::mod_list::FILE)).unwrap_or_default();
    m::set_off(m::off_ids(m::disabled_in_list(&list)));
    let jars: HashMap<String, PathBuf> = m::mod_jars(&here.join("kiln"))
        .into_iter()
        .filter_map(|jar| Some((m::read_jar(&jar).ok()?.id, jar)))
        .collect();
    if jars.is_empty() {
        return;
    }
    for path in horizon_mods::find(&here.join(horizon_client::mod_list::DIR)) {
        let Ok(package) = horizon_mods::Package::read(&path) else {
            continue;
        };
        let manifest = &package.manifest;
        let Some(jar) = jars.get(&manifest.id) else {
            continue;
        };
        if !path.is_dir() || !enabled.contains(&manifest.id) {
            continue;
        }
        let namespaces: Vec<String> = manifest.owned_namespaces().map(str::to_owned).collect();
        match crate::game::kiln_carry::fill(&path, &namespaces, jar) {
            Ok(carried) => {
                let blocks: Result<Vec<_>, String> = carried
                    .blocks
                    .iter()
                    .zip(&carried.block_files)
                    .map(|(id, file)| declared_block(id, file))
                    .collect();
                let items: Result<Vec<_>, String> = carried
                    .items
                    .iter()
                    .zip(&carried.item_files)
                    .map(|(id, file)| declared_item(id, file))
                    .collect();
                let (blocks, items) = match (blocks, items) {
                    (Ok(blocks), Ok(items)) => (blocks, items),
                    (Err(e), _) | (_, Err(e)) => {
                        log::warn!("Kiln: {}'s blocks and items not declared: {e}", manifest.id);
                        continue;
                    }
                };
                if !carried.blocks.is_empty() || !carried.items.is_empty() {
                    log::info!(
                        "Kiln: carried {} block(s), {} item(s) and {} recipe(s) Horizon's import left out of {}, \
                         the blocks and items declared in code",
                        carried.blocks.len(),
                        carried.items.len(),
                        carried.recipes.len(),
                        manifest.id
                    );
                }
                let mut declared = DECLARED.lock().unwrap_or_else(|e| e.into_inner());
                declared.0.extend(blocks);
                declared.1.extend(items);
                for why in carried.skipped {
                    log::warn!("Kiln: {why}, so it stays out");
                }
            }
            Err(e) => log::warn!("Kiln: {} not filled in: {e}", manifest.id),
        }
    }
}

// --- A mod's own screen and the settings pages (D-094) ------------------------------------------------------------

/// The window, each frame: a mod's own screen opens or closes as Java asked. Over the world it is Horizon's screen of
/// a mod's own (`native_mod_screen_open`: the mouse freed, the screen hooks over it); over a screen of the game's
/// (Options > Mods, where a settings page opens) Kiln draws it over that screen and takes its input. Escape is
/// Java's (the mod's `onClose`); the game closing it otherwise (death, leaving) tells Java.
pub(super) fn mod_screen(game: &mut crate::game::Game<'_>) {
    use m::Shown;
    let (wanted, shown) = m::mod_screen(|s| (s.take_wanted(), s.shown));
    match (wanted, shown) {
        (Some(true), Shown::No) => {
            let shown = if game.native_mod_screen_open(m::ID) {
                Shown::Own
            } else if game.screen != crate::game::Screen::Playing {
                Shown::Over
            } else {
                Shown::No
            };
            if shown == Shown::No {
                log::warn!("Kiln: a mod's screen can't open now (the game has a screen of its own up)");
                m::mod_screen(|s| s.closed_by_game());
            } else {
                let (w, h) = m::gui_size();
                log::info!("Kiln: a mod's screen opens {}, {w}x{h} GUI pixels",
                    if shown == Shown::Own { "over the world" } else { "over the game's screen" });
                m::mod_screen(|s| s.show(shown));
            }
        }
        (Some(false), Shown::Own) => {
            game.native_mod_screen_close(m::ID);
            m::mod_screen(|s| s.show(Shown::No));
        }
        (Some(false), Shown::Over) => m::mod_screen(|s| s.show(Shown::No)),
        (None, Shown::Own) if game.native_mod_screen() != Some(m::ID) => m::mod_screen(|s| s.closed_by_game()),
        (None, Shown::Over) if game.screen == crate::game::Screen::Playing => {
            m::mod_screen(|s| s.closed_by_game());
        }
        _ => {}
    }
}

/// Whether the screen the hooks fire for is the mod's own screen Kiln shows: Horizon's mod screen while it is Kiln's,
/// or any while Kiln draws over the game's.
fn showing(game: &crate::game::Game<'_>, screen: &horizon_client::mod_api::NativeScreenView<'_>) -> bool {
    match m::mod_screen(|s| s.shown) {
        m::Shown::Own => screen.id == "mod" && game.native_mod_screen() == Some(m::ID),
        m::Shown::Over => true,
        m::Shown::No => false,
    }
}

/// The open screen drew itself: the mod's own screen Kiln shows, drawn over it. Over a game's screen (JEI's recipe
/// screen over the inventory), the game's screen is taken back out of the frame first (D-172): in vanilla the mod's
/// screen takes its place, so the world and the HUD show under it, dimmed by the mod's own background.
pub(super) fn screen_render_post(
    game: &crate::game::Game<'_>,
    batch: &mut horizon_client::hud::HudBatch,
    screen: &horizon_client::mod_api::NativeScreenView<'_>,
) {
    if !showing(game, screen) {
        over_game_screen(game, batch, screen);
        return;
    }
    m::set_gui(batch.width, batch.height);
    let over = m::mod_screen(|s| {
        s.cursor(screen.mouse);
        s.shown == m::Shown::Over
    });
    if over {
        unscreen(batch);
    }
    draw_ops(game, batch, &m::mod_screen(|s| s.ops.clone()));
}

/// Takes what the open screen drew this frame back out of the batch: the vertices from the screen's first one
/// (`blur_at`, where the game's world and HUD end and its screen begins) on, and their blends. The atlas's patches
/// stay (glyphs the screen's text added are the atlas's, not the frame's).
pub(crate) fn unscreen(batch: &mut horizon_client::hud::HudBatch) {
    let cut = batch.blur_at;
    if cut as usize >= batch.vertices.len() {
        return;
    }
    batch.vertices.truncate(cut as usize);
    batch.blends.retain_mut(|(range, _)| {
        range.end = range.end.min(cut);
        range.start < range.end
    });
}

/// The game's container screens (the inventory, a chest's, a furnace's, creative's), which the Java mods see as
/// vanilla's `AbstractContainerScreen`s (D-156): Java hears where the game drew the window and of which menu, and what
/// the mods draw over it (JEI's item list beside the window) is drawn over the game's, every frame until the next.
const OVER_SCREENS: [&str; 2] = ["inventory", "creative"];

fn over_game_screen(
    game: &crate::game::Game<'_>,
    batch: &mut horizon_client::hud::HudBatch,
    screen: &horizon_client::mod_api::NativeScreenView<'_>,
) {
    let view = OVER_SCREENS.contains(&screen.id).then(|| {
        let [x, y, w, h] = screen.rect;
        vec![
            screen.id.to_owned(),
            // The crafting table is the game's inventory screen with a 3x3 grid (D-166).
            screen.menu.unwrap_or(if open_grid(game) == Some(3) { "minecraft:crafting" } else { "-" }).to_owned(),
            m::num(x),
            m::num(y),
            m::num(w),
            m::num(h),
            screen.slots.to_string(),
        ]
    });
    let ops = m::mod_screen(|s| {
        s.game_screen(view);
        if s.over_game() { s.over_ops.clone() } else { Vec::new() }
    });
    if !ops.is_empty() {
        m::set_gui(batch.width, batch.height);
        draw_ops(game, batch, &ops);
    }
}

/// The title screen's question after a Java mod stopped the last game (D-112): "<mod> stopped the last game. Start the
/// game without it?", with [Start without <mod>], [Keep it on] and [Safe mode] (D-134; for a crash in no mod's call,
/// [Safe mode] and [Start as usual]), over the title screen dimmed. The answer is the
/// player's click ([`crash_question_click`]); the game applies it on its next frame (frame.rs).
pub(super) fn crash_question(
    game: &crate::game::Game<'_>,
    batch: &mut horizon_client::hud::HudBatch,
    screen: &horizon_client::mod_api::NativeScreenView<'_>,
) {
    if screen.id != "title" {
        return;
    }
    let Some(crash) = m::crash_question() else {
        return;
    };
    let dialog = m::crash_dialog(&crash, [batch.width, batch.height], |t| game.native_text_width(t));
    game.native_fill(batch, [0.0, 0.0, batch.width, batch.height], 0xA000_0000);
    let [x, y, w, h] = dialog.panel;
    game.native_fill(batch, [x - 1.0, y - 1.0, w + 2.0, h + 2.0], 0xFF80_8080);
    game.native_fill(batch, dialog.panel, 0xFF20_2020);
    for (text, at, rgb) in &dialog.lines {
        game.native_text(batch, text, *at, *rgb, true);
    }
    let mouse = screen.mouse;
    for (label, [bx, by, bw, bh], _) in &dialog.buttons {
        let over = mouse[0] >= *bx && mouse[0] < bx + bw && mouse[1] >= *by && mouse[1] < by + bh;
        game.native_fill(batch, [bx - 1.0, by - 1.0, bw + 2.0, bh + 2.0], if over { 0xFFFF_FFFF } else { 0xFF00_0000 });
        game.native_fill(batch, [*bx, *by, *bw, *bh], if over { 0xFF6A_6A6A } else { 0xFF4A_4A4A });
        let tx = bx + (bw - game.native_text_width(label)) / 2.0;
        game.native_text(batch, label, [tx, by + (bh - 8.0) / 2.0], m::INK, true);
    }
    m::crash_question_drawn(&dialog);
}

/// A click on the title screen while its question shows: the question's (a button's answer, or nothing), never the
/// title screen's under it.
pub(super) fn crash_question_click(
    game: &crate::game::Game<'_>,
    screen: &horizon_client::mod_api::NativeScreenView<'_>,
) -> bool {
    if screen.id != "title" {
        return false;
    }
    let Some(crash) = m::crash_question() else {
        return false;
    };
    let (w, h) = m::gui_size();
    let size = if screen.rect[2] > 0.0 { [screen.rect[2], screen.rect[3]] } else { [w as f32, h as f32] };
    let dialog = m::crash_dialog(&crash, size, |t| game.native_text_width(t));
    m::crash_click(&dialog, screen.mouse)
}

/// Input over the screen the hooks fire for: Java's when it is the mod's own screen Kiln shows (then the game does
/// nothing with it).
pub(super) fn screen_input(
    game: &crate::game::Game<'_>,
    screen: &horizon_client::mod_api::NativeScreenView<'_>,
    fields: Vec<String>,
    canceled: &mut bool,
) {
    if showing(game, screen) {
        if m::mod_screen(|s| s.input(fields)) {
            *canceled = true;
        }
    } else if OVER_SCREENS.contains(&screen.id) && m::mod_screen(|s| s.over_input(fields, screen.mouse)) {
        // Over the game's container screen, where the mods' drawing takes it (D-156).
        *canceled = true;
    }
}

/// The input's fields: a mouse event at the pointer (`press`, `release`, `drag`, `scroll`) with its own, or a key's.
pub(super) fn at_pointer(kind: &str, screen: &horizon_client::mod_api::NativeScreenView<'_>, rest: &[String]) -> Vec<String> {
    let mut fields = vec![kind.to_owned(), m::num(screen.mouse[0]), m::num(screen.mouse[1])];
    fields.extend_from_slice(rest);
    fields
}

/// What a Java mod drew (its HUD or its screen): Kiln's ops through Horizon's HUD, cut where it set a scissor.
pub(super) fn draw_ops(
    game: &crate::game::Game<'_>,
    batch: &mut horizon_client::hud::HudBatch,
    ops: &[crate::game::kiln_jvm::HudOp],
) {
    use crate::game::kiln_jvm::HudOp;
    let mut clip: Option<[f32; 4]> = None;
    for op in ops {
        let start = batch.vertices.len();
        match op {
            HudOp::Clip(rect) => clip = *rect,
            HudOp::Fill { x, y, w, h, argb } => game.native_fill(batch, [*x, *y, *w, *h], *argb),
            HudOp::Text {
                x,
                y,
                scale,
                argb,
                shadow,
                text,
            } => {
                game.native_text(batch, text, [*x, *y], argb & 0xFF_FFFF, *shadow);
                if *scale != 1.0 {
                    batch.scale_since(start, *x, *y, *scale, *scale);
                }
            }
            HudOp::Item { x, y, size, item } => {
                game.native_item(batch, item, [*x, *y], *size);
            }
            HudOp::Stack { x, y, size, snbt } => draw_stack(game, batch, snbt, [*x, *y], *size),
            HudOp::Styled {
                x,
                y,
                scale,
                argb,
                shadow,
                json,
                ..
            } => {
                let text = horizon_client::component::to_text(json, &mut |s| Text::literal(s));
                let [_, r, g, b] = argb.to_be_bytes();
                let colour = horizon_client::hud::rgba(r, g, b, 0xFF);
                batch.text_styled(&game.gui.atlas, &text, *x, *y, colour, *shadow);
                if *scale != 1.0 {
                    batch.scale_since(start, *x, *y, *scale, *scale);
                }
            }
            HudOp::Sprite {
                rect,
                border,
                fallback,
                tint,
                name,
            } => sprite(game, batch, name, *rect, *border, *fallback, *tint),
            HudOp::Entity {
                rect,
                size,
                offset_y,
                mouse,
                id,
                saved,
            } => draw_entity(game, batch, id, saved, *rect, *size, *offset_y, *mouse),
        }
        if let Some([x0, y0, x1, y1]) = clip
            && batch.vertices.len() > start
        {
            batch.clip_since(start, x0, y0, x1, y1);
        }
    }
}

/// A whole stack's icon (its SNBT) at `at`, `size` pixels square, drawn as the game draws that stack in a slot, its
/// components and all (an enchanted book's glint, a potion's colour, a trim), without the slot's decorations: what the
/// mod draws over it (count, durability bar) is its own (D-164). Nothing for an item the game hasn't or a stack that
/// doesn't read; the plain item for a stack whose components don't.
fn draw_stack(
    game: &crate::game::Game<'_>,
    batch: &mut horizon_client::hud::HudBatch,
    snbt: &str,
    at: [f32; 2],
    size: f32,
) {
    let Some(stack) = parse_snbt(snbt).ok().and_then(|(tag, _)| item_from_nbt(&tag)) else {
        return;
    };
    if &*stack.item == "minecraft:air" || game.res.items().get(&stack.item).is_none() {
        return;
    }
    let start = batch.vertices.len();
    game.draw_item_model(batch, &stack, at[0], at[1]);
    batch.scale_since(start, at[0], at[1], size / 16.0, size / 16.0);
}

/// Entity type `id`'s model in the box `[x0, y0, x1, y1]`, `size` pixels a block, turned toward `mouse`: vanilla's
/// `InventoryScreen.extractEntityInInventoryFollowsMouse`, drawn as the game draws a mount in its horse screen
/// (Horizon's mob renderer, depth-sorted quads with their faces away culled), cut to the box (D-173). A type the game
/// has no mob model for (a mod's entity, a boat) draws its name in the box instead. The mob is built from `saved`
/// (the entity as the game saves one, SNBT) as the game loads a saved mob, so what the mod set on it shows: a
/// sheep's colour or shorn wool, a baby, a variant, a collar, armour or a harness (D-179); from its type alone
/// without it.
#[allow(clippy::too_many_arguments)]
fn draw_entity(
    game: &crate::game::Game<'_>,
    batch: &mut horizon_client::hud::HudBatch,
    id: &str,
    saved: &str,
    [x0, y0, x1, y1]: [f32; 4],
    size: f32,
    offset_y: f32,
    mouse: [f32; 2],
) {
    static SAID: std::sync::Mutex<std::collections::BTreeSet<String>> =
        std::sync::Mutex::new(std::collections::BTreeSet::new());
    let kind = game.res.mob_types().get(id).cloned();
    if SAID.lock().is_ok_and(|mut said| said.insert(id.to_owned())) {
        match kind {
            Some(_) => log::info!("Kiln: drew the model of {id} in a mod's screen"),
            None => log::info!("Kiln: the game has no model of {id}: its name is drawn in a mod's screen instead"),
        }
    }
    let Some(kind) = kind else {
        let name = id.rsplit(':').next().unwrap_or(id).replace('_', " ");
        let width = game.native_text_width(&name);
        let at = [((x0 + x1) / 2.0 - width / 2.0).max(x0), (y0 + y1) / 2.0 - 4.0];
        let start = batch.vertices.len();
        game.native_text(batch, &name, at, 0x40_4040, false);
        batch.clip_since(start, x0, y0, x1, y1);
        return;
    };
    let (cx, cy) = ((x0 + x1) / 2.0, (y0 + y1) / 2.0);
    let x_angle = ((cx - mouse[0]) / 40.0).atan();
    let y_angle = ((cy - mouse[1]) / 40.0).atan();
    let loaded = (!saved.is_empty())
        .then(|| parse_snbt(saved).ok())
        .flatten()
        .and_then(|(tag, _)| horizon_client::entity_nbt::mob_from_nbt(&tag, game.res.mob_types(), 0, None))
        .filter(|m| &*m.kind.id == id);
    if !saved.is_empty() && loaded.is_none() && SAID.lock().is_ok_and(|mut said| said.insert(format!("{id} saved"))) {
        log::warn!("Kiln: a mod's {id} as it saved it doesn't load ({saved}): drawn as its type");
    }
    let mut m = loaded.unwrap_or_else(|| horizon_client::mobs::Mob::new(0, kind, horizon_core::geometry::Vec3::ZERO, 0.0));
    m.position = horizon_core::geometry::Vec3::ZERO;
    m.previous = m.position;
    m.hurt_time = 0;
    m.death_time = 0;
    m.body_yaw = 180.0 + x_angle * 20.0;
    m.previous_body_yaw = m.body_yaw;
    m.head_yaw = m.body_yaw + x_angle * 20.0;
    m.previous_head_yaw = m.head_yaw;
    m.head_pitch = -y_angle * 20.0;
    m.previous_head_pitch = m.head_pitch;
    let camera = glam::DVec3::new(m.position.x, m.position.y, m.position.z);
    let atlas = &game.gui.atlas;
    let lookup = |name: &str| atlas.sprites.get(name).map(|r| [r.u0, r.v0, r.u1, r.v1]);
    let mut verts = Vec::new();
    horizon_client::mobs::cem_anim::in_gui(|| {
        horizon_client::mobs::render::render_with(
            &m,
            1.0,
            camera,
            &lookup,
            240 | 240 << 8,
            game.res.mob_data(),
            &[],
            &mut verts,
        );
    });
    // Its own height: a baby's is less (`getBbHeight`).
    let height = m.height();
    // Vanilla's translation: the box's middle, raised by half the entity's height and the mod's offset.
    let gui = glam::Mat4::from_translation(glam::Vec3::new(cx, cy, 0.0))
        * glam::Mat4::from_scale(glam::Vec3::new(size, size, -size))
        * glam::Mat4::from_translation(glam::Vec3::new(0.0, height / 2.0 + offset_y, 0.0))
        * glam::Mat4::from_rotation_z(std::f32::consts::PI)
        * glam::Mat4::from_rotation_x((y_angle * 20.0).to_radians());
    let mut quads: Vec<(f32, [[f32; 2]; 4], [[f32; 2]; 4], u32)> = Vec::new();
    for q in verts.as_chunks::<4>().0 {
        let p: [glam::Vec3; 4] = std::array::from_fn(|i| gui.transform_point3(glam::Vec3::from_array(q[i].pos)));
        let n = (p[1] - p[0]).cross(p[2] - p[0]);
        if n.z >= 0.0 {
            continue;
        }
        let n = n.normalize_or_zero();
        let shade = (0.55 + 0.45 * -n.z - 0.15 * n.y).clamp(0.4, 1.0);
        let c = q[0].color;
        let ch = |s: u32| ((((c >> s) & 255) as f32 * shade) as u32) << s;
        let color = (c & 0xFF00_0000) | ch(0) | ch(8) | ch(16);
        let depth = p.iter().map(|v| v.z).sum::<f32>() / 4.0;
        quads.push((depth, p.map(|v| [v.x, v.y]), std::array::from_fn(|i| q[i].uv), color));
    }
    quads.sort_by(|a, b| a.0.total_cmp(&b.0));
    let start = batch.vertices.len();
    for (_, p, uv, color) in quads {
        batch.quad(p, uv, color);
    }
    batch.clip_since(start, x0, y0, x1, y1);
}

/// Sprite `name` into `rect`, nine-sliced with edges `border` wide (as vanilla draws its widgets' sprites), its
/// pixels multiplied by `tint` (ARGB, white: as they are); a box of `fallback` where the game has no such sprite.
fn sprite(
    game: &crate::game::Game<'_>,
    batch: &mut horizon_client::hud::HudBatch,
    name: &str,
    rect: [f32; 4],
    border: f32,
    fallback: u32,
    tint: u32,
) {
    let Some(size) = game.native_sprite_size(name) else {
        game.native_fill(batch, rect, fallback);
        return;
    };
    if tint == u32::MAX {
        for (src, dst) in m::nine_slice(size, rect, border) {
            game.native_sprite(batch, name, src, dst);
        }
        return;
    }
    // As native_sprite does, with the tint as the vertices' colour (the HUD's shader multiplies the texture by it).
    let Some(whole) = game.gui.atlas.sprites.get(name).copied() else {
        return;
    };
    let [a, r, g, b] = tint.to_be_bytes();
    let colour = horizon_client::hud::rgba(r, g, b, a);
    let (du, dv) = ((whole.u1 - whole.u0) / whole.w as f32, (whole.v1 - whole.v0) / whole.h as f32);
    for (src, dst) in m::nine_slice(size, rect, border) {
        let mut part = whole;
        part.u0 = whole.u0 + src[0] * du;
        part.v0 = whole.v0 + src[1] * dv;
        part.u1 = part.u0 + src[2] * du;
        part.v1 = part.v0 + src[3] * dv;
        batch.rect_uv(dst[0], dst[1], dst[2], dst[3], part, colour);
    }
}

/// Mod `row`'s page in Options > Mods: a button for its settings page, when its Mod Menu entrypoint has one. Kiln's
/// own page: the button for Kiln's overlay (D-113), while Java runs.
pub(super) fn settings_buttons(row: &str, buttons: &mut Vec<String>) {
    if row == m::ID {
        if m::java_running() {
            buttons.push(m::OVERLAY_BUTTON.to_owned());
        }
    } else if m::mod_screen(|s| s.has_settings(row)) {
        buttons.push(m::SETTINGS_BUTTON.to_owned());
    }
}

/// Kiln's overlay opens, or closes when open (D-113): with the mods in Kiln's folder and the overlay's key as bound.
pub(super) fn toggle_overlay(game: &crate::game::Game<'_>) {
    let kiln_dir = m::kiln_dir(&game.native_data_dir(m::ID));
    let key = game.native_mod_key(m::ID, m::OVERLAY_KEY, m::OVERLAY_KEY_DEFAULT);
    if !m::toggle_overlay(&kiln_dir, &key) {
        log::info!("Kiln: the overlay opens once Java runs, from the first world");
    }
}

/// A button on mod `row`'s page was pressed: Kiln's settings button (the only one Kiln puts there) opens its settings
/// page with the next tick.
pub(super) fn settings_click(row: &str, button: usize) {
    if button == 0 {
        m::mod_screen(|s| s.open_settings(row));
    }
}

/// A stack a mod threw out of a slot of the open menu (`MultiPlayerGameMode.handleContainerInput` with THROW, D-138): the
/// game does what its drop key does over a slot: the click is predicted on this client's menu and sent, and the server
/// drops the stack (button 0 one of it, 1 all of it). Nothing happens out of a world, or on a slot the menu doesn't have.
pub(super) fn throw_slot(game: &mut crate::game::Game<'_>, slot: usize, button: i32) {
    if game.remote.is_none() {
        return;
    }
    if !game.remote_menu_ops(&[(Some(slot), button, horizon_client::menu::Input::Throw)]) {
        log::warn!("Kiln: a mod threw the stack of slot {slot}, which the open menu doesn't have");
    }
}
