package org.sawiq.server;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.sawiq.protocol.PolicyReason;
import org.sawiq.protocol.SharedPreset;
import org.sawiq.protocol.VoiceChangerChannel;
import org.sawiq.protocol.VoiceChangerCodec;
import org.sawiq.protocol.VoiceSourceRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import su.plo.slib.api.entity.player.McPlayer;
import su.plo.slib.api.event.player.McPlayerQuitEvent;
import su.plo.slib.api.permission.PermissionDefault;
import su.plo.slib.api.server.McServerLib;
import su.plo.slib.api.server.channel.McServerChannelHandler;
import su.plo.slib.api.server.entity.player.McServerPlayer;
import su.plo.slib.api.server.event.command.McServerCommandsRegisterEvent;
import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.server.PlasmoVoiceServer;

/**
 * The server half of the mod: the policy, the moderation commands and the
 * voices the server offers.
 *
 * <p>Worth being clear about what this can and cannot do. The voice is changed
 * on the speaker's own machine, before the audio is encoded and sent, so by the
 * time anything reaches the server it has already happened and cannot be
 * undone. Everything here is therefore a policy that an unmodified client
 * obeys, in the same way the vanilla client obeys a server telling it the
 * player is in survival mode. It stops the ordinary player who was asked not
 * to; it does not stop somebody running a patched build, and no server-side
 * code could.</p>
 *
 * <p>All of it runs through Plasmo Voice's own server library, so there is one
 * implementation rather than one per loader and Minecraft version.</p>
 */
@Addon(
        id = "pv-voice-changer-server",
        name = "Plasmo Voice Changer",
        scope = AddonLoaderScope.SERVER,
        version = "1.7",
        authors = {"sawiq_"},
        dependencies = {}
)
public final class VoiceChangerServerAddon implements AddonInitializer {
    /** Everyone may change their voice unless a server says otherwise. */
    public static final String PERMISSION_USE = "pv-voice-changer.use";
    /** Running the moderation command is an operator's job by default. */
    public static final String PERMISSION_COMMAND = "pv-voice-changer.command";

    private static final Logger LOGGER = LoggerFactory.getLogger("Plasmo Voice Changer");
    private static final String CONFIG_DIRECTORY = "pv-voice-changer";

    @InjectPlasmoVoice
    private PlasmoVoiceServer voiceServer;

    /**
     * Players known to have the mod, because they said so on our channel.
     * Sending to anybody else would be a packet on a channel their client has
     * never heard of.
     */
    private final Set<UUID> playersWithMod = ConcurrentHashMap.newKeySet();

    private ServerVoiceChangerConfig config;
    private VoiceChangerMutes mutes;
    private ServerPresetLibrary presetLibrary;

    private final McPlayerQuitEvent.Callback quitCallback = this::onPlayerQuit;
    /**
     * Held rather than passed inline, because unregistering needs the same
     * instance that was registered. An integrated server reloads addons
     * between worlds, and a handler left behind would answer twice.
     */
    private final McServerChannelHandler channelHandler = this::onChannelMessage;

    /**
     * Subscribes the command registration.
     *
     * <p>Called from the mod entrypoint rather than from {@link
     * #onAddonInitialize}, which Plasmo Voice runs only once its own server is
     * up — by then Minecraft has already built its command tree and a listener
     * added there never fires. The command reads its state through this addon,
     * so it copes with being asked something before that state exists.</p>
     */
    public void registerCommands() {
        McServerCommandsRegisterEvent.INSTANCE.registerListener((manager, minecraftServer) ->
                manager.register("voicechanger", new VoiceChangerCommand(this), "vc"));
    }

    /** Whether the addon has been given its configuration yet. */
    public boolean isReady() {
        return this.config != null;
    }

    @Override
    public void onAddonInitialize() {
        McServerLib server = this.voiceServer.getMinecraftServer();
        Path directory = server.getConfigsFolder().toPath().resolve(CONFIG_DIRECTORY);

        this.config = new ServerVoiceChangerConfig(directory);
        this.mutes = new VoiceChangerMutes(directory);
        this.presetLibrary = new ServerPresetLibrary(directory);
        loadFromDisk();

        server.getPermissionManager().register(PERMISSION_USE, PermissionDefault.TRUE);
        server.getPermissionManager().register(PERMISSION_COMMAND, PermissionDefault.OP);

        server.getChannelManager().registerChannelHandler(
                VoiceChangerChannel.CHANNEL, this.channelHandler);

        McPlayerQuitEvent.INSTANCE.registerListener(this.quitCallback);

        LOGGER.info("Voice changer server policy ready: {}, {} muted, {} shared voices",
                this.config.isAllowed() ? "allowed" : "denied",
                this.mutes.size(),
                this.presetLibrary.presets().size());
    }

    @Override
    public void onAddonShutdown() {
        this.voiceServer.getMinecraftServer().getChannelManager()
                .unregisterChannelHandler(VoiceChangerChannel.CHANNEL, this.channelHandler);
        McPlayerQuitEvent.INSTANCE.unregisterListener(this.quitCallback);
        this.playersWithMod.clear();
    }

    // --- State the command acts on ------------------------------------------

    public ServerVoiceChangerConfig config() {
        return this.config;
    }

    public VoiceChangerMutes mutes() {
        return this.mutes;
    }

    public ServerPresetLibrary presetLibrary() {
        return this.presetLibrary;
    }

    public McServerLib server() {
        return this.voiceServer.getMinecraftServer();
    }

    /** Rereads every file, so an operator can edit them without a restart. */
    public void reload() throws IOException {
        this.config.load();
        this.mutes.load();
        this.presetLibrary.reload();
        broadcastPolicy();
    }

    // --- Telling clients what applies ---------------------------------------

    /** Re-sends the policy to everybody who has the mod. */
    public void broadcastPolicy() {
        for (McServerPlayer player : this.voiceServer.getMinecraftServer().getPlayers()) {
            if (this.playersWithMod.contains(player.getUuid())) {
                sendPolicy(player);
            }
        }
    }

    /** Re-sends the policy to one player, if they have the mod. */
    public void sendPolicyIfPresent(UUID playerId) {
        if (!this.playersWithMod.contains(playerId)) {
            return;
        }

        McServerPlayer player = this.voiceServer.getMinecraftServer().getPlayerById(playerId);
        if (player != null) {
            sendPolicy(player);
        }
    }

    private void sendPolicy(McPlayer player) {
        player.sendPacket(VoiceChangerChannel.CHANNEL, VoiceChangerCodec.encodePolicy(policyFor(player)));
    }

    /**
     * The strictest of the three answers wins, and the reason names the first
     * thing that would have to change: telling a muted player that the server
     * has it switched off would send them to complain to the wrong person.
     */
    private VoiceChangerCodec.Policy policyFor(McPlayer player) {
        VoiceSourceRule voices = this.config.getAllowedVoices();

        if (!this.config.isAllowed()) {
            return new VoiceChangerCodec.Policy(
                    false, PolicyReason.SERVER_DISABLED, this.config.getDeniedMessage(), voices);
        }
        if (this.mutes.isMuted(player.getUuid())) {
            return new VoiceChangerCodec.Policy(false, PolicyReason.PLAYER_MUTED, "", voices);
        }
        if (!player.hasPermission(PERMISSION_USE)) {
            return new VoiceChangerCodec.Policy(false, PolicyReason.NO_PERMISSION, "", voices);
        }
        return new VoiceChangerCodec.Policy(true, PolicyReason.ALLOWED, "", voices);
    }

    // --- Channel ------------------------------------------------------------

    /**
     * The client's greeting is the whole handshake: it proves the mod is there
     * and gives us a moment to answer that is guaranteed to be after the client
     * can receive on this channel.
     */
    private void onChannelMessage(McServerPlayer player, byte[] payload) {
        if (VoiceChangerCodec.peekType(payload) != VoiceChangerChannel.TYPE_HELLO) {
            return;
        }

        try {
            VoiceChangerCodec.Hello hello = VoiceChangerCodec.decodeHello(payload);
            this.playersWithMod.add(player.getUuid());
            sendPolicy(player);
            sendPresets(player);

            LOGGER.debug("{} joined with voice changer {} (api {})",
                    player.getName(), hello.modVersion(), hello.apiVersion());
        } catch (IOException exception) {
            // A client we cannot understand is one we cannot usefully answer.
            LOGGER.debug("Ignoring an unreadable voice changer greeting from {}: {}",
                    player.getName(), exception.getMessage());
        }
    }

    private void sendPresets(McPlayer player) {
        if (!this.config.isSharePresets()) {
            return;
        }

        List<SharedPreset> presets = this.presetLibrary.presets();
        if (!presets.isEmpty()) {
            player.sendPacket(VoiceChangerChannel.CHANNEL, VoiceChangerCodec.encodePresets(presets));
        }
    }

    private void onPlayerQuit(McPlayer player) {
        this.playersWithMod.remove(player.getUuid());
    }

    private void loadFromDisk() {
        try {
            this.config.load();
        } catch (IOException exception) {
            LOGGER.warn("Could not read the voice changer server config; using defaults", exception);
        }

        try {
            this.mutes.load();
        } catch (IOException exception) {
            LOGGER.warn("Could not read the voice changer mute list; starting empty", exception);
        }

        try {
            this.presetLibrary.reload();
        } catch (IOException exception) {
            LOGGER.warn("Could not read the shared voice folder; sharing nothing", exception);
        }
    }
}
