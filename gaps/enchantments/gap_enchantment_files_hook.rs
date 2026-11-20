// SPDX-License-Identifier: MIT
// RETURN of `content_overlay` (horizon-gaps, `enchantments`): the world's packs' enchantment files join the content
// files the game's tables read (gap_enchantment_files.rs).
fn gap_enchantment_files(
    files: Vec<(String, Vec<Vec<u8>>)>,
    world_root: &std::path::Path,
    resources: &std::path::Path,
    vanilla_disabled: bool,
    enabled_features: &[String],
) -> Vec<(String, Vec<Vec<u8>>)> {
    self::gap_enchantment_files::add(
        files,
        world_root,
        resources,
        vanilla_disabled,
        enabled_features,
    )
}
