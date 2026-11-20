package kiln;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
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
 * what vanilla's custom payload packet holds: the payload type's id, then the payload as its codec writes it; one of
 * the configuration phase's (D-199) comes after the id {@link #CONFIGURATION}, as Horizon's configuration phase
 * carries no mod's payloads: the server's are sent where the player's play connection starts. Horizon
 * carries them all on one channel of Kiln's ({@link #CHANNEL}), so any id goes, and at most {@link #MAX_BYTES} a message:
 * a longer one (up to {@link #MAX_PAYLOAD}, as the game's packet) goes in parts, joined on the other side (D-234).
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
    public static final Map<Identifier, ServerConfigurationNetworking.ConfigurationPacketHandler<?>>
            SERVER_CONFIGURATION_RECEIVERS = new ConcurrentHashMap<>();
    public static final Map<Identifier, ClientConfigurationNetworking.ConfigurationPayloadHandler<?>>
            CLIENT_CONFIGURATION_RECEIVERS = new ConcurrentHashMap<>();

    /** The id a configuration phase's message starts with, before its payload's type (D-199). */
    static final Identifier CONFIGURATION = Identifier.fromNamespaceAndPath("kiln", "configuration");

    /** A configuration payload as {@code ServerConfigurationNetworking.createClientboundPacket} makes it. */
    public record ConfigurationPacket(CustomPacketPayload payload)
            implements net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.common.ClientCommonPacketListener> {
        @Override
        public net.minecraft.network.protocol.PacketType<? extends net.minecraft.network.protocol.Packet<
                net.minecraft.network.protocol.common.ClientCommonPacketListener>> type() {
            return null;
        }

        @Override
        public void handle(net.minecraft.network.protocol.common.ClientCommonPacketListener listener) {}
    }

    /**
     * A play payload for a player as {@code ServerPlayNetworking.createClientboundPacket} makes it (Architectury's
     * packets, REI's display sync among them, D-234): {@code ServerGamePacketListenerImpl.send} sends its payload.
     */
    public record PlayPacket(CustomPacketPayload payload)
            implements net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.common.ClientCommonPacketListener> {
        @Override
        public net.minecraft.network.protocol.PacketType<? extends net.minecraft.network.protocol.Packet<
                net.minecraft.network.protocol.common.ClientCommonPacketListener>> type() {
            return null;
        }

        @Override
        public void handle(net.minecraft.network.protocol.common.ClientCommonPacketListener listener) {}
    }

    /**
     * The most a payload holds, as the game's clientbound custom payload packet takes (1 MiB): one longer than a
     * Horizon message ({@link #MAX_BYTES}) goes in parts (D-234).
     */
    public static final int MAX_PAYLOAD = 1 << 20;

    /**
     * The id a part of a longer message starts with (D-234), then the message's number (an int), the part's index and
     * the parts' count (var-ints), then the part's bytes: the receiving side joins the parts, in order, into the message.
     */
    static final Identifier PART = Identifier.fromNamespaceAndPath("kiln", "part");
    /** The bytes a part's header takes at most: the id, an int and two var-ints. */
    private static final int PART_HEADER = 32;
    private static final java.util.concurrent.atomic.AtomicInteger parted = new java.util.concurrent.atomic.AtomicInteger();

    /** A message as Horizon carries it: itself, or in parts of at most {@link #MAX_BYTES} when it is longer. */
    static List<byte[]> parts(byte[] message) {
        if (message.length <= MAX_BYTES) {
            return List.of(message);
        }
        int size = MAX_BYTES - PART_HEADER;
        int count = (message.length + size - 1) / size;
        int number = parted.incrementAndGet();
        List<byte[]> parts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int from = i * size;
            int to = Math.min(message.length, from + size);
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(to - from + PART_HEADER), null);
            buffer.writeIdentifier(PART);
            buffer.writeInt(number);
            buffer.writeVarInt(i);
            buffer.writeVarInt(count);
            buffer.writeBytes(message, from, to - from);
            byte[] part = new byte[buffer.readableBytes()];
            buffer.readBytes(part);
            parts.add(part);
        }
        return parts;
    }

    /** The parts received of messages not yet whole, by sender and number (the window's sender is null). */
    private static final Map<String, byte[][]> joining = new java.util.LinkedHashMap<>();
    /** The most messages being joined at once: an older one never finished is dropped. */
    private static final int JOINING_MAX = 16;

    /**
     * A part received from {@code sender} (a player's id, or "server" on the window's side): the whole message once its
     * last part came, null before. A part out of shape drops its message (logged).
     */
    static byte[] joined(String sender, byte[] part) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(part), null);
        buffer.readIdentifier();
        int number = buffer.readInt();
        int index = buffer.readVarInt();
        int count = buffer.readVarInt();
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        String key = sender + " " + number;
        byte[][] got;
        synchronized (joining) {
            got = joining.get(key);
            if (got == null) {
                if (count < 2 || count > MAX_PAYLOAD / (MAX_BYTES - PART_HEADER) + 1) {
                    Host.send("log", "a message's part from " + sender + " says " + count + " parts: dropped");
                    return null;
                }
                if (joining.size() >= JOINING_MAX) {
                    String oldest = joining.keySet().iterator().next();
                    joining.remove(oldest);
                    Host.send("log", "a message in parts from " + oldest + " never finished: dropped");
                }
                got = new byte[count][];
                joining.put(key, got);
            }
            if (index < 0 || index >= got.length || got.length != count) {
                joining.remove(key);
                Host.send("log", "a message's part " + index + " of " + count + " from " + sender + " doesn't fit: dropped");
                return null;
            }
            got[index] = bytes;
            for (byte[] one : got) {
                if (one == null) {
                    return null;
                }
            }
            joining.remove(key);
        }
        java.io.ByteArrayOutputStream whole = new java.io.ByteArrayOutputStream();
        for (byte[] one : got) {
            whole.writeBytes(one);
        }
        return whole.toByteArray();
    }

    /** The window's way to the server during the configuration, as a configuration receiver's response sender. */
    private static final PacketSender CONFIGURATION_TO_SERVER = new PacketSender() {
        @Override
        public void sendPacket(CustomPacketPayload payload) {
            sendConfigurationToServer(payload);
        }
    };

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

    /**
     * The messages for each player not sent yet, in order: Horizon carries at most 32 of a mod's messages to a player a
     * tick and drops the rest, so a long message's parts (REI's display sync is about 60, D-234) leave over several
     * ticks, at most {@link #PER_TICK} a tick; and what a mod sent outside a server call waits for the next call.
     */
    private static final Map<java.util.UUID, java.util.ArrayDeque<String>> waitingFor = new java.util.LinkedHashMap<>();
    /** The most messages a player gets a tick from Kiln: under Horizon's 32, with room for a stray count. */
    static final int PER_TICK = 24;
    /**
     * The messages each player got since the server's last {@code server_tick_end} call began. Horizon sends a tick's
     * messages, and starts its count of 32 over, at its last level's pass; the server's tick ends after that, so what
     * that call sends leaves with the next tick's levels and counts with them. A call's game time doesn't tell those
     * apart (the first try counted by it: 24 at a tick's end and 24 at the next tick's join, 16 dropped).
     */
    private static final Map<java.util.UUID, Integer> sentThisTick = new java.util.HashMap<>();

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
    static byte[] pack(Types<?> types, CustomPacketPayload payload) {
        Identifier id = payload.type().id();
        StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload> codec = types.codec(id);
        if (codec == null) {
            throw new IllegalArgumentException("payload type " + id + " is not registered for " + types.name);
        }
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        if (types == CLIENTBOUND_CONFIGURATION || types == SERVERBOUND_CONFIGURATION) {
            buffer.writeIdentifier(CONFIGURATION);
        }
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
        if (buffer.readableBytes() > MAX_PAYLOAD) {
            throw new IllegalArgumentException("payload " + id + " is " + buffer.readableBytes() + " bytes, more than the "
                    + MAX_PAYLOAD + " a payload holds");
        }
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }

    /** A payload read from a message, with its type's id; a null payload when no mod registered the type. */
    record Unpacked(Identifier id, CustomPacketPayload payload, boolean configuration) {}

    static Unpacked unpack(Types<?> play, Types<?> configuration, byte[] bytes) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), null);
        Identifier id = buffer.readIdentifier();
        Types<?> types = play;
        if (id.equals(CONFIGURATION)) {
            types = configuration;
            id = buffer.readIdentifier();
        }
        StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload> codec = types.codec(id);
        if (codec == null) {
            return new Unpacked(id, null, types == configuration);
        }
        CustomPacketPayload payload = codec.decode(buffer);
        if (buffer.readableBytes() > 0) {
            throw new IllegalStateException("payload " + id + " left " + buffer.readableBytes() + " bytes unread");
        }
        return new Unpacked(id, payload, types == configuration);
    }

    /** The window's side: a mod's payload for the server ({@code ClientPlayNetworking.send}). */
    public static void sendToServer(CustomPacketPayload payload) {
        byte[] packed = pack(SERVERBOUND_PLAY, payload);
        if (packed != null) {
            toServer(packed);
        }
    }

    /** The window's side: a message for the server, in parts when it is longer than Horizon carries. */
    private static void toServer(byte[] packed) {
        for (byte[] part : parts(packed)) {
            Host.send("net", HEX.formatHex(part));
        }
    }

    /** The window's side: a message from the server, for the mods' client receiver of its type. */
    static void fromServer(String hex) {
        if (hex.startsWith(PART_HEX)) {
            // A part of a longer message (D-234): the message goes on once whole, before the window has its player too.
            byte[] whole = joined("server", HEX.parseHex(hex));
            if (whole != null) {
                fromServer(HEX.formatHex(whole));
            }
            return;
        }
        if (hex.startsWith(ATTACHMENT_HEX)) {
            // A synced attachment (D-199): kept by its target's name, so it needs no player of the window's yet.
            Attachments.received(HEX.parseHex(hex));
            return;
        }
        if (hex.startsWith(CONFIGURATION_HEX)) {
            fromServerConfiguring(unpack(CLIENTBOUND_PLAY, CLIENTBOUND_CONFIGURATION, HEX.parseHex(hex)));
            return;
        }
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
        Unpacked message = unpack(CLIENTBOUND_PLAY, CLIENTBOUND_CONFIGURATION, HEX.parseHex(hex));
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

    /** A message's start in hex when it is the configuration phase's. */
    private static final String CONFIGURATION_HEX = hexOf(CONFIGURATION);
    /** A message's start in hex when it is a synced attachment's ({@link Attachments}, D-199). */
    private static final String ATTACHMENT_HEX = hexOf(Attachments.SYNC);
    /** A message's start in hex when it is a part of a longer one. */
    private static final String PART_HEX = hexOf(PART);

    private static String hexOf(Identifier id) {
        RegistryFriendlyByteBuf marker = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        marker.writeIdentifier(id);
        byte[] bytes = new byte[marker.readableBytes()];
        marker.readBytes(bytes);
        return HEX.formatHex(bytes);
    }

    /**
     * The window's side: a configuration payload from the server (D-199), for the mods' configuration receiver of its
     * type, at once: as in vanilla, the configuration's come before the window has its player.
     */
    private static void fromServerConfiguring(Unpacked message) {
        @SuppressWarnings("unchecked")
        ClientConfigurationNetworking.ConfigurationPayloadHandler<CustomPacketPayload> handler =
                (ClientConfigurationNetworking.ConfigurationPayloadHandler<CustomPacketPayload>)
                        CLIENT_CONFIGURATION_RECEIVERS.get(message.id());
        if (message.payload() == null || handler == null) {
            Host.send("log", "a configuration payload from the server no mod receives: " + message.id());
            return;
        }
        if (heard.add("window configuration " + message.id())) {
            Host.send("log", "the window's first " + message.id() + " configuration payload reached its mod");
        }
        Minecraft client = Minecraft.getInstance();
        handler.receive(message.payload(), new ClientConfigurationNetworking.Context() {
            @Override
            public Minecraft client() {
                return client;
            }

            @Override
            public net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl packetListener() {
                return null;
            }

            @Override
            public PacketSender responseSender() {
                return CONFIGURATION_TO_SERVER;
            }
        });
    }

    /** The window's side: a mod's configuration payload for the server ({@code ClientConfigurationNetworking.send}). */
    public static void sendConfigurationToServer(CustomPacketPayload payload) {
        byte[] packed = pack(SERVERBOUND_CONFIGURATION, payload);
        if (packed != null) {
            toServer(packed);
        }
    }

    /** The server's side: a mod's configuration payload for a joining player, an effect of the call (D-199). */
    public static void sendConfigurationToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        byte[] packed = pack(CLIENTBOUND_CONFIGURATION, payload);
        if (packed != null) {
            send(player, packed);
        }
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
        if (packed != null) {
            send(player, packed);
        }
    }

    static void send(ServerPlayer player, byte[] packed) {
        synchronized (waitingFor) {
            java.util.ArrayDeque<String> queue = waitingFor.computeIfAbsent(player.getUUID(), k -> new java.util.ArrayDeque<>());
            for (byte[] part : parts(packed)) {
                queue.add("net " + player.getUUID() + " " + HEX.formatHex(part));
            }
        }
        if (Server.inCall()) {
            flush();
        }
    }

    /**
     * A server call runs: the messages waiting go with its effects, each player's at most {@link #PER_TICK} in the
     * server's tick, in the order they were sent.
     */
    static void flush() {
        synchronized (waitingFor) {
            for (var it = waitingFor.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                int sent = sentThisTick.getOrDefault(entry.getKey(), 0);
                java.util.ArrayDeque<String> queue = entry.getValue();
                while (sent < PER_TICK && !queue.isEmpty()) {
                    Server.effect(queue.poll());
                    sent++;
                }
                sentThisTick.put(entry.getKey(), sent);
                if (queue.isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    /** The server's tick ends ({@code server_tick_end} call): Horizon's count started over at its last level. */
    static void tickEnding() {
        synchronized (waitingFor) {
            sentThisTick.clear();
        }
    }

    /** Whether messages wait for a later tick: the server then calls at each tick's end ({@code server_tick_end}). */
    static boolean waiting() {
        synchronized (waitingFor) {
            return !waitingFor.isEmpty();
        }
    }

    /** A player who left: what still waited for them goes nowhere. */
    static void forget(java.util.UUID player) {
        synchronized (waitingFor) {
            waitingFor.remove(player);
        }
    }

    /** Whether a mod receives payloads on the server: only then does the server hand Kiln the players' messages. */
    static boolean receivesFromClients() {
        return !SERVER_RECEIVERS.isEmpty() || !SERVER_CONFIGURATION_RECEIVERS.isEmpty();
    }

    /** The server's side: a player's message, for the mods' server receiver of its type. The call's result. */
    static String fromClient(String hex, ServerPlayer player) {
        if (hex.startsWith(PART_HEX)) {
            byte[] whole = joined(String.valueOf(player.getUUID()), HEX.parseHex(hex));
            if (whole == null) {
                return "pass";
            }
            hex = HEX.formatHex(whole);
        }
        Unpacked message = unpack(SERVERBOUND_PLAY, SERVERBOUND_CONFIGURATION, HEX.parseHex(hex));
        if (message.configuration()) {
            return fromClientConfiguring(message, player);
        }
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

    /** The server's side: a player's configuration payload (D-199), for the mods' configuration receiver of its type. */
    private static String fromClientConfiguring(Unpacked message, ServerPlayer player) {
        @SuppressWarnings("unchecked")
        ServerConfigurationNetworking.ConfigurationPacketHandler<CustomPacketPayload> handler =
                (ServerConfigurationNetworking.ConfigurationPacketHandler<CustomPacketPayload>)
                        SERVER_CONFIGURATION_RECEIVERS.get(message.id());
        if (message.payload() == null || handler == null) {
            Host.send("log", "a configuration payload from " + player.getName().getString() + " no mod receives: "
                    + message.id());
            return "pass";
        }
        net.minecraft.server.network.ServerConfigurationPacketListenerImpl connection =
                new net.minecraft.server.network.ServerConfigurationPacketListenerImpl(player);
        PacketSender sender = ServerConfigurationNetworking.getSender(connection);
        handler.receive(message.payload(), new ServerConfigurationNetworking.Context() {
            @Override
            public MinecraftServer server() {
                return MinecraftServer.kiln$server();
            }

            @Override
            public net.minecraft.server.network.ServerConfigurationPacketListenerImpl networkHandler() {
                return connection;
            }

            @Override
            public PacketSender responseSender() {
                return sender;
            }
        });
        return "pass";
    }
}
