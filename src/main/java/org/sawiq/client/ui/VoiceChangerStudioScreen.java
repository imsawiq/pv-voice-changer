package org.sawiq.client.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.sawiq.client.VoiceChangerAddon;
import org.sawiq.client.model.VoiceChangerPreset;
import org.sawiq.client.model.VoiceChangerProfile;
import org.sawiq.client.ui.widget.StudioProfileSlider;
import org.sawiq.client.ui.widget.StudioStrengthSlider;

public final class VoiceChangerStudioScreen extends Screen {
    private static final int SLIDER_WIDTH = 206;
    private static final int ROW_HEIGHT = 22;
    private static final String CURRENT_OPTION = "__current__";

    private final Screen parent;
    private final VoiceChangerAddon addon;
    private final List<StudioProfileSlider> sliders = new ArrayList<>();
    private String selectedSavedPreset = CURRENT_OPTION;

    private TextFieldWidget presetNameField;
    private ButtonWidget enabledButton;
    private ButtonWidget selfListenButton;
    private ButtonWidget builtInPresetButton;
    private ButtonWidget savedPresetButton;
    private ButtonWidget autotuneKeyButton;
    private ButtonWidget autotuneScaleButton;
    private StudioStrengthSlider strengthSlider;
    private Text hoveredHint;

    private static final String[] KEY_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    public VoiceChangerStudioScreen(Screen parent, VoiceChangerAddon addon) {
        super(Text.translatable("pvvoicechanger.studio.title"));
        this.parent = parent;
        this.addon = addon;
    }

    @Override
    protected void init() {
        this.clearChildren();
        this.sliders.clear();

        StudioLayout layout = new StudioLayout(this.width, this.height);
        initTopControls(layout);
        initSliders(layout);
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close()).dimensions(this.width / 2 - 60, this.height - 28, 120, 20).build());

        refreshFromAddon(false);
    }

    @Override
    public void close() {
        this.addon.setSelfListenEnabled(false);
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.hoveredHint = null;
        context.fill(0, 0, this.width, this.height, 0xA0000000);
        renderHeader(context);
        super.render(context, mouseX, mouseY, delta);
        updateHoveredHint(mouseX, mouseY);

        if (this.hoveredHint != null) {
            context.drawTooltip(this.textRenderer, this.hoveredHint, mouseX, mouseY);
        }
    }

    private void initTopControls(StudioLayout layout) {
        this.presetNameField = addDrawableChild(new TextFieldWidget(this.textRenderer, layout.left(), layout.top(), layout.fieldWidth(), 20, Text.translatable("pvvoicechanger.studio.preset_name")));
        this.presetNameField.setMaxLength(48);
        this.presetNameField.setText("MyPreset");

        addDrawableChild(ButtonWidget.builder(Text.translatable("pvvoicechanger.studio.save"), button -> savePreset()).dimensions(layout.left() + layout.fieldWidth() + 6, layout.top(), layout.buttonWidth(), 20).build());

        this.enabledButton = addDrawableChild(ButtonWidget.builder(enabledButtonText(), button -> {
            this.addon.setEnabled(!this.addon.getEnabledEntry().value());
            refreshBooleanButtons();
        }).dimensions(layout.left(), layout.top() + 26, layout.topWidth(), 20).build());
        this.selfListenButton = addDrawableChild(ButtonWidget.builder(selfListenButtonText(), button -> {
            this.addon.setSelfListenEnabled(!this.addon.getSelfListenEntry().value());
            refreshBooleanButtons();
        }).dimensions(layout.right(), layout.top() + 26, SLIDER_WIDTH, 20).build());

        this.builtInPresetButton = addDrawableChild(ButtonWidget.builder(builtInPresetButtonText(), button -> cycleBuiltInPreset())
                .dimensions(layout.left(), layout.top() + 52, layout.topWidth(), 20).build());

        this.savedPresetButton = addDrawableChild(ButtonWidget.builder(savedPresetButtonText(), button -> cycleSavedPreset())
                .dimensions(layout.right(), layout.top() + 52, SLIDER_WIDTH, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("pvvoicechanger.studio.open_folder"), button -> openFolder()).dimensions(layout.left(), layout.top() + 78, layout.topWidth(), 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("pvvoicechanger.studio.reload_list"), button -> refreshFromAddon(true)).dimensions(layout.right(), layout.top() + 78, SLIDER_WIDTH, 20).build());

        ButtonWidget deleteButton = addDrawableChild(ButtonWidget.builder(Text.translatable("pvvoicechanger.studio.delete_saved"), button -> deleteSelectedPreset()).dimensions(layout.right(), layout.top() + 104, SLIDER_WIDTH, 20).build());
        deleteButton.setTooltip(Tooltip.of(Text.translatable("pvvoicechanger.studio.delete_saved.desc")));

        ButtonWidget resetButton = addDrawableChild(ButtonWidget.builder(Text.translatable("pvvoicechanger.studio.reset"), button -> resetToPassthrough()).dimensions(layout.left(), layout.top() + 104, layout.topWidth(), 20).build());
        resetButton.setTooltip(Tooltip.of(Text.translatable("pvvoicechanger.studio.reset.desc")));
    }

    private void initSliders(StudioLayout layout) {
        int sliderTop = layout.top() + 132;
        this.strengthSlider = addDrawableChild(new StudioStrengthSlider(layout.left(), sliderTop, SLIDER_WIDTH, () -> this.addon.getStrengthEntry().value(), this.addon::setStrength));

        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT, "pvvoicechanger.slider.mix", 0.0D, 1.0D, VoiceChangerProfile::mix, VoiceChangerProfile::withMix);
        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT * 2, "pvvoicechanger.slider.gain", 0.40D, 2.50D, VoiceChangerProfile::gain, VoiceChangerProfile::withGain);
        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT * 3, "pvvoicechanger.slider.pitch", 0.60D, 1.70D, VoiceChangerProfile::pitch, VoiceChangerProfile::withPitch);
        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT * 4, "pvvoicechanger.slider.formant", 0.45D, 2.00D, VoiceChangerProfile::formant, VoiceChangerProfile::withFormant);
        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT * 5, "pvvoicechanger.slider.low_eq", -10.0D, 10.0D, VoiceChangerProfile::lowEq, VoiceChangerProfile::withLowEq);
        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT * 6, "pvvoicechanger.slider.mid_eq", -10.0D, 10.0D, VoiceChangerProfile::midEq, VoiceChangerProfile::withMidEq);
        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT * 7, "pvvoicechanger.slider.high_eq", -10.0D, 10.0D, VoiceChangerProfile::highEq, VoiceChangerProfile::withHighEq);
        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT * 8, "pvvoicechanger.slider.autotune_mix", 0.0D, 1.0D, VoiceChangerProfile::autotuneMix, VoiceChangerProfile::withAutotuneMix);
        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT * 9, "pvvoicechanger.slider.autotune_strength", 0.0D, 1.0D, VoiceChangerProfile::autotuneStrength, VoiceChangerProfile::withAutotuneStrength);
        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT * 10, "pvvoicechanger.slider.bit_crush", VoiceChangerProfile.BIT_DEPTH_CLEAN, VoiceChangerProfile.BIT_DEPTH_MIN, VoiceChangerProfile::bitDepth, (profile, value) -> profile.withBitDepth((double) Math.round(value)));

        addProfileSlider(layout.right(), sliderTop, "pvvoicechanger.slider.distortion", 0.0D, 0.75D, VoiceChangerProfile::distortion, VoiceChangerProfile::withDistortion);
        addProfileSlider(layout.right(), sliderTop + ROW_HEIGHT, "pvvoicechanger.slider.noise", 0.0D, 0.60D, VoiceChangerProfile::noise, VoiceChangerProfile::withNoise);
        addProfileSlider(layout.right(), sliderTop + ROW_HEIGHT * 2, "pvvoicechanger.slider.robot_mix", 0.0D, 0.85D, VoiceChangerProfile::robotMix, VoiceChangerProfile::withRobotMix);
        addProfileSlider(layout.right(), sliderTop + ROW_HEIGHT * 3, "pvvoicechanger.slider.robot_frequency", 20.0D, 140.0D, VoiceChangerProfile::robotFrequency, (profile, value) -> profile.withRobotFrequency((int) Math.round(value)));
        addProfileSlider(layout.right(), sliderTop + ROW_HEIGHT * 4, "pvvoicechanger.slider.echo_mix", 0.0D, 0.65D, VoiceChangerProfile::echoMix, VoiceChangerProfile::withEchoMix);
        addProfileSlider(layout.right(), sliderTop + ROW_HEIGHT * 5, "pvvoicechanger.slider.echo_delay", 40.0D, 260.0D, VoiceChangerProfile::echoDelayMs, (profile, value) -> profile.withEchoDelayMs((int) Math.round(value)));
        addProfileSlider(layout.right(), sliderTop + ROW_HEIGHT * 6, "pvvoicechanger.slider.echo_feedback", 0.0D, 0.80D, VoiceChangerProfile::echoFeedback, VoiceChangerProfile::withEchoFeedback);
        addProfileSlider(layout.right(), sliderTop + ROW_HEIGHT * 7, "pvvoicechanger.slider.tremolo_depth", 0.0D, 0.75D, VoiceChangerProfile::tremoloDepth, VoiceChangerProfile::withTremoloDepth);
        addProfileSlider(layout.right(), sliderTop + ROW_HEIGHT * 8, "pvvoicechanger.slider.tremolo_rate", 0.20D, 9.00D, VoiceChangerProfile::tremoloRate, VoiceChangerProfile::withTremoloRate);

        int autotuneRowY = sliderTop + ROW_HEIGHT * 9;
        int half = (SLIDER_WIDTH - 4) / 2;
        this.autotuneKeyButton = addDrawableChild(ButtonWidget.builder(autotuneKeyButtonText(), button -> cycleAutotuneKey())
                .dimensions(layout.right(), autotuneRowY, half, 20).build());
        this.autotuneKeyButton.setTooltip(Tooltip.of(Text.translatable("pvvoicechanger.slider.autotune_key.desc")));
        this.autotuneScaleButton = addDrawableChild(ButtonWidget.builder(autotuneScaleButtonText(), button -> cycleAutotuneScale())
                .dimensions(layout.right() + half + 4, autotuneRowY, SLIDER_WIDTH - half - 4, 20).build());
        this.autotuneScaleButton.setTooltip(Tooltip.of(Text.translatable("pvvoicechanger.slider.autotune_scale.desc")));
    }

    private void addProfileSlider(int x, int y, String label, double min, double max, Function<VoiceChangerProfile, Number> getter, BiFunction<VoiceChangerProfile, Double, VoiceChangerProfile> setter) {
        StudioProfileSlider slider = new StudioProfileSlider(
                x,
                y,
                SLIDER_WIDTH,
                label,
                label + ".desc",
                min,
                max,
                this.addon::getCurrentProfileSnapshot,
                this::applyCustomProfile,
                getter,
                setter
        );
        this.sliders.add(slider);
        addDrawableChild(slider);
    }

    private void applyCustomProfile(VoiceChangerProfile profile) {
        this.addon.applyCustomProfile(profile);
        this.selectedSavedPreset = CURRENT_OPTION;
    }

    private void renderHeader(DrawContext context) {
    }

    private void updateHoveredHint(int mouseX, int mouseY) {
        if (this.strengthSlider != null && this.strengthSlider.isMouseOver(mouseX, mouseY)) {
            this.hoveredHint = Text.translatable("pvvoicechanger.slider.strength.desc");
            return;
        }

        for (StudioProfileSlider slider : this.sliders) {
            if (slider.isMouseOver(mouseX, mouseY)) {
                this.hoveredHint = slider.descriptionText();
                return;
            }
        }
    }

    private void refreshFromAddon(boolean reloadList) {
        if (reloadList) {
            try {
                this.addon.listSavedPresetNames();
            } catch (IOException ignored) {
            }
        }

        VoiceChangerProfile profile = this.addon.getCurrentProfileSnapshot();
        this.selectedSavedPreset = this.addon.getSelectedSavedPresetName() == null ? CURRENT_OPTION : this.addon.getSelectedSavedPresetName();
        refreshBooleanButtons();
        refreshPresetButtons();
        this.strengthSlider.refresh();

        for (StudioProfileSlider slider : this.sliders) {
            slider.refresh(profile);
        }
    }

    private void savePreset() {
        try {
            String savedName = this.addon.saveCurrentPreset(this.presetNameField.getText());
            this.presetNameField.setText(savedName);
            this.selectedSavedPreset = savedName;
            refreshFromAddon(true);
            notifyUser(Text.translatable("pvvoicechanger.message.saved", savedName));
        } catch (IOException | IllegalArgumentException exception) {
            notifyUser(Text.translatable("pvvoicechanger.message.save_failed", exception.getMessage()));
        }
    }

    private void deleteSelectedPreset() {
        String selected = currentSavedSelection();
        if (CURRENT_OPTION.equals(selected)) {
            notifyUser(Text.translatable("pvvoicechanger.message.select_saved_first"));
            return;
        }

        try {
            this.addon.deleteSavedPreset(selected);
            this.selectedSavedPreset = CURRENT_OPTION;
            refreshFromAddon(true);
            notifyUser(Text.translatable("pvvoicechanger.message.deleted", selected));
        } catch (IOException exception) {
            notifyUser(Text.translatable("pvvoicechanger.message.delete_failed", exception.getMessage()));
        }
    }

    private void openFolder() {
        try {
            this.addon.ensurePresetDirectory();
            Util.getOperatingSystem().open(this.addon.getPresetDirectory());
        } catch (IOException exception) {
            notifyUser(Text.translatable("pvvoicechanger.message.open_folder_failed", exception.getMessage()));
        }
    }

    private List<String> savedPresetOptions() {
        List<String> options = new ArrayList<>();
        options.add(CURRENT_OPTION);
        options.addAll(this.addon.getSavedPresetNamesCached());
        return options;
    }

    private String currentSavedSelection() {
        return this.selectedSavedPreset == null ? CURRENT_OPTION : this.selectedSavedPreset;
    }

    private Text enabledButtonText() {
        return this.addon.getEnabledEntry().value()
                ? Text.translatable("pvvoicechanger.studio.enabled_on")
                : Text.translatable("pvvoicechanger.studio.enabled_off");
    }

    private Text selfListenButtonText() {
        return this.addon.getSelfListenEntry().value()
                ? Text.translatable("pvvoicechanger.studio.self_listen_on")
                : Text.translatable("pvvoicechanger.studio.self_listen_off");
    }

    private void refreshBooleanButtons() {
        if (this.enabledButton != null) {
            this.enabledButton.setMessage(enabledButtonText());
        }
        if (this.selfListenButton != null) {
            this.selfListenButton.setMessage(selfListenButtonText());
        }
    }

    private Text builtInPresetButtonText() {
        return Text.translatable("pvvoicechanger.studio.built_in_value", Text.translatable(this.addon.getSelectedPreset().getTranslationKey()));
    }

    private Text savedPresetButtonText() {
        String value = currentSavedSelection();
        Text current = CURRENT_OPTION.equals(value) ? Text.translatable("pvvoicechanger.saved.current") : Text.literal(value);
        return Text.translatable("pvvoicechanger.studio.saved_value", current);
    }

    private void refreshPresetButtons() {
        if (this.builtInPresetButton != null) {
            this.builtInPresetButton.setMessage(builtInPresetButtonText());
        }
        if (this.savedPresetButton != null) {
            this.savedPresetButton.setMessage(savedPresetButtonText());
        }
        refreshAutotuneButtons();
    }

    private Text autotuneKeyButtonText() {
        int key = ((this.addon.getCurrentProfileSnapshot().autotuneKey() % 12) + 12) % 12;
        return Text.translatable("pvvoicechanger.studio.autotune_key_value", KEY_NAMES[key]);
    }

    private Text autotuneScaleButtonText() {
        int scale = this.addon.getCurrentProfileSnapshot().autotuneScale();
        String key = switch (scale) {
            case VoiceChangerProfile.SCALE_MAJOR -> "pvvoicechanger.studio.autotune_scale.major";
            case VoiceChangerProfile.SCALE_MINOR -> "pvvoicechanger.studio.autotune_scale.minor";
            default -> "pvvoicechanger.studio.autotune_scale.chromatic";
        };
        return Text.translatable("pvvoicechanger.studio.autotune_scale_value", Text.translatable(key));
    }

    private void refreshAutotuneButtons() {
        if (this.autotuneKeyButton != null) {
            this.autotuneKeyButton.setMessage(autotuneKeyButtonText());
        }
        if (this.autotuneScaleButton != null) {
            this.autotuneScaleButton.setMessage(autotuneScaleButtonText());
        }
    }

    private void cycleAutotuneKey() {
        VoiceChangerProfile profile = this.addon.getCurrentProfileSnapshot();
        applyCustomProfile(profile.withAutotuneKey((profile.autotuneKey() + 1) % 12));
        refreshAutotuneButtons();
    }

    private void cycleAutotuneScale() {
        VoiceChangerProfile profile = this.addon.getCurrentProfileSnapshot();
        int next = (profile.autotuneScale() + 1) % 3;
        applyCustomProfile(profile.withAutotuneScale(next));
        refreshAutotuneButtons();
    }

    private void cycleBuiltInPreset() {
        VoiceChangerPreset[] presets = VoiceChangerPreset.values();
        VoiceChangerPreset current = this.addon.getSelectedPreset();
        int currentIndex = 0;

        for (int i = 0; i < presets.length; i++) {
            if (presets[i] == current) {
                currentIndex = i;
                break;
            }
        }

        for (int step = 1; step <= presets.length; step++) {
            VoiceChangerPreset candidate = presets[(currentIndex + step) % presets.length];
            if (candidate != VoiceChangerPreset.CUSTOM) {
                this.addon.applyBuiltInPreset(candidate);
                this.selectedSavedPreset = CURRENT_OPTION;
                refreshFromAddon(false);
                return;
            }
        }
    }

    private void cycleSavedPreset() {
        List<String> options = savedPresetOptions();
        if (options.isEmpty()) {
            return;
        }

        String current = currentSavedSelection();
        int currentIndex = Math.max(0, options.indexOf(current));
        String next = options.get((currentIndex + 1) % options.size());
        this.selectedSavedPreset = next;

        if (!CURRENT_OPTION.equals(next)) {
            try {
                this.addon.loadSavedPreset(next);
            } catch (IOException exception) {
                notifyUser(Text.translatable("pvvoicechanger.message.load_failed", exception.getMessage()));
            }
        }

        refreshFromAddon(false);
    }

    private void resetToPassthrough() {
        // Neutral starting point: every effect at zero but a working wet/dry mix,
        // so the voice sounds essentially untouched yet any slider takes effect
        // immediately. For a fully raw mic signal, disable the effect instead.
        this.addon.applyCustomProfile(VoiceChangerProfile.passthrough().withMix(0.90D));
        this.selectedSavedPreset = CURRENT_OPTION;
        refreshFromAddon(false);
    }

    private void notifyUser(Text message) {
        if (this.client != null && this.client.player != null) {
            this.client.player.sendMessage(message, false);
        }
    }

    private record StudioLayout(int width, int height) {
        private int top() {
            return 22;
        }

        private int left() {
            return Math.max(16, this.width / 2 - 216);
        }

        private int right() {
            return this.width / 2 + 10;
        }

        private int topWidth() {
            return 202;
        }

        private int fieldWidth() {
            return 132;
        }

        private int buttonWidth() {
            return 64;
        }
    }
}
