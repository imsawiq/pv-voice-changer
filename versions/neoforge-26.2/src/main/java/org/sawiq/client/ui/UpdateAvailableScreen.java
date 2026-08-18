package org.sawiq.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import net.neoforged.fml.ModList;

public final class UpdateAvailableScreen extends Screen {
    private final Screen parent;
    private final String newVersion;
    private final String currentVersion;
    private final String url;
    private final String curseForgeUrl;

    public UpdateAvailableScreen(Screen parent, String newVersion, String url, String curseForgeUrl) {
        super(Component.translatable("pvvoicechanger.update.title"));
        this.parent = parent;
        this.newVersion = newVersion;
        this.url = url;
        this.curseForgeUrl = curseForgeUrl;
        this.currentVersion = ModList.get().getModContainerById("pv_voice_changer")
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        addRenderableWidget(Button.builder(Component.translatable("pvvoicechanger.update.open_modrinth"), button -> openUrl(this.url))
                .bounds(centerX - 100, centerY + 24, 200, 20)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("pvvoicechanger.update.open_curseforge"), button -> openUrl(this.curseForgeUrl))
                .bounds(centerX - 100, centerY + 48, 200, 20)
                .build());

        addRenderableWidget(Button.builder(Component.translatable("pvvoicechanger.update.dismiss"), button -> onClose())
                .bounds(centerX - 100, centerY + 72, 200, 20)
                .build());
    }

    private static void openUrl(String target) {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(target));
        } catch (Exception ignored) {
        }
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
