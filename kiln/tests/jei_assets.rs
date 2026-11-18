// JEI's own pictures and keys (D-160), with real Java: a test mod's GUI pictures (PNGs of several kinds) go from
// `kiln.Sprites` to the game as `sprite` lines, decoded as the PNG says, and Kiln packs them into the game's mod image
// where each name draws its own pixels; a texture's part a mod blits is that part alone. With JEI's jar (`KILN_JEI`, or
// `KILN_MOD` when it is a jar), every one of its GUI sprites goes too and fits. JEI's way of keeping its keys (its
// mapping keeps the key itself and sets vanilla's to none) reaches the game's page with its own key, never "none":
// the player's choice on the page reaches JEI's mapping, and a key JEI sets itself is kept as the player's choice.
// Needs a JDK and the jars from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test jei_assets -- --ignored --nocapture

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};
use std::process::Command;

use horizon_kiln_mod::game::kiln_jvm::{Reply, parse};
use horizon_kiln_mod::game::kiln_sprites::{SLOT, Sheet};

fn java(class_path: &[PathBuf], args: &[&str]) -> String {
    let repo = Path::new(env!("CARGO_MANIFEST_DIR")).join("..");
    let out = Command::new("java")
        .arg("-cp")
        .arg(std::env::join_paths(class_path).unwrap())
        .arg(repo.join("kiln/tests/JeiAssets.java"))
        .args(args)
        .output()
        .expect("java on PATH: a JDK, as tools/build-java.sh needs");
    assert!(
        out.status.success(),
        "{}",
        String::from_utf8_lossy(&out.stderr)
    );
    String::from_utf8_lossy(&out.stdout).into_owned()
}

/// JEI's jar, when the test is given one.
fn jei_jar() -> Option<PathBuf> {
    ["KILN_JEI", "KILN_MOD"]
        .iter()
        .filter_map(|v| std::env::var_os(v))
        .map(PathBuf::from)
        .find(|p| p.is_file() && p.extension().is_some_and(|e| e == "jar"))
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn mods_pictures_reach_the_games_atlas_and_jeis_keys_its_page() {
    let repo = Path::new(env!("CARGO_MANIFEST_DIR")).join("..");
    let runtime = repo.join("target/kiln/kiln-runtime.jar");
    assert!(runtime.is_file(), "run sh tools/build-java.sh first");
    let libs: Vec<PathBuf> = std::fs::read_dir(repo.join("target/kiln/lib"))
        .into_iter()
        .flatten()
        .flatten()
        .map(|e| e.path())
        .filter(|p| p.extension().is_some_and(|e| e == "jar"))
        .collect();
    let dir = std::env::temp_dir().join(format!("kiln-jei-assets-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&dir);
    std::fs::create_dir_all(&dir).unwrap();
    let test_jar = dir.join("testassets.jar");
    let mut class_path = vec![runtime];
    class_path.extend(libs);
    java(&class_path, &["write", test_jar.to_str().unwrap()]);
    class_path.push(test_jar);
    let jei = jei_jar();
    let mut args = vec!["check".to_owned()];
    if let Some(jei) = &jei {
        class_path.push(jei.clone());
        args.push(jei.to_string_lossy().into_owned());
    }
    let args: Vec<&str> = args.iter().map(String::as_str).collect();
    let stdout = java(&class_path, &args);

    // The lines in order: the checks' results, and the game's replies by the phase of the keys' part they came in.
    let mut results = BTreeMap::new();
    let mut sprites: Vec<(String, u32, u32, Vec<u8>)> = Vec::new();
    let mut phase = String::from("sprites");
    let mut by_phase: BTreeMap<String, Vec<Reply>> = BTreeMap::new();
    for line in stdout.lines() {
        if let Some(rest) = line.strip_prefix("result ") {
            let (what, value) = rest.split_once(' ').unwrap_or((rest, ""));
            if what == "phase" {
                phase = value.to_owned();
            } else {
                results.insert(what.to_owned(), value.to_owned());
            }
            continue;
        }
        match parse(line) {
            Reply::Sprite(name, w, h, rgba) => sprites.push((name, w, h, rgba)),
            Reply::Log(text) => println!("java: {text}"),
            reply => by_phase.entry(phase.clone()).or_default().push(reply),
        }
    }
    println!("{results:#?}");
    let result = |what: &str| results.get(what).map(String::as_str).unwrap_or("(none)");

    // The names.
    assert_eq!(result("button"), "testassets:gui/sprites/button");
    assert_eq!(result("button_again"), "testassets:gui/sprites/button");
    assert_eq!(result("gui_atlas"), "testassets:gui/sprites/button");
    assert_eq!(result("palette"), "testassets:gui/sprites/palette");
    assert_eq!(result("animated"), "testassets:gui/sprites/animated");
    assert_eq!(result("interlaced"), "testassets:gui/sprites/interlaced");
    assert_eq!(result("missing"), "null");
    assert_eq!(result("vanilla"), "gui/sprites/widget/button");
    assert_eq!(result("blocks_atlas"), "null");
    assert_eq!(
        result("blit"),
        "kiln:blit/testassets/textures/gui/machine/16_8_8_4"
    );
    assert_eq!(result("blit_outside"), "null");
    assert_eq!(result("scaling"), "nine_slice");

    // Each picture sent once, decoded as its PNG says.
    let picture = |name: &str| {
        let found: Vec<_> = sprites.iter().filter(|s| s.0 == name).collect();
        assert_eq!(found.len(), 1, "{name} sent {} times", found.len());
        found[0].clone()
    };
    let px = |p: &(String, u32, u32, Vec<u8>), x: u32, y: u32| -> [u8; 4] {
        let i = ((y * p.1 + x) * 4) as usize;
        [p.3[i], p.3[i + 1], p.3[i + 2], p.3[i + 3]]
    };
    let button = picture("testassets:gui/sprites/button");
    assert_eq!((button.1, button.2), (4, 3));
    for y in 0..3 {
        for x in 0..4 {
            assert_eq!(
                px(&button, x, y),
                [(x * 60) as u8, (y * 80) as u8, (x + y) as u8, 255 - x as u8],
                "button {x},{y}"
            );
        }
    }
    let palette = picture("testassets:gui/sprites/palette");
    assert_eq!((palette.1, palette.2), (3, 2));
    let (red, green, blue, white) = (
        [255, 0, 0, 255],
        [0, 255, 0, 255],
        [0, 0, 255, 128],
        [255, 255, 255, 255],
    );
    let row: Vec<[u8; 4]> = (0..6).map(|i| px(&palette, i % 3, i / 3)).collect();
    assert_eq!(row, [red, green, blue, white, blue, green]);
    let animated = picture("testassets:gui/sprites/animated");
    assert_eq!((animated.1, animated.2), (2, 2), "the first frame alone");
    assert!((0..4).all(|i| px(&animated, i % 2, i / 2) == [0, 0, 0, 255]));
    let interlaced = picture("testassets:gui/sprites/interlaced");
    assert_eq!((interlaced.1, interlaced.2), (9, 9));
    for y in 0..9 {
        for x in 0..9 {
            assert_eq!(
                px(&interlaced, x, y),
                [(x * 20) as u8, (y * 20) as u8, 7, 255],
                "interlaced {x},{y}"
            );
        }
    }
    let blit = picture("kiln:blit/testassets/textures/gui/machine/16_8_8_4");
    assert_eq!((blit.1, blit.2), (8, 4));
    for y in 0..4 {
        for x in 0..8 {
            assert_eq!(px(&blit, x, y), [16 + x as u8, 8 + y as u8, 0, 255]);
        }
    }

    // The game's sheet: every picture where its name says, its own pixels there.
    let mut sheet = Sheet::default();
    for (name, w, h, rgba) in &sprites {
        assert!(sheet.add(name, *w, *h, rgba), "{name} ({w}x{h}) didn't fit");
    }
    let upload = sheet.upload(1).expect("a sheet to upload");
    assert_eq!(upload.width, SLOT);
    for (name, w, h, rgba) in &sprites {
        let [x0, y0, pw, ph] = sheet.place(name).unwrap();
        assert_eq!((pw, ph), (*w, *h));
        for y in 0..ph {
            let from = (((y0 + y) * SLOT + x0) * 4) as usize;
            let line = (pw * 4) as usize;
            let own = (y * pw * 4) as usize;
            assert_eq!(
                &upload.rgba[from..from + line],
                &rgba[own..own + line],
                "{name} row {y}"
            );
        }
    }
    assert_eq!(upload.names.len(), sprites.len());
    if jei.is_some() {
        let (all, sent) = (result("jar_sprites"), result("jar_sent"));
        println!(
            "JEI's GUI sprites: {sent} of {all} sent, {} pictures in a sheet {}x{}",
            sprites.len(),
            upload.width,
            upload.height
        );
        assert_eq!(all, sent, "every one of JEI's GUI sprites decodes");
        assert!(all.parse::<usize>().unwrap() > 40);
    } else {
        println!("JEI's own sprites not checked: no KILN_JEI jar");
    }

    // The keys: nothing told while JEI's mapping is made and hides itself, or while the game gives the choice.
    let phase = |p: &str| by_phase.get(p).cloned().unwrap_or_default();
    let chosen = |p: &str| -> Vec<(String, String)> {
        phase(p)
            .into_iter()
            .filter_map(|r| match r {
                Reply::KeyChosen(name, key) => Some((name, key)),
                _ => None,
            })
            .collect()
    };
    assert_eq!(
        chosen("construct"),
        [],
        "a mapping being made tells the game nothing"
    );
    let keys: Vec<_> = phase("register")
        .into_iter()
        .filter_map(|r| match r {
            Reply::Keys(owner, keys) => Some((owner, keys)),
            _ => None,
        })
        .collect();
    assert_eq!(keys.len(), 1, "{:?}", phase("register"));
    assert_eq!(keys[0].0, "mezz.jei.fabric.JustEnoughItemsClient");
    // Its name and the key JEI set (R), never the none it gives vanilla's table.
    let listed: Vec<(&str, &str)> = keys[0]
        .1
        .iter()
        .map(|k| (k[0].as_str(), k[3].as_str()))
        .collect();
    assert_eq!(
        listed,
        [
            ("key.jei.showRecipe", "key.keyboard.r"),
            ("key.testassets.plain", "key.keyboard.k")
        ]
    );
    assert_eq!(chosen("register"), []);
    assert_eq!(chosen("bind"), [], "the game's own choice isn't told back");
    assert_eq!(
        result("y_matches"),
        "true",
        "the player's choice reaches JEI's own key"
    );
    assert_eq!(result("r_matches"), "false");
    assert_eq!(result("bound_of"), "key.keyboard.y");
    assert_eq!(
        chosen("mod_sets"),
        [
            ("key.jei.showRecipe".to_owned(), "key.keyboard.u".to_owned()),
            (
                "key.testassets.plain".to_owned(),
                "key.keyboard.j".to_owned()
            ),
        ],
        "a key JEI sets itself is kept as its own, not as none"
    );
    assert_eq!(result("u_matches"), "true");
    assert_eq!(chosen("forget"), []);
    assert_eq!(result("default_matches"), "true");
    assert_eq!(result("plain_down_key"), "key.keyboard.j");
    let _ = std::fs::remove_dir_all(&dir);
}
