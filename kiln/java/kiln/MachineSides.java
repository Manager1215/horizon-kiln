package kiln;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A machine's side configuration (D-214): which faces take items into each of its slots and give them out of it, and
 * whether the machine pushes and pulls by itself, as RebornCore keeps it ({@code SlotConfiguration}, Tech Reborn's
 * machines). Read by reflection (Kiln is built without the mods). The screen shows it from the menu's data slots
 * ({@link #of}); a click on Kiln's side panel comes to the server as a menu button ({@link #button}) and changes it
 * through RebornCore's own payloads, as its screen's tab does.
 */
public final class MachineSides {
    private MachineSides() {}

    /** Menu buttons: a face's mode, {@code FACE + slot * 100 + face * 10 + mode} (the face as the screen names it). */
    public static final int FACE = 1_000_000;
    /** And a slot's switch, {@code SWITCH + slot * 100 + which * 10 + on} (auto input 0, auto output 1, filter 2). */
    public static final int SWITCH = 2_000_000;

    private static final int[] NONE = new int[0];

    /**
     * The data slots after the energy and the tank: the machine's facing ({@code Direction}'s 3D value), how many
     * slots it configures, then one value each: two bits per face (down, up, north, south, west, east; RebornCore's
     * {@code ExtractConfig}: none 0, input 1, output 2, both 3), then auto input (bit 12), auto output (13) and the
     * filter (14). None for a block entity without RebornCore's side configuration.
     */
    public static int[] of(BlockEntity entity) {
        Object config = call(entity, "getSlotConfiguration");
        if (!(call(config, "getSlotDetails") instanceof List<?> holders) || holders.isEmpty()) {
            return NONE;
        }
        int[] shown = new int[2 + holders.size()];
        shown[0] = call(entity, "getFacing") instanceof Direction facing ? facing.get3DDataValue() : -1;
        shown[1] = holders.size();
        int i = 2;
        for (Object holder : holders) {
            int value = 0;
            for (Direction face : Direction.values()) {
                Object io = call(call(call(holder, "getSideDetail", face), "getSlotIO"), "getIoConfig");
                int mode = io instanceof Enum<?> e ? e.ordinal() & 3 : 0;
                value |= mode << (2 * face.get3DDataValue());
            }
            value |= flag(holder, "autoInput") << 12 | flag(holder, "autoOutput") << 13 | flag(holder, "filter") << 14;
            shown[i++] = value;
        }
        return shown;
    }

    /**
     * A player's click on Kiln's side panel of the machine at {@code pos} (menu button {@code id}): RebornCore's own
     * payload for it ({@code SlotSavePayload} for a face, {@code IoSavePayload} for a switch), packed with its codec
     * and handed to its server receiver as from the player, which checks the machine, changes it and tells the
     * players. False for a button that isn't one of these, or a block entity without side configuration.
     */
    public static boolean button(BlockEntity entity, ServerPlayer player, int id) {
        Object config = call(entity, "getSlotConfiguration");
        if (config == null || id < FACE || id >= SWITCH + 100_000) {
            return false;
        }
        boolean face = id < SWITCH;
        int rest = id - (face ? FACE : SWITCH);
        int slot = rest / 100;
        int which = rest / 10 % 10;
        int value = rest % 10;
        Object holder = call(config, "getSlotDetails", slot);
        if (holder == null) {
            return false;
        }
        ClassLoader loader = config.getClass().getClassLoader();
        BlockPos pos = entity.getBlockPos();
        try {
            CustomPacketPayload payload;
            if (face) {
                if (which > 5 || value > 3) {
                    return false;
                }
                Class<?> extract = Class.forName("reborncore.common.blockentity.SlotConfiguration$ExtractConfig", true, loader);
                Class<?> io = Class.forName("reborncore.common.blockentity.SlotConfiguration$SlotIO", true, loader);
                Class<?> slotConfig = Class.forName("reborncore.common.blockentity.SlotConfiguration$SlotConfig", true, loader);
                Object mode = extract.getEnumConstants()[value];
                Object detail = slotConfig.getConstructor(Direction.class, io, int.class)
                        .newInstance(Direction.from3DDataValue(which), io.getConstructor(extract).newInstance(mode), slot);
                payload = (CustomPacketPayload) Class.forName("reborncore.common.network.serverbound.SlotSavePayload", true, loader)
                        .getConstructor(BlockPos.class, slotConfig).newInstance(pos, detail);
            } else {
                if (which > 2) {
                    return false;
                }
                boolean on = value != 0;
                boolean input = which == 0 ? on : flag(holder, "autoInput") != 0;
                boolean output = which == 1 ? on : flag(holder, "autoOutput") != 0;
                boolean filter = which == 2 ? on : flag(holder, "filter") != 0;
                int priority = call(holder, "getPriority") instanceof Integer p ? p : 0;
                Constructor<?> make = Class.forName("reborncore.common.network.serverbound.IoSavePayload", true, loader)
                        .getConstructor(BlockPos.class, int.class, boolean.class, boolean.class, boolean.class, int.class);
                payload = (CustomPacketPayload) make.newInstance(pos, slot, input, output, filter, priority);
            }
            // Through the codec and the receiver, as the payload from the player's screen would come.
            Net.fromClient(java.util.HexFormat.of().formatHex(Net.pack(Net.SERVERBOUND_PLAY, payload)), player);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Host.send("log", "a machine's side setting failed: " + e);
            return false;
        }
    }

    private static int flag(Object holder, String name) {
        return Boolean.TRUE.equals(call(holder, name)) ? 1 : 0;
    }

    /** {@code target.name(args)} by reflection on its public methods; null when there is no such method or it fails. */
    private static Object call(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                try {
                    return method.invoke(target, args);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    return null;
                }
            }
        }
        return null;
    }
}
