package org.sawiq.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.sawiq.client.ui.UpdateAvailableScreen;
import org.sawiq.client.update.ModrinthVersionChecker;
import su.plo.voice.client.ModVoiceClient;

public class PvVoiceChangerClient implements ClientModInitializer {
    private static boolean initialized;
    private final ModrinthVersionChecker versionChecker = new ModrinthVersionChecker();
    private ModrinthVersionChecker.Result pendingUpdate;
    private boolean updateScreenShown;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (initialized || ModVoiceClient.INSTANCE == null) {
                return;
            }

            VoiceChangerAddon.INSTANCE.initialize(ModVoiceClient.INSTANCE);
            initialized = true;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!initialized) {
                return;
            }
            VoiceChangerAddon.INSTANCE.tick();

            if (this.pendingUpdate != null && !this.updateScreenShown && client.screen instanceof TitleScreen titleScreen) {
                this.updateScreenShown = true;
                client.setScreen(new UpdateAvailableScreen(titleScreen, this.pendingUpdate.version(), this.pendingUpdate.url()));
                this.pendingUpdate = null;
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (!initialized) {
                return;
            }

            VoiceChangerAddon.INSTANCE.shutdown();
            initialized = false;
        });

        this.versionChecker.checkAsync().thenAccept(result -> {
            if (result != null && Minecraft.getInstance() != null) {
                Minecraft.getInstance().execute(() -> this.pendingUpdate = result);
            }
        });
    }
}
