package org.sawiq.client.compat;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.Identifier;
import org.sawiq.PvVoiceChanger;
import org.sawiq.protocol.VoiceChangerChannel;
import su.plo.slib.mod.channel.ByteArrayCodec;
import su.plo.slib.mod.channel.ByteArrayPayload;
import su.plo.slib.mod.channel.ModChannelManager;

/**
 * The client end of the plugin channel.
 *
 * <p>The payload type itself comes from Plasmo Voice's own server library,
 * which is what the server side sends through. Registering a second type for
 * the same channel id would be a different packet as far as Minecraft is
 * concerned, so this asks that library for the one it already uses.</p>
 *
 * <p>This is the Fabric 26.2 variant.</p>
 */
public final class ServerChannel {
    private static ByteArrayCodec codec;

    private ServerChannel() {
    }

    /**
     * Registers the receiver. Must be called during client initialisation:
     * Minecraft settles which payload types exist before anyone connects, and
     * a type registered later would never arrive.
     */
    public static void initialize(Consumer<byte[]> receiver) {
        if (codec != null) {
            return;
        }

        codec = ModChannelManager.Companion.getOrRegisterCodec(
                Identifier.parse(VoiceChangerChannel.CHANNEL));

        ClientPlayNetworking.registerGlobalReceiver(codec.getType(), (payload, context) -> {
            byte[] data = payload.getData();
            // Arrives on a network thread; everything it touches is client state.
            context.client().execute(() -> receiver.accept(data));
        });
    }

    /** Whether there is a server on the other end that speaks this channel. */
    public static boolean isConnected() {
        return codec != null && ClientPlayNetworking.canSend(codec.getType());
    }

    /** @return whether the message was handed to the network layer */
    public static boolean send(byte[] payload) {
        if (!isConnected()) {
            return false;
        }

        try {
            ClientPlayNetworking.send(new ByteArrayPayload(codec.getType(), payload));
            return true;
        } catch (RuntimeException exception) {
            // The connection can drop between the check and the send.
            PvVoiceChanger.LOGGER.debug("Could not send on the voice changer channel", exception);
            return false;
        }
    }
}
