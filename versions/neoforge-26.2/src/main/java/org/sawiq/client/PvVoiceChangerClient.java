package org.sawiq.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import org.sawiq.PvVoiceChanger;
import org.sawiq.client.ui.UpdateAvailableScreen;
import org.sawiq.client.update.ModrinthVersionChecker;
import su.plo.voice.client.ModVoiceClient;

@EventBusSubscriber(modid = PvVoiceChanger.MOD_ID)
public final class PvVoiceChangerClient {
    private static boolean initialized;
    private static final ModrinthVersionChecker VERSION_CHECKER = new ModrinthVersionChecker();
    private static ModrinthVersionChecker.Result pendingUpdate;
    private static boolean updateScreenShown;
    private static boolean versionCheckStarted;

    private PvVoiceChangerClient() {
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

        if (pendingUpdate != null && !updateScreenShown && client.gui.screen() instanceof TitleScreen titleScreen) {
            updateScreenShown = true;
            client.setScreenAndShow(new UpdateAvailableScreen(titleScreen, pendingUpdate.version(), pendingUpdate.url()));
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
