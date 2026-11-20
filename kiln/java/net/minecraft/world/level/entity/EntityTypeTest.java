package net.minecraft.world.level.entity;

/** Kiln's stand-in for {@code EntityTypeTest}: which entities a level's search takes, as the type they are taken as. */
public interface EntityTypeTest<B, T extends B> {
    /** The entities of a class (and its subclasses). */
    static <B, T extends B> EntityTypeTest<B, T> forClass(Class<T> type) {
        return new EntityTypeTest<>() {
            @Override
            public T tryCast(B entity) {
                return type.isInstance(entity) ? type.cast(entity) : null;
            }

            @Override
            public Class<? extends B> getBaseClass() {
                return type;
            }
        };
    }

    /** The entity as {@code T}, or null when this test doesn't take it. */
    T tryCast(B entity);

    Class<? extends B> getBaseClass();
}
