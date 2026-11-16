// A living entity is about to be hurt, on the server's thread: the game's LivingEntity.hurtServer, for the mods' mixins
// in it (Kiln's Mixin bridge, D-102): one that cancels it cancels the hit, one that changes the amount the damage
// (horizon.rs).
fn kiln_living_incoming_damage(
    entity: i32,
    kind: &str,
    damage_type: &str,
    causing: i32,
    direct: i32,
    amount: &mut f32,
    canceled: &mut bool,
) {
    crate::game::kiln_horizon::incoming_damage(entity, kind, damage_type, causing, direct, amount, canceled);
}
