// A Java mod reading blocks away from the player (#219, D-186), with real Java, as the game drives it: Kiln's test mod
// (kiln/test-mods/chunks) reads blocks of the chunk at 6, 6 from a thread of its own on its key, V, as a block scanner
// does, through `ClientLevel.getBlockState` and through `getChunk(..).getSections()`. Java asks for the chunk
// (`want_chunk`), the game gives its sections with its next tick (`chunk_blocks`, here written as the game's
// `chunk_blocks` in horizon.rs writes them), and the mod's thread, which waited, reads them. Needs a JDK and the jars
// from `sh tools/build-java.sh`.
//
//   sh tools/build-java.sh && cargo test -p horizon-kiln-mod --test client_chunks -- --ignored --nocapture

use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use horizon_kiln_mod::game::kiln as m;

/// A game folder with Kiln's runtime, the libraries and the test mod; the mod's data folder in it.
fn game_folder() -> (PathBuf, PathBuf) {
    let built = Path::new(env!("CARGO_MANIFEST_DIR")).join("../target/kiln");
    let jar = built.join("test/kiln-test-chunks.jar");
    assert!(jar.is_file(), "run sh tools/build-java.sh first");
    let game = std::env::temp_dir().join(format!("kiln-chunks-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&game);
    let kiln = game.join("kiln");
    std::fs::create_dir_all(kiln.join("mods")).unwrap();
    std::fs::create_dir_all(kiln.join("lib")).unwrap();
    std::fs::copy(
        built.join("kiln-runtime.jar"),
        kiln.join("kiln-runtime.jar"),
    )
    .unwrap();
    for lib in m::jars(&built.join("lib")) {
        std::fs::copy(&lib, kiln.join("lib").join(lib.file_name().unwrap())).unwrap();
    }
    std::fs::copy(&jar, kiln.join("mods/kiln-test-chunks.jar")).unwrap();
    let data = game.join("horizon-mods/.horizon-mod-data/kiln");
    std::fs::create_dir_all(&data).unwrap();
    (game, data)
}

/// The chunk at 6, 6 as the game writes it for Java: 24 sections of air from y -64, but the one from y 64 holds
/// diamond ore at 100, 64, 100 (its cell x 4, y 0, z 4).
fn far_chunk() -> String {
    let mut sections = vec!["minecraft:air".to_owned(); 24];
    let mut cells = vec!["00"; 4096];
    cells[4 << 4 | 4] = "01";
    sections[(64 + 64) / 16] = format!("minecraft:air|minecraft:diamond_ore#{}", cells.concat());
    sections.join(";")
}

/// The window's game in a world, its player at the origin, the chunks around it and the one at 6, 6 loaded; the
/// blocks of the chunks Java asked for.
fn in_world(blocks: Vec<(i32, i32, String)>) -> m::Snapshot {
    m::Snapshot {
        connected: true,
        in_world: true,
        game_time: 10,
        dimension: "minecraft:overworld".into(),
        player: Some(m::PlayerSnapshot {
            uuid: 0x00c0_ffee,
            name: "Kiln".into(),
            dimension: "minecraft:overworld".into(),
            position: [0.5, 70.0, 0.5],
            health: 20.0,
            ..Default::default()
        }),
        state: m::WindowState {
            render_distance: 12,
            height: (-64, 384),
            chunks: vec![[0, 0], [6, 6]],
            chunk_blocks: blocks,
            ..Default::default()
        },
        ..m::Snapshot::default()
    }
}

/// Ticks at the game's pace until a chat line passes `done`, or for `at_most`, answering the chunks Java asks for as
/// the game does; the chat heard and the chunks asked for.
fn tick_for(
    data: &Path,
    at_most: Duration,
    done: impl Fn(&str) -> bool,
) -> (Vec<String>, Vec<(i32, i32)>) {
    let deadline = Instant::now() + at_most;
    let mut chat = Vec::new();
    let mut asked = Vec::new();
    while Instant::now() < deadline && !chat.iter().any(|l: &String| done(l)) {
        let wanted = m::wanted_chunks(8);
        asked.extend(wanted.iter().copied());
        let blocks = wanted
            .into_iter()
            .map(|(x, z)| {
                (
                    x,
                    z,
                    if (x, z) == (6, 6) {
                        far_chunk()
                    } else {
                        String::new()
                    },
                )
            })
            .collect();
        chat.extend(m::tick(data, || None, || vec![6; 128], in_world(blocks)));
        std::thread::sleep(m::TICK);
    }
    (chat, asked)
}

#[test]
#[ignore = "needs a JDK and sh tools/build-java.sh"]
fn a_mod_reads_a_far_chunks_blocks() {
    let (game, data) = game_folder();
    let (chat, _) = tick_for(&data, Duration::from_secs(20), |l| {
        l.contains("Java mods running")
    });
    assert!(
        chat.iter()
            .any(|l| l.starts_with("Kiln: 1 of 1 Java mods running")),
        "{chat:?}"
    );

    // The mod's thread reads the chunk: Java asks for it once, the game's next tick gives it, the thread reads the ore,
    // the air beside it, the same cell through the chunk's sections; a chunk the game hasn't is none.
    m::key("key.keyboard.v");
    let (chat, asked) = tick_for(&data, Duration::from_secs(10), |l| {
        l.starts_with("Test chunks: ")
    });
    let said = chat
        .iter()
        .find(|l| l.starts_with("Test chunks: "))
        .expect("the mod said nothing");
    assert_eq!(
        said, "Test chunks: minecraft:diamond_ore minecraft:air minecraft:diamond_ore none",
        "chunks asked for: {asked:?}"
    );
    assert_eq!(
        asked,
        [(6, 6)],
        "Java asks for the chunk once, while it is fresh"
    );
    let _ = std::fs::remove_dir_all(&game);
}
