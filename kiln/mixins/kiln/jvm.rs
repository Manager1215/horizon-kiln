// Kiln's JVM: Java in a process of its own, beside the game's. The game's code forbids `unsafe`
// (`#![forbid(unsafe_code)]` in horizon-client), so a native mod can't load a JVM into the game's process
// through JNI; Kiln runs `java kiln.Host` instead and talks to it over its stdin and stdout, a command a
// line (kiln/java/kiln/Host.java has the protocol). The window's game never waits on Java: one thread writes
// the commands from a bounded queue, another reads the replies into a channel the game drains each tick, so a
// slow or stuck mod never stalls the game. A mod stuck for longer than the queue holds stops Java.
//
// The integrated server's game asks Java things and waits for the answer (`ServerLink::call`), at most a
// timeout: its answers (`return`) come on a channel of their own, and so do Java's questions about the world while a
// call runs (`ask`), which the waiting server answers.

use std::ffi::OsString;
use std::io::{BufRead, BufReader, Write};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::mpsc::{
    Receiver, RecvTimeoutError, SyncSender, TrySendError, channel, sync_channel,
};
use std::time::{Duration, Instant};

/// Commands waiting for Java to read them: about a minute of ticks. A pipe holds only 64 KiB and a write
/// to a full one blocks, so the game never writes to Java's stdin itself.
pub const QUEUE: usize = 1200;

/// What Java said.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Reply {
    /// It started: its version.
    Ready(String),
    /// A mod's entrypoint class ran.
    Loaded(String),
    /// A mod's entrypoint class didn't run, and why.
    Failed(String, String),
    /// What the Java mods draw on the HUD changed: all of it, in order.
    Draw(Vec<HudOp>),
    /// A line for the game's log.
    Log(String),
    /// A line for the player's chat.
    Chat(String),
    /// The mods' items with a use of their own: all of them, when that changed.
    Items(Vec<String>),
    /// The server events the mods hear (`join`, `respawn`): all of them, when that changed.
    Listens(Vec<String>),
    /// The pools the mods add to a loot table (its id, the pools as the loot table format's JSON array).
    Loot(String, String),
    /// The answer to a server call.
    Return(Return),
    /// During a server call (its number), Java asks the game something (`block <x> <y> <z>`).
    Ask(u64, Vec<String>),
    /// The mods' blocks with code of their own, each `<id> <what>[,<what>]`: all of them, when that changed.
    Blocks(Vec<String>),
    /// A command was done (its name).
    Done(String),
    /// The Java process ended: why.
    Exited(String),
}

/// Java's answer to a server call: the call's number, the result and what the mod's code did (kiln/java/kiln/Server.java
/// has their forms).
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Return {
    pub call: u64,
    pub result: String,
    pub effects: Vec<String>,
}

/// `\`, tab, newline and carriage return escaped, as Host.java reads them.
pub fn escape(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for c in s.chars() {
        match c {
            '\\' => out.push_str("\\\\"),
            '\t' => out.push_str("\\t"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            c => out.push(c),
        }
    }
    out
}

pub fn unescape(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut chars = s.chars();
    while let Some(c) = chars.next() {
        if c != '\\' {
            out.push(c);
            continue;
        }
        match chars.next() {
            Some('t') => out.push('\t'),
            Some('n') => out.push('\n'),
            Some('r') => out.push('\r'),
            Some(other) => out.push(other),
            None => out.push('\\'),
        }
    }
    out
}

/// One thing a Java mod drew on the HUD, in GUI pixels; colours are ARGB.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum HudOp {
    Fill {
        x: i32,
        y: i32,
        w: i32,
        h: i32,
        argb: u32,
    },
    Text {
        x: i32,
        y: i32,
        argb: u32,
        shadow: bool,
        text: String,
    },
}

/// A draw op as GuiGraphicsExtractor.java writes it: `f x y w h argb` or `t x y argb shadow text`.
pub fn parse_op(op: &str) -> Option<HudOp> {
    let mut parts = op.splitn(6, ' ');
    let kind = parts.next()?;
    let mut number = || parts.next()?.parse::<i64>().ok();
    match kind {
        "f" => Some(HudOp::Fill {
            x: number()? as i32,
            y: number()? as i32,
            w: number()? as i32,
            h: number()? as i32,
            argb: number()? as u32,
        }),
        "t" => {
            let (x, y, argb, shadow) = (number()?, number()?, number()?, number()?);
            Some(HudOp::Text {
                x: x as i32,
                y: y as i32,
                argb: argb as u32,
                shadow: shadow == 1,
                text: parts.next().unwrap_or_default().to_owned(),
            })
        }
        _ => None,
    }
}

/// A reply line: a word and tab-separated, escaped fields.
pub fn parse(line: &str) -> Reply {
    let mut fields = line.split('\t');
    let kind = fields.next().unwrap_or("");
    match kind {
        "draw" => return Reply::Draw(fields.filter_map(|f| parse_op(&unescape(f))).collect()),
        "items" => return Reply::Items(fields.map(unescape).collect()),
        "blocks" => return Reply::Blocks(fields.map(unescape).collect()),
        "ask" => {
            let call = fields.next().and_then(|c| c.parse().ok()).unwrap_or(0);
            return Reply::Ask(call, fields.map(unescape).collect());
        }
        "listens" => return Reply::Listens(fields.map(unescape).collect()),
        "return" => {
            let call = fields.next().and_then(|c| c.parse().ok()).unwrap_or(0);
            let result = fields.next().map(unescape).unwrap_or_default();
            let effects = fields.map(unescape).collect();
            return Reply::Return(Return {
                call,
                result,
                effects,
            });
        }
        _ => {}
    }
    let mut next = || fields.next().map(unescape).unwrap_or_default();
    match kind {
        "ready" => Reply::Ready(next()),
        "loaded" => Reply::Loaded(next()),
        "failed" => {
            let class = next();
            Reply::Failed(class, next())
        }
        "loot" => {
            let id = next();
            Reply::Loot(id, next())
        }
        "log" => Reply::Log(next()),
        "chat" => Reply::Chat(next()),
        "done" => Reply::Done(next()),
        _ => Reply::Log(format!("unknown reply: {line}")),
    }
}

/// The `java` program inside a Java home.
pub fn java_program(home: &Path) -> PathBuf {
    let name = if cfg!(windows) { "java.exe" } else { "java" };
    home.join("bin").join(name)
}

/// A command line: the command and its escaped fields, tab-separated.
pub fn command_line(command: &str, fields: &[&str]) -> String {
    let mut line = command.to_owned();
    for f in fields {
        line.push('\t');
        line.push_str(&escape(f));
    }
    line
}

/// Queues `line` for Java's stdin; never waits.
fn queue(commands: &SyncSender<String>, line: String) -> Result<(), String> {
    commands.try_send(line).map_err(|e| match e {
        TrySendError::Full(_) => {
            format!("Java hasn't read the last {QUEUE} commands: a mod is stuck")
        }
        TrySendError::Disconnected(_) => "Java isn't listening".to_owned(),
    })
}

/// The Java process, the queue its writer thread takes commands from, the replies its reader thread has
/// collected, and the server's link until the game takes it.
pub struct JavaHost {
    child: Child,
    commands: SyncSender<String>,
    replies: Receiver<Reply>,
    server: Option<ServerLink>,
}

/// The integrated server's way to Java: the same command queue, and the answers to its calls (and Java's questions
/// during them). Any thread may hold it; one call at a time.
pub struct ServerLink {
    commands: SyncSender<String>,
    returns: Receiver<Reply>,
}

impl ServerLink {
    /// Sends `command` with `fields` (the first is the call's number, `call`) and waits for its answer, up to
    /// `timeout` after the call or Java's last question. Java's questions meanwhile (`ask`) get `answer`'s answers;
    /// answers to earlier calls, which came too late, are dropped, and questions from them get an empty answer, so
    /// Java never waits for a game that gave up.
    pub fn call(
        &self,
        call: u64,
        command: &str,
        fields: &[&str],
        timeout: Duration,
        answer: &mut dyn FnMut(&[String]) -> String,
    ) -> Result<Return, String> {
        queue(&self.commands, command_line(command, fields))?;
        let mut deadline = Instant::now() + timeout;
        loop {
            let left = deadline.saturating_duration_since(Instant::now());
            match self.returns.recv_timeout(left) {
                Ok(Reply::Return(answer)) if answer.call == call => return Ok(answer),
                Ok(Reply::Ask(asked, what)) => {
                    let value = if asked == call {
                        answer(&what)
                    } else {
                        String::new()
                    };
                    let asked = asked.to_string();
                    queue(&self.commands, command_line("answer", &[&asked, &value]))?;
                    deadline = Instant::now() + timeout;
                }
                Ok(_) => {}
                Err(RecvTimeoutError::Timeout) => {
                    return Err(format!("no answer from Java in {} ms", timeout.as_millis()));
                }
                Err(RecvTimeoutError::Disconnected) => {
                    return Err("Java isn't listening".to_owned());
                }
            }
        }
    }
}

impl JavaHost {
    /// Starts `java kiln.Host` from the Java home `home`, with `class_path` (Kiln's runtime jar first, then
    /// the libraries' and the mods' jars) and Kiln's folder `dir` (the mods' game folder); its stderr, the mods' own output,
    /// goes to `dir`/java.log.
    pub fn start(home: &Path, class_path: &[PathBuf], dir: &Path) -> Result<JavaHost, String> {
        JavaHost::start_logged(home, class_path, dir, "java.log")
    }

    /// As [`JavaHost::start`], the mods' own output going to `dir`/`log`.
    pub fn start_logged(
        home: &Path,
        class_path: &[PathBuf],
        dir: &Path,
        log: &str,
    ) -> Result<JavaHost, String> {
        // Java runs in `dir`: relative paths would be read from there.
        let absolute = |p: &Path| std::path::absolute(p).unwrap_or_else(|_| p.to_path_buf());
        let class_path: Vec<PathBuf> = class_path.iter().map(|p| absolute(p)).collect();
        let dir = &absolute(dir);
        let joined = std::env::join_paths(&class_path).map_err(|e| e.to_string())?;
        let log_file = dir.join(log);
        let log =
            std::fs::File::create(&log_file).map_err(|e| format!("{}: {e}", log_file.display()))?;
        // As an OsString, so a game folder whose name isn't UTF-8 reaches Java unchanged.
        let mut kiln_dir = OsString::from("-Dkiln.dir=");
        kiln_dir.push(dir);
        // In Kiln's folder, as vanilla runs in the game folder: a mod's relative `config/` is FabricLoader's.
        let mut child = Command::new(java_program(home))
            .current_dir(dir)
            .arg("-Xmx512m")
            .arg(kiln_dir)
            .arg("-cp")
            .arg(joined)
            .arg("kiln.Host")
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(log)
            .spawn()
            .map_err(|e| format!("can't run {}: {e}", java_program(home).display()))?;
        let mut stdin = child.stdin.take().ok_or("no stdin for Java")?;
        let stdout = child.stdout.take().ok_or("no stdout for Java")?;
        let (commands, queue) = sync_channel::<String>(QUEUE);
        // Ends when Java stops reading (its stdin breaks) or the game drops the JavaHost.
        std::thread::Builder::new()
            .name("kiln-java-in".into())
            .spawn(move || {
                for line in queue {
                    if writeln!(stdin, "{line}")
                        .and_then(|()| stdin.flush())
                        .is_err()
                    {
                        return;
                    }
                }
            })
            .map_err(|e| e.to_string())?;
        let (sender, replies) = channel();
        let (answers, returns) = channel();
        std::thread::Builder::new()
            .name("kiln-java".into())
            .spawn(move || {
                let mut why = "it closed its output".to_owned();
                for line in BufReader::new(stdout).lines() {
                    match line {
                        // Nobody waiting for an answer any more is no reason to stop.
                        Ok(line) => match parse(&line) {
                            reply @ (Reply::Return(_) | Reply::Ask(..)) => {
                                let _ = answers.send(reply);
                            }
                            reply => {
                                if sender.send(reply).is_err() {
                                    return;
                                }
                            }
                        },
                        Err(e) => {
                            why = e.to_string();
                            break;
                        }
                    }
                }
                let _ = sender.send(Reply::Exited(why));
            })
            .map_err(|e| e.to_string())?;
        let server = Some(ServerLink {
            commands: commands.clone(),
            returns,
        });
        Ok(JavaHost {
            child,
            commands,
            replies,
            server,
        })
    }

    /// Queues a command (`load`, `font`, `key`, `adv`, `tick`) with its fields; never waits.
    pub fn send(&mut self, command: &str, fields: &[&str]) -> Result<(), String> {
        queue(&self.commands, command_line(command, fields))
    }

    /// The integrated server's link to this Java, once.
    pub fn take_server_link(&mut self) -> Option<ServerLink> {
        self.server.take()
    }

    /// The replies since the last call, oldest first; never waits.
    pub fn replies(&self) -> Vec<Reply> {
        self.replies.try_iter().collect()
    }
}

impl Drop for JavaHost {
    fn drop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fields_survive_escaping() {
        let text = "a\tb\nc\\d\re";
        assert_eq!(escape(text), "a\\tb\\nc\\\\d\\re");
        assert_eq!(unescape(&escape(text)), text);
        assert_eq!(unescape("trailing\\"), "trailing\\");
    }

    /// A "Java" that never reads its stdin, as a mod stuck in a tick: the game's sends return at once, and
    /// once the queue is full they fail instead of blocking the game.
    #[cfg(unix)]
    #[test]
    fn a_stuck_java_never_blocks_the_game() {
        use std::os::unix::fs::PermissionsExt;
        let home = std::env::temp_dir().join(format!("kiln-stuck-java-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&home);
        std::fs::create_dir_all(home.join("bin")).unwrap();
        let program = java_program(&home);
        std::fs::write(&program, "#!/bin/sh\nexec sleep 60\n").unwrap();
        std::fs::set_permissions(&program, std::fs::Permissions::from_mode(0o755)).unwrap();
        let mut java = JavaHost::start(&home, &[], &home).unwrap();
        let line = "x".repeat(200);
        let started = std::time::Instant::now();
        let mut failed = None;
        for i in 0..QUEUE * 10 {
            if let Err(e) = java.send("tick", &[&line]) {
                failed = Some((i, e));
                break;
            }
        }
        assert!(
            started.elapsed() < std::time::Duration::from_secs(5),
            "{:?}",
            started.elapsed()
        );
        let (sent, why) = failed.expect("the queue should fill");
        // The writer thread takes a few commands into the pipe (64 KiB) before it blocks, then the queue fills.
        assert!(sent >= QUEUE, "{sent}");
        assert!(why.contains("stuck"), "{why}");
        drop(java);
        let _ = std::fs::remove_dir_all(&home);
    }

    /// A "Java" that answers each call twice, an old call's number first: the server's call gets its own
    /// answer, never the other, and the window's replies never see them.
    #[cfg(unix)]
    #[test]
    fn a_call_gets_its_own_answer() {
        use std::os::unix::fs::PermissionsExt;
        let home = std::env::temp_dir().join(format!("kiln-call-java-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&home);
        std::fs::create_dir_all(home.join("bin")).unwrap();
        let program = java_program(&home);
        let script = "#!/bin/sh\nwhile IFS=\"$(printf '\\t')\" read -r command call rest; do\n  \
                      printf 'return\\t0\\tpass\\n'\n  \
                      printf 'return\\t%s\\tsuccess\\thealth 24.0\\n' \"$call\"\n  \
                      printf 'done\\t%s\\n' \"$command\"\ndone\n";
        std::fs::write(&program, script).unwrap();
        std::fs::set_permissions(&program, std::fs::Permissions::from_mode(0o755)).unwrap();
        let mut java = JavaHost::start(&home, &[], &home).unwrap();
        let link = java.take_server_link().unwrap();
        assert!(java.take_server_link().is_none());
        let timeout = Duration::from_secs(5);
        for call in [3, 4] {
            let answer = link
                .call(call, "use", &[&call.to_string()], timeout, &mut |_| {
                    unreachable!("nothing asked")
                })
                .unwrap();
            assert_eq!(answer.call, call);
            assert_eq!(answer.result, "success");
            assert_eq!(answer.effects, ["health 24.0"]);
        }
        std::thread::sleep(Duration::from_millis(100));
        assert_eq!(
            java.replies(),
            [Reply::Done("use".into()), Reply::Done("use".into())]
        );
        drop(java);
        assert!(
            link.call(5, "use", &["5"], timeout, &mut |_| String::new())
                .is_err()
        );
        let _ = std::fs::remove_dir_all(&home);
    }

    /// A "Java" that asks about a block during each call and returns what it was told: the call's own `answer`
    /// answers it.
    #[cfg(unix)]
    #[test]
    fn java_asks_the_game_during_a_call() {
        use std::os::unix::fs::PermissionsExt;
        let home = std::env::temp_dir().join(format!("kiln-ask-java-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&home);
        std::fs::create_dir_all(home.join("bin")).unwrap();
        let program = java_program(&home);
        let script = "#!/bin/sh\nwhile IFS=\"$(printf '\\t')\" read -r command a b; do\n  \
                      case \"$command\" in\n    \
                      use_block) printf 'ask\\t%s\\tblock\\t1\\t2\\t3\\n' \"$a\" ;;\n    \
                      answer) printf 'return\\t%s\\tsuccess\\tgot %s\\n' \"$a\" \"$b\" ;;\n  \
                      esac\ndone\n";
        std::fs::write(&program, script).unwrap();
        std::fs::set_permissions(&program, std::fs::Permissions::from_mode(0o755)).unwrap();
        let mut java = JavaHost::start(&home, &[], &home).unwrap();
        let link = java.take_server_link().unwrap();
        let mut asked = Vec::new();
        let answer = link
            .call(
                7,
                "use_block",
                &["7"],
                Duration::from_secs(5),
                &mut |what| {
                    asked.push(what.to_vec());
                    "minecraft:stone".to_owned()
                },
            )
            .unwrap();
        assert_eq!(asked, [["block", "1", "2", "3"]]);
        assert_eq!(answer.effects, ["got minecraft:stone"]);
        drop(java);
        let _ = std::fs::remove_dir_all(&home);
    }

    #[test]
    fn replies_parse() {
        assert_eq!(
            parse("ready\t25.0.2 (Eclipse)"),
            Reply::Ready("25.0.2 (Eclipse)".into())
        );
        assert_eq!(
            parse("draw\tf 1 2 30 40 2013265920\tt 5 6 4294967295 1 Hello\\tworld 1:02.345"),
            Reply::Draw(vec![
                HudOp::Fill {
                    x: 1,
                    y: 2,
                    w: 30,
                    h: 40,
                    argb: 0x7800_0000
                },
                HudOp::Text {
                    x: 5,
                    y: 6,
                    argb: 0xFFFF_FFFF,
                    shadow: true,
                    text: "Hello\tworld 1:02.345".into()
                },
            ])
        );
        assert_eq!(parse("draw"), Reply::Draw(Vec::new()));
        assert_eq!(
            parse_op("t 1 2 3 0 "),
            Some(HudOp::Text {
                x: 1,
                y: 2,
                argb: 3,
                shadow: false,
                text: String::new()
            })
        );
        assert_eq!(parse_op("x 1"), None);
        assert_eq!(
            parse("failed\ta.B\tjava.lang.ClassNotFoundException: a.B"),
            Reply::Failed("a.B".into(), "java.lang.ClassNotFoundException: a.B".into())
        );
        assert_eq!(parse("done\ttick"), Reply::Done("tick".into()));
        assert_eq!(
            parse("items\theart_crystals:heart_crystal\ta:b"),
            Reply::Items(vec!["heart_crystals:heart_crystal".into(), "a:b".into()])
        );
        assert_eq!(parse("items"), Reply::Items(Vec::new()));
        assert_eq!(
            parse("ask\t9\tblock\t1\t-2\t3"),
            Reply::Ask(9, vec!["block".into(), "1".into(), "-2".into(), "3".into()])
        );
        assert_eq!(
            parse("blocks\tmymod:lamp use,step"),
            Reply::Blocks(vec!["mymod:lamp use,step".into()])
        );
        assert_eq!(
            parse("loot\tminecraft:chests/simple_dungeon\t[{\"rolls\":1,\"entries\":[]}]"),
            Reply::Loot(
                "minecraft:chests/simple_dungeon".into(),
                "[{\"rolls\":1,\"entries\":[]}]".into()
            )
        );
        assert_eq!(
            parse("listens\tjoin\trespawn"),
            Reply::Listens(vec!["join".into(), "respawn".into()])
        );
        assert_eq!(
            parse("return\t7\tsuccess\thealth 24.0\toverlay {\"text\":\"a\\tb\"}"),
            Reply::Return(Return {
                call: 7,
                result: "success".into(),
                effects: vec!["health 24.0".into(), "overlay {\"text\":\"a\tb\"}".into()],
            })
        );
        assert_eq!(parse("chat"), Reply::Chat(String::new()));
        assert!(matches!(parse("what"), Reply::Log(_)));
    }
}
