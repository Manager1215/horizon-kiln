// Kiln's JVM: Java in a process of its own, beside the game's. The game's code forbids `unsafe`
// (`#![forbid(unsafe_code)]` in horizon-client), so a native mod can't load a JVM into the game's process
// through JNI; Kiln runs `java kiln.Host` instead and talks to it over its stdin and stdout, a command a
// line (kiln/java/kiln/Host.java has the protocol). Nothing here waits on Java: a thread reads its replies
// into a channel the game drains each tick, so a slow or stuck mod never stalls the game.

use std::io::{BufRead, BufReader, Write};
use std::path::{Path, PathBuf};
use std::process::{Child, ChildStdin, Command, Stdio};
use std::sync::mpsc::{Receiver, channel};

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
    /// A command was done (its name).
    Done(String),
    /// The Java process ended: why.
    Exited(String),
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
    if kind == "draw" {
        return Reply::Draw(fields.filter_map(|f| parse_op(&unescape(f))).collect());
    }
    let mut next = || fields.next().map(unescape).unwrap_or_default();
    match kind {
        "ready" => Reply::Ready(next()),
        "loaded" => Reply::Loaded(next()),
        "failed" => {
            let class = next();
            Reply::Failed(class, next())
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

/// The Java process and the replies its reader thread has collected.
pub struct JavaHost {
    child: Child,
    stdin: ChildStdin,
    replies: Receiver<Reply>,
}

impl JavaHost {
    /// Starts `java kiln.Host` from the Java home `home`, with `class_path` (Kiln's runtime jar first, then
    /// the mods' jars) and Kiln's folder `dir` (the mods' game folder); its stderr, the mods' own output,
    /// goes to `dir`/java.log.
    pub fn start(home: &Path, class_path: &[PathBuf], dir: &Path) -> Result<JavaHost, String> {
        let joined = std::env::join_paths(class_path).map_err(|e| e.to_string())?;
        let log_file = dir.join("java.log");
        let log =
            std::fs::File::create(&log_file).map_err(|e| format!("{}: {e}", log_file.display()))?;
        let mut child = Command::new(java_program(home))
            .arg("-Xmx512m")
            .arg(format!("-Dkiln.dir={}", dir.display()))
            .arg("-cp")
            .arg(joined)
            .arg("kiln.Host")
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(log)
            .spawn()
            .map_err(|e| format!("can't run {}: {e}", java_program(home).display()))?;
        let stdin = child.stdin.take().ok_or("no stdin for Java")?;
        let stdout = child.stdout.take().ok_or("no stdout for Java")?;
        let (sender, replies) = channel();
        std::thread::Builder::new()
            .name("kiln-java".into())
            .spawn(move || {
                let mut why = "it closed its output".to_owned();
                for line in BufReader::new(stdout).lines() {
                    match line {
                        Ok(line) => {
                            if sender.send(parse(&line)).is_err() {
                                return;
                            }
                        }
                        Err(e) => {
                            why = e.to_string();
                            break;
                        }
                    }
                }
                let _ = sender.send(Reply::Exited(why));
            })
            .map_err(|e| e.to_string())?;
        Ok(JavaHost {
            child,
            stdin,
            replies,
        })
    }

    /// Sends a command (`load`, `font`, `key`, `adv`, `tick`) with its fields.
    pub fn send(&mut self, command: &str, fields: &[&str]) -> Result<(), String> {
        let mut line = command.to_owned();
        for f in fields {
            line.push('\t');
            line.push_str(&escape(f));
        }
        writeln!(self.stdin, "{line}")
            .and_then(|()| self.stdin.flush())
            .map_err(|e| format!("Java isn't listening: {e}"))
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
        assert_eq!(parse("chat"), Reply::Chat(String::new()));
        assert!(matches!(parse("what"), Reply::Log(_)));
    }
}
