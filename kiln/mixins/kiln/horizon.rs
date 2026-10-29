// What Kiln reads of the game itself, where the hook points' helpers say nothing (DECISIONS.md, D-008): the
// pause, the world's game time and the player's advancements. This module is a child of `crate::game`, so it
// sees `Game`'s fields; it only reads them. A rename in a new Horizon breaks the build here, and only here.
// It names the game's types, so it isn't part of the mod's Cargo library: `native.py check` builds it.
//
// Advancements come from the single-player server's own `Game` when there is one (its thread publishes them,
// the window's reads them): Horizon's client view of them loses most of them after a trip between dimensions.
// Without a server in the process (playing on someone's server), the client view is all there is.

use std::cell::Cell;

use crate::game::kiln as m;

thread_local! {
    /// The advancement tree's size and the criteria granted, as last read on this thread: a change means
    /// the made advancements may have changed.
    static ADVANCEMENTS: Cell<Option<(usize, usize)>> = const { Cell::new(None) };
    /// The server's publication the window last took.
    static SERVER_SEEN: Cell<u64> = const { Cell::new(0) };
}

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
