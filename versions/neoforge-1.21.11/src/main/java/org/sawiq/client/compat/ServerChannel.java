package org.sawiq.client.compat;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
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
 * <p>This is the NeoForge 1.21.11 variant: Minecraft renamed
 * {@code ResourceLocation} to {@code Identifier} in that release.</p>
 */
public final class ServerChannel {
    private static ByteArrayCodec codec;
    private static Identifier channelId;

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

        channelId = Identifier.parse(VoiceChangerChannel.CHANNEL);
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
            ClientPacketDistributor.sendToServer(new ByteArrayPayload(codec.getType(), payload));
            return true;
        } catch (RuntimeException exception) {
            // The connection can drop between the check and the send.
            PvVoiceChanger.LOGGER.debug("Could not send on the voice changer channel", exception);
            return false;
        }
    }
}
