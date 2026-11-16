package net.minecraft.world.inventory;

/**
 * Kiln's stand-in for {@code MenuType}: a kind of menu, as registered. Kiln opens a mod's menu on the game's side
 * from the slots the mod's own code puts in it, so a type is only a name here.
 */
public class MenuType<T extends AbstractContainerMenu> {
    /** The game's menus, by name only: a mod compares a menu's type with them (a recipe viewer's transfer). */
    public static final MenuType<AbstractContainerMenu> GENERIC_9x1 = new MenuType<>("minecraft:generic_9x1");
    public static final MenuType<AbstractContainerMenu> GENERIC_9x2 = new MenuType<>("minecraft:generic_9x2");
    public static final MenuType<AbstractContainerMenu> GENERIC_9x3 = new MenuType<>("minecraft:generic_9x3");
    public static final MenuType<AbstractContainerMenu> GENERIC_9x4 = new MenuType<>("minecraft:generic_9x4");
    public static final MenuType<AbstractContainerMenu> GENERIC_9x5 = new MenuType<>("minecraft:generic_9x5");
    public static final MenuType<AbstractContainerMenu> GENERIC_9x6 = new MenuType<>("minecraft:generic_9x6");
    public static final MenuType<AbstractContainerMenu> GENERIC_3x3 = new MenuType<>("minecraft:generic_3x3");
    public static final MenuType<AbstractContainerMenu> CRAFTER_3x3 = new MenuType<>("minecraft:crafter_3x3");
    public static final MenuType<AbstractContainerMenu> ANVIL = new MenuType<>("minecraft:anvil");
    public static final MenuType<AbstractContainerMenu> BEACON = new MenuType<>("minecraft:beacon");
    public static final MenuType<AbstractContainerMenu> BLAST_FURNACE = new MenuType<>("minecraft:blast_furnace");
    public static final MenuType<AbstractContainerMenu> BREWING_STAND = new MenuType<>("minecraft:brewing_stand");
    public static final MenuType<AbstractContainerMenu> CRAFTING = new MenuType<>("minecraft:crafting");
    public static final MenuType<AbstractContainerMenu> ENCHANTMENT = new MenuType<>("minecraft:enchantment");
    public static final MenuType<AbstractContainerMenu> FURNACE = new MenuType<>("minecraft:furnace");
    public static final MenuType<AbstractContainerMenu> GRINDSTONE = new MenuType<>("minecraft:grindstone");
    public static final MenuType<AbstractContainerMenu> HOPPER = new MenuType<>("minecraft:hopper");
    public static final MenuType<AbstractContainerMenu> LECTERN = new MenuType<>("minecraft:lectern");
    public static final MenuType<AbstractContainerMenu> LOOM = new MenuType<>("minecraft:loom");
    public static final MenuType<AbstractContainerMenu> MERCHANT = new MenuType<>("minecraft:merchant");
    public static final MenuType<AbstractContainerMenu> SHULKER_BOX = new MenuType<>("minecraft:shulker_box");
    public static final MenuType<AbstractContainerMenu> SMITHING = new MenuType<>("minecraft:smithing");
    public static final MenuType<AbstractContainerMenu> SMOKER = new MenuType<>("minecraft:smoker");
    public static final MenuType<AbstractContainerMenu> CARTOGRAPHY_TABLE = new MenuType<>("minecraft:cartography_table");
    public static final MenuType<AbstractContainerMenu> STONECUTTER = new MenuType<>("minecraft:stonecutter");
    private final String kiln$name;

    /** What makes a mod's menu on the client, or null for the game's. */
    private final MenuSupplier<T> constructor;

    protected MenuType() {
        this(null);
    }

    private MenuType(String name) {
        kiln$name = name;
        constructor = null;
    }

    /** A mod's menu type, as vanilla's: what makes its menu, and the features it needs (none, in Kiln: D-165). */
    public MenuType(MenuSupplier<T> constructor, net.minecraft.world.flag.FeatureFlagSet requiredFeatures) {
        kiln$name = null;
        this.constructor = constructor;
    }

    /** A menu of this type, as the client makes one when the server opens it; null for the game's own. */
    public T create(int containerId, net.minecraft.world.entity.player.Inventory inventory) {
        return constructor == null ? null : constructor.create(containerId, inventory);
    }

    /** What makes a menu of a type: its window id and the player's inventory. */
    public interface MenuSupplier<T extends AbstractContainerMenu> {
        T create(int containerId, net.minecraft.world.entity.player.Inventory inventory);
    }

    @Override
    public String toString() {
        return kiln$name != null ? kiln$name : super.toString();
    }
}
