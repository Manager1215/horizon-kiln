// The tests' own NBT: a block entity's compound goes between Kiln and its Java as the network's binary NBT (D-140),
// and the tests write and read it as SNBT, as Kiln's Java writes it (kiln/java/kiln/Snbt.java). Strings are kept as
// Rust's; a test's text is plain.
#![allow(dead_code)]

use std::collections::BTreeMap;

#[derive(Clone, Debug, PartialEq)]
enum Tag {
    Byte(i8),
    Short(i16),
    Int(i32),
    Long(i64),
    Float(f32),
    Double(f64),
    Bytes(Vec<i8>),
    Text(String),
    List(Vec<Tag>),
    Compound(BTreeMap<Key, Tag>),
    Ints(Vec<i32>),
    Longs(Vec<i64>),
}

/// A compound's key, ordered as Java orders strings (by UTF-16 units).
#[derive(Clone, Debug, PartialEq, Eq)]
struct Key(String);

impl Ord for Key {
    fn cmp(&self, other: &Self) -> std::cmp::Ordering {
        self.0.encode_utf16().cmp(other.0.encode_utf16())
    }
}

impl PartialOrd for Key {
    fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
        Some(self.cmp(other))
    }
}

impl Tag {
    fn id(&self) -> u8 {
        match self {
            Tag::Byte(_) => 1,
            Tag::Short(_) => 2,
            Tag::Int(_) => 3,
            Tag::Long(_) => 4,
            Tag::Float(_) => 5,
            Tag::Double(_) => 6,
            Tag::Bytes(_) => 7,
            Tag::Text(_) => 8,
            Tag::List(_) => 9,
            Tag::Compound(_) => 10,
            Tag::Ints(_) => 11,
            Tag::Longs(_) => 12,
        }
    }
}

/// The compound `text` writes, as binary NBT. Panics on text that isn't one.
pub fn nbt(text: &str) -> Vec<u8> {
    let mut p = Parser {
        s: text.chars().collect(),
        i: 0,
    };
    let tag = p.value();
    assert!(matches!(tag, Tag::Compound(_)), "not a compound: {text}");
    let mut out = vec![10];
    write(&tag, &mut out);
    out
}

/// The compound `bytes` hold, as Kiln's Java writes it in SNBT. Panics on bytes that aren't one.
pub fn snbt(bytes: &[u8]) -> String {
    assert_eq!(bytes.first(), Some(&10), "not a compound: {bytes:?}");
    let mut r = Reader { b: bytes, i: 1 };
    let tag = r.payload(10);
    assert_eq!(r.i, bytes.len(), "bytes after the compound");
    let mut out = String::new();
    text(&tag, &mut out);
    out
}

fn write(tag: &Tag, out: &mut Vec<u8>) {
    match tag {
        Tag::Byte(v) => out.push(*v as u8),
        Tag::Short(v) => out.extend(v.to_be_bytes()),
        Tag::Int(v) => out.extend(v.to_be_bytes()),
        Tag::Long(v) => out.extend(v.to_be_bytes()),
        Tag::Float(v) => out.extend(v.to_be_bytes()),
        Tag::Double(v) => out.extend(v.to_be_bytes()),
        Tag::Bytes(a) => {
            out.extend((a.len() as i32).to_be_bytes());
            out.extend(a.iter().map(|b| *b as u8));
        }
        Tag::Text(s) => utf(s, out),
        Tag::List(list) => {
            let id = list.first().map_or(0, Tag::id);
            let mixed = list.iter().any(|t| t.id() != id);
            out.push(if mixed { 10 } else { id });
            out.extend((list.len() as i32).to_be_bytes());
            for t in list {
                if mixed && !matches!(t, Tag::Compound(_)) {
                    out.push(t.id());
                    utf("", out);
                    write(t, out);
                    out.push(0);
                } else {
                    write(t, out);
                }
            }
        }
        Tag::Compound(map) => {
            for (k, v) in map {
                out.push(v.id());
                utf(&k.0, out);
                write(v, out);
            }
            out.push(0);
        }
        Tag::Ints(a) => {
            out.extend((a.len() as i32).to_be_bytes());
            a.iter().for_each(|v| out.extend(v.to_be_bytes()));
        }
        Tag::Longs(a) => {
            out.extend((a.len() as i32).to_be_bytes());
            a.iter().for_each(|v| out.extend(v.to_be_bytes()));
        }
    }
}

/// Java's modified UTF-8 (`DataOutput.writeUTF`).
fn utf(s: &str, out: &mut Vec<u8>) {
    let mut bytes = Vec::new();
    for unit in s.encode_utf16() {
        match unit {
            1..=0x7f => bytes.push(unit as u8),
            0 | 0x80..=0x7ff => {
                bytes.push(0xc0 | (unit >> 6) as u8);
                bytes.push(0x80 | (unit & 0x3f) as u8);
            }
            _ => {
                bytes.push(0xe0 | (unit >> 12) as u8);
                bytes.push(0x80 | (unit >> 6 & 0x3f) as u8);
                bytes.push(0x80 | (unit & 0x3f) as u8);
            }
        }
    }
    out.extend((bytes.len() as u16).to_be_bytes());
    out.extend(bytes);
}

struct Reader<'a> {
    b: &'a [u8],
    i: usize,
}

impl Reader<'_> {
    fn take<const N: usize>(&mut self) -> [u8; N] {
        let a = self.b[self.i..self.i + N].try_into().unwrap();
        self.i += N;
        a
    }

    fn len(&mut self) -> usize {
        i32::from_be_bytes(self.take()) as usize
    }

    fn utf(&mut self) -> String {
        let n = u16::from_be_bytes(self.take()) as usize;
        let bytes = &self.b[self.i..self.i + n];
        self.i += n;
        let mut units = Vec::new();
        let mut k = 0;
        while k < bytes.len() {
            let b = u16::from(bytes[k]);
            let (unit, step) = match b {
                0..=0x7f => (b, 1),
                0xc0..=0xdf => ((b & 0x1f) << 6 | u16::from(bytes[k + 1]) & 0x3f, 2),
                _ => (
                    (b & 0x0f) << 12
                        | (u16::from(bytes[k + 1]) & 0x3f) << 6
                        | u16::from(bytes[k + 2]) & 0x3f,
                    3,
                ),
            };
            units.push(unit);
            k += step;
        }
        String::from_utf16(&units).unwrap()
    }

    fn payload(&mut self, id: u8) -> Tag {
        match id {
            1 => Tag::Byte(self.take::<1>()[0] as i8),
            2 => Tag::Short(i16::from_be_bytes(self.take())),
            3 => Tag::Int(i32::from_be_bytes(self.take())),
            4 => Tag::Long(i64::from_be_bytes(self.take())),
            5 => Tag::Float(f32::from_be_bytes(self.take())),
            6 => Tag::Double(f64::from_be_bytes(self.take())),
            7 => {
                let n = self.len();
                Tag::Bytes((0..n).map(|_| self.take::<1>()[0] as i8).collect())
            }
            8 => Tag::Text(self.utf()),
            9 => {
                let elements = self.take::<1>()[0];
                let n = self.len();
                Tag::List(
                    (0..n)
                        .map(|_| match self.payload(elements) {
                            Tag::Compound(m)
                                if m.len() == 1 && m.contains_key(&Key(String::new())) =>
                            {
                                m.into_values().next().unwrap()
                            }
                            t => t,
                        })
                        .collect(),
                )
            }
            10 => {
                let mut map = BTreeMap::new();
                loop {
                    let t = self.take::<1>()[0];
                    if t == 0 {
                        break Tag::Compound(map);
                    }
                    let k = self.utf();
                    map.insert(Key(k), self.payload(t));
                }
            }
            11 => {
                let n = self.len();
                Tag::Ints((0..n).map(|_| i32::from_be_bytes(self.take())).collect())
            }
            12 => {
                let n = self.len();
                Tag::Longs((0..n).map(|_| i64::from_be_bytes(self.take())).collect())
            }
            other => panic!("unknown NBT type {other}"),
        }
    }
}

/// Java's `Float.toString` and `Double.toString` for the plain numbers tests meet: a whole one ends in `.0`.
fn decimal(v: f64, s: String) -> String {
    if v.is_finite() && v.fract() == 0.0 && v.abs() < 1e7 && !s.contains('e') {
        format!("{s}.0")
    } else {
        s
    }
}

fn text(tag: &Tag, out: &mut String) {
    match tag {
        Tag::Byte(v) => out.push_str(&format!("{v}b")),
        Tag::Short(v) => out.push_str(&format!("{v}s")),
        Tag::Int(v) => out.push_str(&v.to_string()),
        Tag::Long(v) => out.push_str(&format!("{v}L")),
        Tag::Float(v) => out.push_str(&format!("{}f", decimal(f64::from(*v), v.to_string()))),
        Tag::Double(v) => out.push_str(&format!("{}d", decimal(*v, v.to_string()))),
        Tag::Bytes(a) => {
            let items: Vec<String> = a.iter().map(|v| format!("{v}B")).collect();
            out.push_str(&format!("[B;{}]", items.join(",")));
        }
        Tag::Ints(a) => {
            let items: Vec<String> = a.iter().map(|v| v.to_string()).collect();
            out.push_str(&format!("[I;{}]", items.join(",")));
        }
        Tag::Longs(a) => {
            let items: Vec<String> = a.iter().map(|v| format!("{v}L")).collect();
            out.push_str(&format!("[L;{}]", items.join(",")));
        }
        Tag::Text(s) => quote(s, out),
        Tag::List(list) => {
            out.push('[');
            for (k, t) in list.iter().enumerate() {
                if k > 0 {
                    out.push(',');
                }
                text(t, out);
            }
            out.push(']');
        }
        Tag::Compound(map) => {
            out.push('{');
            for (k, (key, v)) in map.iter().enumerate() {
                if k > 0 {
                    out.push(',');
                }
                if bare(&key.0) {
                    out.push_str(&key.0);
                } else {
                    quote(&key.0, out);
                }
                out.push(':');
                text(v, out);
            }
            out.push('}');
        }
    }
}

fn bare(key: &str) -> bool {
    let Some(first) = key.chars().next() else {
        return false;
    };
    !key.eq_ignore_ascii_case("true")
        && !key.eq_ignore_ascii_case("false")
        && (first.is_ascii_alphabetic() || first == '.' || first == '_')
        && key
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '_' | '+' | '-'))
}

fn quote(s: &str, out: &mut String) {
    out.push('"');
    for c in s.chars() {
        match c {
            '\\' => out.push_str("\\\\"),
            '"' => out.push_str("\\\""),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            '\u{8}' => out.push_str("\\b"),
            '\u{c}' => out.push_str("\\f"),
            c => out.push(c),
        }
    }
    out.push('"');
}

struct Parser {
    s: Vec<char>,
    i: usize,
}

impl Parser {
    fn skip(&mut self) {
        while self.i < self.s.len() && self.s[self.i].is_whitespace() {
            self.i += 1;
        }
    }

    fn peek(&self) -> char {
        self.s[self.i]
    }

    fn expect(&mut self, c: char) {
        assert_eq!(self.peek(), c, "expected {c} at {}", self.i);
        self.i += 1;
    }

    fn value(&mut self) -> Tag {
        self.skip();
        match self.peek() {
            '{' => self.compound(),
            '[' => self.list(),
            '"' | '\'' => Tag::Text(self.quoted()),
            _ => {
                let w = self.word();
                scalar(&w)
            }
        }
    }

    fn compound(&mut self) -> Tag {
        self.i += 1;
        let mut map = BTreeMap::new();
        self.skip();
        if self.peek() == '}' {
            self.i += 1;
            return Tag::Compound(map);
        }
        loop {
            self.skip();
            let key = if matches!(self.peek(), '"' | '\'') {
                self.quoted()
            } else {
                self.word()
            };
            self.skip();
            self.expect(':');
            let v = self.value();
            map.insert(Key(key), v);
            self.skip();
            if self.peek() == ',' {
                self.i += 1;
                continue;
            }
            self.expect('}');
            return Tag::Compound(map);
        }
    }

    fn list(&mut self) -> Tag {
        self.i += 1;
        if self.i + 1 < self.s.len() && self.s[self.i + 1] == ';' && "BIL".contains(self.s[self.i])
        {
            let kind = self.s[self.i];
            self.i += 2;
            let mut values = Vec::new();
            self.skip();
            while self.peek() != ']' {
                let w = self.word();
                let digits = w.trim_end_matches(['B', 'b', 'L', 'l']);
                values.push(digits.parse::<i64>().unwrap());
                self.skip();
                if self.peek() == ',' {
                    self.i += 1;
                    self.skip();
                }
            }
            self.expect(']');
            return match kind {
                'B' => Tag::Bytes(values.iter().map(|v| *v as i8).collect()),
                'I' => Tag::Ints(values.iter().map(|v| *v as i32).collect()),
                _ => Tag::Longs(values),
            };
        }
        let mut list = Vec::new();
        self.skip();
        if self.peek() == ']' {
            self.i += 1;
            return Tag::List(list);
        }
        loop {
            list.push(self.value());
            self.skip();
            if self.peek() == ',' {
                self.i += 1;
                continue;
            }
            self.expect(']');
            return Tag::List(list);
        }
    }

    fn quoted(&mut self) -> String {
        let quote = self.s[self.i];
        self.i += 1;
        let mut out = String::new();
        loop {
            let c = self.s[self.i];
            self.i += 1;
            if c == quote {
                return out;
            }
            if c == '\\' {
                let e = self.s[self.i];
                self.i += 1;
                match e {
                    'n' => out.push('\n'),
                    'r' => out.push('\r'),
                    't' => out.push('\t'),
                    'b' => out.push('\u{8}'),
                    'f' => out.push('\u{c}'),
                    'u' => {
                        let hex: String = self.s[self.i..self.i + 4].iter().collect();
                        out.push(char::from_u32(u32::from_str_radix(&hex, 16).unwrap()).unwrap());
                        self.i += 4;
                    }
                    e => out.push(e),
                }
            } else {
                out.push(c);
            }
        }
    }

    fn word(&mut self) -> String {
        let start = self.i;
        while self.i < self.s.len()
            && !",:]}".contains(self.s[self.i])
            && !self.s[self.i].is_whitespace()
        {
            self.i += 1;
        }
        self.s[start..self.i].iter().collect()
    }
}

/// A bare word, as Kiln's Java reads one: a typed number, a boolean (a byte), else a string.
fn scalar(w: &str) -> Tag {
    let integer = |s: &str| {
        let d = s.strip_prefix(['+', '-']).unwrap_or(s);
        !d.is_empty() && d.chars().all(|c| c.is_ascii_digit())
    };
    let decimal = |s: &str| {
        integer(s)
            || s.parse::<f64>().is_ok()
                && s.chars().all(|c| c.is_ascii_digit() || "+-.eE".contains(c))
    };
    match w {
        "true" => return Tag::Byte(1),
        "false" => return Tag::Byte(0),
        _ => {}
    }
    if integer(w) {
        if let Ok(v) = w.parse() {
            return Tag::Int(v);
        }
        return Tag::Text(w.to_owned());
    }
    if decimal(w) {
        return Tag::Double(w.parse().unwrap());
    }
    let Some(last) = w.chars().last() else {
        return Tag::Text(String::new());
    };
    let n = &w[..w.len() - last.len_utf8()];
    let parsed = match last.to_ascii_lowercase() {
        'b' if integer(n) => n.parse().ok().map(Tag::Byte),
        's' if integer(n) => n.parse().ok().map(Tag::Short),
        'l' if integer(n) => n.parse().ok().map(Tag::Long),
        'f' if decimal(n) => n.parse().ok().map(Tag::Float),
        'd' if decimal(n) => n.parse().ok().map(Tag::Double),
        _ => None,
    };
    parsed.unwrap_or_else(|| Tag::Text(w.to_owned()))
}

#[test]
fn snbt_goes_to_binary_and_back() {
    for text in [
        "{}",
        r#"{loads:1,placed_in:"minecraft:overworld",started:1001L,ticks:45}"#,
        r#"{Items:[{Slot:0b,count:3,id:"minecraft:cobblestone"}],crushed:1,progress:0}"#,
        r#"{a:[B;1B,-2B],b:[I;3,4],c:[L;5L],d:1.5f,e:2.0d,f:-3s,"g h":"x\"y",l:[]}"#,
        r#"{m:[1,"two",{three:3}]}"#,
    ] {
        assert_eq!(snbt(&nbt(text)), text);
    }
}
