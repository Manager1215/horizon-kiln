package kiln;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How fast an item mines a block and whether the block drops for it, as the game's {@code Tool} component says
 * (D-232): {@code ItemStack.getDestroySpeed} and {@code isCorrectToolForDrops}, which AutoSwap's tool choice scores
 * the hotbar by. A game item's tool is its {@code minecraft:tool} in the prepared {@code items.json}: rules in order,
 * the first whose blocks hold the block and that says a speed gives it (else the tool's default speed), the first that
 * says whether it drops decides it. An item without a tool mines at 1 and drops nothing that needs a tool. A mod's
 * own item's tool isn't read here yet.
 */
public final class ItemTools {
    private ItemTools() {}

    /** A tool rule: the blocks (a block id, or a tag as {@code #ns:path}), and its speed and drops when it says them. */
    private record Rule(List<String> blocks, Float speed, Boolean drops) {}

    private record Tool(List<Rule> rules, float defaultSpeed) {}

    /** No tool: what an item without one mines at. */
    private static final Tool NONE = new Tool(List.of(), 1.0F);

    private static final Map<String, Tool> TOOLS = new ConcurrentHashMap<>();

    /** The speed {@code stack} mines {@code state} at (1 without a tool), before enchantments and effects. */
    public static float destroySpeed(ItemStack stack, BlockState state) {
        Tool tool = tool(stack);
        String block = block(state);
        for (Rule rule : tool.rules()) {
            if (rule.speed() != null && holds(rule, block)) {
                return rule.speed();
            }
        }
        return tool.defaultSpeed();
    }

    /** Whether {@code state} drops when {@code stack} mines it, as far as its tool goes (false without a tool). */
    public static boolean correctForDrops(ItemStack stack, BlockState state) {
        Tool tool = tool(stack);
        String block = block(state);
        for (Rule rule : tool.rules()) {
            if (rule.drops() != null && holds(rule, block)) {
                return rule.drops();
            }
        }
        return false;
    }

    private static String block(BlockState state) {
        return state == null ? "" : String.valueOf(state.getBlock().kiln$id());
    }

    private static boolean holds(Rule rule, String block) {
        for (String blocks : rule.blocks()) {
            if (blocks.startsWith("#")
                    ? GameItems.inTag("block", Identifier.parse(blocks.substring(1)), block)
                    : blocks.equals(block)) {
                return true;
            }
        }
        return false;
    }

    private static Tool tool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return NONE;
        }
        String id = String.valueOf(stack.getItem().kiln$id());
        return TOOLS.computeIfAbsent(id, ItemTools::read);
    }

    /** The game item {@code id}'s tool from {@code items.json}; none for an item without one, or a mod's. */
    private static Tool read(String id) {
        if (!(GameItems.itemComponent(id, "minecraft:tool") instanceof JsonObject tool)) {
            return NONE;
        }
        try {
            List<Rule> rules = new ArrayList<>();
            if (tool.get("rules") instanceof JsonArray all) {
                for (JsonElement e : all) {
                    if (!(e instanceof JsonObject rule)) {
                        continue;
                    }
                    List<String> blocks = new ArrayList<>();
                    JsonElement named = rule.get("blocks");
                    if (named instanceof JsonArray list) {
                        list.forEach(b -> blocks.add(withNamespace(b.getAsString())));
                    } else if (named != null) {
                        blocks.add(withNamespace(named.getAsString()));
                    }
                    rules.add(new Rule(blocks, rule.has("speed") ? rule.get("speed").getAsFloat() : null,
                            rule.has("correct_for_drops") ? rule.get("correct_for_drops").getAsBoolean() : null));
                }
            }
            float speed = tool.has("default_mining_speed") ? tool.get("default_mining_speed").getAsFloat() : 1.0F;
            return new Tool(List.copyOf(rules), speed);
        } catch (RuntimeException e) {
            System.err.println("Kiln: the tool of " + id + " didn't read: " + e);
            return NONE;
        }
    }

    /** {@code #mineable/pickaxe} as {@code #minecraft:mineable/pickaxe}, {@code cobweb} as {@code minecraft:cobweb}. */
    private static String withNamespace(String name) {
        boolean tag = name.startsWith("#");
        String bare = tag ? name.substring(1) : name;
        return (tag ? "#" : "") + (bare.contains(":") ? bare : "minecraft:" + bare);
    }
}
