// What Kiln reads of the game itself, where the hook points' helpers say nothing: the
// pause, the world's game time and the player's advancements; and, on the integrated server, the acting player's
// attributes, health and hands, which a Java mod's code changes (`use_item`, `player_tick`, `player_respawned`), and
// the blocks a mod's block code reads and sets (`right_click_block`, `random_tick`, `queue_step`, `tick_block`,
// `neighbor_changed`, `state_for_placement`, `place_block`, `destroy_block`).
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

use horizon_client::first_person::Hand;
use horizon_client::items::ItemStack;
use horizon_client::text::Text;
use horizon_core::position::BlockPos;
use horizon_world::attribute_map::ValueKind;
use horizon_world::level::{Dimension as LevelDimension, LevelAccess, PlacementContext, Ticks};
use horizon_world::registry::{BlockRegistry, BlockStateId};

use crate::game::kiln as m;

/// The attributes a server call carries: the ones Kiln's Java mods read so far.
const ATTRIBUTES: [&str; 1] = ["minecraft:max_health"];

thread_local! {
    /// The advancement tree's size and the criteria granted, as last read on this thread: a change means
    /// the made advancements may have changed.
    static ADVANCEMENTS: Cell<Option<(usize, usize)>> = const { Cell::new(None) };
    /// The server's publication the window last took.
    static SERVER_SEEN: Cell<u64> = const { Cell::new(0) };
    /// The server's thread: the level each player was last in, as Java mods heard (`join`).
    static LOADED: RefCell<HashMap<u128, String>> = RefCell::new(HashMap::new());
    /// The server's thread: each player as they were at their last tick, for a respawn's old player.
    static LAST: RefCell<HashMap<u128, m::PlayerSnapshot>> = RefCell::new(HashMap::new());
    /// The server's thread: each player's attributes Java mods changed, by id: the base they set last, and the base
    /// before their first change (a new body's).
    static KEPT: RefCell<HashMap<u128, HashMap<String, (f64, f64)>>> = RefCell::new(HashMap::new());
    /// The server's thread: the open world's folder, for calls from hooks that don't know it (a random tick).
    static WORLD: RefCell<Option<PathBuf>> = const { RefCell::new(None) };
}

/// Players standing on a mod's block, as Horizon's step hook heard them (it has no game to call Java from):
/// (dimension, entity, state, position), taken at that player's tick. At most a tick's worth for a few players.
static STEPS: Mutex<Vec<(String, i32, BlockStateId, [i32; 3])>> = Mutex::new(Vec::new());
/// What a mod's code did from a hook that can't change the world (a neighbour change, a placement's state): done at
/// the server's next tick, with the dimension it was in.
static PENDING: Mutex<Vec<(String, m::Effect)>> = Mutex::new(Vec::new());

/// The game's state for Kiln's Java mods, as of now (the window's game).
pub(super) fn snapshot(game: &crate::game::Game<'_>) -> m::Snapshot {
    // On the title screen `title` is set; travelling between dimensions, Horizon has no player but no title.
    let connected = game.title.is_none();
    if !connected {
        m::forget_server_advancements();
        SERVER_SEEN.set(0);
    }
    let Some(player) = game.native_player() else {
        ADVANCEMENTS.set(None);
        return m::Snapshot {
            connected,
            paused: game.pause,
            ..m::Snapshot::default()
        };
    };
    m::Snapshot {
        connected,
        in_world: true,
        paused: game.pause,
        game_time: game.world.game_time.max(0) as u64,
        dimension: player.dimension.to_owned(),
        advancements: window_advancements(game),
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
    };
    WORLD.with(|w| {
        if w.borrow().as_deref() != Some(world.as_path()) {
            *w.borrow_mut() = Some(world.clone());
        }
    });
    Some((player, world, game.world.game_time.max(0) as u64))
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
/// when it is the same block, else that block's defaults. None for a block or value Horizon doesn't have.
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
        if !state.properties.contains_key(key) {
            return None;
        }
        state.properties.insert(key.to_owned(), value.to_owned());
    }
    registry.id(&state)
}

/// What Kiln's Java asks of the world during a call (`block <x> <y> <z>`), answered from `block`, the world's block
/// at a position.
fn answer(
    registry: &BlockRegistry,
    block: impl Fn([i32; 3]) -> BlockStateId,
    what: &[String],
) -> String {
    match what {
        [kind, x, y, z] if kind == "block" => match (x.parse(), y.parse(), z.parse()) {
            (Ok(x), Ok(y), Ok(z)) => state_text(registry, block([x, y, z])),
            _ => String::new(),
        },
        _ => String::new(),
    }
}

/// The integrated server's game answering Kiln's Java.
fn ask_game(game: &crate::game::Game<'_>, what: &[String]) -> String {
    answer(
        game.pack.assets.registry,
        |[x, y, z]| game.world.block(x, y, z),
        what,
    )
}

/// A server level answering Kiln's Java, from a level's hook (a random tick).
fn ask_level(level: &crate::game::GameLevel<'_>, what: &[String]) -> String {
    answer(
        level.registry,
        |[x, y, z]| level.block_state(BlockPos::new(x, y, z)),
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
/// use and the item's). When it is a Java mod's block with a use of its own, Kiln asks the mod (`useItemOn`, then
/// `useWithoutItem`), does what its code did, and takes the click unless the mod passed.
pub(super) fn right_click_block(
    game: &mut crate::game::Game<'_>,
    pos: [i32; 3],
    face: [i32; 3],
    canceled: &mut bool,
) {
    let registry = game.pack.assets.registry;
    let state = game.world.block(pos[0], pos[1], pos[2]);
    if *canceled || !modded(registry, state).is_some_and(|id| m::block_does(id, "use")) {
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
    if outcome.result.takes_use() {
        *canceled = true;
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
            other => log::info!("Kiln: {other:?} isn't done in a block's tick yet"),
        }
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
            | m::Effect::Schedule { .. }
            | m::Effect::Sound { .. } => apply(game, 0, &dimension, effect),
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
/// use of its own, Kiln asks it what using it does, does that, and takes the use unless the mod passed.
pub(super) fn use_item(game: &mut crate::game::Game<'_>, stack: &ItemStack, claimed: &mut bool) {
    if *claimed || !m::has_use(&stack.item) {
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
        WORLD.with(|w| *w.borrow_mut() = Some(world));
    }
    keep_attributes(game);
    steps(game);
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

/// The integrated server: a player left; when they come back, the Java mods hear them come into a level again.
pub(super) fn player_logged_out(uuid: u128) {
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
        m::Effect::Schedule { pos, block, delay } => {
            game.with_level(|_, level, ticks| {
                level.native_schedule_block_tick(ticks, pos, &block, delay)
            });
        }
        m::Effect::Placement(_) => {}
    }
}
