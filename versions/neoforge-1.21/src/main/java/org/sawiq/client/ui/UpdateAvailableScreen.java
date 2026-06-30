package org.sawiq.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import net.neoforged.fml.ModList;

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
        this.currentVersion = ModList.get().getModContainerById("pv_voice_changer")
                .map(c -> c.getModInfo().getVersion().toString())
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
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int baseY = this.height / 2 - 40;

        drawCenteredText(context, this.title, centerX, baseY, 0xFFFFFFFF);
        drawCenteredText(context, Component.translatable("pvvoicechanger.update.subtitle", this.newVersion), centerX, baseY + 18, 0xFF55FF55);
        drawCenteredText(context, Component.translatable("pvvoicechanger.update.current", this.currentVersion), centerX, baseY + 32, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        if (this.minecraft == null) {
            return;
        }
        if (this.parent != null) {
            this.minecraft.setScreen(this.parent);
        } else {
            this.minecraft.setScreen(null);
        }
    }

    private void drawCenteredText(GuiGraphics context, Component text, int centerX, int y, int color) {
        int width = this.font.width(text);
        context.drawString(this.font, text, centerX - width / 2, y, color);
    }
}
