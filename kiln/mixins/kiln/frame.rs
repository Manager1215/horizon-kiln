// Every frame, in a world or not: while no game tick comes (paused, on the title screen), Kiln still gives
// its Java mods a client tick 20 times a second, as vanilla's client ticks there too.
fn kiln_frame(game: &mut crate::game::Game<'_>, elapsed: std::time::Duration) {
    use crate::game::{kiln as m, kiln_horizon as h};
    let _ = elapsed;
    if !game.native_on(m::ID) || !game.native_window() {
        return;
    }
    // A mod Java turned off because it ran Java out of its heap or stack (D-112): off in Options > Mods too, so it stays
    // off from the next start until the player turns it back on.
    for id in m::take_tripped() {
        game.settings.mods.disabled.insert(m::row_id(&id));
    }
    // Kiln's overlay (D-113): a switch flipped there is Options > Mods' switch, kept in horizon-mods.txt; the soft switch
    // (below, with Java's next tick) stops or starts the mod at once. The mods' folder shown when asked.
    let asks = m::take_overlay_asks();
    // The banner after Restart Java now (D-134): the game starts itself again, so one Java runs.
    if asks.restart_game
        && let Err(e) = m::restart_game()
    {
        log::warn!("Kiln: the game couldn't start itself again: {e}");
        game.native_chat(&format!("Kiln: the game couldn't start itself again ({e}); close it and start it yourself"));
    }
    if !asks.switched.is_empty() || asks.open.is_some() {
        let kiln_dir = m::kiln_dir(&game.native_data_dir(m::ID));
        for (id, on) in asks.switched {
            let row = m::row_id(&id);
            let kept = if on {
                game.settings.mods.disabled.remove(&row);
                kiln_dir.parent().map(|folder| m::turn_on_in_list(folder, &id))
            } else {
                game.settings.mods.disabled.insert(row);
                kiln_dir.parent().map(|folder| m::turn_off_in_list(folder, &id))
            };
            if let Some(Err(e)) = kept {
                log::warn!("Kiln: {id} is {} for this game, but horizon-mods.txt didn't take it: {e}", if on { "on" } else { "off" });
            }
        }
        if let Some(folder) = asks.open {
            m::show_folder(&folder);
        }
    }
    // On the title screen, the last game's stop by a Java mod (D-112), which the title screen asks about
    // (horizon.rs): started without it, its switch goes off now and in horizon-mods.txt.
    if game.title.is_some() {
        let kiln_dir = m::kiln_dir(&game.native_data_dir(m::ID));
        m::load_crash_question(&kiln_dir);
        for id in m::take_turn_on() {
            game.settings.mods.disabled.remove(&m::row_id(&id));
            if let Some(folder) = kiln_dir.parent()
                && let Err(e) = m::turn_on_in_list(folder, &id)
            {
                log::warn!("Kiln: {id} is on for this game, but horizon-mods.txt didn't take it: {e}");
            }
        }
        // Safe mode (D-134) is the question's own (no Java mods in this game); its switches stay.
        if let Some((id, m::CrashChoice::Without)) = m::take_crash_answer()
            && !id.is_empty()
        {
            game.settings.mods.disabled.insert(m::row_id(&id));
            if let Some(folder) = kiln_dir.parent()
                && let Err(e) = m::turn_off_in_list(folder, &id)
            {
                log::warn!("Kiln: {id} is off for this game, but horizon-mods.txt didn't take it: {e}");
            }
        }
    }
    // Options > Mods' switches for the Java mods, as the player leaves them; on the title screen, a change starts
    // Java again with the next world.
    m::set_off(m::off_ids(game.settings.mods.disabled.iter().map(String::as_str)));
    // A mod's key bound on its page with modifiers held is that combination (D-178).
    h::combine_mod_keys(game);
    if game.title.is_some() {
        m::restart_for_switches();
    }
    // The system's clipboard (D-174): what a mod copied onto it, through the game's own (the game's chat pastes it,
    // horizon-ctl's status shows it); read for a mod's text field when a paste key waits for Java.
    {
        use horizon_client::clipboard::Clipboard;
        if let Some(text) = m::take_copied() {
            game.clipboard.set(&text);
        }
        if m::mod_screen(|s| s.wants_clipboard()) {
            let text = game.clipboard.get();
            m::mod_screen(|s| s.clipboard(text));
        }
    }
    let lines = m::frame(|| h::snapshot(game));
    let before = h::chat_count(game);
    for line in lines {
        game.native_chat(&line);
    }
    for component in m::take_chat_components() {
        h::chat_component(game, component);
    }
    h::chat_written(game, before);
    h::play(game, m::take_played());
    // A mod's own screen opens or closes as Java asked (D-094).
    h::mod_screen(game);
    // The Java mods' GUI pictures (D-160), when one came or the game built a new atlas: into the game's mod image,
    // each named in the atlas (a name the game has already, a content mod's, stays the game's).
    if let Some(sheet) = crate::game::kiln_sprites::take_upload(game.gui.atlas.revision) {
        let slot = game.gui.atlas.sprites.get(horizon_client::hud::MOD_IMAGE_SPRITE).copied();
        match slot {
            Some(slot) if game.native_mod_image(sheet.width, sheet.height, &sheet.rgba) => {
                let (count, rows) = (sheet.names.len(), sheet.height);
                if sheet.fresh {
                    log::info!("Kiln: the mods' GUI pictures go into the game's atlas: {count}, {rows} rows of its mod image");
                } else {
                    log::debug!("Kiln: the mods' GUI pictures in the game's atlas: {count}, {rows} rows");
                }
                let atlas = [game.gui.atlas.width, game.gui.atlas.height];
                for (name, place) in sheet.names {
                    let [u0, v0, u1, v1] = crate::game::kiln_sprites::uv([slot.u0, slot.v0], atlas, place);
                    game.gui.atlas.sprites.entry(name).or_insert(horizon_client::hud::Rect {
                        u0,
                        v0,
                        u1,
                        v1,
                        w: place[2],
                        h: place[3],
                    });
                }
            }
            _ => log::warn!("Kiln: the game has no mod image to take the mods' GUI pictures; they draw as boxes"),
        }
    }
}
