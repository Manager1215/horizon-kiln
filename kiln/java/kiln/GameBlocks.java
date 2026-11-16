package kiln;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TorchflowerCropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * The game's blocks as their own classes (#149): a mod's code asks whether one is a {@code CropBlock} or a {@code
 * DoorBlock}, and reads its properties. The game's prepared resources ({@code block-behaviours.json}) give each block's
 * class and what it extends; a block is made as the first of those Kiln writes by hand, else as a plain {@code Block}.
 * A door's set (its sounds, whether a hand opens it) comes from there too.
 */
public final class GameBlocks {
    private static Map<String, JsonObject> blocks;

    private GameBlocks() {}

    /** The game's block {@code id}, made with {@code properties}: as its class, where Kiln has one. */
    public static Block make(Identifier id, BlockBehaviour.Properties properties) {
        JsonObject block = "minecraft".equals(id.getNamespace()) ? known().get(id.toString()) : null;
        if (block != null && block.get("class") != null && block.get("class").isJsonArray()) {
            for (JsonElement name : block.getAsJsonArray("class")) {
                Block made = as(name.getAsString(), block, properties);
                if (made != null) {
                    return made;
                }
            }
        }
        return new Block(properties);
    }

    private static Block as(String type, JsonObject block, BlockBehaviour.Properties properties) {
        return switch (type) {
            case "BeetrootBlock" -> new BeetrootBlock(properties.sound(SoundType.CROP));
            case "TorchflowerCropBlock" -> new TorchflowerCropBlock(properties.sound(SoundType.CROP));
            case "CropBlock" -> new CropBlock(properties.sound(SoundType.CROP));
            case "PitcherCropBlock" -> new PitcherCropBlock(properties.sound(SoundType.CROP));
            case "DoublePlantBlock" -> new DoublePlantBlock(properties.sound(SoundType.GRASS));
            case "SugarCaneBlock" -> new SugarCaneBlock(properties.sound(SoundType.GRASS));
            case "DoorBlock" -> new DoorBlock(setType(block, BlockSetType.OAK), properties);
            default -> null;
        };
    }

    /** The game's door {@code id}, of the set its data gives, else of {@code fallback}. */
    public static DoorBlock door(Identifier id, BlockBehaviour.Properties properties, BlockSetType fallback) {
        JsonObject block = known().get(id.toString());
        return new DoorBlock(block == null ? fallback : setType(block, fallback), properties);
    }

    /** A door's set, from its {@code type} field; {@code fallback} when the data has none. */
    static BlockSetType setType(JsonObject block, BlockSetType fallback) {
        if (!(block.get("fields") instanceof JsonObject fields) || !(fields.get("type") instanceof JsonObject type)) {
            return fallback;
        }
        try {
            SoundType sounds = SoundType.WOOD;
            if (type.get("soundType") instanceof JsonObject s) {
                sounds = new SoundType(s.get("volume").getAsFloat(), s.get("pitch").getAsFloat(),
                        BlockSetType.kiln$sound(text(s, "break")), null, BlockSetType.kiln$sound(text(s, "place")),
                        null, null);
            }
            return new BlockSetType(text(type, "name"), type.get("canOpenByHand").getAsBoolean(),
                    type.get("canOpenByWindCharge").getAsBoolean(),
                    type.get("canButtonBeActivatedByArrows").getAsBoolean(),
                    BlockSetType.PressurePlateSensitivity.valueOf(text(type, "pressurePlateSensitivity")), sounds,
                    sound(type, "doorClose"), sound(type, "doorOpen"), sound(type, "trapdoorClose"),
                    sound(type, "trapdoorOpen"), sound(type, "pressurePlateClickOff"),
                    sound(type, "pressurePlateClickOn"), sound(type, "buttonClickOff"), sound(type, "buttonClickOn"));
        } catch (RuntimeException e) {
            System.err.println("Kiln: a door's set in the game's data: " + e);
            return fallback;
        }
    }

    private static net.minecraft.sounds.SoundEvent sound(JsonObject o, String key) {
        return BlockSetType.kiln$sound(text(o, key));
    }

    private static String text(JsonObject o, String key) {
        JsonElement value = o.get(key);
        return value == null || !value.isJsonPrimitive() ? null : value.getAsString();
    }

    /** The game's blocks by id, from its prepared resources; none without them. */
    private static synchronized Map<String, JsonObject> known() {
        if (blocks != null) {
            return blocks;
        }
        blocks = new HashMap<>();
        String resources = System.getProperty("kiln.resources", "");
        Path file = resources.isEmpty() ? null : Path.of(resources, "block-behaviours.json");
        if (file == null || !Files.isRegularFile(file)) {
            return blocks;
        }
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement list = JsonParser.parseReader(in).getAsJsonObject().get("blocks");
            if (list != null && list.isJsonArray()) {
                for (JsonElement entry : list.getAsJsonArray()) {
                    if (entry instanceof JsonObject block && block.get("name") != null) {
                        blocks.put(block.get("name").getAsString(), block);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Kiln: the game's block classes: " + e);
        }
        return blocks;
    }
}
