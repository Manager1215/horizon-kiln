// The Java mods' GUI pictures in the game's atlas (D-160). Horizon's GUI atlas has vanilla's sprites and its content
// mods'; a Java mod's own (JEI's buttons and backgrounds, a machine's GUI texture) it lacks. Java sends each picture
// the first time a mod draws it (`sprite <name> <w> <h> <base64 RGBA>`, kiln.Sprites); Kiln packs them into one sheet
// the size of the one image a native mod may upload into the atlas (`native_mod_image`, API 355: the slot
// `horizon/modimage`, 1024 square) and names each picture's part of that slot in the atlas's sprites, so the game's
// `native_sprite` and the `b` op draw it by name like any other. A sheet is sent again only when a picture was added,
// or when the game built a new atlas (a resource pack's change: the slot is blank there, and the names gone).

use std::cell::RefCell;

/// The side of the game's mod image (Horizon's `MOD_IMAGE_SIZE`).
pub const SLOT: u32 = 1024;
/// Pixels left blank between two pictures, so a sampler's edge never reads the next one.
const GAP: u32 = 1;

/// The pictures packed so far, left to right in rows (shelves), and what the game was last sent.
#[derive(Debug, Default)]
pub struct Sheet {
    placed: Vec<(String, [u32; 4])>,
    /// `SLOT` wide, as high as the rows used, RGBA.
    pixels: Vec<u8>,
    shelf: [u32; 3],
    changed: bool,
    sent: Option<u64>,
    full: bool,
}

/// What to upload: a picture `width` by `height` for the slot's top left, and each name's part of it.
#[derive(Debug, PartialEq)]
pub struct Upload {
    pub width: u32,
    pub height: u32,
    pub rgba: Vec<u8>,
    pub names: Vec<(String, [u32; 4])>,
    /// Whether it is the first upload into this atlas.
    pub fresh: bool,
}

impl Sheet {
    /// Picture `name`, `width` by `height` RGBA pixels, into the sheet. False when it isn't one (a wrong size), when
    /// there is no room left (the name stays undrawable: its fallback), or when the name is there already in another
    /// size (the first stays). A name there already in this size gets these pixels in its place: a mod's dynamic
    /// texture changed (D-179), or a Java started again sends its pictures again.
    pub fn add(&mut self, name: &str, width: u32, height: u32, rgba: &[u8]) -> bool {
        if name.is_empty()
            || width == 0
            || height == 0
            || rgba.len() != width as usize * height as usize * 4
        {
            return false;
        }
        if let Some([x, y, w, h]) = self.place(name) {
            if (w, h) != (width, height) {
                return false;
            }
            let line = width as usize * 4;
            let mut same = true;
            for r in 0..height as usize {
                let at = ((y as usize + r) * SLOT as usize + x as usize) * 4;
                let row = &rgba[r * line..(r + 1) * line];
                if &self.pixels[at..at + line] != row {
                    self.pixels[at..at + line].copy_from_slice(row);
                    same = false;
                }
            }
            self.changed |= !same;
            return !same;
        }
        let [mut x, mut y, mut row] = self.shelf;
        if x + width > SLOT {
            (x, y, row) = (0, y + row + GAP, 0);
        }
        if width > SLOT || y + height > SLOT {
            if !self.full {
                self.full = true;
                log::warn!(
                    "Kiln: the mods' GUI pictures fill the game's mod image; {name} and the next ones draw as boxes"
                );
            }
            return false;
        }
        let rows = (y + height) as usize;
        if self.pixels.len() < rows * SLOT as usize * 4 {
            self.pixels.resize(rows * SLOT as usize * 4, 0);
        }
        let line = width as usize * 4;
        for r in 0..height as usize {
            let at = ((y as usize + r) * SLOT as usize + x as usize) * 4;
            self.pixels[at..at + line].copy_from_slice(&rgba[r * line..(r + 1) * line]);
        }
        self.placed.push((name.to_owned(), [x, y, width, height]));
        self.shelf = [x + width + GAP, y, row.max(height)];
        self.changed = true;
        true
    }

    /// Where picture `name` is in the sheet: `[x, y, w, h]`.
    pub fn place(&self, name: &str) -> Option<[u32; 4]> {
        self.placed.iter().find(|(n, _)| n == name).map(|(_, p)| *p)
    }

    /// The sheet to upload into an atlas of `revision` (`HudAtlas::revision`): when a picture came since the last
    /// upload, or the atlas is a new one; nothing otherwise, or while the sheet is empty.
    pub fn upload(&mut self, revision: u64) -> Option<Upload> {
        if self.placed.is_empty() || (!self.changed && self.sent == Some(revision)) {
            return None;
        }
        let fresh = self.sent != Some(revision);
        self.changed = false;
        self.sent = Some(revision);
        let height = (self.pixels.len() / (SLOT as usize * 4)) as u32;
        Some(Upload {
            width: SLOT,
            height,
            rgba: self.pixels.clone(),
            names: self.placed.clone(),
            fresh,
        })
    }
}

/// A picture's part of the slot as the atlas's UVs `[u0, v0, u1, v1]`: the slot's top left at `slot` (UVs), the atlas
/// `atlas` pixels large, the picture at `place` (`[x, y, w, h]` in the slot's pixels).
pub fn uv(slot: [f32; 2], atlas: [u32; 2], place: [u32; 4]) -> [f32; 4] {
    let (w, h) = (atlas[0] as f32, atlas[1] as f32);
    let [x, y, pw, ph] = place.map(|v| v as f32);
    [
        slot[0] + x / w,
        slot[1] + y / h,
        slot[0] + (x + pw) / w,
        slot[1] + (y + ph) / h,
    ]
}

/// Standard base64 (with or without padding); None for any other character.
pub fn base64(text: &str) -> Option<Vec<u8>> {
    let mut out = Vec::with_capacity(text.len() / 4 * 3);
    let (mut acc, mut bits) = (0u32, 0u32);
    for c in text.bytes() {
        let v = match c {
            b'A'..=b'Z' => c - b'A',
            b'a'..=b'z' => c - b'a' + 26,
            b'0'..=b'9' => c - b'0' + 52,
            b'+' => 62,
            b'/' => 63,
            b'=' => break,
            _ => return None,
        };
        acc = (acc << 6) | v as u32;
        bits += 6;
        if bits >= 8 {
            bits -= 8;
            out.push((acc >> bits) as u8);
            acc &= (1 << bits) - 1;
        }
    }
    Some(out)
}

thread_local! {
    static SHEET: RefCell<Sheet> = RefCell::new(Sheet::default());
}

/// Java sent picture `name` (`sprite`): into the game's sheet.
pub fn add(name: &str, width: u32, height: u32, rgba: &[u8]) {
    SHEET.with(|s| s.borrow_mut().add(name, width, height, rgba));
}

/// The sheet to upload this frame into the atlas of `revision`, if it changed (see [`Sheet::upload`]).
pub fn take_upload(revision: u64) -> Option<Upload> {
    SHEET.with(|s| s.borrow_mut().upload(revision))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn picture(w: u32, h: u32, v: u8) -> Vec<u8> {
        vec![v; (w * h * 4) as usize]
    }

    #[test]
    fn pictures_are_packed_in_rows_with_a_gap() {
        let mut s = Sheet::default();
        assert!(s.add("a", 600, 10, &picture(600, 10, 1)));
        assert!(s.add("b", 400, 20, &picture(400, 20, 2)));
        // No room left in the first row: the next row starts under its highest picture.
        assert!(s.add("c", 100, 5, &picture(100, 5, 3)));
        assert_eq!(s.place("a"), Some([0, 0, 600, 10]));
        assert_eq!(s.place("b"), Some([601, 0, 400, 20]));
        assert_eq!(s.place("c"), Some([0, 21, 100, 5]));
        let up = s.upload(7).unwrap();
        assert_eq!((up.width, up.height), (SLOT, 26));
        let at = |x: u32, y: u32| up.rgba[((y * SLOT + x) * 4) as usize];
        assert_eq!(
            (
                at(0, 0),
                at(599, 9),
                at(600, 0),
                at(601, 19),
                at(0, 21),
                at(99, 25)
            ),
            (1, 1, 0, 2, 3, 3)
        );
        assert_eq!(up.names.len(), 3);
    }

    #[test]
    fn the_sheet_goes_again_only_when_it_changed_or_the_atlas_is_new() {
        let mut s = Sheet::default();
        assert_eq!(s.upload(1), None);
        s.add("a", 2, 2, &picture(2, 2, 9));
        assert_eq!(s.upload(1).map(|u| u.fresh), Some(true));
        assert_eq!(s.upload(1), None);
        // A resource pack's change: a new atlas, blank there.
        assert_eq!(s.upload(2).map(|u| u.fresh), Some(true));
        assert_eq!(s.upload(2), None);
        s.add("b", 2, 2, &picture(2, 2, 9));
        assert_eq!(
            s.upload(2).map(|u| (u.names.len(), u.fresh)),
            Some((2, false))
        );
    }

    #[test]
    fn wrong_sizes_a_full_sheet_and_names_again_are_left_out() {
        let mut s = Sheet::default();
        assert!(!s.add("a", 2, 2, &picture(2, 1, 0)));
        assert!(!s.add("", 1, 1, &picture(1, 1, 0)));
        assert!(!s.add("big", SLOT + 1, 1, &picture(SLOT + 1, 1, 0)));
        assert!(s.add("tall", 10, SLOT - 2, &picture(10, SLOT - 2, 0)));
        assert!(!s.add("tall", 10, 1, &picture(10, 1, 0)));
        assert_eq!(s.place("tall"), Some([0, 0, 10, SLOT - 2]));
        // Fits beside it in its row; a row under it doesn't.
        assert!(s.add("side", 1000, 3, &picture(1000, 3, 0)));
        assert!(!s.add("under", 100, 3, &picture(100, 3, 0)));
        assert_eq!(s.place("under"), None);
    }

    #[test]
    fn a_picture_again_in_its_size_takes_its_place() {
        let mut s = Sheet::default();
        assert!(s.add("a", 2, 2, &picture(2, 2, 1)));
        assert!(s.add("b", 3, 1, &picture(3, 1, 2)));
        assert!(s.upload(1).is_some());
        // The same pixels again (a Java started again): nothing to send.
        assert!(!s.add("a", 2, 2, &picture(2, 2, 1)));
        assert_eq!(s.upload(1), None);
        // A dynamic texture's new pixels (D-179): where it was, the sheet sent again.
        assert!(s.add("a", 2, 2, &picture(2, 2, 7)));
        assert_eq!(s.place("a"), Some([0, 0, 2, 2]));
        let up = s.upload(1).unwrap();
        assert!(!up.fresh);
        assert_eq!(
            (
                up.rgba[0],
                up.rgba[((1 * SLOT + 1) * 4) as usize],
                up.rgba[(3 * 4) as usize]
            ),
            (7, 7, 2)
        );
    }

    #[test]
    fn a_part_of_the_slot_as_uvs() {
        let [u0, v0, u1, v1] = uv([0.5, 0.25], [4096, 2048], [1024, 512, 512, 256]);
        assert_eq!((u0, v0, u1, v1), (0.75, 0.5, 0.875, 0.625));
    }

    #[test]
    fn base64_as_java_writes_it() {
        assert_eq!(base64("").unwrap(), b"");
        assert_eq!(base64("AQID/w==").unwrap(), [1, 2, 3, 255]);
        assert_eq!(base64("AQID").unwrap(), [1, 2, 3]);
        assert_eq!(base64("AQ").unwrap(), [1]);
        assert_eq!(base64("A Q"), None);
    }
}
