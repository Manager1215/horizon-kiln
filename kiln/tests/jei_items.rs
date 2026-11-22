// A recipe viewer's view of the game's items with components (D-164), with real Java and the game's resources: an
// enchanted book's, a potion's, a trim's and the rest of a stack's tooltip lines as the game writes them, each part in
// its colour; a stack with components drawn whole (the game draws its glint, its potion's colour, its trim), styled
// text in one op; and what an enchanting table and an anvil take. Needs a JDK on PATH, the jars from
// `sh tools/build-java.sh` and the game's prepared resources in KILN_RESOURCES:
//
//   KILN_RESOURCES=~/horizon-resources/26.3 cargo test -p horizon-kiln-mod --test jei_items -- --ignored --nocapture

use std::collections::BTreeMap;
use std::path::Path;
use std::process::Command;

use horizon_kiln_mod::game::kiln_jvm::{HudOp, parse_op};

#[test]
#[ignore = "needs a JDK, sh tools/build-java.sh and KILN_RESOURCES"]
fn a_recipe_viewer_sees_the_games_items_with_their_components() {
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
        // The JVM's own warnings (a busy computer's perf-data file locked by another JVM) on stderr: stdout is
        // what the program printed (D-266).
        .args(["-Xlog:disable", "-Xlog:all=warning:stderr"])
        .arg(resources_arg)
        .arg("-cp")
        .arg(&class_path)
        .arg(repo.join("kiln/tests/JeiItems.java"))
        .output()
        .expect("java on PATH: a JDK, as tools/build-java.sh needs");
    let log = String::from_utf8_lossy(&out.stderr);
    println!("{log}");
    assert!(out.status.success(), "{log}");
    let stdout = String::from_utf8_lossy(&out.stdout);
    println!("{stdout}");

    // Each stack's lines, and the checks.
    let mut tooltips: BTreeMap<String, Vec<String>> = BTreeMap::new();
    let mut checks: BTreeMap<String, String> = BTreeMap::new();
    let mut ops = Vec::new();
    let mut current = String::new();
    for line in stdout.lines() {
        if let Some(name) = line.strip_prefix("tooltip ") {
            current = name.to_owned();
            tooltips.entry(current.clone()).or_default();
        } else if let Some(rest) = line.strip_prefix("line ") {
            let (_, runs) = rest.split_once(' ').unwrap();
            tooltips.get_mut(&current).unwrap().push(runs.to_owned());
        } else if let Some(op) = line.strip_prefix("op ") {
            ops.push(parse_op(op).unwrap_or_else(|| panic!("an op Kiln's Rust reads: {op}")));
        } else if let Some(rest) = line.strip_prefix("check ") {
            let (what, value) = rest.split_once(' ').unwrap();
            checks.insert(what.to_owned(), value.to_owned());
        }
    }
    let lines = |name: &str| tooltips.get(name).cloned().unwrap_or_default();
    // A line with its runs of one style joined: "[#5555ff]+6 Attack Damage" (D-177's lines are made of parts).
    let merged = |name: &str| -> Vec<String> {
        lines(name)
            .iter()
            .map(|line| {
                let mut out = String::new();
                let mut last = "";
                for run in line.split('|') {
                    let (style, text) = run.split_once(']').unwrap_or(("", run));
                    if style != last {
                        out.push_str(style);
                        out.push(']');
                        last = style;
                    }
                    out.push_str(text);
                }
                out
            })
            .collect()
    };

    // An enchanted book: aqua (its rarity, rare), a curse red and first (the game's tooltip order), the rest grey.
    assert_eq!(
        lines("book"),
        [
            "[#55ffff]Enchanted Book",
            "[#ff5555]Curse of Binding",
            "[#aaaaaa]Sharpness|[#aaaaaa] |[#aaaaaa]V"
        ]
    );
    // Potions named by their potion; an effect in its category's colour with its duration, scaled by the item.
    // Then what the effects change while they last (D-172): an empty line, "When Applied:", each change.
    assert_eq!(
        lines("potion"),
        [
            "[#ffffff]Potion of Swiftness",
            "[#5555ff]Speed (03:00)",
            "",
            "[#aa00aa]When Applied:",
            "[#5555ff]+20% Speed"
        ]
    );
    assert_eq!(
        lines("strength"),
        [
            "[#ffffff]Splash Potion of Strength",
            "[#5555ff]Strength II (01:30)",
            "",
            "[#aa00aa]When Applied:",
            "[#5555ff]+6 Attack Damage"
        ]
    );
    assert_eq!(
        lines("weakness"),
        [
            "[#ffffff]Potion of Weakness",
            "[#ff5555]Weakness (01:30)",
            "",
            "[#aa00aa]When Applied:",
            "[#ff5555]-4 Attack Damage"
        ]
    );
    assert_eq!(
        lines("lingering"),
        [
            "[#ffffff]Lingering Potion of Poison",
            "[#ff5555]Poison II (00:05)"
        ]
    );
    assert_eq!(
        lines("arrow"),
        [
            "[#ffffff]Arrow of Night Vision",
            "[#5555ff]Night Vision (01:00)"
        ]
    );
    assert_eq!(
        lines("water"),
        ["[#ffffff]Water Bottle", "[#aaaaaa]No Effects"]
    );
    // A trim: "Upgrade:", the pattern in the material's colour, the material; then its enchantments.
    assert_eq!(
        lines("chestplate"),
        [
            "[#55ffff]Diamond Chestplate",
            "[#aaaaaa]Upgrade: ",
            "[-] |[#deb12d]Coast Armor Trim",
            "[-] |[#deb12d]Gold Material",
            "[#aaaaaa]Protection|[#aaaaaa] |[#aaaaaa]IV",
            "[#aaaaaa]Mending",
            "",
            "[#aaaaaa]When on Chest:",
            "[#5555ff]+8 Armor",
            "[#5555ff]+2 Armor Toughness"
        ]
    );
    // The attributes come after the lore and before "Unbreakable", as the game's addDetailsToTooltip (D-177).
    assert_eq!(
        merged("helmet"),
        [
            "[#ffffff]Leather Cap",
            "[#aaaaaa italic]Dyed",
            "",
            "[#aaaaaa]When on Head:",
            "[#5555ff]+1 Armor",
            "[#5555ff]Unbreakable"
        ]
    );
    let sword = [
        "[#ffffff]Diamond Sword",
        "",
        "[#aaaaaa]When in Main Hand:",
        "[#5555ff]+6 Attack Damage",
        "[#ff5555]-2.4 Attack Speed",
    ];
    let hidden = merged("hidden");
    assert_eq!(
        hidden[1..],
        sword[1..],
        "tooltip_display hides its enchantments, not its attributes"
    );
    assert_eq!(hidden[0], "[#55ffff]Diamond Sword");
    assert_eq!(lines("stick"), ["[#ffffff]Stick"]);
    // The special items' components (D-172), as the game writes them.
    assert_eq!(lines("horn"), ["[#ffff55]Goat Horn", "[#aaaaaa]Sing"]);
    assert_eq!(
        lines("ponder"),
        ["[#ffff55]Goat Horn", "[#aaaaaa]Ponder"],
        "the item's own instrument"
    );
    assert_eq!(lines("disc"), ["[#ffff55]Music Disc", "[#aaaaaa]C418 - 13"]);
    assert_eq!(
        lines("rocket"),
        [
            "[#ffffff]Firework Rocket",
            "[#aaaaaa]Flight Duration: 2",
            "[#aaaaaa]2 x Star-shaped",
            "[#aaaaaa]  |[#aaaaaa]Red",
            "[#aaaaaa]  |[#aaaaaa]Trail"
        ]
    );
    assert_eq!(
        lines("star"),
        [
            "[#ffffff]Firework Star",
            "[#aaaaaa]Creeper-shaped",
            "[#aaaaaa]Red|[#aaaaaa], |[#aaaaaa]Custom",
            "[#aaaaaa]Fade to|[#aaaaaa] |[#aaaaaa]Orange",
            "[#aaaaaa]Twinkle"
        ]
    );
    assert_eq!(
        lines("bottle"),
        [
            "[#ffff55]Ominous Bottle",
            "[#5555ff]Bad Omen III (01:40:00)"
        ]
    );
    assert_eq!(
        lines("banner"),
        ["[#ffffff]White Banner", "[#aaaaaa]Cyan Lozenge"]
    );
    assert_eq!(
        lines("painting"),
        [
            "[#ffffff]Painting",
            "[#ffff55]Albanian",
            "[#aaaaaa]Kristoffer Zetterstrand",
            "[-]1x1"
        ]
    );
    let advanced = merged("advanced");
    assert_eq!(advanced[..5], sword);
    assert_eq!(
        advanced[5..7],
        [
            "[-]Durability: 1551 / 1561",
            "[#555555]minecraft:diamond_sword"
        ]
    );
    assert!(
        advanced[7].starts_with("[#555555]") && advanced[7].ends_with(" component(s)"),
        "{advanced:?}"
    );

    // D-177. A written book: its title as its name, "by <author>" unless blank, its generation; all grey.
    assert_eq!(
        merged("written"),
        [
            "[#ffffff]Field Notes",
            "[#aaaaaa]by Alex",
            "[#aaaaaa]Copy of original"
        ]
    );
    assert_eq!(merged("tattered"), ["[#ffffff]Old", "[#aaaaaa]Tattered"]);
    // A map: "Unknown Map" until the window has its data, then its id, its lock, and with advanced tooltips its scale.
    assert_eq!(merged("map"), ["[#ffffff]Map", "[#aaaaaa]Unknown Map"]);
    assert_eq!(
        merged("map_known"),
        ["[#ffffff]Map", "[#aaaaaa]ID #7", "[#aaaaaa]Locked"]
    );
    assert_eq!(
        merged("map_advanced")[..5],
        [
            "[#ffffff]Map",
            "[#aaaaaa]ID #7",
            "[#aaaaaa]Locked",
            "[#aaaaaa]Scaling at 1:4",
            "[#aaaaaa](Level 2/4)"
        ]
    );
    // A shulker box: its first five stacks by slot, then how many more, in italics.
    assert_eq!(
        merged("shulker"),
        [
            "[#ffffff]Shulker Box",
            "[-]Diamond x 3",
            "[-]Stick x 2",
            "[-]Dirt x 1",
            "[-]Dirt x 1",
            "[-]Dirt x 1",
            "[- italic]and 2 more..."
        ]
    );
    // Attributes: each slot group's header, a gain blue and a loss red, a multiplier as a percentage, knockback
    // resistance times ten, a neutral one grey, a hidden one and its header left out, an enchantment's attribute
    // effect (Efficiency V: 5 squared and 1) after the item's.
    assert_eq!(merged("sword"), sword);
    assert_eq!(
        merged("pickaxe"),
        [
            "[#55ffff]Diamond Pickaxe",
            "[#aaaaaa]Efficiency V",
            "",
            "[#aaaaaa]When in Main Hand:",
            "[#5555ff]+4 Attack Damage",
            "[#ff5555]-2.8 Attack Speed",
            "[#5555ff]+26 Mining Efficiency"
        ]
    );
    assert_eq!(
        merged("leggings"),
        [
            "[#ffffff]Netherite Leggings",
            "",
            "[#aaaaaa]When on Legs:",
            "[#5555ff]+6 Armor",
            "[#5555ff]+3 Armor Toughness",
            "[#5555ff]+1 Knockback Resistance"
        ]
    );
    assert_eq!(
        merged("no_attributes"),
        ["[#ffffff]Iron Sword"],
        "tooltip_display hides the attributes"
    );
    assert_eq!(
        merged("own_attributes"),
        [
            "[#ffffff]Stick",
            "",
            "[#aaaaaa]When equipped:",
            "[#aaaaaa]-0.5 Gravity",
            "",
            "[#aaaaaa]When on Feet:",
            "[#5555ff]+10% Speed"
        ]
    );
    // With a player looking (JEI passes the game's player): a weapon's attack as the player's totals, dark green.
    assert_eq!(
        merged("player_sword")[3..],
        ["[#00aa00] 7 Attack Damage", "[#00aa00] 1.6 Attack Speed"]
    );
    // A mod's armour and sword made as Tech Reborn makes them (humanoidArmor; sword of the diamond tier): the game's
    // modifiers, durability and slot; modifiers a suit sets on its stack, and a tooltip_display that hides them.
    assert_eq!(
        merged("mod_armor")[1..],
        [
            "",
            "[#aaaaaa]When on Chest:",
            "[#5555ff]+6 Armor",
            "[#5555ff]+1 Armor Toughness",
            "[#5555ff]+1 Knockback Resistance"
        ]
    );
    assert_eq!(merged("mod_sword")[1..], sword[1..]);
    assert_eq!(
        merged("suit_set")[1..],
        ["", "[#aaaaaa]When on Chest:", "[#5555ff]+8 Armor"]
    );
    assert_eq!(
        merged("suit_hidden").len(),
        1,
        "{:?}",
        merged("suit_hidden")
    );
    // Bundles: the picture under the tooltip, as the game draws it.
    assert_eq!(merged("bundle"), ["[#ffffff]Bundle"]);
    let drawn = |prefix: &str| -> Vec<String> {
        stdout
            .lines()
            .filter_map(|l| l.strip_prefix(prefix))
            .filter_map(|op| {
                match parse_op(op).unwrap_or_else(|| panic!("an op Kiln's Rust reads: {op}")) {
                    HudOp::Sprite { name, rect, .. } => Some(format!(
                        "sprite {name} {} {} {} {}",
                        rect[0], rect[1], rect[2], rect[3]
                    )),
                    HudOp::Item { item, x, y, .. } => Some(format!("item {item} {x} {y}")),
                    HudOp::Text { text, x, y, .. } => Some(format!("text {text} {x} {y}")),
                    _ => None,
                }
            })
            .collect()
    };
    // The box's text at (112, 88); the picture under it from y 100, 96 wide: the stick (put in last, the list's end)
    // in the bottom-right slot, the diamonds left of it with their count, the bar a sixteenth full, no label.
    assert_eq!(
        drawn("bundle_op "),
        [
            "text Bundle 112 88",
            "sprite gui/sprites/container/bundle/slot_background 184 100 24 24",
            "item minecraft:stick 188 104",
            "sprite gui/sprites/container/bundle/slot_background 160 100 24 24",
            "item minecraft:diamond 164 104",
            "text 3 175 113",
            "sprite gui/sprites/container/bundle/bundle_progressbar_fill 113 128 5 13",
            "sprite gui/sprites/container/bundle/bundle_progressbar_border 112 128 96 13"
        ]
    );
    // An empty one: its description, wrapped at 96, and "Empty" over the bar.
    let empty = drawn("empty_bundle_op ");
    assert!(
        empty
            .first()
            .is_some_and(|t| t.starts_with("text Can hold a")),
        "{empty:?}"
    );
    assert!(
        empty.iter().any(
            |t| t.starts_with("sprite gui/sprites/container/bundle/bundle_progressbar_border ")
        )
    );
    assert!(
        empty.last().is_some_and(|t| t.starts_with("text Empty ")),
        "{empty:?}"
    );

    // The book drawn whole (the game draws its glint), the stick by its id; plain and coloured text as before, bold
    // and italic text in one op of the game's text JSON.
    assert!(
        matches!(&ops[0], HudOp::Stack { x, y, size, snbt }
        if [*x, *y, *size] == [10.0, 20.0, 16.0] && snbt.contains("minecraft:stored_enchantments")
            && snbt.contains("minecraft:enchanted_book")),
        "{ops:?}"
    );
    assert_eq!(
        ops[1],
        HudOp::Item {
            x: 30.0,
            y: 20.0,
            size: 16.0,
            item: "minecraft:stick".into()
        }
    );
    assert!(matches!(&ops[2], HudOp::Text { text, argb: 0xFFFF_FFFF, .. } if text == "plain"));
    assert!(matches!(&ops[3], HudOp::Text { text, argb: 0xFFFF_5555, .. } if text == "red"));
    let HudOp::Styled { text, json, .. } = &ops[4] else {
        panic!("styled text: {:?}", ops[4]);
    };
    assert_eq!(text, "Bold and blue");
    assert_eq!(json["extra"][0]["bold"], true);
    assert_eq!(json["extra"][1]["color"], "#5555FF");
    assert_eq!(json["extra"][1]["italic"], true);

    for (what, expected) in [
        ("width_bold", "true"),
        ("width_codes", "true"),
        ("enchantable_sword", "true"),
        ("enchantable_enchanted_sword", "false"),
        ("enchantable_book", "true"),
        ("enchantable_stick", "false"),
        ("instrument_same", "true"),
        ("instrument_apart", "true"),
        ("painting_variant", "true"),
        ("sharpness_sword", "true"),
        ("sharpness_sword_primary", "true"),
        ("sharpness_pickaxe", "false"),
        ("efficiency_pickaxe", "true"),
        ("rarity_book", "RARE"),
        ("rarity_enchanted_sword", "RARE"),
        ("mod_armor_durability", "192"),
        ("mod_armor_slot", "CHEST"),
        ("bundle_image", "BundleTooltip"),
        ("empty_bundle_image", "BundleTooltip"),
        ("stick_image", "false"),
        ("bundle_weight", "1/16"),
        ("full_bundle_weight", "[1, 1]"),
        ("bundle_size", "96x45"),
    ] {
        assert_eq!(
            checks.get(what).map(String::as_str),
            Some(expected),
            "{what}"
        );
    }
}
