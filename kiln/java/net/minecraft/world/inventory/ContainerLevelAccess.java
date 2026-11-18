package net.minecraft.world.inventory;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Kiln's stand-in for {@code ContainerLevelAccess}: where a menu's block is (a crafting table's), for the menu to look
 * at; {@link #NULL} for a menu with no block.
 */
public interface ContainerLevelAccess {
    ContainerLevelAccess NULL = new ContainerLevelAccess() {
        @Override
        public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> function) {
            return Optional.empty();
        }
    };

    /** The block at {@code pos} in {@code level}. */
    static ContainerLevelAccess create(Level level, BlockPos pos) {
        return new ContainerLevelAccess() {
            @Override
            public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> function) {
                return Optional.ofNullable(function.apply(level, pos));
            }
        };
    }

    <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> function);

    default <T> T evaluate(BiFunction<Level, BlockPos, T> function, T otherwise) {
        return evaluate(function).orElse(otherwise);
    }

    default void execute(BiConsumer<Level, BlockPos> action) {
        evaluate((level, pos) -> {
            action.accept(level, pos);
            return Optional.empty();
        });
    }
}
