package org.sawiq;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = PvVoiceChanger.MOD_ID, dist = Dist.CLIENT)
public final class PvVoiceChanger {
    public static final String MOD_ID = "pv_voice_changer";
    public static final String RESOURCE_ID = "pv-voice-changer";
    public static final String MOD_NAME = "Plasmo Voice Changer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public PvVoiceChanger() {
        LOGGER.info("Loaded {} for NeoForge", MOD_NAME);
    }
}
