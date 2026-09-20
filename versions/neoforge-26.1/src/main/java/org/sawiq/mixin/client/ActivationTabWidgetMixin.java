package org.sawiq.mixin.client;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.sawiq.client.VoiceChangerAddon;
import org.sawiq.client.compat.ClientScreens;
import org.sawiq.client.model.VoiceChangerPreset;
import org.sawiq.client.ui.VoiceChangerStudioScreen;
import org.sawiq.protocol.PolicyReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.plo.config.entry.ConfigEntry;
import su.plo.lib.mod.client.gui.components.Button;
import su.plo.slib.api.chat.component.McTextComponent;
import su.plo.slib.api.chat.style.McTextStyle;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.client.config.VoiceClientConfig;
import su.plo.voice.client.gui.settings.VoiceSettingsScreen;
import su.plo.voice.client.gui.settings.tab.AbstractHotKeysTabWidget;
import su.plo.voice.client.gui.settings.tab.ActivationTabWidget;
import su.plo.voice.client.gui.settings.widget.DropDownWidget;
import su.plo.voice.client.gui.settings.widget.ToggleButton;

/**
 * The voice changer's own section of Plasmo Voice's activation settings.
 *
 * <p>While a server refuses, the on/off switch reads off and is greyed, and a
 * line above it says which of the possible reasons applies. A player who was
 * muted needs to be able to find that out, and a switch that still reads on
 * while nothing reaches anybody is what makes that impossible.</p>
 *
 * <p>Nothing else is greyed. Choosing a voice changes only what happens on
 * this machine, so somebody waiting to be unmuted can still set one up.</p>
 */
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

        boolean allowed = addon.isAllowedByServer();

        this.addEntry(new CategoryEntry(tr("pvvoicechanger.tab.category")));
        if (!allowed) {
            this.addEntry(pvvoicechanger$statusBanner(addon));
        }

        pvvoicechanger$addEnableToggle(addon, allowed);
        pvvoicechanger$addPresetDropDown(addon);

        this.addEntry(this.createHotKey(
                "pvvoicechanger.tab.toggle_bind_label",
                "pvvoicechanger.tab.toggle_bind.desc",
                addon.getToggleHotkeyEntry()
        ));

        // The studio and the preset folder stay usable while the server says
        // no: the studio is where the reason is spelled out in full, and
        // someone locked out is exactly the person who wants to read it.
        this.addEntry(new FullWidthEntry<>(new Button(
                0,
                0,
                220,
                20,
                tr("pvvoicechanger.tab.open_studio"),
                button -> {
                    if (this.parent instanceof VoiceSettingsScreen settingsScreen) {
                        ClientScreens.open(new VoiceChangerStudioScreen(settingsScreen.getMinecraftScreen(), addon));
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

    /**
     * The switch reports what is actually happening, which is not always what
     * the player asked for: while a server refuses, it reads off even though
     * their own preference is still remembered and comes back untouched the
     * moment the server allows it again.
     */
    private void pvvoicechanger$addEnableToggle(VoiceChangerAddon addon, boolean allowed) {
        ConfigEntry<Boolean> shown = new ConfigEntry<>(allowed && addon.isEffectEnabled());

        // Driven from the entry rather than from the button's press action:
        // the row also carries a reset button, and that writes the entry
        // without ever going through a press. While the server refuses, the
        // switch is only a readout, so nothing is written back.
        shown.addChangeListener(value -> {
            if (allowed) {
                addon.setEnabled(value);
            }
        });

        ToggleButton toggle = new ToggleButton(shown, 0, 0, 124, 20);
        toggle.setActive(allowed);
        this.addEntry(new OptionEntry<>(
                tr("pvvoicechanger.tab.enable"),
                toggle,
                shown,
                allowed ? tr("pvvoicechanger.tab.enable.desc") : pvvoicechanger$blockedTooltip(addon)
        ));
    }

    /**
     * Never greyed, even while a server refuses: picking a voice changes
     * nothing that leaves the machine, and somebody waiting to be unmuted
     * should still be able to have a voice ready for when they are.
     */
    private void pvvoicechanger$addPresetDropDown(VoiceChangerAddon addon) {
        VoiceChangerPreset[] builtInPresets = VoiceChangerPreset.selectable();
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
        this.addEntry(new OptionEntry<>(
                tr("pvvoicechanger.tab.preset"),
                presetDropDown[0],
                presetIndexEntry,
                tr("pvvoicechanger.tab.preset.desc")
        ));
    }

    /**
     * A line the player cannot miss, saying which of the possible reasons
     * applies. Rendered as a button purely to get a full-width row; it is
     * inert and never becomes pressable.
     */
    private FullWidthEntry<Button> pvvoicechanger$statusBanner(VoiceChangerAddon addon) {
        Button banner = new Button(
                0,
                0,
                220,
                20,
                pvvoicechanger$reasonLine(addon).withStyle(McTextStyle.RED),
                Button.NO_ACTION,
                Button.NO_TOOLTIP
        );
        banner.setActive(false);
        return new FullWidthEntry<>(banner);
    }

    /**
     * The operator's own wording when they wrote one, since they know why they
     * switched it off here; otherwise the translated reason.
     */
    private static McTextComponent pvvoicechanger$reasonLine(VoiceChangerAddon addon) {
        String operatorMessage = addon.getServerDenialMessage();
        if (!operatorMessage.isBlank()) {
            return McTextComponent.literal(operatorMessage);
        }

        PolicyReason reason = addon.getServerDenialReason();
        return McTextComponent.translatable("pvvoicechanger.tab.status." + reason.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static McTextComponent pvvoicechanger$blockedTooltip(VoiceChangerAddon addon) {
        return McTextComponent.translatable(
                "pvvoicechanger.tab.status.tooltip", pvvoicechanger$reasonLine(addon));
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
