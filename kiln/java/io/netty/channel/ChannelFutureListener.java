package io.netty.channel;

/**
 * Kiln's stand-in for Netty's {@code ChannelFutureListener}, only its name: Kiln carries no Netty channels (its
 * runtime has Netty's buffers only), yet mods name the type in calls such as a configuration connection's
 * {@code send(packet, listener)} (D-199), where they pass null. A listener Kiln is given is not called.
 */
public interface ChannelFutureListener {}
