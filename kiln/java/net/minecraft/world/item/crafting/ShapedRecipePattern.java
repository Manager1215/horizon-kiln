package net.minecraft.world.item.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Kiln's stand-in for {@code ShapedRecipePattern}: a shaped recipe's grid, read from its {@code key} and {@code
 * pattern} as the game's data has them (D-152: the game's recipes for JEI, JEI's own codecs). Matching a crafting grid
 * is the game's: Horizon crafts.
 */
public final class ShapedRecipePattern {
    public static final char EMPTY_SLOT = ' ';
    private static final int MAX_SIZE = 3;
    public static final MapCodec<ShapedRecipePattern> MAP_CODEC = Data.MAP_CODEC.flatXmap(ShapedRecipePattern::unpack,
            p -> p.data.map(DataResult::success).orElseGet(() -> DataResult.error(() -> "a pattern made in code can't be written")));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ShapedRecipePattern> STREAM_CODEC =
            kiln.Constants.streamCodec("net.minecraft.world.item.crafting.ShapedRecipePattern.STREAM_CODEC");

    private final int width;
    private final int height;
    private final List<Optional<Ingredient>> ingredients;
    private final Optional<Data> data;
    private final int ingredientCount;

    public ShapedRecipePattern(int width, int height, List<Optional<Ingredient>> ingredients, Optional<Data> data) {
        this.width = width;
        this.height = height;
        this.ingredients = ingredients;
        this.data = data;
        this.ingredientCount = (int) ingredients.stream().filter(Optional::isPresent).count();
    }

    public static ShapedRecipePattern of(Map<Character, Ingredient> key, String... pattern) {
        return of(key, List.of(pattern));
    }

    public static ShapedRecipePattern of(Map<Character, Ingredient> key, List<String> pattern) {
        return unpack(new Data(key, pattern)).getOrThrow();
    }

    private static DataResult<ShapedRecipePattern> unpack(Data data) {
        String[] rows = shrink(data.pattern);
        int width = rows.length == 0 ? 0 : rows[0].length();
        int height = rows.length;
        List<Optional<Ingredient>> ingredients = new ArrayList<>(width * height);
        Set<Character> unused = new HashSet<>(data.key.keySet());
        for (String row : rows) {
            for (int i = 0; i < row.length(); i++) {
                char symbol = row.charAt(i);
                if (symbol == EMPTY_SLOT) {
                    ingredients.add(Optional.empty());
                    continue;
                }
                Ingredient ingredient = data.key.get(symbol);
                if (ingredient == null) {
                    return DataResult.error(() -> "Pattern references symbol '" + symbol + "' but it's not defined in the key");
                }
                unused.remove(symbol);
                ingredients.add(Optional.of(ingredient));
            }
        }
        if (!unused.isEmpty()) {
            return DataResult.error(() -> "Key defines symbols that aren't used in pattern: " + unused);
        }
        return DataResult.success(new ShapedRecipePattern(width, height, ingredients, Optional.of(data)));
    }

    /** The pattern without its empty rows and columns around it, as the game trims one. */
    public static String[] shrink(List<String> pattern) {
        int first = Integer.MAX_VALUE;
        int last = 0;
        int top = 0;
        int emptyBelow = 0;
        for (int row = 0; row < pattern.size(); row++) {
            String line = pattern.get(row);
            first = Math.min(first, firstNonEmpty(line));
            int end = lastNonEmpty(line);
            last = Math.max(last, end);
            if (end < 0) {
                if (top == row) {
                    top++;
                }
                emptyBelow++;
            } else {
                emptyBelow = 0;
            }
        }
        if (pattern.size() == emptyBelow) {
            return new String[0];
        }
        String[] rows = new String[pattern.size() - emptyBelow - top];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = pattern.get(i + top).substring(first, last + 1);
        }
        return rows;
    }

    private static int firstNonEmpty(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == EMPTY_SLOT) {
            i++;
        }
        return i;
    }

    private static int lastNonEmpty(String line) {
        int i = line.length() - 1;
        while (i >= 0 && line.charAt(i) == EMPTY_SLOT) {
            i--;
        }
        return i;
    }

    /**
     * Whether the grid holds this pattern, as the game's: as many filled slots as the pattern has ingredients, the
     * grid the pattern's size, each slot what its cell takes, the pattern mirrored too unless it is symmetrical.
     * Horizon crafts in its own grids; a mod's machine that crafts by shaped recipes (Tech Reborn's rolling machine)
     * asks here.
     */
    public boolean matches(CraftingInput input) {
        if (input.ingredientCount() != ingredientCount || input.width() != width || input.height() != height) {
            return false;
        }
        return (!symmetrical() && matches(input, true)) || matches(input, false);
    }

    private boolean matches(CraftingInput input, boolean mirrored) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Optional<Ingredient> cell = ingredients.get((mirrored ? width - x - 1 : x) + y * width);
                if (!Ingredient.testOptionalIngredient(cell, input.getItem(x, y))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean symmetrical() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width / 2; x++) {
                if (!ingredients.get(x + y * width).equals(ingredients.get(width - x - 1 + y * width))) {
                    return false;
                }
            }
        }
        return true;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public List<Optional<Ingredient>> ingredients() {
        return ingredients;
    }

    /** Kiln's own: how many of the grid's cells take an ingredient. */
    public int kiln$ingredientCount() {
        return ingredientCount;
    }

    /** The key and pattern, as the data has them. */
    public record Data(Map<Character, Ingredient> key, List<String> pattern) {
        private static final Codec<List<String>> PATTERN_CODEC = Codec.STRING.listOf().comapFlatMap(rows -> {
            if (rows.size() > MAX_SIZE || rows.isEmpty()) {
                return DataResult.error(() -> "Invalid pattern: 1 to " + MAX_SIZE + " rows");
            }
            int width = rows.get(0).length();
            for (String row : rows) {
                if (row.length() > MAX_SIZE || row.length() != width) {
                    return DataResult.error(() -> "Invalid pattern: rows of one width, at most " + MAX_SIZE);
                }
            }
            return DataResult.success(rows);
        }, rows -> rows);
        private static final Codec<Character> SYMBOL_CODEC = Codec.STRING.comapFlatMap(symbol -> {
            if (symbol.length() != 1) {
                return DataResult.error(() -> "Invalid key entry: '" + symbol + "' is an invalid symbol (must be 1 character only).");
            }
            return EMPTY_SLOT == symbol.charAt(0) ? DataResult.error(() -> "Invalid key entry: ' ' is a reserved symbol.")
                    : DataResult.success(symbol.charAt(0));
        }, String::valueOf);
        public static final MapCodec<Data> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.unboundedMap(SYMBOL_CODEC, Ingredient.CODEC).fieldOf("key").forGetter(Data::key),
                PATTERN_CODEC.fieldOf("pattern").forGetter(Data::pattern)).apply(i, Data::new));
    }
}
