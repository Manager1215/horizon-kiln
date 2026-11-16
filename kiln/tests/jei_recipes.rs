// The game's recipes as a recipe viewer (JEI, #203) gets them on the client: Kiln reads every one of the game's
// recipe files (`<resources>/data/minecraft/recipe`, 2042 in 26.3) into `kiln.ClientRecipes`, of each of the game's
// kinds: crafting (shaped, shapeless, transmute, imbue, dye and the special ones), the four cooking kinds, the
// stonecutter's, smithing (transform, trim) and brewing (D-152, D-157). JEI's crafting category shows the ones with a
// shaped or shapeless display, the fireworks by their classes, and makes its own of the shield and tipped arrow ones;
// the rest of the special ones (a banner copied, a map zoomed out...) it doesn't show, as in the game. Needs a JDK on
// PATH, the jars from `sh tools/build-java.sh` and the game's prepared resources in KILN_RESOURCES:
//
//   KILN_RESOURCES=~/horizon-resources/26.3 cargo test -p horizon-kiln-mod --test jei_recipes -- --ignored --nocapture

use std::collections::BTreeMap;
use std::path::Path;
use std::process::Command;

/// The game's recipe files under `dir`, counted by their `type` (with its namespace).
fn files_by_type(dir: &Path, counts: &mut BTreeMap<String, u64>) {
    for entry in std::fs::read_dir(dir).unwrap().flatten() {
        let path = entry.path();
        if path.is_dir() {
            files_by_type(&path, counts);
        } else if path.extension().is_some_and(|e| e == "json") {
            let json: serde_json::Value =
                serde_json::from_slice(&std::fs::read(&path).unwrap()).unwrap();
            let kind = json["type"].as_str().unwrap_or("none").to_string();
            *counts.entry(kind).or_default() += 1;
        }
    }
}

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_RESOURCES"]
fn a_recipe_viewer_gets_every_one_of_the_games_recipes() {
    let Some(resources) = std::env::var_os("KILN_RESOURCES") else {
        println!("KILN_RESOURCES names no resources: nothing to read");
        return;
    };
    let repo = Path::new(env!("CARGO_MANIFEST_DIR")).join("..");
    let runtime = repo.join("target/kiln/kiln-runtime.jar");
    assert!(runtime.is_file(), "run sh tools/build-java.sh first");
    let libs = std::fs::read_dir(repo.join("target/kiln/lib"))
        .into_iter()
        .flatten()
        .flatten()
        .map(|e| e.path())
        .filter(|p| p.extension().is_some_and(|e| e == "jar"));
    let class_path = std::env::join_paths(std::iter::once(runtime).chain(libs)).unwrap();
    let mut resources_arg = std::ffi::OsString::from("-Dkiln.resources=");
    resources_arg.push(&resources);
    let out = Command::new("java")
        .arg(resources_arg)
        .arg("-cp")
        .arg(&class_path)
        .arg(repo.join("kiln/tests/JeiRecipes.java"))
        .output()
        .expect("java on PATH: a JDK, as tools/build-java.sh needs");
    let log = String::from_utf8_lossy(&out.stderr);
    println!("{log}");
    assert!(out.status.success(), "{log}");
    let read: serde_json::Value = serde_json::from_slice(&out.stdout).unwrap();
    println!("{read:#}");

    // Every file is read: the client has as many recipes of each kind as the game has files of it.
    let mut files = BTreeMap::new();
    files_by_type(
        &Path::new(&resources).join("data/minecraft/recipe"),
        &mut files,
    );
    let file_count: u64 = files.values().sum();
    assert_eq!(
        read["total"], file_count,
        "every recipe file read: {files:?}"
    );
    let classes = [
        ("crafting_shaped", "ShapedRecipe"),
        ("crafting_shapeless", "ShapelessRecipe"),
        ("crafting_transmute", "TransmuteRecipe"),
        ("crafting_imbue", "ImbueRecipe"),
        ("crafting_dye", "DyeRecipe"),
        ("crafting_decorated_pot", "DecoratedPotRecipe"),
        ("crafting_special_bannerduplicate", "BannerDuplicateRecipe"),
        ("crafting_special_bookcloning", "BookCloningRecipe"),
        ("crafting_special_mapextending", "MapExtendingRecipe"),
        ("crafting_special_repairitem", "RepairItemRecipe"),
        (
            "crafting_special_shielddecoration",
            "ShieldDecorationRecipe",
        ),
        ("crafting_special_firework_rocket", "FireworkRocketRecipe"),
        ("crafting_special_firework_star", "FireworkStarRecipe"),
        (
            "crafting_special_firework_star_fade",
            "FireworkStarFadeRecipe",
        ),
        ("smelting", "SmeltingRecipe"),
        ("blasting", "BlastingRecipe"),
        ("smoking", "SmokingRecipe"),
        ("campfire_cooking", "CampfireCookingRecipe"),
        ("stonecutting", "StonecutterRecipe"),
        ("smithing_transform", "SmithingTransformRecipe"),
        ("smithing_trim", "SmithingTrimRecipe"),
        ("brewing", "BrewingRecipe"),
    ];
    for (kind, class) in classes {
        let files = files
            .get(&format!("minecraft:{kind}"))
            .copied()
            .unwrap_or(0);
        assert_eq!(read["types"][class], files, "{kind}'s files as {class}s");
    }
    assert_eq!(
        files.len(),
        classes.len(),
        "a kind of recipe Kiln doesn't know: {files:?}"
    );

    // 26.3's numbers, by the recipe types JEI asks the client's recipe map for.
    let by_type = &read["by_type"];
    assert_eq!(read["total"], 2042);
    assert_eq!(by_type["crafting"], 1266);
    assert_eq!(by_type["smelting"], 73);
    assert_eq!(by_type["blasting"], 25);
    assert_eq!(by_type["smoking"], 9);
    assert_eq!(by_type["campfire_cooking"], 9);
    assert_eq!(by_type["stonecutting"], 351);
    assert_eq!(by_type["smithing"], 30);
    assert_eq!(by_type["brewing"], 279);
    // The crafting ones as JEI's crafting category takes them: 827 shaped, 375 shapeless, 33 transmute and 6 dye
    // shown; three fireworks by their classes; the shield and the tipped arrows remade; 20 not shown, as in the game.
    assert_eq!(read["crafting_shown"], 1241);
    assert_eq!(read["crafting_extended"], 3);
    assert_eq!(read["crafting_replaced"], 2);
    assert_eq!(read["crafting_hidden"], 20);
}
