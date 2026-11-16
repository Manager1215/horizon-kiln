import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Stacks as the game saves them, read through Kiln's component stand-ins: a potion's contents (an id alone, then the
 * whole object), a bundle's and a shulker box's. A line each, for kiln/tests/components.rs. It runs as a source file
 * with kiln-runtime.jar on the class path: {@code java -cp kiln-runtime.jar Components.java}.
 */
public class Components {
    static ItemStack stack(String json) {
        return ItemStack.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    static String effects(PotionContents contents) {
        StringBuilder out = new StringBuilder();
        for (MobEffectInstance e : contents.getAllEffects()) {
            out.append(' ').append(BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect().value()))
                    .append('/').append(e.getDuration()).append('/').append(e.getAmplifier());
        }
        return out.toString();
    }

    public static void main(String[] args) {
        PotionContents swift = stack("{\"id\":\"minecraft:potion\",\"count\":1,"
                + "\"components\":{\"minecraft:potion_contents\":\"minecraft:strong_swiftness\"}}")
                .get(DataComponents.POTION_CONTENTS);
        System.out.println("potion " + swift.is(Potions.STRONG_SWIFTNESS) + effects(swift));
        PotionContents custom = stack("{\"id\":\"minecraft:potion\",\"components\":{\"minecraft:potion_contents\":"
                + "{\"potion\":\"minecraft:fire_resistance\",\"custom_effects\":[{\"id\":\"minecraft:luck\","
                + "\"duration\":200,\"amplifier\":2}]}}}").get(DataComponents.POTION_CONTENTS);
        System.out.println("custom" + effects(custom));
        System.out.println("holder " + BuiltInRegistries.POTION.wrapAsHolder(Potions.HEALING.value()).is(Potions.HEALING));
        var bundle = stack("{\"id\":\"minecraft:bundle\",\"components\":{\"minecraft:bundle_contents\":"
                + "[{\"id\":\"minecraft:arrow\",\"count\":12},{\"id\":\"minecraft:torch\",\"count\":3}]}}")
                .get(DataComponents.BUNDLE_CONTENTS);
        StringBuilder items = new StringBuilder("bundle");
        for (ItemStackTemplate t : bundle.items()) {
            ItemStack s = t.create();
            items.append(' ').append(s.getItem().kiln$id()).append('x').append(s.getCount());
        }
        System.out.println(items);
        var box = stack("{\"id\":\"minecraft:shulker_box\",\"components\":{\"minecraft:container\":"
                + "[{\"slot\":0,\"item\":{\"id\":\"minecraft:dirt\",\"count\":64}},"
                + "{\"slot\":5,\"item\":{\"id\":\"minecraft:arrow\",\"count\":7}}]}}")
                .get(DataComponents.CONTAINER);
        StringBuilder held = new StringBuilder("container " + box.size());
        for (ItemStackTemplate t : box.nonEmptyItems()) {
            ItemStack s = t.create();
            held.append(' ').append(s.getItem().kiln$id()).append('x').append(s.getCount());
        }
        System.out.println(held);
    }
}
