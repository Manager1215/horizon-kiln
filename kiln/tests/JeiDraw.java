package kiln.test;

import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * A JEI plugin of kiln/tests/jei_draw.rs's (D-179): once JEI's runtime is up, it draws JEI's mod icon as its config
 * screen's sorting page does (a DynamicTexture made from the mod's icon file) and a sheep as a mod's entity page
 * loads one from NBT, and prints the draw ops: {@code Kiln's JEI icons: <values> <icons> <ops>} and {@code Kiln's
 * JEI entity: <op>}, {@code Kiln's JEI entity again: <op>} (saved and loaded into a second entity).
 */
public class JeiDraw implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath("kiln_jei_draw", "draw");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        try {
            icons(runtime);
        } catch (Throwable e) {
            System.out.println("Kiln's JEI icons failed: " + e);
            e.printStackTrace(System.out);
        }
        try {
            entity();
        } catch (Throwable e) {
            System.out.println("Kiln's JEI entity failed: " + e);
            e.printStackTrace(System.out);
        }
    }

    /**
     * JEI's mod icon as its config screen's sorting page makes one ({@code SortingOrderConfigValues
     * .createIngredientModNameIcon}: the mod's icon file's bytes from its platform helper, a NativeImage, a
     * DynamicTexture registered by a name, drawn by a blit of the whole). The page itself is Mezz Config GUI's, a mod
     * this test doesn't have; the try in the game opens it.
     */
    private static void icons(IJeiRuntime runtime) throws java.io.IOException {
        java.util.List<byte[]> candidates = mezz.jei.common.platform.Services.PLATFORM.getModHelper()
                .getModIconByteCandidates("jei");
        GuiGraphicsExtractor g = new GuiGraphicsExtractor(427, 240);
        int icons = 0;
        for (byte[] bytes : candidates) {
            com.mojang.blaze3d.platform.NativeImage image = com.mojang.blaze3d.platform.NativeImage.read(
                    new java.io.ByteArrayInputStream(bytes));
            int w = image.getWidth(), h = image.getHeight();
            Identifier id = Identifier.fromNamespaceAndPath("jei", "mod_icon/jei_" + icons);
            net.minecraft.client.Minecraft.getInstance().getTextureManager().register(id,
                    new net.minecraft.client.renderer.texture.DynamicTexture(() -> "jei icon", image));
            g.blit(null, id, 0, icons * 18, 0, 0, 16, 16, w, h, w, h);
            icons++;
        }
        List<String> ops = new ArrayList<>(g.kiln$ops());
        System.out.println("Kiln's JEI icons: " + candidates.size() + " " + icons + " " + String.join("|", ops));
        // Drawn again after the mod changed its pixels: the same name, sent again (the plugin can't see the
        // texture JEI made, so one of its own).
        net.minecraft.client.renderer.texture.DynamicTexture own = new net.minecraft.client.renderer.texture.DynamicTexture(
                () -> "kiln test", 4, 4, true);
        own.getPixels().fillRect(0, 0, 4, 4, 0xFF00FF00);
        own.upload();
        Identifier id = Identifier.fromNamespaceAndPath("kiln_jei_draw", "own");
        net.minecraft.client.Minecraft.getInstance().getTextureManager().register(id, own);
        GuiGraphicsExtractor first = new GuiGraphicsExtractor(427, 240);
        first.blit(null, id, 0, 0, 0, 0, 4, 4, 4, 4);
        own.getPixels().fillRect(0, 0, 4, 4, 0xFFFF0000);
        own.upload();
        GuiGraphicsExtractor second = new GuiGraphicsExtractor(427, 240);
        second.blit(null, id, 0, 0, 0, 0, 4, 4, 4, 4);
        System.out.println("Kiln's JEI own texture: " + String.join("|", first.kiln$ops()) + " then "
                + String.join("|", second.kiln$ops()));
    }

    private static void entity() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("Color", 11);
        nbt.putBoolean("Sheared", true);
        nbt.putInt("Age", -24000);
        LivingEntity sheep = (LivingEntity) EntityType.SHEEP.create(null, (EntitySpawnReason) null);
        sheep.load(TagValueInput.create(ProblemReporter.DISCARDING, null, nbt));
        System.out.println("Kiln's JEI entity: " + draw(sheep));
        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        sheep.saveWithoutId(out);
        LivingEntity again = (LivingEntity) EntityType.SHEEP.create(null, (EntitySpawnReason) null);
        again.load(TagValueInput.create(ProblemReporter.DISCARDING, null, out.buildResult()));
        System.out.println("Kiln's JEI entity again: " + draw(again));
        // Just Enough Breeding's horse with its armour, the item's id without its namespace as its data has it.
        CompoundTag armour = new CompoundTag();
        armour.putString("id", "netherite_horse_armor");
        CompoundTag equipment = new CompoundTag();
        equipment.put("body", armour);
        CompoundTag armoured = new CompoundTag();
        armoured.put("equipment", equipment);
        LivingEntity horse = (LivingEntity) EntityType.HORSE.create(null, (EntitySpawnReason) null);
        horse.load(TagValueInput.create(ProblemReporter.DISCARDING, null, armoured));
        System.out.println("Kiln's JEI entity horse: " + draw(horse));
        // Its cat, tamed by an owner its JSON writes as a list of numbers.
        CompoundTag tamed = (CompoundTag) com.mojang.serialization.JsonOps.INSTANCE.convertTo(
                net.minecraft.nbt.NbtOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"Owner\": [0, 0, 0, 0], \"CollarColor\": 9}"));
        LivingEntity cat = (LivingEntity) EntityType.CAT.create(null, (EntitySpawnReason) null);
        cat.load(TagValueInput.create(ProblemReporter.DISCARDING, null, tamed));
        System.out.println("Kiln's JEI entity cat: " + draw(cat));
        // Babies (D-180): an ageable mob's Age below 0, a zombie's IsBaby; each one's box beside its adult's.
        StringBuilder babies = new StringBuilder();
        for (EntityType<?> type : new EntityType<?>[] {EntityType.SHEEP, EntityType.VILLAGER, EntityType.ZOMBIE,
                EntityType.COW}) {
            CompoundTag young = new CompoundTag();
            if (type == EntityType.ZOMBIE) {
                young.putBoolean("IsBaby", true);
            } else {
                young.putInt("Age", -24000);
            }
            LivingEntity baby = (LivingEntity) type.create(null, (EntitySpawnReason) null);
            baby.load(TagValueInput.create(ProblemReporter.DISCARDING, null, young));
            LivingEntity adult = (LivingEntity) type.create(null, (EntitySpawnReason) null);
            babies.append(EntityType.getKey(type)).append('=').append(adult.getBbHeight()).append(',')
                    .append(baby.getBbHeight()).append(',').append(baby.getBbWidth()).append(';');
        }
        System.out.println("Kiln's JEI babies: " + babies);
        LivingEntity plain = (LivingEntity) EntityType.COW.create(null, (EntitySpawnReason) null);
        System.out.println("Kiln's JEI entity plain: " + draw(plain));
    }

    private static String draw(LivingEntity entity) {
        GuiGraphicsExtractor g = new GuiGraphicsExtractor(427, 240);
        InventoryScreen.extractEntityInInventoryFollowsMouse(g, 10, 20, 60, 90, 30, 0.0625F, 35, 40, entity);
        return String.join("|", g.kiln$ops());
    }
}
