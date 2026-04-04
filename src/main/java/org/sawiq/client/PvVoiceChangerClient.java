package org.sawiq.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import su.plo.voice.client.ModVoiceClient;

public class PvVoiceChangerClient implements ClientModInitializer {
    private static boolean initialized;

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
            if (initialized) {
                VoiceChangerAddon.INSTANCE.tick();
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (!initialized) {
                return;
            }

            VoiceChangerAddon.INSTANCE.shutdown();
            initialized = false;
        });
    }
}
