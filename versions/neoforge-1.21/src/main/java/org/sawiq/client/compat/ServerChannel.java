package org.sawiq.client.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.sawiq.PvVoiceChanger;
import org.sawiq.protocol.VoiceChangerChannel;
import su.plo.slib.mod.channel.ByteArrayCodec;
import su.plo.slib.mod.channel.ByteArrayPayload;
import su.plo.slib.mod.channel.ModChannelManager;

/**
 * The client end of the plugin channel.
 *
 * <p>The payload type itself comes from Plasmo Voice's own server library,
 * which is what the server side sends through, and which registers it with
 * NeoForge as an optional bidirectional payload. Registering a second type for
 * the same channel id would be a different packet as far as Minecraft is
 * concerned, so this asks that library for the one it already uses.</p>
 *
 * <p>This is the NeoForge 1.21 variant.</p>
 */
public final class ServerChannel {
    private static ByteArrayCodec codec;
    private static ResourceLocation channelId;

    private ServerChannel() {
    }

    /**
     * Registers the receiver. Must be called while mods are being constructed:
     * NeoForge collects payload handlers in an event fired once loading is
     * done, and anything registered after that is never delivered.
     */
    public static void initialize(Consumer<byte[]> receiver) {
        if (codec != null) {
            return;
        }

        channelId = ResourceLocation.parse(VoiceChangerChannel.CHANNEL);
        codec = ModChannelManager.Companion.getOrRegisterCodec(channelId);

        ModChannelManager.Companion.registerClientHandler(channelId, (payload, context) -> {
            byte[] data = payload.getData();
            // Arrives on a network thread; everything it touches is client state.
            context.enqueueWork(() -> receiver.accept(data));
        });
    }

    /** Whether there is a server on the other end that speaks this channel. */
    public static boolean isConnected() {
        if (codec == null) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        return client.getConnection() != null
                && NetworkRegistry.hasChannel(client.getConnection(), channelId);
    }

    /** @return whether the message was handed to the network layer */
    public static boolean send(byte[] payload) {
        if (!isConnected()) {
            return false;
        }

        try {
            sendToServer(new ByteArrayPayload(codec.getType(), payload));
            return true;
        } catch (RuntimeException exception) {
            // The connection can drop between the check and the send.
            PvVoiceChanger.LOGGER.debug("Could not send on the voice changer channel", exception);
            return false;
        }
    }

    /**
     * NeoForge moved the client-side send out of {@code PacketDistributor} and
     * into {@code ClientPacketDistributor} in 1.21.7, removing the old entry
     * point at the same time. This build covers 1.21 through 1.21.11, which
     * sits on both sides of that move, so neither class can be named at
     * compile time without breaking half the range.
     *
     * <p>Resolved once when the class loads. Sending is rare here - a greeting
     * per connection and a reply to a policy change, never audio.</p>
     */
    private static final Method SEND_TO_SERVER = resolveSendToServer();

    private static Method resolveSendToServer() {
        String[] candidates = {
            "net.neoforged.neoforge.client.network.ClientPacketDistributor", // 1.21.7 and later
            "net.neoforged.neoforge.network.PacketDistributor",              // up to 1.21.6
        };

        for (String className : candidates) {
            try {
                Class<?> distributor = Class.forName(className);
                return distributor.getMethod(
                        "sendToServer", CustomPacketPayload.class, CustomPacketPayload[].class);
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                // Expected: only one of the two exists on any given version.
            }
        }

        PvVoiceChanger.LOGGER.error(
                "No NeoForge packet sender found; the voice changer cannot talk to the server");
        return null;
    }

    private static void sendToServer(CustomPacketPayload payload) {
        if (SEND_TO_SERVER == null) {
            return;
        }

        try {
            SEND_TO_SERVER.invoke(null, payload, new CustomPacketPayload[0]);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Could not send on the voice changer channel", exception);
        }
    }
}
