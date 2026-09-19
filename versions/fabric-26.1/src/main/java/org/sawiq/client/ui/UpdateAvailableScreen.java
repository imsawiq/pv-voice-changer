package org.sawiq.client.ui;

import java.awt.Desktop;
import java.net.URI;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.sawiq.client.compat.ClientScreens;
import org.sawiq.client.compat.ModVersion;
import org.sawiq.client.ui.compat.CompatScreen;
import org.sawiq.client.ui.compat.StudioGraphics;

/** Shown once on the title screen when Modrinth reports a newer release. */
public final class UpdateAvailableScreen extends CompatScreen {
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private final String newVersion;
    private final String currentVersion;
    private final String modrinthUrl;
    private final String curseForgeUrl;

    public UpdateAvailableScreen(Screen parent, String newVersion, String modrinthUrl, String curseForgeUrl) {
        super(Component.translatable("pvvoicechanger.update.title"));
        this.parent = parent;
        this.newVersion = newVersion;
        this.modrinthUrl = modrinthUrl;
        this.curseForgeUrl = curseForgeUrl;
        this.currentVersion = ModVersion.current();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        addButton(Component.translatable("pvvoicechanger.update.open_modrinth"), centerX, centerY + 24, () -> openUrl(this.modrinthUrl));
        addButton(Component.translatable("pvvoicechanger.update.open_curseforge"), centerX, centerY + 48, () -> openUrl(this.curseForgeUrl));
        addButton(Component.translatable("pvvoicechanger.update.dismiss"), centerX, centerY + 72, this::onClose);
    }

    @Override
    protected void paintAboveWidgets(StudioGraphics graphics, int mouseX, int mouseY) {
        int centerX = this.width / 2;
        int baseY = this.height / 2 - 40;

        graphics.centeredText(this.title, centerX, baseY, 0xFFFFFFFF);
        graphics.centeredText(Component.translatable("pvvoicechanger.update.subtitle", this.newVersion), centerX, baseY + 18, 0xFF55FF55);
        graphics.centeredText(Component.translatable("pvvoicechanger.update.current", this.currentVersion), centerX, baseY + 32, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        ClientScreens.open(this.parent);
    }

    private void addButton(Component label, int centerX, int y, Runnable action) {
        addRenderableWidget(Button.builder(label, button -> action.run())
                .bounds(centerX - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private static void openUrl(String target) {
        try {
            Desktop.getDesktop().browse(URI.create(target));
        } catch (Exception ignored) {
            // Nothing useful to do if the platform has no browser hook; the
            // version number is on screen and can be looked up by hand.
        }
    }
}
