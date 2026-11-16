// Kiln's Mixin bridge's table (kiln/java/kiln/bridge.json, D-102), checked against what it names: each row's method in
// Kiln's stand-in of its class, each hook row's hook among the hooks Kiln's Rust forwards (horizon-mixin.toml), and,
// where Horizon's source is at hand (.deps), in Horizon's hook catalog at the commit Kiln is tested on. Reads files only.
//
//   cargo test -p horizon-kiln-mod --test bridge_table

use std::path::{Path, PathBuf};

fn root() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("..")
}

fn table() -> serde_json::Value {
    let text = std::fs::read_to_string(root().join("kiln/java/kiln/bridge.json")).unwrap();
    serde_json::from_str(&text).unwrap()
}

fn text<'a>(row: &'a serde_json::Value, key: &str) -> &'a str {
    row[key].as_str().unwrap_or_else(|| panic!("{key} missing in {row}"))
}

/// How many arguments a JVM method descriptor takes.
fn arguments(desc: &str) -> usize {
    let inner = &desc[1..desc.find(')').unwrap()];
    let (mut n, mut chars) = (0, inner.chars());
    while let Some(c) = chars.next() {
        match c {
            '[' => continue,
            'L' => {
                for c in chars.by_ref() {
                    if c == ';' {
                        break;
                    }
                }
            }
            _ => {}
        }
        n += 1;
    }
    n
}

#[test]
fn every_row_names_a_method_kiln_has_and_a_hook_kiln_forwards() {
    let table = table();
    let rows = table["rows"].as_array().unwrap();
    assert!(!rows.is_empty());
    let toml = std::fs::read_to_string(root().join("kiln/mixins/kiln/horizon-mixin.toml")).unwrap();
    let revision = text(&table, "horizon");
    let catalog = root().join(format!(".deps/horizon-mods/native/target/horizon/{revision}/native-hooks.json"));
    let catalog: Option<serde_json::Value> =
        std::fs::read_to_string(catalog).ok().map(|t| serde_json::from_str(&t).unwrap());
    let mut ids = std::collections::BTreeSet::new();
    for row in rows {
        let id = text(row, "id");
        assert!(ids.insert(id), "{id} twice");
        let (class, method, desc) = (text(row, "class"), text(row, "method"), text(row, "desc"));
        assert!(desc.starts_with('(') && desc.contains(')'), "{id}: {desc}");
        assert!(["client", "server"].contains(&text(row, "side")), "{id}");
        // The stand-in declares the method (by hand, or generated), so the call finds it whether or not a mixin named it.
        let path = class.split('$').next().unwrap().replace('.', "/") + ".java";
        let source = std::fs::read_to_string(root().join("kiln/java").join(&path))
            .or_else(|_| std::fs::read_to_string(root().join("kiln/java-gen").join(&path)))
            .unwrap_or_else(|_| panic!("{id}: no stand-in {path}"));
        assert!(source.contains(&format!(" {method}(")), "{id}: {class} has no {method}");
        let hook = text(row, "hook");
        match text(row, "by") {
            "hook" => {
                assert!(
                    toml.contains(&format!("::native_{hook}\"")),
                    "{id}: Kiln's Rust doesn't forward {hook} (horizon-mixin.toml)"
                );
                let args = row["args"].as_array().unwrap();
                assert_eq!(args.len(), arguments(desc), "{id}: its arguments");
                assert!(row["this"].is_string(), "{id}");
                assert!(row["out"].is_object(), "{id}");
                for (_, out) in row["out"].as_object().unwrap() {
                    let out = out.as_str().unwrap();
                    assert!(
                        out == "skipped" || out == "return" || out.starts_with("arg:"),
                        "{id}: {out}"
                    );
                }
                assert!(
                    row["out"].as_object().unwrap().is_empty() || text(row, "side") == "server",
                    "{id}: the window doesn't wait for Java, so a client row gives nothing back"
                );
            }
            "kiln" => assert!(row["at"].is_string(), "{id}: where Kiln calls it"),
            // Called each client tick, its outs going back to Horizon by key (kiln/java/kiln/Back.java, D-105).
            "tick" => {
                assert_eq!(text(row, "side"), "client", "{id}");
                assert_eq!(row["args"].as_array().unwrap().len(), arguments(desc), "{id}: its arguments");
                let back = ["view_fov", "flying_speed"];
                for (key, out) in row["out"].as_object().unwrap() {
                    assert!(back.contains(&key.as_str()), "{id}: Kiln's Rust has no back key {key}");
                    assert_eq!(out, "return", "{id}");
                }
            }
            other => panic!("{id}: by {other}"),
        }
        if let Some(catalog) = &catalog {
            assert!(
                catalog["hooks"].as_array().unwrap().iter().any(|h| h["name"] == hook),
                "{id}: Horizon {revision} has no hook {hook}"
            );
        }
    }
}

#[test]
fn descriptors_count_their_arguments() {
    assert_eq!(arguments("()V"), 0);
    assert_eq!(arguments("(JDD)V"), 3);
    assert_eq!(arguments("(JLnet/minecraft/client/input/MouseButtonInfo;I)V"), 3);
    assert_eq!(arguments("([I[[Ljava/lang/String;Z)V"), 3);
}
