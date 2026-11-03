// Every world's data packs, plus Kiln's: the Java mods' world generation (structures.rs), as `file/kiln`.
fn kiln_pack(mut packs: Vec<DataPack>) -> Vec<DataPack> {
    let pack = std::path::Path::new("kiln/generated/datapack");
    if pack.join("pack.mcmeta").is_file()
        && !packs.iter().any(|p| p.id == "file/kiln")
        && let Ok(pack) = std::path::absolute(pack)
    {
        packs.push(DataPack {
            id: "file/kiln".into(),
            source: Source::Dir(pack),
        });
    }
    packs
}
