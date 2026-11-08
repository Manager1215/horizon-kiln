package kiln;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Mods' own payloads (Fabric's {@code PayloadTypeRegistry}, {@code ClientPlayNetworking}, {@code
 * ServerPlayNetworking}) over Horizon's channel for mods, between Kiln's Java on a player's side and Kiln's Java on the
 * server's: in single player the window joins its own server through memory, on a LAN through the network. A message is
 * what vanilla's custom payload packet holds: the payload type's id, then the payload as its codec writes it. Horizon
 * carries them all on one channel of Kiln's ({@link #CHANNEL}), so any id goes, and at most {@link #MAX_BYTES} a message.
 *
 * <p>The window's side: {@code net <hex>} (a reply) is a message for the server, sent with the game's next tick;
 * {@code from_server <hex>} (a command) one from it, for the mods' client receivers. The server's side: {@code
 * from_client <call> ... <hex> <player>} (a server call, at the sender's next tick) one from a player, for the mods'
 * server receivers; {@code net <uuid> <hex>} (an effect) one for a player, which leaves at the end of the server's tick.
 */
public final class Net {
    /** Horizon's channel for Kiln's messages ({@code mod_id:name}). */
    public static final String CHANNEL = "kiln:payload";
    /** The most a message for mods holds in Horizon. */
    public static final int MAX_BYTES = 30000;
    private static final HexFormat HEX = HexFormat.of();

    public static final Types<RegistryFriendlyByteBuf> SERVERBOUND_PLAY = new Types<>("serverbound play");
    public static final Types<RegistryFriendlyByteBuf> CLIENTBOUND_PLAY = new Types<>("clientbound play");
    public static final Types<FriendlyByteBuf> SERVERBOUND_CONFIGURATION = new Types<>("serverbound configuration");
    public static final Types<FriendlyByteBuf> CLIENTBOUND_CONFIGURATION = new Types<>("clientbound configuration");

    public static final Map<Identifier, ServerPlayNetworking.PlayPayloadHandler<?>> SERVER_RECEIVERS =
            new ConcurrentHashMap<>();
    public static final Map<Identifier, ClientPlayNetworking.PlayPayloadHandler<?>> CLIENT_RECEIVERS =
            new ConcurrentHashMap<>();

    /** The window's way to the server, as a client receiver's response sender and {@code getSender()}. */
    public static final PacketSender TO_SERVER = new PacketSender() {
        @Override
        public void sendPacket(CustomPacketPayload payload) {
            sendToServer(payload);
        }
    };

    /** The payload types a receiver has had, by side: the first of each is logged, so the game's log shows them. */
    private static final java.util.Set<String> heard = ConcurrentHashMap.newKeySet();

    /** Payloads from the server that came before the window had its player: they go to the mods once it has. */
    private static final List<String> early = new ArrayList<>();
    private static final int EARLY_MAX = 64;

    /** The window has its player: the payloads from the server that came before go to the mods' receivers now. */
    static void playerReady() {
        List<String> waiting;
        synchronized (early) {
            if (early.isEmpty()) {
                return;
            }
            waiting = new ArrayList<>(early);
            early.clear();
        }
        for (String hex : waiting) {
            fromServer(hex);
        }
    }

    /** What a mod sent a player outside a server call: it goes with the next call's effects. */
    private static final List<String> later = new ArrayList<>();

    private Net() {}

    /** The payload types of one direction and phase, by id, each with its codec. */
    public static final class Types<B extends FriendlyByteBuf> implements PayloadTypeRegistry<B> {
        private final String name;
        private final Map<Identifier, StreamCodec<? super B, ?>> codecs = new ConcurrentHashMap<>();

        Types(String name) {
            this.name = name;
        }

        @Override
        public <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(
                CustomPacketPayload.Type<T> type, StreamCodec<? super B, T> codec) {
            if (codecs.putIfAbsent(type.id(), codec) != null) {
                throw new IllegalArgumentException("payload type " + type.id() + " is already registered for " + name);
            }
            @SuppressWarnings("unchecked")
            StreamCodec<B, T> narrowed = (StreamCodec<B, T>) codec;
            return new CustomPacketPayload.TypeAndCodec<>(type, narrowed);
        }

        public boolean has(Identifier id) {
            return codecs.containsKey(id);
        }

        @SuppressWarnings("unchecked")
        private StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload> codec(Identifier id) {
            return (StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload>) codecs.get(id);
        }
    }

    /** A payload as a message: its type's id, then what its codec writes; null when its codec fails (logged). */
    static byte[] pack(Types<RegistryFriendlyByteBuf> types, CustomPacketPayload payload) {
        Identifier id = payload.type().id();
        StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload> codec = types.codec(id);
        if (codec == null) {
            throw new IllegalArgumentException("payload type " + id + " is not registered for " + types.name);
        }
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        buffer.writeIdentifier(id);
        try {
            codec.encode(buffer, payload);
        } catch (RuntimeException e) {
            // As in vanilla, where a payload is written on the network's thread: the mod's code goes on. Said once a
            // type.
            if (heard.add("unwritten " + id)) {
                Host.send("log", "a payload of " + id + " didn't write, and isn't sent: " + e);
            }
            return null;
        }
        if (buffer.readableBytes() > MAX_BYTES) {
            throw new IllegalArgumentException("payload " + id + " is " + buffer.readableBytes() + " bytes, more than the "
                    + MAX_BYTES + " Horizon carries");
        }
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }

    /** A payload read from a message, with its type's id; a null payload when no mod registered the type. */
    record Unpacked(Identifier id, CustomPacketPayload payload) {}

    static Unpacked unpack(Types<RegistryFriendlyByteBuf> types, byte[] bytes) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), null);
        Identifier id = buffer.readIdentifier();
        StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload> codec = types.codec(id);
        if (codec == null) {
            return new Unpacked(id, null);
        }
        CustomPacketPayload payload = codec.decode(buffer);
        if (buffer.readableBytes() > 0) {
            throw new IllegalStateException("payload " + id + " left " + buffer.readableBytes() + " bytes unread");
        }
        return new Unpacked(id, payload);
    }

    /** The window's side: a mod's payload for the server ({@code ClientPlayNetworking.send}). */
    public static void sendToServer(CustomPacketPayload payload) {
        byte[] packed = pack(SERVERBOUND_PLAY, payload);
        if (packed != null) {
            Host.send("net", HEX.formatHex(packed));
        }
    }

    /** The window's side: a message from the server, for the mods' client receiver of its type. */
    static void fromServer(String hex) {
        if (Minecraft.getInstance().player == null) {
            // Before the window's first tick in the world (a server's sync for a joining player): as in vanilla, a
            // payload is handled once the player is in the world.
            synchronized (early) {
                if (early.size() < EARLY_MAX) {
                    early.add(hex);
                } else {
                    Host.send("log", "a payload from the server dropped: the window has no player yet");
                }
            }
            return;
        }
        Unpacked message = unpack(CLIENTBOUND_PLAY, HEX.parseHex(hex));
        @SuppressWarnings("unchecked")
        ClientPlayNetworking.PlayPayloadHandler<CustomPacketPayload> handler =
                (ClientPlayNetworking.PlayPayloadHandler<CustomPacketPayload>) CLIENT_RECEIVERS.get(message.id());
        if (message.payload() == null || handler == null) {
            Host.send("log", "a payload from the server no mod receives: " + message.id());
            return;
        }
        if (heard.add("window " + message.id())) {
            Host.send("log", "the window's first " + message.id() + " payload from the server reached its mod");
        }
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        handler.receive(message.payload(), new ClientPlayNetworking.Context() {
            @Override
            public Minecraft client() {
                return client;
            }

            @Override
            public LocalPlayer player() {
                return player;
            }

            @Override
            public PacketSender responseSender() {
                return TO_SERVER;
            }
        });
    }

    /** The server's side: the way to a player, as a server receiver's response sender and {@code getSender}. */
    public static PacketSender toPlayer(ServerPlayer player) {
        return new PacketSender() {
            @Override
            public void sendPacket(CustomPacketPayload payload) {
                sendToPlayer(player, payload);
            }
        };
    }

    /** The server's side: a mod's payload for a player ({@code ServerPlayNetworking.send}), an effect of the call. */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        byte[] packed = pack(CLIENTBOUND_PLAY, payload);
        if (packed == null) {
            return;
        }
        String effect = "net " + player.getUUID() + " " + HEX.formatHex(packed);
        if (Server.inCall()) {
            Server.effect(effect);
        } else {
            synchronized (later) {
                later.add(effect);
            }
        }
    }

    /** What mods sent players outside a call, for the call starting now. */
    static List<String> takeLater() {
        synchronized (later) {
            List<String> taken = new ArrayList<>(later);
            later.clear();
            return taken;
        }
    }

    /** Whether a mod receives payloads on the server: only then does the server hand Kiln the players' messages. */
    static boolean receivesFromClients() {
        return !SERVER_RECEIVERS.isEmpty();
    }

    /** The server's side: a player's message, for the mods' server receiver of its type. The call's result. */
    static String fromClient(String hex, ServerPlayer player) {
        Unpacked message = unpack(SERVERBOUND_PLAY, HEX.parseHex(hex));
        @SuppressWarnings("unchecked")
        ServerPlayNetworking.PlayPayloadHandler<CustomPacketPayload> handler =
                (ServerPlayNetworking.PlayPayloadHandler<CustomPacketPayload>) SERVER_RECEIVERS.get(message.id());
        if (message.payload() == null || handler == null) {
            Host.send("log", "a payload from " + player.getName().getString() + " no mod receives: " + message.id());
            return "pass";
        }
        if (heard.add("server " + message.id())) {
            Host.send("log", "the server's first " + message.id() + " payload, from " + player.getName().getString()
                    + ", reached its mod");
        }
        PacketSender sender = toPlayer(player);
        handler.receive(message.payload(), new ServerPlayNetworking.Context() {
            @Override
            public MinecraftServer server() {
                return MinecraftServer.kiln$server();
            }

            @Override
            public ServerPlayer player() {
                return player;
            }

            @Override
            public PacketSender responseSender() {
                return sender;
            }
        });
        return "pass";
    }
}
