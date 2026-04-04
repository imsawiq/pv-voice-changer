package org.sawiq.mixin.client;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import org.sawiq.client.VoiceChangerAddon;
import org.sawiq.client.model.VoiceChangerPreset;
import org.sawiq.client.ui.VoiceChangerStudioScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.plo.lib.mod.client.gui.components.Button;
import su.plo.slib.api.chat.component.McTextComponent;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.client.config.VoiceClientConfig;
import su.plo.voice.client.gui.settings.VoiceSettingsScreen;
import su.plo.voice.client.gui.settings.tab.AbstractHotKeysTabWidget;
import su.plo.voice.client.gui.settings.tab.ActivationTabWidget;

@Mixin(ActivationTabWidget.class)
public abstract class ActivationTabWidgetMixin extends AbstractHotKeysTabWidget {
    protected ActivationTabWidgetMixin(VoiceSettingsScreen parent, PlasmoVoiceClient voiceClient, VoiceClientConfig config) {
        super(parent, voiceClient, config);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void pvvoicechanger$injectVoiceChangerControls(CallbackInfo ci) {
        VoiceChangerAddon addon = VoiceChangerAddon.INSTANCE;
        if (!addon.isInitialized()) {
            return;
        }

        List<McTextComponent> presetLabels = Arrays.stream(VoiceChangerPreset.values())
                .map(preset -> McTextComponent.translatable(preset.getTranslationKey()))
                .collect(Collectors.toList());

        this.addEntry(new CategoryEntry(tr("pvvoicechanger.tab.category")));
        this.addEntry(this.createToggleEntry(
                tr("pvvoicechanger.tab.enable"),
                tr("pvvoicechanger.tab.enable.desc"),
                addon.getEnabledEntry()
        ));
        this.addEntry(this.createDropDownEntry(
                tr("pvvoicechanger.tab.preset"),
                tr("pvvoicechanger.tab.preset.desc"),
                VoiceChangerPreset.class,
                presetLabels,
                addon.getPresetControl(),
                false
        ));
        this.addEntry(this.createHotKey(
                "pvvoicechanger.tab.toggle_bind_label",
                "pvvoicechanger.tab.toggle_bind.desc",
                addon.getToggleHotkeyEntry()
        ));
        this.addEntry(new FullWidthEntry<>(new Button(
                0,
                0,
                220,
                20,
                tr("pvvoicechanger.tab.open_studio"),
                button -> {
                    if (this.parent instanceof VoiceSettingsScreen settingsScreen) {
                        Minecraft.getInstance().setScreen(new VoiceChangerStudioScreen(settingsScreen.getMinecraftScreen(), addon));
                    }
                },
                Button.NO_TOOLTIP
        )));
        this.addEntry(new FullWidthEntry<>(new Button(
                0,
                0,
                220,
                20,
                tr("pvvoicechanger.tab.open_folder"),
                button -> {
                    try {
                        addon.ensurePresetDirectory();
                        net.minecraft.util.Util.getPlatform().openPath(addon.getPresetDirectory());
                    } catch (java.io.IOException ignored) {
                    }
                },
                Button.NO_TOOLTIP
        )));
    }

    private static McTextComponent tr(String key) {
        return McTextComponent.translatable(key);
    }
}
