// A Java machine's item slots for the game's hoppers, by the machine in place (D-214): Horizon asks a mod container's
// rules by block; a machine with a side configuration of its own answers by its own (horizon.rs).
fn kiln_container_view(containers: &Containers<'_>, pos: (i32, i32, i32)) -> std::ops::ControlFlow<Option<View>> {
    match crate::game::kiln_horizon::sided_container(containers.world, pos) {
        Some(container) => std::ops::ControlFlow::Break(Some(View {
            parts: vec![(pos, container.slots)],
            kind: Kind::Mod,
            container: Some(std::sync::Arc::new(container)),
        })),
        None => std::ops::ControlFlow::Continue(()),
    }
}
