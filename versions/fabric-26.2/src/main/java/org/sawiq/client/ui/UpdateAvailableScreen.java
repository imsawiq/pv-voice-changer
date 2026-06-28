package org.sawiq.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.fabricmc.loader.api.FabricLoader;

public final class UpdateAvailableScreen extends Screen {
    private final Screen parent;
    private final String newVersion;
    private final String currentVersion;
    private final String url;

    public UpdateAvailableScreen(Screen parent, String newVersion, String url) {
        super(Component.translatable("pvvoicechanger.update.title"));
        this.parent = parent;
        this.newVersion = newVersion;
        this.url = url;
        this.currentVersion = FabricLoader.getInstance()
                .getModContainer("pv-voice-changer")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        addRenderableWidget(Button.builder(Component.translatable("pvvoicechanger.update.open_page"), button -> {
                    try {
                        java.awt.Desktop.getDesktop().browse(java.net.URI.create(this.url));
                    } catch (Exception ignored) {
                    }
                })
                .bounds(centerX - 100, centerY + 24, 200, 20)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("pvvoicechanger.update.dismiss"), button -> onClose())
                .bounds(centerX - 100, centerY + 50, 200, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int baseY = this.height / 2 - 40;

        context.centeredText(this.font, this.title, centerX, baseY, 0xFFFFFFFF);
        context.centeredText(this.font, Component.translatable("pvvoicechanger.update.subtitle", this.newVersion), centerX, baseY + 18, 0xFF55FF55);
        context.centeredText(this.font, Component.translatable("pvvoicechanger.update.current", this.currentVersion), centerX, baseY + 32, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        if (this.minecraft == null) {
            return;
        }
        if (this.parent != null) {
            this.minecraft.setScreenAndShow(this.parent);
        } else {
            this.minecraft.setScreenAndShow(null);
        }
    }
}
