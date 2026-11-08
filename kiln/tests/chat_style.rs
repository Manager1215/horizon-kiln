// A mod's chat line keeps its style on its way to the game (#82): Calc colours its errors with
// `Component.literal(...).withStyle(ChatFormatting.RED)`, and the window's chat stand-ins sent only `getString()`, so
// they showed in the default colour while `§c` codes in the text worked. They send the component's JSON now, and the
// game reads it as Kiln's reply parser does. Needs a JDK on PATH and the jars from `sh tools/build-java.sh`:
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test chat_style -- --ignored --nocapture

use horizon_kiln_mod::game::kiln_jvm::{Reply, parse};
use std::path::Path;
use std::process::Command;

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mods_chat_line_keeps_its_colour_bold_and_italic() {
    let repo = Path::new(env!("CARGO_MANIFEST_DIR")).join("..");
    let runtime = repo.join("target/kiln/kiln-runtime.jar");
    assert!(runtime.is_file(), "run sh tools/build-java.sh first");
    // The libraries the stand-ins use (Gson for the JSON), as Kiln's Java has them.
    let libs = std::fs::read_dir(repo.join("target/kiln/lib"))
        .into_iter()
        .flatten()
        .flatten()
        .map(|e| e.path())
        .filter(|p| p.extension().is_some_and(|e| e == "jar"));
    let class_path = std::env::join_paths(std::iter::once(runtime).chain(libs)).unwrap();
    let out = Command::new("java")
        .arg("-cp")
        .arg(&class_path)
        .arg(repo.join("kiln/tests/ChatStyle.java"))
        .output()
        .expect("java on PATH: a JDK, as tools/build-java.sh needs");
    assert!(
        out.status.success(),
        "{}",
        String::from_utf8_lossy(&out.stderr)
    );
    let replies: Vec<Reply> = String::from_utf8_lossy(&out.stdout)
        .lines()
        .map(parse)
        .collect();
    println!("{replies:#?}");
    let [
        Reply::ChatComponent(calc),
        Reply::ChatComponent(sky),
        Reply::Chat(code),
    ] = replies.as_slice()
    else {
        panic!("two styled chat lines and a plain one, not {replies:?}");
    };
    assert_eq!(calc["text"], "Division by zero");
    assert_eq!(calc["color"], "red");
    assert_eq!(calc["bold"], true);
    assert_eq!(calc["italic"], true);
    assert_eq!(*sky, serde_json::json!({"text": "sky", "color": "#55AAFF"}));
    assert_eq!(
        code, "§cred by its code",
        "only text: it goes as text, and the code stays in it for the chat to read"
    );
}
