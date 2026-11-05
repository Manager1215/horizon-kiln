// Kiln's carry of the blocks Horizon's import leaves out, on a real mod: KILN_CARRY names the content mod Horizon's
// import made (`horizon-port-pack check <jar> <out>`, its <out>/carry), KILN_CARRY_JAR the jar it came from. A copy
// of the content mod is filled in; the test prints what Kiln declares (in code, D-078) and wrote (recipes), and why it
// left any block out. Every item the jar defines in the mod's namespace must be declared then, under the id its code
// registers it by (D-074): by the import, as a block's, or by Kiln.
//
//   KILN_CARRY=out/carry KILN_CARRY_JAR=mod.jar cargo test -p horizon-kiln-mod --test carry -- --ignored --nocapture

use std::path::{Path, PathBuf};

use horizon_kiln_mod::game::kiln_carry as carry;

#[test]
#[ignore = "needs KILN_CARRY and KILN_CARRY_JAR"]
fn a_mods_left_out_blocks_are_carried() {
    let (Some(content), Some(jar)) = (
        std::env::var_os("KILN_CARRY"),
        std::env::var_os("KILN_CARRY_JAR"),
    ) else {
        println!("KILN_CARRY and KILN_CARRY_JAR name no content mod and jar: nothing to carry");
        return;
    };
    let copy = std::env::temp_dir().join(format!("kiln-carry-test-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&copy);
    copy_dir(Path::new(&content), &copy);
    let manifest = std::fs::read_to_string(copy.join("horizon-mod.toml")).unwrap();
    let mut namespaces: Vec<String> = Vec::new();
    for line in manifest.lines() {
        let quoted = |l: &str| -> Vec<String> {
            l.split('"').skip(1).step_by(2).map(str::to_owned).collect()
        };
        if line.starts_with("id ") {
            namespaces.splice(0..0, quoted(line));
        } else if line.starts_with("namespaces ") {
            namespaces.extend(quoted(line));
        }
    }
    let carried = carry::fill(&copy, &namespaces, Path::new(&jar)).unwrap();
    println!(
        "== {} blocks: {}",
        carried.blocks.len(),
        carried.blocks.join(", ")
    );
    println!(
        "== {} items: {}",
        carried.items.len(),
        carried.items.join(", ")
    );
    println!("== {} recipes", carried.recipes.len());
    println!("== left out: {:?}", carried.skipped);
    for (id, file) in carried.blocks.iter().zip(&carried.block_files).take(3) {
        println!("== {id}\n{file}");
    }
    let own = &namespaces[0];
    let left_out: Vec<&str> = carried
        .skipped
        .iter()
        .filter_map(|why| why.split_once(':').map(|(_, rest)| rest))
        .filter_map(|rest| rest.split_once(':').map(|(name, _)| name))
        .collect();
    let zip = zip::ZipArchive::new(std::fs::File::open(&jar).unwrap()).unwrap();
    let defined: Vec<String> = zip
        .file_names()
        .filter_map(|n| {
            n.ok()?
                .strip_prefix(&format!("assets/{own}/items/"))?
                .strip_suffix(".json")
                .map(str::to_owned)
        })
        .collect();
    let undeclared: Vec<&String> = defined
        .iter()
        .filter(|path| !left_out.contains(&path.as_str()))
        .filter(|path| {
            let id = format!("{own}:{path}");
            !carried.items.contains(&id)
                && !carried.blocks.contains(&id)
                && ["item", "block"].iter().all(|kind| {
                    !copy
                        .join(format!("data/{own}/horizon/{kind}/{path}.json"))
                        .is_file()
                })
        })
        .collect();
    println!(
        "== {} item definitions in the jar, undeclared: {undeclared:?}",
        defined.len()
    );
    assert!(undeclared.is_empty(), "{undeclared:?}");
    if own == "techreborn" {
        // What the industrial grinder makes of an iron ore beside the raw iron (D-072).
        for id in ["techreborn:tin_nugget", "techreborn:nickel_nugget"] {
            assert!(carried.items.iter().any(|i| i == id), "{id} not carried");
        }
        let at = carried.items.iter().position(|i| i == "techreborn:tin_nugget").unwrap();
        println!("== techreborn:tin_nugget\n{}", carried.item_files[at]);
    }
    // Carried again, the same.
    assert_eq!(
        carry::fill(&copy, &namespaces, Path::new(&jar)).unwrap(),
        carried
    );
    let _ = std::fs::remove_dir_all(&copy);
}

fn copy_dir(from: &Path, to: &Path) {
    std::fs::create_dir_all(to).unwrap();
    for entry in std::fs::read_dir(from).unwrap().flatten() {
        let target: PathBuf = to.join(entry.file_name());
        if entry.path().is_dir() {
            copy_dir(&entry.path(), &target);
        } else {
            std::fs::copy(entry.path(), target).unwrap();
        }
    }
}
