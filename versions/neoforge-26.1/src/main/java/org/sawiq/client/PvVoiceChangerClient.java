package org.sawiq.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import org.sawiq.PvVoiceChanger;
import org.sawiq.client.compat.ServerChannel;
import org.sawiq.client.ui.UpdateAvailableScreen;
import org.sawiq.client.update.ModrinthVersionChecker;
import su.plo.voice.client.ModVoiceClient;

/**
 * The client entrypoint.
 *
 * <p>Its own {@code @Mod} class rather than a branch inside the common one, so
 * that nothing here — nor anything it loads — is ever touched on a dedicated
 * server. That is the mechanism NeoForge provides for splitting a mod by
 * physical side.</p>
 */
@Mod(value = PvVoiceChanger.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = PvVoiceChanger.MOD_ID, value = Dist.CLIENT)
public final class PvVoiceChangerClient {
    private static boolean initialized;
    private static final ModrinthVersionChecker VERSION_CHECKER = new ModrinthVersionChecker();
    private static ModrinthVersionChecker.Result pendingUpdate;
    private static boolean updateScreenShown;
    private static boolean versionCheckStarted;

    public PvVoiceChangerClient() {
        // Before anything can connect: NeoForge collects payload handlers once
        // loading is done, and one registered after that is never delivered.
        ServerChannel.initialize(payload ->
                VoiceChangerAddon.INSTANCE.serverSession().receive(payload));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();

        if (!initialized && ModVoiceClient.INSTANCE != null) {
            VoiceChangerAddon.INSTANCE.initialize(ModVoiceClient.INSTANCE);
            initialized = true;
        }

        if (!versionCheckStarted) {
            versionCheckStarted = true;
            VERSION_CHECKER.checkAsync().thenAccept(result -> {
                if (result != null) {
                    Minecraft.getInstance().execute(() -> pendingUpdate = result);
                }
            });
        }

        if (!initialized) {
            return;
        }

        VoiceChangerAddon.INSTANCE.tick();

        if (pendingUpdate != null && !updateScreenShown && client.screen instanceof TitleScreen titleScreen) {
            updateScreenShown = true;
            client.setScreen(new UpdateAvailableScreen(titleScreen, pendingUpdate.version(), pendingUpdate.url(), pendingUpdate.curseForgeUrl()));
            pendingUpdate = null;
        }
    }

    @SubscribeEvent
    public static void onClientStopping(ClientStoppingEvent event) {
        if (!initialized) {
            return;
        }

        VoiceChangerAddon.INSTANCE.shutdown();
        initialized = false;
    }
}
