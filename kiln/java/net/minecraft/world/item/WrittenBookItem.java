package net.minecraft.world.item;

/**
 * Kiln's stand-in for {@code WrittenBookItem}: the game's signed book ({@code Items.WRITTEN_BOOK}), which a mod checks
 * by class. Horizon opens and reads it; its content is the stack's {@code minecraft:written_book_content}.
 */
public class WrittenBookItem extends Item {
    public WrittenBookItem(Item.Properties properties) {
        super(properties);
    }
}
