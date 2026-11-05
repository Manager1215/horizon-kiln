// While in a world: what Kiln's Java mods draw, then Kiln's own status box in the bottom-right corner (only
// while it starts, for a few seconds after, or when something is wrong).
fn kiln_hud(
    game: &mut crate::game::Game<'_>,
    batch: &mut horizon_client::hud::HudBatch,
    corners: &mut horizon_client::extras_hud::Corners,
) {
    use crate::game::kiln as m;
    use crate::game::kiln_jvm::HudOp;
    use horizon_client::extras::Corner;
    // Turned off in Options > Mods: nothing until the next build leaves it out.
    if !game.native_on(m::ID) || game.native_player().is_none() {
        return;
    }
    m::set_gui(batch.width, batch.height);
    // What a mod drew inside its scissor is cut to it.
    let mut clip: Option<[f32; 4]> = None;
    for op in m::hud_ops() {
        let start = batch.vertices.len();
        match op {
            HudOp::Clip(rect) => clip = rect,
            HudOp::Fill { x, y, w, h, argb } => game.native_fill(batch, [x, y, w, h], argb),
            HudOp::Text {
                x,
                y,
                scale,
                argb,
                shadow,
                text,
            } => {
                let start = batch.vertices.len();
                game.native_text(batch, &text, [x, y], argb & 0xFF_FFFF, shadow);
                if scale != 1.0 {
                    batch.scale_since(start, x, y, scale, scale);
                }
            }
            HudOp::Item { x, y, size, item } => {
                game.native_item(batch, &item, [x, y], size);
            }
        }
        if let Some([x0, y0, x1, y1]) = clip
            && batch.vertices.len() > start
        {
            batch.clip_since(start, x0, y0, x1, y1);
        }
    }
    let hidden = game.ui.hide_gui;
    crate::game::kiln_horizon::draw_toasts(game, batch, hidden);
    let lines = m::status_lines();
    if lines.is_empty() || !game.native_corner_shown(Corner::BottomRight) {
        return;
    }
    let w = lines
        .iter()
        .map(|(text, _)| game.native_text_width(text))
        .fold(0.0, f32::max)
        + 4.0;
    let h = 10.0 * lines.len() as f32 + 2.0;
    let (x, y) = corners.place(Corner::BottomRight, w, h);
    game.native_fill(batch, [x, y, w, h], m::BACKGROUND);
    for (i, (text, rgb)) in lines.iter().enumerate() {
        game.native_text(batch, text, [x + 2.0, y + 2.0 + 10.0 * i as f32], *rgb, true);
    }
}
