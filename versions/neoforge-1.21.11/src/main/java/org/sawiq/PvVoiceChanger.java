package org.sawiq;

import net.neoforged.fml.common.Mod;
import org.sawiq.server.VoiceChangerServerAddon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import su.plo.voice.api.server.PlasmoVoiceServer;

/**
 * The common entrypoint, which loads the server half of the mod.
 *
 * <p>Not restricted to a dist any more, so that a world opened to LAN gets the
 * same policy as a real server. The client half lives in
 * {@link org.sawiq.client.PvVoiceChangerClient}, which is annotated for the
 * client dist so that nothing touching Minecraft's client classes is ever
 * loaded on a dedicated server.</p>
 */
@Mod(PvVoiceChanger.MOD_ID)
public final class PvVoiceChanger {
    public static final String MOD_ID = "pv_voice_changer";
    public static final String RESOURCE_ID = "pv-voice-changer";
    public static final String MOD_NAME = "Plasmo Voice Changer";
    /** Sent to the server in the greeting, purely so operators can read it in a log. */
    public static final String MOD_VERSION = "1.7";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public PvVoiceChanger() {
        PlasmoVoiceServer.getAddonsLoader().load(new VoiceChangerServerAddon());
        LOGGER.info("Loaded {} for NeoForge", MOD_NAME);
    }
}
