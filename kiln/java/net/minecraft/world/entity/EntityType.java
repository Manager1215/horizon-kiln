package net.minecraft.world.entity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Kiln's stand-in for {@code EntityType}: a kind of entity, registered by id. Kiln names the kinds of the entities it
 * hands to mods: the player.
 */
public class EntityType<T extends Entity> {
    public static final EntityType<net.minecraft.world.entity.player.Player> PLAYER = register("player");

    static {
        game();
    }

    // The game's entity types by name, as vanilla's fields (D-173): the ids of the prepared resources'
    // entity-types.json (26.3), each the registry's own entry (kiln$game), made here where the resources are missing.
    public static final EntityType<Entity> ACACIA_BOAT = kiln$game("acacia_boat");
    public static final EntityType<Entity> ACACIA_CHEST_BOAT = kiln$game("acacia_chest_boat");
    public static final EntityType<Entity> ALLAY = kiln$game("allay");
    public static final EntityType<Entity> AREA_EFFECT_CLOUD = kiln$game("area_effect_cloud");
    public static final EntityType<Entity> ARMADILLO = kiln$game("armadillo");
    public static final EntityType<Entity> ARMOR_STAND = kiln$game("armor_stand");
    public static final EntityType<Entity> ARROW = kiln$game("arrow");
    public static final EntityType<Entity> AXOLOTL = kiln$game("axolotl");
    public static final EntityType<Entity> BAMBOO_CHEST_RAFT = kiln$game("bamboo_chest_raft");
    public static final EntityType<Entity> BAMBOO_RAFT = kiln$game("bamboo_raft");
    public static final EntityType<Entity> BAT = kiln$game("bat");
    public static final EntityType<Entity> BEE = kiln$game("bee");
    public static final EntityType<Entity> BIRCH_BOAT = kiln$game("birch_boat");
    public static final EntityType<Entity> BIRCH_CHEST_BOAT = kiln$game("birch_chest_boat");
    public static final EntityType<Entity> BLAZE = kiln$game("blaze");
    public static final EntityType<Entity> BLOCK_DISPLAY = kiln$game("block_display");
    public static final EntityType<Entity> BOGGED = kiln$game("bogged");
    public static final EntityType<Entity> BREEZE = kiln$game("breeze");
    public static final EntityType<Entity> BREEZE_WIND_CHARGE = kiln$game("breeze_wind_charge");
    public static final EntityType<Entity> CAMEL = kiln$game("camel");
    public static final EntityType<Entity> CAMEL_HUSK = kiln$game("camel_husk");
    public static final EntityType<Entity> CAT = kiln$game("cat");
    public static final EntityType<Entity> CAVE_SPIDER = kiln$game("cave_spider");
    public static final EntityType<Entity> CHERRY_BOAT = kiln$game("cherry_boat");
    public static final EntityType<Entity> CHERRY_CHEST_BOAT = kiln$game("cherry_chest_boat");
    public static final EntityType<Entity> CHEST_MINECART = kiln$game("chest_minecart");
    public static final EntityType<Entity> CHICKEN = kiln$game("chicken");
    public static final EntityType<Entity> COD = kiln$game("cod");
    public static final EntityType<Entity> COMMAND_BLOCK_MINECART = kiln$game("command_block_minecart");
    public static final EntityType<Entity> COPPER_GOLEM = kiln$game("copper_golem");
    public static final EntityType<Entity> COW = kiln$game("cow");
    public static final EntityType<Entity> CREAKING = kiln$game("creaking");
    public static final EntityType<Entity> CREEPER = kiln$game("creeper");
    public static final EntityType<Entity> CUSHION = kiln$game("cushion");
    public static final EntityType<Entity> DARK_OAK_BOAT = kiln$game("dark_oak_boat");
    public static final EntityType<Entity> DARK_OAK_CHEST_BOAT = kiln$game("dark_oak_chest_boat");
    public static final EntityType<Entity> DOLPHIN = kiln$game("dolphin");
    public static final EntityType<Entity> DONKEY = kiln$game("donkey");
    public static final EntityType<Entity> DRAGON_FIREBALL = kiln$game("dragon_fireball");
    public static final EntityType<Entity> DROWNED = kiln$game("drowned");
    public static final EntityType<Entity> EGG = kiln$game("egg");
    public static final EntityType<Entity> ELDER_GUARDIAN = kiln$game("elder_guardian");
    public static final EntityType<Entity> END_CRYSTAL = kiln$game("end_crystal");
    public static final EntityType<Entity> ENDER_DRAGON = kiln$game("ender_dragon");
    public static final EntityType<Entity> ENDER_PEARL = kiln$game("ender_pearl");
    public static final EntityType<Entity> ENDERMAN = kiln$game("enderman");
    public static final EntityType<Entity> ENDERMITE = kiln$game("endermite");
    public static final EntityType<Entity> EVOKER = kiln$game("evoker");
    public static final EntityType<Entity> EVOKER_FANGS = kiln$game("evoker_fangs");
    public static final EntityType<Entity> EXPERIENCE_BOTTLE = kiln$game("experience_bottle");
    public static final EntityType<Entity> EXPERIENCE_ORB = kiln$game("experience_orb");
    public static final EntityType<Entity> EYE_OF_ENDER = kiln$game("eye_of_ender");
    public static final EntityType<Entity> FALLING_BLOCK = kiln$game("falling_block");
    public static final EntityType<Entity> FIREBALL = kiln$game("fireball");
    public static final EntityType<Entity> FIREWORK_ROCKET = kiln$game("firework_rocket");
    public static final EntityType<Entity> FISHING_BOBBER = kiln$game("fishing_bobber");
    public static final EntityType<Entity> FOX = kiln$game("fox");
    public static final EntityType<Entity> FROG = kiln$game("frog");
    public static final EntityType<Entity> FURNACE_MINECART = kiln$game("furnace_minecart");
    public static final EntityType<Entity> GHAST = kiln$game("ghast");
    public static final EntityType<Entity> GIANT = kiln$game("giant");
    public static final EntityType<Entity> GLOW_ITEM_FRAME = kiln$game("glow_item_frame");
    public static final EntityType<Entity> GLOW_SQUID = kiln$game("glow_squid");
    public static final EntityType<Entity> GOAT = kiln$game("goat");
    public static final EntityType<Entity> GUARDIAN = kiln$game("guardian");
    public static final EntityType<Entity> HAPPY_GHAST = kiln$game("happy_ghast");
    public static final EntityType<Entity> HOGLIN = kiln$game("hoglin");
    public static final EntityType<Entity> HOPPER_MINECART = kiln$game("hopper_minecart");
    public static final EntityType<Entity> HORSE = kiln$game("horse");
    public static final EntityType<Entity> HUSK = kiln$game("husk");
    public static final EntityType<Entity> ILLUSIONER = kiln$game("illusioner");
    public static final EntityType<Entity> INTERACTION = kiln$game("interaction");
    public static final EntityType<Entity> IRON_GOLEM = kiln$game("iron_golem");
    public static final EntityType<Entity> ITEM = kiln$game("item");
    public static final EntityType<Entity> ITEM_DISPLAY = kiln$game("item_display");
    public static final EntityType<Entity> ITEM_FRAME = kiln$game("item_frame");
    public static final EntityType<Entity> JUNGLE_BOAT = kiln$game("jungle_boat");
    public static final EntityType<Entity> JUNGLE_CHEST_BOAT = kiln$game("jungle_chest_boat");
    public static final EntityType<Entity> LEASH_KNOT = kiln$game("leash_knot");
    public static final EntityType<Entity> LIGHTNING_BOLT = kiln$game("lightning_bolt");
    public static final EntityType<Entity> LINGERING_POTION = kiln$game("lingering_potion");
    public static final EntityType<Entity> LLAMA = kiln$game("llama");
    public static final EntityType<Entity> LLAMA_SPIT = kiln$game("llama_spit");
    public static final EntityType<Entity> MAGMA_CUBE = kiln$game("magma_cube");
    public static final EntityType<Entity> MANGROVE_BOAT = kiln$game("mangrove_boat");
    public static final EntityType<Entity> MANGROVE_CHEST_BOAT = kiln$game("mangrove_chest_boat");
    public static final EntityType<Entity> MANNEQUIN = kiln$game("mannequin");
    public static final EntityType<Entity> MARKER = kiln$game("marker");
    public static final EntityType<Entity> MINECART = kiln$game("minecart");
    public static final EntityType<Entity> MOOSHROOM = kiln$game("mooshroom");
    public static final EntityType<Entity> MULE = kiln$game("mule");
    public static final EntityType<Entity> NAUTILUS = kiln$game("nautilus");
    public static final EntityType<Entity> OAK_BOAT = kiln$game("oak_boat");
    public static final EntityType<Entity> OAK_CHEST_BOAT = kiln$game("oak_chest_boat");
    public static final EntityType<Entity> OCELOT = kiln$game("ocelot");
    public static final EntityType<Entity> OMINOUS_ITEM_SPAWNER = kiln$game("ominous_item_spawner");
    public static final EntityType<Entity> PAINTING = kiln$game("painting");
    public static final EntityType<Entity> PALE_OAK_BOAT = kiln$game("pale_oak_boat");
    public static final EntityType<Entity> PALE_OAK_CHEST_BOAT = kiln$game("pale_oak_chest_boat");
    public static final EntityType<Entity> PANDA = kiln$game("panda");
    public static final EntityType<Entity> PARCHED = kiln$game("parched");
    public static final EntityType<Entity> PARROT = kiln$game("parrot");
    public static final EntityType<Entity> PHANTOM = kiln$game("phantom");
    public static final EntityType<Entity> PIG = kiln$game("pig");
    public static final EntityType<Entity> PIGLIN = kiln$game("piglin");
    public static final EntityType<Entity> PIGLIN_BRUTE = kiln$game("piglin_brute");
    public static final EntityType<Entity> PILLAGER = kiln$game("pillager");
    public static final EntityType<Entity> POLAR_BEAR = kiln$game("polar_bear");
    public static final EntityType<Entity> POPLAR_BOAT = kiln$game("poplar_boat");
    public static final EntityType<Entity> POPLAR_CHEST_BOAT = kiln$game("poplar_chest_boat");
    public static final EntityType<Entity> PUFFERFISH = kiln$game("pufferfish");
    public static final EntityType<Entity> RABBIT = kiln$game("rabbit");
    public static final EntityType<Entity> RAVAGER = kiln$game("ravager");
    public static final EntityType<Entity> SALMON = kiln$game("salmon");
    public static final EntityType<Entity> SHEEP = kiln$game("sheep");
    public static final EntityType<Entity> SHULKER = kiln$game("shulker");
    public static final EntityType<Entity> SHULKER_BULLET = kiln$game("shulker_bullet");
    public static final EntityType<Entity> SILVERFISH = kiln$game("silverfish");
    public static final EntityType<Entity> SKELETON = kiln$game("skeleton");
    public static final EntityType<Entity> SKELETON_HORSE = kiln$game("skeleton_horse");
    public static final EntityType<Entity> SLIME = kiln$game("slime");
    public static final EntityType<Entity> SMALL_FIREBALL = kiln$game("small_fireball");
    public static final EntityType<Entity> SNIFFER = kiln$game("sniffer");
    public static final EntityType<Entity> SNOW_GOLEM = kiln$game("snow_golem");
    public static final EntityType<Entity> SNOWBALL = kiln$game("snowball");
    public static final EntityType<Entity> SPAWNER_MINECART = kiln$game("spawner_minecart");
    public static final EntityType<Entity> SPECTRAL_ARROW = kiln$game("spectral_arrow");
    public static final EntityType<Entity> SPIDER = kiln$game("spider");
    public static final EntityType<Entity> SPLASH_POTION = kiln$game("splash_potion");
    public static final EntityType<Entity> SPRUCE_BOAT = kiln$game("spruce_boat");
    public static final EntityType<Entity> SPRUCE_CHEST_BOAT = kiln$game("spruce_chest_boat");
    public static final EntityType<Entity> SQUID = kiln$game("squid");
    public static final EntityType<Entity> STRAY = kiln$game("stray");
    public static final EntityType<Entity> STRIDER = kiln$game("strider");
    public static final EntityType<Entity> SULFUR_CUBE = kiln$game("sulfur_cube");
    public static final EntityType<Entity> TADPOLE = kiln$game("tadpole");
    public static final EntityType<Entity> TEXT_DISPLAY = kiln$game("text_display");
    public static final EntityType<Entity> TNT = kiln$game("tnt");
    public static final EntityType<Entity> TNT_MINECART = kiln$game("tnt_minecart");
    public static final EntityType<Entity> TRADER_LLAMA = kiln$game("trader_llama");
    public static final EntityType<Entity> TRIDENT = kiln$game("trident");
    public static final EntityType<Entity> TROPICAL_FISH = kiln$game("tropical_fish");
    public static final EntityType<Entity> TURTLE = kiln$game("turtle");
    public static final EntityType<Entity> VEX = kiln$game("vex");
    public static final EntityType<Entity> VILLAGER = kiln$game("villager");
    public static final EntityType<Entity> VINDICATOR = kiln$game("vindicator");
    public static final EntityType<Entity> WANDERING_TRADER = kiln$game("wandering_trader");
    public static final EntityType<Entity> WARDEN = kiln$game("warden");
    public static final EntityType<Entity> WIND_CHARGE = kiln$game("wind_charge");
    public static final EntityType<Entity> WITCH = kiln$game("witch");
    public static final EntityType<Entity> WITHER = kiln$game("wither");
    public static final EntityType<Entity> WITHER_SKELETON = kiln$game("wither_skeleton");
    public static final EntityType<Entity> WITHER_SKULL = kiln$game("wither_skull");
    public static final EntityType<Entity> WOLF = kiln$game("wolf");
    public static final EntityType<Entity> ZOGLIN = kiln$game("zoglin");
    public static final EntityType<Entity> ZOMBIE = kiln$game("zombie");
    public static final EntityType<Entity> ZOMBIE_HORSE = kiln$game("zombie_horse");
    public static final EntityType<Entity> ZOMBIE_NAUTILUS = kiln$game("zombie_nautilus");
    public static final EntityType<Entity> ZOMBIE_VILLAGER = kiln$game("zombie_villager");
    public static final EntityType<Entity> ZOMBIFIED_PIGLIN = kiln$game("zombified_piglin");

    /** The game's type {@code id} as the registry has it (game() put it there), else registered here. */
    @SuppressWarnings("unchecked")
    private static <T extends Entity> EntityType<T> kiln$game(String id) {
        Identifier key = Identifier.withDefaultNamespace(id);
        return BuiltInRegistries.ENTITY_TYPE.containsKey(key)
                ? (EntityType<T>) BuiltInRegistries.ENTITY_TYPE.getValue(key) : register(id);
    }

    private final EntityFactory<T> factory;
    private final MobCategory category;
    private final float width;
    private final float height;
    private float eyeHeight;
    private final boolean fireImmune;
    private String descriptionId;
    /**
     * How far players see it, in chunks, and how often they hear of it, in ticks: a mod's type says them through its
     * builder (vanilla's defaults 5 and 3); the game's own types don't come with theirs in the prepared resources, so
     * theirs is 32, the farthest view distance: a player sees one as far as its chunk is sent (D-199).
     */
    private int clientTrackingRange = 32;
    private int updateInterval = 3;

    private EntityType(EntityFactory<T> factory, MobCategory category, float width, float height, boolean fireImmune,
            String descriptionId) {
        this.factory = factory;
        this.category = category;
        this.width = width;
        this.height = height;
        this.eyeHeight = height * 0.85F;
        this.fireImmune = fireImmune;
        this.descriptionId = descriptionId;
    }

    private static <T extends Entity> EntityType<T> register(String id) {
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, Identifier.withDefaultNamespace(id),
                new EntityType<T>(null, MobCategory.MISC, 0.6F, 1.8F, false, null));
    }

    /**
     * The game's other entity types, by id with their size and category, from the prepared resources'
     * {@code entity-types.json}, as vanilla's registry has them before any mod runs: a mod's data that names one
     * ({@code "minecraft:cow"} in Just Enough Breeding's recipes) finds it (D-171). None without the resources.
     */
    private static void game() {
        String resources = System.getProperty("kiln.resources", "");
        java.nio.file.Path file = resources.isEmpty() ? null : java.nio.file.Path.of(resources, "entity-types.json");
        if (file == null || !java.nio.file.Files.isRegularFile(file)) {
            return;
        }
        try (java.io.Reader in = java.nio.file.Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
            com.google.gson.JsonObject all = com.google.gson.JsonParser.parseReader(in).getAsJsonObject();
            for (var entry : all.entrySet()) {
                Identifier id = Identifier.parse(entry.getKey());
                if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                    continue;
                }
                com.google.gson.JsonObject type = entry.getValue().getAsJsonObject();
                MobCategory category = MobCategory.MISC;
                for (MobCategory c : MobCategory.values()) {
                    if (c.getName().equals(type.has("category") ? type.get("category").getAsString() : "")) {
                        category = c;
                    }
                }
                EntityType<?> made = new EntityType<>(null, category,
                        type.has("width") ? type.get("width").getAsFloat() : 0.6F,
                        type.has("height") ? type.get("height").getAsFloat() : 1.8F,
                        type.has("fire_immune") && type.get("fire_immune").getAsBoolean(), null);
                if (type.has("eye_height")) {
                    made.eyeHeight = type.get("eye_height").getAsFloat();
                }
                if (type.has("tracking_range")) {
                    made.clientTrackingRange = type.get("tracking_range").getAsInt();
                }
                Registry.register(BuiltInRegistries.ENTITY_TYPE, id, made);
            }
        } catch (java.io.IOException | RuntimeException e) {
            System.err.println("Kiln can't read the game's entity types: " + e);
        }
    }

    public static Identifier getKey(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type);
    }

    public MobCategory getCategory() {
        return category;
    }

    /** How far players see an entity of the type, in chunks ({@code PlayerLookup.tracking}, D-199). */
    public int clientTrackingRange() {
        return clientTrackingRange;
    }

    public int updateInterval() {
        return updateInterval;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public EntityDimensions getDimensions() {
        return EntityDimensions.kiln$of(width, height, eyeHeight);
    }

    /** Each game mob's size as a baby, by its id, as the game sent it ({@link #kiln$babies}); D-180. */
    private static volatile java.util.Map<String, float[]> babies = java.util.Map.of();

    /**
     * Kiln's own: the game's {@code babies} line, {@code <id>=a,<width>,<height>,<eye height>} (a box of its own) or
     * {@code <id>=s,<factor>[,<eye height>]} (its adult's scaled) for each mob that has a baby, {@code ;} between them.
     * A part that doesn't read is left out.
     */
    public static void kiln$babies(String line) {
        java.util.Map<String, float[]> read = new java.util.HashMap<>();
        for (String part : line.split(";")) {
            int eq = part.indexOf('=');
            String[] f = eq < 0 ? new String[0] : part.substring(eq + 1).split(",");
            try {
                if (f.length == 4 && f[0].equals("a")) {
                    read.put(part.substring(0, eq), new float[] {
                        Float.parseFloat(f[1]), Float.parseFloat(f[2]), Float.parseFloat(f[3])});
                } else if ((f.length == 2 || f.length == 3) && f[0].equals("s")) {
                    // A factor alone: NaN for "the adult's eye height scaled as well".
                    read.put(part.substring(0, eq), new float[] {
                        Float.parseFloat(f[1]), f.length == 3 ? Float.parseFloat(f[2]) : Float.NaN});
                }
            } catch (NumberFormatException e) {
                // Left out: drawn at its adult's size.
            }
        }
        babies = java.util.Map.copyOf(read);
    }

    /** Its size as a baby (vanilla's {@code BABY_DIMENSIONS}), as the game sent it; null for a mob that has none. */
    EntityDimensions kiln$babyDimensions() {
        Identifier id = getKey(this);
        float[] baby = id == null ? null : babies.get(id.toString());
        if (baby == null) {
            return null;
        }
        if (baby.length == 3) {
            return EntityDimensions.kiln$of(baby[0], baby[1], baby[2]);
        }
        float s = baby[0];
        return EntityDimensions.kiln$of(width * s, height * s, Float.isNaN(baby[1]) ? eyeHeight * s : baby[1]);
    }

    /**
     * An entity of it in {@code level}, not in the world: a mod's type makes its own entity class; a game type makes
     * Kiln's {@link Preview}, a living entity of that type, which a recipe viewer draws in a screen (Just Enough
     * Breeding's mobs) as the game draws that mob's model (D-173). The game's world gets none of them.
     */
    @SuppressWarnings("unchecked")
    public T create(net.minecraft.world.level.Level level, EntitySpawnReason reason) {
        if (factory != null) {
            return factory.create(this, level);
        }
        return this == PLAYER ? null : (T) new Preview(this, level);
    }

    /**
     * Kiln's own: an entity of the type as the client's level has the game's mob (D-211): Kiln's {@link Preview}, a
     * living entity of the type, whatever class a mod's type makes (the game, not Java, runs the mob).
     */
    public static Entity kiln$client(EntityType<?> type, net.minecraft.world.level.Level level) {
        return new Preview(type, level);
    }

    public T create(net.minecraft.world.level.Level level, EntitySpawnRequest request) {
        return create(level, request == null ? EntitySpawnReason.LOAD : request.reason());
    }

    public boolean fireImmune() {
        return fireImmune;
    }

    /** {@code entity.<namespace>.<path>}, from its id. */
    public String getDescriptionId() {
        if (descriptionId == null) {
            Identifier id = getKey(this);
            descriptionId = id == null ? "entity.unregistered" : "entity." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        }
        return descriptionId;
    }

    public Component getDescription() {
        return Component.translatable(getDescriptionId());
    }

    public String toShortString() {
        Identifier id = getKey(this);
        return id == null ? "unregistered" : id.getPath();
    }

    @Override
    public String toString() {
        return getDescriptionId();
    }

    /** Kiln's own: what makes an entity of it (a mod's); none for the player's. */
    public EntityFactory<T> kiln$factory() {
        return factory;
    }

    /**
     * Kiln's entity of a game type, made outside the world ({@link #create}): its type, size and level; what a
     * screen draws of it is that type's model, by id ({@code GuiGraphicsExtractor}'s entity op).
     */
    public static final class Preview extends LivingEntity {
        private final EntityType<?> type;

        Preview(EntityType<?> type, net.minecraft.world.level.Level level) {
            this.type = type;
            this.level = level;
            this.name = "";
        }

        @Override
        public EntityType<?> getType() {
            return type;
        }

        @Override
        public Component getName() {
            return type.getDescription();
        }

        /**
         * What it was loaded with past Kiln's own fields (a sheep's {@code Color}, a baby's {@code Age}, a horse's
         * {@code equipment}, a variant), kept whole: the game draws it by that, as it loads a saved mob (D-179).
         */
        private final java.util.Map<String, Object> data = new java.util.TreeMap<>();

        @Override
        public void load(net.minecraft.world.level.storage.ValueInput in) {
            super.load(in);
            java.util.Map<String, Object> read = kiln.TagValues.map(in);
            if (read != null) {
                read.forEach((key, value) -> {
                    // Kiln's own fields stay Entity's (saveWithoutId writes them again).
                    if (!key.startsWith("kiln:") && !key.equals("id") && !key.equals("OnGround")) {
                        data.put(key, value);
                    }
                });
            }
        }

        @Override
        public void saveWithoutId(net.minecraft.world.level.storage.ValueOutput out) {
            super.saveWithoutId(out);
            java.util.Map<String, Object> written = kiln.TagValues.map(out);
            if (written != null) {
                data.forEach(written::putIfAbsent);
            }
        }

        /** A list or array of whole numbers as ints; null for anything else. */
        private static int[] ints(Object value) {
            return switch (value) {
                case int[] a -> a;
                case byte[] a -> {
                    int[] out = new int[a.length];
                    for (int i = 0; i < a.length; i++) {
                        out[i] = a[i];
                    }
                    yield out;
                }
                case long[] a -> java.util.Arrays.stream(a).mapToInt(l -> (int) l).toArray();
                case java.util.List<?> list when list.stream().allMatch(v -> v instanceof Byte || v instanceof Short
                        || v instanceof Integer || v instanceof Long) ->
                        list.stream().mapToInt(v -> ((Number) v).intValue()).toArray();
                case null, default -> null;
            };
        }

        /** {@code Age} below 0, as the game's ageable mobs (and zombies' {@code IsBaby}) save a baby. */
        public boolean isBaby() {
            return data.get("Age") instanceof Number age ? age.intValue() < 0
                    : data.get("IsBaby") instanceof Number baby && baby.intValue() != 0;
        }

        /**
         * A baby's box is the baby's, as vanilla's {@code getDefaultDimensions} gives it: a screen that places the
         * mob by its height (vanilla's {@code extractEntityInInventoryFollowsMouse}, Just Enough Breeding's offset)
         * places the smaller model as vanilla does (D-180).
         */
        @Override
        public EntityDimensions getDimensions(Pose pose) {
            EntityDimensions baby = isBaby() ? type.kiln$babyDimensions() : null;
            return baby != null ? baby : super.getDimensions(pose);
        }

        /** Kiln's own: what it was loaded with, as the game saves an entity (SNBT, its type's id first); D-179. */
        public String kiln$snbt() {
            java.util.Map<String, Object> tag = new java.util.TreeMap<>(data);
            // Its equipment as the game reads a stack: an item's id with its namespace (Just Enough Breeding writes
            // "netherite_horse_armor", which vanilla's item codec reads as minecraft's).
            if (tag.get("equipment") instanceof java.util.Map<?, ?> equipment) {
                java.util.Map<String, Object> named = new java.util.TreeMap<>();
                equipment.forEach((slot, stack) -> {
                    if (stack instanceof java.util.Map<?, ?> item && item.get("id") instanceof String itemId) {
                        java.util.Map<String, Object> copy = new java.util.TreeMap<>();
                        item.forEach((k, v) -> copy.put(String.valueOf(k), v));
                        copy.put("id", Identifier.parse(itemId).toString());
                        named.put(String.valueOf(slot), copy);
                    } else {
                        named.put(String.valueOf(slot), stack);
                    }
                });
                tag.put("equipment", named);
            }
            // A UUID as the game saves one, four ints: a mod's JSON gives {@code "Owner": [0, 0, 0, 0]}, which the
            // game's codecs read as a UUID from any list of numbers (a tamed cat, its collar drawn).
            for (String key : new String[] {"Owner", "UUID", "LoveCause"}) {
                int[] uuid = ints(tag.get(key));
                if (uuid != null && uuid.length == 4) {
                    tag.put(key, uuid);
                }
            }
            Identifier id = getKey(type);
            if (id != null) {
                tag.put("id", id.toString());
            }
            // Where it is doesn't change how it looks, but a saved mob has a place.
            tag.putIfAbsent("Pos", java.util.List.of(0.0, 0.0, 0.0));
            return kiln.Snbt.write(tag);
        }
    }

    /** Makes an entity of a type in a level (a mod's entity class's constructor). */
    @FunctionalInterface
    public interface EntityFactory<T extends Entity> {
        T create(EntityType<T> type, net.minecraft.world.level.Level level);
    }

    /** Builds an entity type: its factory, category and size; what else a mod sets is kept by the game's own data. */
    public static class Builder<T extends Entity> {
        private final EntityFactory<T> factory;
        private final MobCategory category;
        private float width = 0.6F;
        private float height = 1.8F;
        private boolean fireImmune;
        private int clientTrackingRange = 5;
        private int updateInterval = 3;

        private Builder(EntityFactory<T> factory, MobCategory category) {
            this.factory = factory;
            this.category = category;
        }

        public static <T extends Entity> Builder<T> of(EntityFactory<T> factory, MobCategory category) {
            return new Builder<>(factory, category);
        }

        public static <T extends Entity> Builder<T> createNothing(MobCategory category) {
            return new Builder<>((type, level) -> null, category);
        }

        public Builder<T> sized(float width, float height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder<T> fireImmune() {
            this.fireImmune = true;
            return this;
        }

        public Builder<T> clientTrackingRange(int range) {
            this.clientTrackingRange = range;
            return this;
        }

        public Builder<T> updateInterval(int interval) {
            this.updateInterval = interval;
            return this;
        }

        public Builder<T> noSummon() {
            return this;
        }

        public Builder<T> noSave() {
            return this;
        }

        public EntityType<T> build(net.minecraft.resources.ResourceKey<EntityType<?>> key) {
            Identifier id = key.identifier();
            EntityType<T> made = new EntityType<>(factory, category, width, height, fireImmune,
                    "entity." + id.getNamespace() + "." + id.getPath().replace('/', '.'));
            made.clientTrackingRange = clientTrackingRange;
            made.updateInterval = updateInterval;
            return made;
        }
        // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
        public net.minecraft.world.entity.EntityDimensions dimensions;
        public net.minecraft.world.entity.EntityType.Builder<T> eyeHeight(float p0) { kiln.Missing.called("net.minecraft.world.entity.EntityType.Builder.eyeHeight"); return this; }
        public net.minecraft.world.entity.EntityType.Builder<T> passengerAttachments(float... p0) { kiln.Missing.called("net.minecraft.world.entity.EntityType.Builder.passengerAttachments"); return this; }
        public net.minecraft.world.entity.EntityType.Builder<T> ridingOffset(float p0) { kiln.Missing.called("net.minecraft.world.entity.EntityType.Builder.ridingOffset"); return this; }
        public net.minecraft.world.entity.EntityType.Builder<T> canSpawnFarFromPlayer() { kiln.Missing.called("net.minecraft.world.entity.EntityType.Builder.canSpawnFarFromPlayer"); return this; }
        public net.minecraft.world.entity.EntityType.Builder<T> noLootTable() { kiln.Missing.called("net.minecraft.world.entity.EntityType.Builder.noLootTable"); return this; }
        // End of what tools/kiln-stubs.py generated.
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public net.minecraft.world.entity.EntityDimensions dimensions;
    public static <T extends net.minecraft.world.entity.Entity> net.minecraft.world.entity.PostSpawnProcessor<T> createDefaultStackConfig(net.minecraft.world.level.Level p0, net.minecraft.world.item.ItemStack p1, net.minecraft.world.entity.LivingEntity p2) { kiln.Missing.called("net.minecraft.world.entity.EntityType.createDefaultStackConfig"); return kiln.Constants.nothing(net.minecraft.world.entity.PostSpawnProcessor.class); }
    public T spawn(net.minecraft.server.level.ServerLevel p0, net.minecraft.core.BlockPos p1, net.minecraft.world.entity.EntitySpawnReason p2) { kiln.Missing.called("net.minecraft.world.entity.EntityType.spawn"); return null; }
    public T spawn(net.minecraft.server.level.ServerLevel p0, net.minecraft.world.entity.PostSpawnProcessor<T> p1, net.minecraft.core.BlockPos p2, net.minecraft.world.entity.EntitySpawnReason p3, boolean p4, boolean p5) { kiln.Missing.called("net.minecraft.world.entity.EntityType.spawn"); return null; }
    public boolean canSerialize() { kiln.Missing.called("net.minecraft.world.entity.EntityType.canSerialize"); return false; }
    public boolean canSummon() { kiln.Missing.called("net.minecraft.world.entity.EntityType.canSummon"); return false; }
    public java.util.Optional<net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable>> getDefaultLootTable() { kiln.Missing.called("net.minecraft.world.entity.EntityType.getDefaultLootTable"); return java.util.Optional.empty(); }
    public static java.util.Optional<net.minecraft.world.entity.Entity> create(net.minecraft.world.level.storage.ValueInput p0, net.minecraft.world.level.Level p1, net.minecraft.world.entity.EntitySpawnRequest p2) { kiln.Missing.called("net.minecraft.world.entity.EntityType.create"); return java.util.Optional.empty(); }
    public static net.minecraft.world.entity.Entity loadEntityRecursive(net.minecraft.nbt.CompoundTag p0, net.minecraft.world.level.Level p1, net.minecraft.world.entity.EntitySpawnRequest p2, net.minecraft.world.entity.EntityProcessor p3) { kiln.Missing.called("net.minecraft.world.entity.EntityType.loadEntityRecursive"); return null; }
    public static net.minecraft.world.entity.Entity loadEntityRecursive(net.minecraft.world.entity.EntityType<?> p0, net.minecraft.nbt.CompoundTag p1, net.minecraft.world.level.Level p2, net.minecraft.world.entity.EntitySpawnReason p3, net.minecraft.world.entity.EntityProcessor p4) { kiln.Missing.called("net.minecraft.world.entity.EntityType.loadEntityRecursive"); return null; }
    public static java.util.stream.Stream<net.minecraft.world.entity.Entity> loadEntitiesRecursive(net.minecraft.world.level.storage.ValueInput.ValueInputList p0, net.minecraft.world.level.Level p1, net.minecraft.world.entity.EntitySpawnReason p2) { kiln.Missing.called("net.minecraft.world.entity.EntityType.loadEntitiesRecursive"); return null; }
    public T tryCast(net.minecraft.world.entity.Entity p0) { kiln.Missing.called("net.minecraft.world.entity.EntityType.tryCast"); return null; }
    public net.minecraft.core.Holder.Reference<net.minecraft.world.entity.EntityType<?>> builtInRegistryHolder() { kiln.Missing.called("net.minecraft.world.entity.EntityType.builtInRegistryHolder"); return null; }
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
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    // End of what tools/kiln-stubs.py generated.
}
