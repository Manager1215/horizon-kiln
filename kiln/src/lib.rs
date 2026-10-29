// The bundle's modules as one library for `cargo test`, at the paths the game gives them
// (`crate::game::kiln`, `crate::game::kiln_jvm`), so they name each other the same way in both.

#[path = "../mixins/kiln"]
pub mod game {
    #[path = "runtime.rs"]
    pub mod kiln;
    #[path = "jvm.rs"]
    pub mod kiln_jvm;
}
