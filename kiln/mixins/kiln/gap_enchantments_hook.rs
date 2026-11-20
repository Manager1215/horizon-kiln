// SPDX-License-Identifier: MIT
// HEAD of `native_tick` (horizon-gaps, `enchantments`): once a world's tables are built (a world's first open, each
// `/reload`), their registry gets the enchantments of the mods' namespaces and of the world's data packs
// (gap_enchantments.rs), for its items and its stations, which share it. The functions that build the registry can't
// take a hook that changes their result (they return `Self`, or use `?`, closures or logging macros), so it is
// replaced here, before the tick runs anything; the registry it put in is kept to know the tables are done.
fn gap_enchantments(game: &mut crate::game::Game<'_>) {
    use horizon_items::enchant::{Enchantments, gap_enchantments};
    use std::sync::{Arc, Mutex};
    static DONE: Mutex<Option<Arc<Enchantments>>> = Mutex::new(None);
    let root = game.options.resources.clone();
    let Some(tables) = game.world_tables.as_mut() else {
        return;
    };
    let Ok(mut done) = DONE.lock() else {
        return;
    };
    if done
        .as_ref()
        .is_some_and(|d| Arc::ptr_eq(d, &tables.items.enchantments))
    {
        return;
    }
    let overlay = horizon_world::loot::PackOverlay {
        files: &tables.files,
    };
    if !gap_enchantments::files(&root, &overlay).is_empty() {
        let registry = Enchantments::load_with(&root, &overlay);
        let registry = Arc::new(gap_enchantments::add(registry, &root, &overlay));
        tables.items.enchantments = Arc::clone(&registry);
        tables.stations.enchantments = registry;
    }
    *done = Some(Arc::clone(&tables.items.enchantments));
}
