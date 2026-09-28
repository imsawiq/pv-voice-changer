package org.sawiq.client.compat;

import net.neoforged.fml.ModList;

/**
 * The installed version of this mod, read from the loader.
 *
 * <p>This is the NeoForge variant; the Fabric builds ship a copy backed by
 * {@code FabricLoader}. Keeping the lookup here is what lets the update checker
 * and the update screen stay identical across every build.</p>
 */
public final class ModVersion {
    private static final String MOD_ID = "pv_voice_changer";
    private static final String UNKNOWN = "unknown";

    private ModVersion() {
    }

    public static String current() {
        return ModList.get()
                .getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(UNKNOWN);
    }
}
