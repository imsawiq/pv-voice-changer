package org.sawiq.client.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The installed version of this mod, read from the loader.
 *
 * <p>This is the Fabric variant; the NeoForge builds ship a copy backed by
 * {@code ModList}. Keeping the lookup here is what lets the update checker and
 * the update screen stay identical across all six builds.</p>
 */
public final class ModVersion {
    private static final String MOD_ID = "pv-voice-changer";
    private static final String UNKNOWN = "unknown";

    private ModVersion() {
    }

    public static String current() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse(UNKNOWN);
    }
}
