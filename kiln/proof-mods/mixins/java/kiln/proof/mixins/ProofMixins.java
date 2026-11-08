package kiln.proof.mixins;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kiln.proof.mixins.mixin.KeyMappingAccessor;
import kiln.proof.mixins.mixin.MenuInvoker;
import kiln.proof.mixins.mixin.NeverAccessor;
import kiln.proof.mixins.mixin.SecretAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kiln's proof of Mixin: accessors, invokers, {@code @Inject} at HEAD, RETURN and TAIL, {@code @Overwrite},
 * MixinExtras' {@code @ModifyReturnValue} and {@code @WrapMethod}, and field initializers. At start, the mod reads and
 * changes what only its mixins reach, and stops with what went wrong if any of it isn't as Mixin makes it. Then, as the
 * game plays (a world joined, a screen opened and closed, the world left), its injections into the game's methods mods
 * inject into most say when they ran ({@link Moments}); once the world is left, it checks those too and says how many
 * of all its checks passed.
 */
public final class ProofMixins implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("kiln_proof_mixins");
    /** Read by KeyMappingMixin's HEAD: whether clicks are refused. */
    public static boolean blockClicks;
    /** Read by SlotMixin's HEAD: whether a slot's set is refused; how many were. */
    public static boolean frozen;
    public static int refused;
    /** Counted by both MinecraftMixins' RETURN: the times Minecraft.getInstance() gave the game, twice each. */
    public static int asked;
    /** Who Gadget.greet greeted, as its own code saw it (GadgetWrapOuterMixin's HEAD). */
    public static final List<String> greeted = new ArrayList<>();
    /** Counted by GadgetFieldsMixin's initializer: one for each gadget made. */
    public static int serials;
    /** Read by SlotWholeMixin: whether slots are inactive, what their index gets, whether they take nothing. */
    public static boolean slotsHidden;
    public static int slotShift;
    public static boolean slotsLocked;
    /** Counted and kept by the Machine mixins and ScreenMixin (D-089). */
    public static int fieldReads;
    public static int captured;
    public static int local;
    public static int screenInits;
    /** Counted by OptionsMixin and LocalPlayerMixin (D-093). */
    public static int optionsLoaded;
    public static int inventoryAsked;
    private static final List<String> wrong = new ArrayList<>();
    private static int checks;

    @Override
    public void onInitializeClient() {
        Secret secret = SecretAccessor.kiln$create("kiln");
        SecretAccessor s = (SecretAccessor) secret;
        check("a private field", s.kiln$word(), "kiln");
        check("a private method", s.kiln$shout("!"), "KILN!");
        check("a private static method", SecretAccessor.callTwice(21), 42);
        check("a private static final field", SecretAccessor.getGREETING(), "hello");
        SecretAccessor.setGREETING("hi");
        check("a @Mutable setter", secret.greet(), "hi, kiln");
        check("a dedicated server's mixin left out", ((Object) secret) instanceof NeverAccessor, false);

        KeyMapping key = new KeyMapping("key.kiln_proof_mixins.check", 74, KeyMapping.Category.MISC);
        KeyMappingAccessor k = (KeyMappingAccessor) key;
        check("a stand-in's field", k.kiln$name(), "key.kiln_proof_mixins.check");
        k.setClickCount(2);
        check("a stand-in's field set", k.getClickCount(), 2);
        check("the stand-in's own code sees it", key.consumeClick() && key.consumeClick() && !key.consumeClick(), true);
        check("a stand-in's static field", KeyMappingAccessor.kiln$all().get("key.kiln_proof_mixins.check"), key);
        check("a field the stand-in hasn't", k.kiln$missing(), null);

        // @Inject: HEAD (cancelling with a value), RETURN (reading it), a constructor's TAIL, duck interfaces.
        check("a mixin's interface on a stand-in", ((KeyMappingStats) key).kiln$consumed(), 2);
        int made = ((KeyMappingStats) key).kiln$made();
        KeyMapping fresh = new KeyMapping("key.kiln_proof_mixins.fresh", 75, KeyMapping.Category.MISC);
        KeyMappingStats stats = (KeyMappingStats) fresh;
        KeyMappingAccessor f = (KeyMappingAccessor) fresh;
        check("a constructor's TAIL", stats.kiln$made(), made + 1);
        check("each mapping its own fields", stats.kiln$consumed(), 0);
        f.setClickCount(2);
        check("RETURN reads the value returned", fresh.consumeClick() && fresh.consumeClick() && !fresh.consumeClick()
                && stats.kiln$consumed() == 2, true);
        f.setClickCount(1);
        blockClicks = true;
        check("HEAD cancels with a value", fresh.consumeClick(), false);
        check("a cancelled call ran no further (@Shadow)", stats.kiln$waiting(), 1);
        blockClicks = false;
        check("and the call runs once not cancelled", fresh.consumeClick() && stats.kiln$consumed() == 3, true);
        int before = asked;
        net.minecraft.client.Minecraft.getInstance();
        check("a static method's RETURN, by two mixins of one name with a handler of one name", asked, before + 2);

        AbstractContainerMenu menu = new AbstractContainerMenu(null, 7) {
            @Override
            public ItemStack quickMoveStack(Player player, int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        };
        Slot slot = ((MenuInvoker) menu).kiln$addSlot(new Slot(null, 0, 8, 18));
        check("a stand-in's protected method", menu.slots.size() == 1 && menu.getSlot(0) == slot, true);
        frozen = true;
        slot.set(ItemStack.EMPTY);
        frozen = false;
        check("a void method's HEAD with its arguments, cancelled", refused, 1);
        check("a @Redirect at a call in a stand-in's code, with its mixin's members",
                hasMethod(Slot.class, "kiln$neverMerged"), true);
        check("a @Shadow of a field the stand-in hasn't: Kiln adds it, with its default",
                ((Object) slot) instanceof SlotTag t && t.kiln$tag() == 0, true);

        // @Overwrite, @ModifyReturnValue, @WrapMethod and field initializers on the mod's own class (D-081).
        Gadget gadget = new Gadget("kiln");
        check("an @Overwrite: the higher priority's", gadget.label(), "by priority");
        check("an @Overwrite by an alias of a private method", gadget.reveal(), "no secret");
        check("a static @Overwrite: the first at the same priority", Gadget.twice(5), 15);
        check("a mixin whose @Overwrite lost merges the rest", ((GadgetMarks) gadget).kiln$marks(), 7);
        check("a private @Overwrite made public (conformVisibility)",
                gadget.describe() + " " + isPublic(Gadget.class, "describe"), "conformed true");
        check("an @Overwrite less visible than its target is left out", hasMethod(Gadget.class, "kiln$neverNarrowed"),
                false);
        check("@ModifyReturnValue twice on one method, a static handler", gadget.size(), 28);
        check("@ModifyReturnValue with the target's arguments (a long)", gadget.total(5, 3), 803L);
        check("@ModifyReturnValue at TAIL, not at an early return", gadget.ratio(4) + " " + gadget.ratio(-2),
                "1002.0 -1.0");
        greeted.clear();
        check("@WrapMethod twice, the second outside, other arguments", gadget.greet("kiln", 1), "[KILN x2!]");
        check("an injection goes into the code inside the wrappers", greeted, List.of("KILN"));
        check("a static @WrapMethod", Gadget.scale(30, 2.5) + " " + Gadget.scale(1000, 3), "75 1000");
        gadget.touch();
        check("a void method's @WrapMethod calling it twice", gadget.touched, 2);
        check("Operation.call with the method's arguments", gadget.sum(1, 2), 3);
        String wrongCall = null;
        try {
            gadget.sum(-1, 2);
        } catch (RuntimeException e) {
            wrongCall = e.getMessage();
        }
        check("Operation.call with too few arguments throws", wrongCall != null
                && wrongCall.contains("Expected 2 but got 1"), true);
        GadgetFields fields = (GadgetFields) gadget;
        check("a field initializer (a number)", fields.kiln$count(), 3);
        check("a field initializer (a list)", fields.kiln$seen(), List.of("made"));
        check("a field initializer with a branch", fields.kiln$kind(), "long");
        fields.kiln$bump();
        check("a field initializer's lambda", fields.kiln$count(), 4);
        int serial = fields.kiln$serial();
        GadgetFields plain = (GadgetFields) new Gadget();
        check("initializers once for a constructor that calls another", plain.kiln$serial(), serial + 1);
        check("each gadget its own fields", plain.kiln$seen() != fields.kiln$seen() && plain.kiln$count() == 3, true);

        // Inside a method, on the mod's own class, whose code is there (D-089): Mixin's library applies every kind.
        Machine machine = new Machine();
        check("a mixin's static initializer, after the class's own", Machine.made, 11);
        machine.twoLines();
        check("@Inject at a call by its ordinal, and shifted after one", machine.log,
                List.of("a", "after a", "between", "b"));
        machine.log.clear();
        int reads = fieldReads;
        check("@Inject at a field read", machine.power() + " " + (fieldReads - reads), "5 1");
        check("@Inject after an assignment, the method's locals captured", machine.assigned(6) + " " + captured,
                "7 7");
        machine.sliced();
        check("@Inject in a slice", machine.log, List.of("one", "sliced", "two"));
        machine.log.clear();
        check("@Inject with MixinExtras' @Local", machine.localed(4) + " " + local, "8 8");
        machine.log.clear();
        check("@Redirect of a call", machine.redirected(2), 200);
        check("@Redirect of a field read", machine.charge(), 99);
        machine.argued();
        check("@ModifyArg", machine.log, List.of("argued"));
        machine.log.clear();
        check("@ModifyArgs (Mixin's synthetic Args class)", machine.paired(1, 2), 21);
        check("@ModifyVariable at a local's store", machine.variable(1), 204);
        check("@ModifyVariable of an argument at HEAD", machine.argument(4), 12);
        check("@ModifyConstant", machine.constant(), 43);
        check("@WrapOperation of a call", machine.wrapped(2), 30);
        check("@WrapOperation of a new", machine.built(), "x!y");
        check("@ModifyExpressionValue", machine.expression(2), 7);
        machine.conditional();
        check("@WrapWithCondition", machine.log, List.of("kept"));
        machine.log.clear();
        check("@Share between two handlers", machine.shared(), "s5");
        check("a config's plugin, loaded with its package", ProofPlugin.loaded, "kiln.proof.mixins.plugged");
        check("a config's plugin refusing one of its mixins", machine.plugged(), "plugged kept");
        // At a call in a hand-written stand-in's own code.
        int inits = screenInits;
        new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("kiln")) {}
                .init(320, 240);
        check("@Inject at a call in a stand-in's code", screenInits - inits, 1);

        // The same kinds on a hand-written stand-in.
        Slot whole = new Slot(null, 4, 8, 18);
        SlotStats wholeStats = (SlotStats) whole;
        slotsHidden = true;
        check("an @Overwrite on a stand-in", whole.isActive(), false);
        slotsHidden = false;
        slotShift = 10;
        check("@ModifyReturnValue on a stand-in", whole.getContainerSlot(), 14);
        slotShift = 0;
        check("@WrapMethod on a stand-in, its code called", whole.mayPlace(ItemStack.EMPTY), true);
        slotsLocked = true;
        check("@WrapMethod on a stand-in, its code not called", whole.mayPlace(ItemStack.EMPTY), false);
        slotsLocked = false;
        check("a stand-in's field initializers", wholeStats.kiln$asked() + " " + wholeStats.kiln$notes(), "2 []");
        // What a mixin takes as a whole, in a method Kiln's stand-in hasn't: Kiln adds the method (D-093).
        check("an @Overwrite of a method the stand-in hasn't: Kiln adds it, the mod's code in it",
                hasMethod(Slot.class, "kiln$neverOverwritten") + " " + call(whole, "getNoSuchThing"), "true 1");
        check("an @Inject at the HEAD of a method the stand-in hasn't, named without its descriptor: Kiln adds ()I",
                call(whole, "getAddedCount"), 7);
        check("the game's options read as they were made, Options.load's TAIL", optionsLoaded, 1);
        check("a mixin whose call the stand-in's code doesn't make is left out",
                hasMethod(Slot.class, "kiln$neverInjected"), false);

        if (!wrong.isEmpty()) {
            throw new IllegalStateException("Kiln's Mixin: " + String.join("; ", wrong));
        }
        LOG.info("Kiln's Mixin: {} checks at start passed; the game's moments come as it plays", checks);
        Moments.listen();
    }

    /** All the checks done (the game's moments last): how many passed, or what went wrong and what was seen. */
    static void report(List<String> seen) {
        if (wrong.isEmpty()) {
            LOG.info("Kiln's Mixin: {} of {} checks of accessors, invokers, injectors, overwrites and initializers "
                    + "passed", checks, checks);
        } else {
            LOG.error("Kiln's Mixin: {} of {} checks failed: {}; seen first: {}", wrong.size(), checks,
                    String.join("; ", wrong), seen.subList(0, Math.min(400, seen.size())));
        }
    }

    private static boolean isPublic(Class<?> c, String name) {
        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return java.lang.reflect.Modifier.isPublic(m.getModifiers());
            }
        }
        return false;
    }

    /** A method of the object's class by name, called with no arguments; what it gave, or why it couldn't. */
    private static Object call(Object o, String name) {
        try {
            java.lang.reflect.Method m = o.getClass().getDeclaredMethod(name);
            m.setAccessible(true);
            return m.invoke(o);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return e.toString();
        }
    }

    private static boolean hasMethod(Class<?> c, String name) {
        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    static void check(String what, Object got, Object expected) {
        checks++;
        if (!Objects.equals(got, expected)) {
            wrong.add(what + ": " + got + ", not " + expected);
        }
    }
}
