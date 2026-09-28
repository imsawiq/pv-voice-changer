package org.sawiq.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The handful of client calls whose signatures differ between Minecraft
 * versions, in one place.
 *
 * <p>Every supported version ships its own copy of this class and of
 * {@code ui.compat}. Everything else in the mod is identical across every
 * build, which is what lets {@code scripts/sync-versions.ps1} copy the
 * shared sources verbatim instead of leaving the forks to drift apart.</p>
 *
 * <p>This is the Minecraft 1.21.11 variant.</p>
 */
public final class ClientScreens {
    private ClientScreens() {
    }

    /** The screen currently displayed, or {@code null} when in-game. */
    public static Screen current() {
        return Minecraft.getInstance().screen;
    }

    public static void open(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    /** Writes a line into the chat log. No-op when no player is loaded. */
    public static void sendChatMessage(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(message, false);
        }
    }

    /** Writes a line above the hotbar. No-op when no player is loaded. */
    public static void sendActionBarMessage(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(message, true);
        }
    }
}
