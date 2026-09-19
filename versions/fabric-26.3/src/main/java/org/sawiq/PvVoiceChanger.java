package org.sawiq;

import net.fabricmc.api.ModInitializer;
import org.sawiq.server.VoiceChangerServerAddon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import su.plo.voice.api.server.PlasmoVoiceServer;

/**
 * Loads the server half of the mod.
 *
 * <p>Registered as a common rather than a dedicated-server entrypoint, so that
 * a world opened to LAN gets the same policy as a real server. On a client that
 * never hosts anything, the addon is loaded and simply never has a player to
 * talk to.</p>
 */
public class PvVoiceChanger implements ModInitializer {
    public static final String MOD_ID = "pv-voice-changer";
    public static final String MOD_NAME = "Plasmo Voice Changer";
    /** Sent to the server in the greeting, purely so operators can read it in a log. */
    public static final String MOD_VERSION = "1.7";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    @Override
    public void onInitialize() {
        VoiceChangerServerAddon addon = new VoiceChangerServerAddon();

        // Before the addon is loaded: Plasmo Voice initialises addons once its
        // own server is running, which is after Minecraft has built its command
        // tree, so the command has to be subscribed from here to appear at all.
        addon.registerCommands();
        PlasmoVoiceServer.getAddonsLoader().load(addon);
    }
}
