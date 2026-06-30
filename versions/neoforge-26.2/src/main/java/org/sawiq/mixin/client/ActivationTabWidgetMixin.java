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
import su.plo.config.entry.ConfigEntry;
import su.plo.lib.mod.client.gui.components.Button;
import su.plo.slib.api.chat.component.McTextComponent;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.client.config.VoiceClientConfig;
import su.plo.voice.client.gui.settings.VoiceSettingsScreen;
import su.plo.voice.client.gui.settings.tab.AbstractHotKeysTabWidget;
import su.plo.voice.client.gui.settings.tab.ActivationTabWidget;
import su.plo.voice.client.gui.settings.widget.DropDownWidget;

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

        addon.reloadSavedPresetNames();

        VoiceChangerPreset[] builtInPresets = VoiceChangerPreset.values();
        List<String> savedPresets = addon.getSavedPresetNamesCached();
        List<McTextComponent> presetLabels = Arrays.stream(builtInPresets)
                .map(preset -> McTextComponent.translatable(preset.getTranslationKey()))
                .collect(Collectors.toList());
        presetLabels.addAll(savedPresets.stream().map(McTextComponent::literal).collect(Collectors.toList()));
        int initialIndex = currentPresetIndex(addon, builtInPresets, savedPresets);
        ConfigEntry<Integer> presetIndexEntry = new ConfigEntry<>(initialIndex);
        DropDownWidget[] presetDropDown = new DropDownWidget[1];
        presetDropDown[0] = new DropDownWidget(
                this.parent,
                0,
                0,
                160,
                20,
                presetLabels.get(initialIndex),
                presetLabels,
                false,
                index -> {
                    presetIndexEntry.set(index);
                    presetDropDown[0].setText(presetLabels.get(index));

                    if (index < builtInPresets.length) {
                        addon.applyBuiltInPreset(builtInPresets[index]);
                        return;
                    }

                    String savedPresetName = savedPresets.get(index - builtInPresets.length);
                    try {
                        addon.loadSavedPreset(savedPresetName);
                    } catch (java.io.IOException ignored) {
                    }
                }
        );

        this.addEntry(new CategoryEntry(tr("pvvoicechanger.tab.category")));
        this.addEntry(this.createToggleEntry(
                tr("pvvoicechanger.tab.enable"),
                tr("pvvoicechanger.tab.enable.desc"),
                addon.getEnabledEntry()
        ));
        this.addEntry(new OptionEntry<>(
                tr("pvvoicechanger.tab.preset"),
                presetDropDown[0],
                presetIndexEntry,
                tr("pvvoicechanger.tab.preset.desc")
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
                        Minecraft.getInstance().setScreenAndShow(new VoiceChangerStudioScreen(settingsScreen.getMinecraftScreen(), addon));
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
                        java.awt.Desktop.getDesktop().open(addon.getPresetDirectory().toFile());
                    } catch (java.io.IOException ignored) {
                    }
                },
                Button.NO_TOOLTIP
        )));
    }

    private static McTextComponent tr(String key) {
        return McTextComponent.translatable(key);
    }

    private static int currentPresetIndex(VoiceChangerAddon addon, VoiceChangerPreset[] builtInPresets, List<String> savedPresets) {
        String selectedSavedPreset = addon.getSelectedSavedPresetName();
        if (selectedSavedPreset != null) {
            int savedIndex = savedPresets.indexOf(selectedSavedPreset);
            if (savedIndex >= 0) {
                return builtInPresets.length + savedIndex;
            }
        }

        VoiceChangerPreset selectedPreset = addon.getSelectedPreset();
        for (int index = 0; index < builtInPresets.length; index++) {
            if (builtInPresets[index] == selectedPreset) {
                return index;
            }
        }

        return 0;
    }
}
