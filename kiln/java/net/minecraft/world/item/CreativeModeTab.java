package net.minecraft.world.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.ItemLike;

/**
 * Kiln's stand-in for {@code CreativeModeTab}: a tab of the creative inventory, its title, icon and the items it
 * lists. Horizon draws the tabs; a mod's tab is kept and its items listed when asked, not shown yet.
 */
public class CreativeModeTab {
    private final Row row;
    private final int column;
    private final Type type;
    private final Component displayName;
    private final Supplier<ItemStack> iconGenerator;
    // Public: mods widen it (REI's access widener reads it to list a tab's items, D-215).
    public final DisplayItemsGenerator displayItemsGenerator;
    private Identifier backgroundTexture = createTextureLocation("items");
    private boolean canScroll = true;
    private boolean showTitle = true;
    private boolean alignedRight;
    private ItemStack iconItemStack;
    private Collection<ItemStack> displayItems = List.of();
    private Collection<ItemStack> searchTabDisplayItems = List.of();

    CreativeModeTab(Row row, int column, Type type, Component displayName, Supplier<ItemStack> iconGenerator,
            DisplayItemsGenerator displayItemsGenerator) {
        this.row = row;
        this.column = column;
        this.type = type;
        this.displayName = displayName;
        this.iconGenerator = iconGenerator;
        this.displayItemsGenerator = displayItemsGenerator;
    }

    public static Identifier createTextureLocation(String name) {
        return Identifier.withDefaultNamespace("textures/gui/container/creative_inventory/tab_" + name + ".png");
    }

    /** Kiln's own: one of the game's tabs, as its prepared resources list it (kiln.GameTabs). */
    public static CreativeModeTab kiln$game(Row row, int column, Type type, Component displayName,
            Supplier<ItemStack> iconGenerator, DisplayItemsGenerator displayItemsGenerator) {
        return new CreativeModeTab(row, column, type, displayName, iconGenerator, displayItemsGenerator);
    }

    public static Builder builder(Row row, int column) {
        return new Builder(row, column);
    }

    public Component getDisplayName() {
        return displayName;
    }

    public ItemStack getIconItem() {
        if (iconItemStack == null) {
            iconItemStack = iconGenerator.get();
        }
        return iconItemStack;
    }

    public Identifier getBackgroundTexture() {
        return backgroundTexture;
    }

    public boolean showTitle() {
        return showTitle;
    }

    public boolean canScroll() {
        return canScroll;
    }

    public int column() {
        return column;
    }

    public Row row() {
        return row;
    }

    public boolean hasAnyItems() {
        return !displayItems.isEmpty();
    }

    public boolean shouldDisplay() {
        return type != Type.CATEGORY || hasAnyItems();
    }

    public boolean isAlignedRight() {
        return alignedRight;
    }

    public Type getType() {
        return type;
    }

    /**
     * The items it lists, from its generator, then Fabric's {@code CreativeModeTabEvents} for its key and for every
     * tab, as Fabric's own hook into the game's tab does: a mod puts its items into its own tab or the game's there
     * (Tech Reborn's tab is empty without it, and so JEI's list lacked its items, D-171). A listener that throws loses
     * its own items only.
     */
    public void buildContents(ItemDisplayParameters parameters) {
        List<ItemStack> items = new ArrayList<>();
        List<ItemStack> search = new ArrayList<>();
        net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput output =
                new net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput(parameters, items, search);
        displayItemsGenerator.accept(parameters, output);
        net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(this).ifPresent(key -> {
            try {
                net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(key).invoker()
                        .modifyOutput(output);
            } catch (RuntimeException | LinkageError e) {
                System.err.println("Kiln: a mod's items for the creative tab " + key.identifier() + " failed: " + e);
            }
        });
        try {
            net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.MODIFY_OUTPUT_ALL.invoker()
                    .modifyOutput(this, output);
        } catch (RuntimeException | LinkageError e) {
            System.err.println("Kiln: a mod's items for every creative tab failed: " + e);
        }
        displayItems = items;
        searchTabDisplayItems = search;
    }

    public Collection<ItemStack> getDisplayItems() {
        return displayItems;
    }

    public Collection<ItemStack> getSearchTabDisplayItems() {
        return searchTabDisplayItems;
    }

    public boolean contains(ItemStack stack) {
        return displayItems.stream().anyMatch(s -> s.getItem() == stack.getItem());
    }

    /** How a tab is made: its title, icon and items. */
    public static class Builder {
        private final Row row;
        private final int column;
        private Component displayName = Component.empty();
        private Supplier<ItemStack> iconGenerator = () -> ItemStack.EMPTY;
        private DisplayItemsGenerator displayItemsGenerator = (parameters, output) -> {};
        private boolean canScroll = true;
        private boolean showTitle = true;
        private boolean alignedRight;
        private Type type = Type.CATEGORY;
        private Identifier backgroundTexture;

        public Builder(Row row, int column) {
            this.row = row;
            this.column = column;
        }

        public Builder title(Component displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder icon(Supplier<ItemStack> iconGenerator) {
            this.iconGenerator = iconGenerator;
            return this;
        }

        public Builder displayItems(DisplayItemsGenerator displayItemsGenerator) {
            this.displayItemsGenerator = displayItemsGenerator;
            return this;
        }

        public Builder alignedRight() {
            alignedRight = true;
            return this;
        }

        public Builder hideTitle() {
            showTitle = false;
            return this;
        }

        public Builder noScrollBar() {
            canScroll = false;
            return this;
        }

        protected Builder type(Type type) {
            this.type = type;
            return this;
        }

        public Builder backgroundTexture(Identifier backgroundTexture) {
            this.backgroundTexture = backgroundTexture;
            return this;
        }

        public CreativeModeTab build() {
            CreativeModeTab tab = new CreativeModeTab(row, column, type, displayName, iconGenerator,
                    displayItemsGenerator);
            tab.alignedRight = alignedRight;
            tab.showTitle = showTitle;
            tab.canScroll = canScroll;
            if (backgroundTexture != null) {
                tab.backgroundTexture = backgroundTexture;
            }
            return tab;
        }
    }

    /** What lists a tab's items. */
    @FunctionalInterface
    public interface DisplayItemsGenerator {
        void accept(ItemDisplayParameters parameters, Output output);
    }

    /** What a tab's items are listed for: the features on, whether the player may use operator items. */
    public record ItemDisplayParameters(FeatureFlagSet enabledFeatures, boolean hasPermissions,
            HolderLookup.Provider holders) {
        // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
        public boolean needsUpdate(net.minecraft.world.flag.FeatureFlagSet p0, boolean p1, net.minecraft.core.HolderLookup.Provider p2) { kiln.Missing.called("net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters.needsUpdate"); return false; }
        // End of what tools/kiln-stubs.py generated.
}

    /** Where a tab's items are listed to. */
    public interface Output {
        void accept(ItemStack stack, TabVisibility visibility);

        default void accept(ItemStack stack) {
            accept(stack, TabVisibility.PARENT_AND_SEARCH_TABS);
        }

        default void accept(ItemLike item, TabVisibility visibility) {
            accept(new ItemStack(item), visibility);
        }

        default void accept(ItemLike item) {
            accept(new ItemStack(item), TabVisibility.PARENT_AND_SEARCH_TABS);
        }

        default void acceptAll(Collection<ItemStack> stacks, TabVisibility visibility) {
            stacks.forEach(stack -> accept(stack, visibility));
        }

        default void acceptAll(Collection<ItemStack> stacks) {
            acceptAll(stacks, TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    public enum Row {
        TOP,
        BOTTOM
    }

    public enum Type {
        CATEGORY,
        INVENTORY,
        HOTBAR,
        SEARCH
    }

    public enum TabVisibility {
        PARENT_AND_SEARCH_TABS,
        PARENT_TAB_ONLY,
        SEARCH_TAB_ONLY
    }
    /**
     * The game's own output a tab's generator lists into: its items, and the search tab's, each stack once (the game
     * throws on a stack listed twice; Kiln keeps the first). REI calls a tab's generator with one itself (D-215).
     */
    public static class ItemDisplayBuilder implements Output {
        public final Collection<ItemStack> tabContents = new ArrayList<>();
        public final Collection<ItemStack> searchTabContents = new ArrayList<>();
        public ItemDisplayBuilder(CreativeModeTab tab, net.minecraft.world.flag.FeatureFlagSet enabledFeatures) {
        }

        @Override
        public void accept(ItemStack stack, TabVisibility visibility) {
            if (stack == null || stack.isEmpty()) {
                return;
            }
            if (visibility != TabVisibility.SEARCH_TAB_ONLY) {
                add(tabContents, stack);
            }
            if (visibility != TabVisibility.PARENT_TAB_ONLY) {
                add(searchTabContents, stack);
            }
        }

        private static void add(Collection<ItemStack> to, ItemStack stack) {
            if (to.stream().noneMatch(s -> ItemStack.isSameItemSameComponents(s, stack))) {
                to.add(stack);
            }
        }
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
}
