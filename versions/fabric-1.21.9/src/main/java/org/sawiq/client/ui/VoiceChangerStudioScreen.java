package org.sawiq.client.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.sawiq.client.VoiceChangerAddon;
import org.sawiq.client.api.VoicePreset;
import org.sawiq.client.audio.SelfListenMonitor;
import org.sawiq.client.audio.VoiceDiagnostics;
import org.sawiq.client.compat.ClientScreens;
import org.sawiq.client.model.AutotuneScale;
import org.sawiq.client.model.VoiceChangerPreset;
import org.sawiq.client.model.VoiceParameter;
import org.sawiq.client.model.VoiceProfile;
import org.sawiq.client.ui.compat.CompatScreen;
import org.sawiq.client.ui.compat.StudioGraphics;
import org.sawiq.client.ui.widget.ParameterSlider;
import org.sawiq.client.ui.widget.StrengthSlider;
import org.sawiq.protocol.PolicyReason;
import org.sawiq.protocol.VoiceSourceRule;

/**
 * The studio.
 *
 * <p>Two modes, because the audience is split. <b>Simple</b> shows the voices
 * as a grid of buttons, one strength control and a live input meter, and is
 * what opens by default. <b>Advanced</b> exposes every parameter, laid out
 * automatically from {@link VoiceParameter} rather than from a hand-maintained
 * list of ranges.</p>
 */
public final class VoiceChangerStudioScreen extends CompatScreen {
    private static final int ROW_HEIGHT = 22;
    private static final int WIDGET_HEIGHT = 20;
    private static final int CONTENT_TOP = 34;
    private static final int FOOTER_HEIGHT = 34;
    private static final int COLUMN_WIDTH = 206;
    private static final int COLUMN_GAP = 8;
    private static final int PRESET_COLUMNS = 4;
    /**
     * Vertical space between the top row and the voice grid, reserved for the
     * level meter, its caption and the monitor-source note. The note is only
     * drawn sometimes, but the space is reserved unconditionally so the grid
     * does not shift under the pointer when the monitor changes source.
     */
    private static final int METER_BLOCK_HEIGHT = 58;
    private static final int METER_BAR_OFFSET = 6;
    private static final int METER_BAR_HEIGHT = 6;
    private static final int METER_CAPTION_OFFSET = 16;
    private static final int METER_NOTE_OFFSET = 28;
    private static final int METER_DIAGNOSTIC_OFFSET = 39;

    private static final int KEY_UP = 265;
    private static final int KEY_DOWN = 264;
    private static final int KEY_PAGE_UP = 266;
    private static final int KEY_PAGE_DOWN = 267;

    private static final String CURRENT_PRESET_OPTION = "__current__";
    private static final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    /** Parameters shown in the advanced view's left column, in order. */
    private static final VoiceParameter[] LEFT_COLUMN = {
            VoiceParameter.MIX,
            VoiceParameter.GAIN,
            VoiceParameter.PITCH,
            VoiceParameter.FORMANT,
            VoiceParameter.VOICE_MATCH,
            VoiceParameter.GROWL,
            VoiceParameter.LOW_EQ,
            VoiceParameter.MID_EQ,
            VoiceParameter.HIGH_EQ,
            VoiceParameter.GATE,
            VoiceParameter.DE_ESS
    };

    /** Parameters shown in the advanced view's right column, in order. */
    private static final VoiceParameter[] RIGHT_COLUMN = {
            VoiceParameter.RADIO,
            VoiceParameter.DISTORTION,
            VoiceParameter.BIT_DEPTH,
            VoiceParameter.NOISE,
            VoiceParameter.ROBOT_MIX,
            VoiceParameter.ROBOT_FREQUENCY,
            VoiceParameter.TREMOLO_DEPTH,
            VoiceParameter.TREMOLO_RATE,
            VoiceParameter.REVERB_MIX,
            VoiceParameter.REVERB_SIZE,
            VoiceParameter.REVERB_DECAY,
            VoiceParameter.AUTOTUNE_MIX,
            VoiceParameter.AUTOTUNE_SPEED
    };

    private enum Mode { SIMPLE, ADVANCED }

    private final Screen parent;
    private final VoiceChangerAddon addon;
    private final ScrollPanel scrollPanel = new ScrollPanel();
    private final List<ParameterSlider> parameterSliders = new ArrayList<>();
    private final List<VoiceButton> voiceButtons = new ArrayList<>();
    /**
     * The contributed voices the current layout was built from. The registry
     * hands out an immutable snapshot per change, so an identity comparison is
     * enough to notice that a mod added or removed one while this was open.
     */
    private List<VoicePreset> layoutContributedVoices = List.of();

    private Mode mode = Mode.SIMPLE;
    private String selectedSavedPreset = CURRENT_PRESET_OPTION;

    private EditBox presetNameField;
    private Button enabledButton;
    private Button selfListenButton;
    private Button savedPresetButton;
    private Button autotuneKeyButton;
    private Button autotuneScaleButton;
    private StrengthSlider strengthSlider;
    private double meterLevel;

    public VoiceChangerStudioScreen(Screen parent, VoiceChangerAddon addon) {
        super(Component.translatable("pvvoicechanger.studio.title"));
        this.parent = parent;
        this.addon = addon;
    }

    // --- Screen lifecycle --------------------------------------------------

    @Override
    protected void init() {
        clearWidgets();
        this.scrollPanel.clear();
        this.parameterSliders.clear();
        this.voiceButtons.clear();

        this.layoutContributedVoices = this.addon.api().contributedPresets().all();

        int top = CONTENT_TOP;
        if (this.mode == Mode.SIMPLE) {
            buildSimpleMode(top);
        } else {
            buildAdvancedMode(top);
        }

        this.scrollPanel.layout(CONTENT_TOP, contentBottom());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(this.width / 2 - 60, this.height - 28, 120, WIDGET_HEIGHT)
                .build());

        refreshFromAddon(false);
    }

    @Override
    public void onClose() {
        this.addon.setSelfListenEnabled(false);
        ClientScreens.open(this.parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.scrollPanel.scrollBy(-(int) Math.round(verticalAmount * ROW_HEIGHT * 2))) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    protected boolean onKeyPressed(int keyCode) {
        if (!this.scrollPanel.isScrollable()) {
            return false;
        }

        int step = switch (keyCode) {
            case KEY_UP -> -ROW_HEIGHT;
            case KEY_DOWN -> ROW_HEIGHT;
            case KEY_PAGE_UP -> -this.scrollPanel.viewportHeight();
            case KEY_PAGE_DOWN -> this.scrollPanel.viewportHeight();
            default -> 0;
        };

        return step != 0 && this.scrollPanel.scrollBy(step);
    }

    // --- Rendering ---------------------------------------------------------

    @Override
    protected void paintBehindWidgets(StudioGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xA0000000);
        graphics.centeredText(this.title, this.width / 2, 12, 0xFFFFFFFF);

        // Under the title in both modes: somebody whose voice changer has
        // stopped working opens this screen first, and the answer should be
        // the first thing here rather than something to hunt for.
        if (!this.addon.isAllowedByServer()) {
            graphics.centeredText(serverBlockedText(), this.width / 2, 23, 0xFFFF7777);
        } else if (this.addon.getVoiceRule() != VoiceSourceRule.ALL) {
            graphics.centeredText(voicesRestrictedText(), this.width / 2, 23,
                    this.addon.isSelectedVoiceAllowed() ? 0xFFFFAA55 : 0xFFFF7777);
        }
    }

    /**
     * What the server allows, and — when the player's own voice is not among it
     * — that they have to pick something else before anything is heard.
     */
    private Component voicesRestrictedText() {
        Component allowed = Component.translatable(this.addon.getVoiceRule().translationKey());
        return this.addon.isSelectedVoiceAllowed()
                ? Component.translatable("pvvoicechanger.studio.voices_restricted", allowed)
                : Component.translatable("pvvoicechanger.studio.voices_restricted_pick", allowed);
    }

    /** The server's own wording when it sent one, otherwise ours, translated. */
    private Component serverBlockedText() {
        String message = this.addon.getServerDenialMessage();
        Component reason = message.isBlank()
                ? Component.translatable(this.addon.getServerDenialReason().translationKey())
                : Component.literal(message);
        return Component.translatable("pvvoicechanger.studio.server_blocked", reason);
    }

    @Override
    protected void paintAboveWidgets(StudioGraphics graphics, int mouseX, int mouseY) {
        this.scrollPanel.renderScrollbar(graphics, this.width - 7);

        if (this.mode == Mode.SIMPLE) {
            renderLevelMeter(graphics);
        }
    }

    /**
     * Peak input level, smoothed on the way down so the bar falls at a
     * readable speed instead of flickering at the audio block rate.
     */
    private void renderLevelMeter(StudioGraphics graphics) {
        double peak = Math.min(1.0D, this.addon.getInputLevel());
        this.meterLevel = peak > this.meterLevel ? peak : this.meterLevel * 0.90D;

        int left = centeredLeft(COLUMN_WIDTH * 2 + COLUMN_GAP);
        int right = left + COLUMN_WIDTH * 2 + COLUMN_GAP;
        int rowBottom = CONTENT_TOP + WIDGET_HEIGHT - this.scrollPanel.scrollOffset();

        int barTop = rowBottom + METER_BAR_OFFSET;
        graphics.fill(left, barTop, right, barTop + METER_BAR_HEIGHT, 0xFF202020);
        int filled = left + (int) ((right - left) * this.meterLevel);
        graphics.fill(left, barTop, filled, barTop + METER_BAR_HEIGHT,
                this.meterLevel > 0.92D ? 0xFFFF5555 : 0xFF55DD55);
        graphics.text(Component.translatable("pvvoicechanger.studio.level"),
                left, rowBottom + METER_CAPTION_OFFSET, 0xFFAAAAAA);

        drawDiagnostics(graphics, left, rowBottom + METER_DIAGNOSTIC_OFFSET);

        // The monitor never silently switches to another microphone, so the
        // only thing worth reporting is that it could not start at all.
        if (this.addon.getSelfListenMode() == SelfListenMonitor.Mode.UNAVAILABLE) {
            graphics.text(Component.translatable("pvvoicechanger.studio.self_listen_unavailable"),
                    left, rowBottom + METER_NOTE_OFFSET, 0xFFFFAA55);
        }
    }

    /**
     * One line saying what the chain is doing to the voice right now: the pitch
     * it measured, where the preset is taking it, and whether microphone blocks
     * are arriving at all. Without it, a preset that is running but inaudible
     * looks exactly like one that never ran.
     */
    private void drawDiagnostics(StudioGraphics graphics, int left, int y) {
        VoiceDiagnostics diagnostics = this.addon.getDiagnostics();

        String heard = diagnostics.speakerPitchHz() > 0.0D
                ? Math.round(diagnostics.speakerPitchHz()) + " Hz"
                : "?";
        String target = diagnostics.speakerPitchHz() > 0.0D
                ? Math.round(diagnostics.targetPitchHz()) + " Hz"
                : "?";

        String line = String.format("%s -> %s   x%.2f   %d ch  %d fr  %d/s",
                heard, target, diagnostics.appliedPitchRatio(),
                diagnostics.channels(), diagnostics.blockFrames(), diagnostics.blocksPerSecond());

        graphics.text(Component.literal(line), left, y,
                diagnostics.isRunning() ? 0xFF88CCFF : 0xFFFF7777);
    }

    /** Rebuilds when a mod contributes or withdraws a voice while this is open. */
    @Override
    public void tick() {
        super.tick();
        if (this.addon.api().contributedPresets().all() != this.layoutContributedVoices) {
            init();
        }
    }

    // --- Simple mode -------------------------------------------------------

    private void buildSimpleMode(int top) {
        int fullWidth = COLUMN_WIDTH * 2 + COLUMN_GAP;
        int left = centeredLeft(fullWidth);
        int half = (fullWidth - COLUMN_GAP) / 2;

        this.enabledButton = addScrollable(Button.builder(enabledButtonText(), button -> toggleEnabled())
                .bounds(left, top, half, WIDGET_HEIGHT).build());
        this.selfListenButton = addScrollable(withTooltip(Button.builder(selfListenButtonText(), button -> toggleSelfListen())
                .bounds(left + half + COLUMN_GAP, top, half, WIDGET_HEIGHT).build(), "pvvoicechanger.studio.self_listen.desc"));

        int presetsTop = top + WIDGET_HEIGHT + METER_BLOCK_HEIGHT;
        int presetRow = buildPresetGrid(left, presetsTop, fullWidth);

        this.strengthSlider = addScrollable(new StrengthSlider(
                left, presetRow, fullWidth, WIDGET_HEIGHT,
                this.addon::getStrength, this.addon::setStrength));

        int libraryRow = presetRow + ROW_HEIGHT + 4;
        this.savedPresetButton = addScrollable(libraryWidget(withTooltip(
                Button.builder(savedPresetButtonText(), button -> cycleSavedPreset())
                        .bounds(left, libraryRow, half, WIDGET_HEIGHT).build(),
                "pvvoicechanger.studio.saved_value.desc")));
        addScrollable(withTooltip(Button.builder(Component.translatable("pvvoicechanger.studio.open_folder"), button -> openPresetFolder())
                .bounds(left + half + COLUMN_GAP, libraryRow, half, WIDGET_HEIGHT).build(), "pvvoicechanger.studio.open_folder.desc"));

        addScrollable(withTooltip(Button.builder(Component.translatable("pvvoicechanger.studio.advanced"), button -> switchMode(Mode.ADVANCED))
                .bounds(left, libraryRow + ROW_HEIGHT + 4, fullWidth, WIDGET_HEIGHT).build(), "pvvoicechanger.studio.advanced.desc"));
    }

    /** Lays the voices out in a grid. @return the y of the row below it */
    private int buildPresetGrid(int left, int top, int fullWidth) {
        List<VoiceChoice> choices = voiceChoices();
        int cellWidth = (fullWidth - COLUMN_GAP * (PRESET_COLUMNS - 1)) / PRESET_COLUMNS;

        int row = 0;
        for (int index = 0; index < choices.size(); index++) {
            VoiceChoice choice = choices.get(index);
            row = index / PRESET_COLUMNS;
            int column = index % PRESET_COLUMNS;
            int x = left + column * (cellWidth + COLUMN_GAP);
            int y = top + row * ROW_HEIGHT;

            Button button = addScrollable(Button.builder(voiceButtonText(choice), ignored -> selectVoice(choice))
                    .bounds(x, y, cellWidth, WIDGET_HEIGHT).build());
            if (choice.description() != null) {
                button.setTooltip(Tooltip.create(choice.description()));
            }
            this.voiceButtons.add(new VoiceButton(choice, button));
        }

        return top + (row + 1) * ROW_HEIGHT + 6;
    }

    /**
     * The built-in voices first, then whatever other mods contributed, in the
     * order they registered. Built-ins keep their places so a mod appearing or
     * disappearing never moves the buttons the player already knows.
     */
    private List<VoiceChoice> voiceChoices() {
        VoiceSourceRule rule = this.addon.getVoiceRule();
        List<VoiceChoice> choices = new ArrayList<>();

        for (VoiceChangerPreset preset : VoiceChangerPreset.selectable()) {
            if (!rule.allows(preset.id())) {
                continue;
            }
            choices.add(new VoiceChoice(
                    preset.id(),
                    Component.translatable(preset.getTranslationKey()),
                    null,
                    () -> this.addon.applyBuiltInPreset(preset)));
        }
        for (VoicePreset preset : this.addon.api().contributedPresets().all()) {
            if (!rule.allows(preset.id())) {
                continue;
            }
            choices.add(new VoiceChoice(
                    preset.id(),
                    preset.displayName(),
                    preset.description(),
                    () -> this.addon.applyContributedPreset(preset)));
        }
        return choices;
    }

    // --- Advanced mode -----------------------------------------------------

    private void buildAdvancedMode(int top) {
        int left = centeredLeft(COLUMN_WIDTH * 2 + COLUMN_GAP);
        int right = left + COLUMN_WIDTH + COLUMN_GAP;
        int half = (COLUMN_WIDTH - 4) / 2;

        this.presetNameField = addScrollable(new EditBox(this.font, left, top, COLUMN_WIDTH - 70, WIDGET_HEIGHT,
                Component.translatable("pvvoicechanger.studio.preset_name")));
        this.presetNameField.setMaxLength(48);
        this.presetNameField.setValue("MyPreset");
        addScrollable(libraryWidget(
                Button.builder(Component.translatable("pvvoicechanger.studio.save"), button -> saveCurrentPreset())
                        .bounds(left + COLUMN_WIDTH - 64, top, 64, WIDGET_HEIGHT).build()));

        this.savedPresetButton = addScrollable(libraryWidget(withTooltip(
                Button.builder(savedPresetButtonText(), button -> cycleSavedPreset())
                        .bounds(right, top, COLUMN_WIDTH, WIDGET_HEIGHT).build(),
                "pvvoicechanger.studio.saved_value.desc")));

        int secondRow = top + ROW_HEIGHT + 4;
        this.enabledButton = addScrollable(Button.builder(enabledButtonText(), button -> toggleEnabled())
                .bounds(left, secondRow, COLUMN_WIDTH, WIDGET_HEIGHT).build());
        this.selfListenButton = addScrollable(withTooltip(Button.builder(selfListenButtonText(), button -> toggleSelfListen())
                .bounds(right, secondRow, COLUMN_WIDTH, WIDGET_HEIGHT).build(), "pvvoicechanger.studio.self_listen.desc"));

        int thirdRow = secondRow + ROW_HEIGHT + 4;
        addScrollable(tuningWidget(withTooltip(
                Button.builder(Component.translatable("pvvoicechanger.studio.reset"), button -> resetToNeutral())
                        .bounds(left, thirdRow, COLUMN_WIDTH, WIDGET_HEIGHT).build(),
                "pvvoicechanger.studio.reset.desc")));
        addScrollable(libraryWidget(withTooltip(
                Button.builder(Component.translatable("pvvoicechanger.studio.delete_saved"), button -> deleteSelectedPreset())
                        .bounds(right, thirdRow, COLUMN_WIDTH, WIDGET_HEIGHT).build(),
                "pvvoicechanger.studio.delete_saved.desc")));

        int fourthRow = thirdRow + ROW_HEIGHT + 4;
        addScrollable(withTooltip(Button.builder(Component.translatable("pvvoicechanger.studio.simple"), button -> switchMode(Mode.SIMPLE))
                .bounds(left, fourthRow, COLUMN_WIDTH, WIDGET_HEIGHT).build(), "pvvoicechanger.studio.simple.desc"));
        addScrollable(withTooltip(Button.builder(Component.translatable("pvvoicechanger.studio.open_folder"), button -> openPresetFolder())
                .bounds(right, fourthRow, COLUMN_WIDTH, WIDGET_HEIGHT).build(), "pvvoicechanger.studio.open_folder.desc"));

        int slidersTop = fourthRow + ROW_HEIGHT + 8;
        this.strengthSlider = addScrollable(new StrengthSlider(
                left, slidersTop, COLUMN_WIDTH, WIDGET_HEIGHT,
                this.addon::getStrength, this.addon::setStrength));

        for (int i = 0; i < LEFT_COLUMN.length; i++) {
            addParameterSlider(left, slidersTop + ROW_HEIGHT * (i + 1), LEFT_COLUMN[i]);
        }
        for (int i = 0; i < RIGHT_COLUMN.length; i++) {
            addParameterSlider(right, slidersTop + ROW_HEIGHT * i, RIGHT_COLUMN[i]);
        }

        int autotuneRow = slidersTop + ROW_HEIGHT * RIGHT_COLUMN.length;
        this.autotuneKeyButton = addScrollable(tuningWidget(withTooltip(
                Button.builder(autotuneKeyButtonText(), button -> cycleAutotuneKey())
                        .bounds(right, autotuneRow, half, WIDGET_HEIGHT).build(),
                VoiceParameter.AUTOTUNE_KEY.descriptionKey())));
        this.autotuneScaleButton = addScrollable(tuningWidget(withTooltip(
                Button.builder(autotuneScaleButtonText(), button -> cycleAutotuneScale())
                        .bounds(right + half + 4, autotuneRow, COLUMN_WIDTH - half - 4, WIDGET_HEIGHT).build(),
                VoiceParameter.AUTOTUNE_SCALE.descriptionKey())));
    }

    /**
     * Greys out everything that would tune the voice by hand.
     *
     * <p>The controller refuses those edits anyway; this is so the player can
     * see that rather than watching a slider move and nothing happen.</p>
     */
    private <T extends AbstractWidget> T tuningWidget(T widget) {
        if (!this.addon.getVoiceRule().allowsHandTuning()) {
            widget.active = false;
            widget.setTooltip(Tooltip.create(
                    Component.translatable("pvvoicechanger.studio.tuning_locked")));
        }
        return widget;
    }

    /** Greys out the personal preset library, which a server may also withhold. */
    private <T extends AbstractWidget> T libraryWidget(T widget) {
        if (!this.addon.getVoiceRule().allowsPersonalPresets()) {
            widget.active = false;
            widget.setTooltip(Tooltip.create(
                    Component.translatable("pvvoicechanger.studio.library_locked")));
        }
        return widget;
    }

    private void addParameterSlider(int x, int y, VoiceParameter parameter) {
        ParameterSlider slider = new ParameterSlider(
                x, y, COLUMN_WIDTH, WIDGET_HEIGHT, parameter,
                this.addon::getPlayerProfile, this::applyCustomProfile);
        this.parameterSliders.add(slider);
        addScrollable(tuningWidget(slider));
    }

    // --- Actions -----------------------------------------------------------

    private void switchMode(Mode target) {
        this.mode = target;
        init();
    }

    private void toggleEnabled() {
        this.addon.setEnabled(!this.addon.getEnabledEntry().value());
        refreshButtonLabels();
    }

    private void toggleSelfListen() {
        this.addon.setSelfListenEnabled(!this.addon.getSelfListenEntry().value());
        refreshButtonLabels();
    }

    private void selectVoice(VoiceChoice choice) {
        choice.apply().run();
        this.selectedSavedPreset = CURRENT_PRESET_OPTION;
        refreshFromAddon(false);
    }

    private void applyCustomProfile(VoiceProfile profile) {
        this.addon.applyCustomProfile(profile);
        this.selectedSavedPreset = CURRENT_PRESET_OPTION;
        refreshButtonLabels();
    }

    private void resetToNeutral() {
        this.addon.applyCustomProfile(VoiceProfile.neutral());
        this.selectedSavedPreset = CURRENT_PRESET_OPTION;
        refreshFromAddon(false);
    }

    private void saveCurrentPreset() {
        if (this.presetNameField == null) {
            return;
        }

        try {
            String savedName = this.addon.saveCurrentPreset(this.presetNameField.getValue());
            this.presetNameField.setValue(savedName);
            this.selectedSavedPreset = savedName;
            refreshFromAddon(true);
            notifyUser(Component.translatable("pvvoicechanger.message.saved", savedName));
        } catch (IOException | IllegalArgumentException exception) {
            notifyUser(Component.translatable("pvvoicechanger.message.save_failed", describe(exception)));
        }
    }

    private void deleteSelectedPreset() {
        if (CURRENT_PRESET_OPTION.equals(this.selectedSavedPreset)) {
            notifyUser(Component.translatable("pvvoicechanger.message.select_saved_first"));
            return;
        }

        String target = this.selectedSavedPreset;
        try {
            this.addon.deleteSavedPreset(target);
            this.selectedSavedPreset = CURRENT_PRESET_OPTION;
            refreshFromAddon(true);
            notifyUser(Component.translatable("pvvoicechanger.message.deleted", target));
        } catch (IOException exception) {
            notifyUser(Component.translatable("pvvoicechanger.message.delete_failed", describe(exception)));
        }
    }

    private void cycleSavedPreset() {
        List<String> options = new ArrayList<>();
        options.add(CURRENT_PRESET_OPTION);
        options.addAll(this.addon.getSavedPresetNamesCached());

        int currentIndex = Math.max(0, options.indexOf(this.selectedSavedPreset));
        String next = options.get((currentIndex + 1) % options.size());
        this.selectedSavedPreset = next;

        if (!CURRENT_PRESET_OPTION.equals(next)) {
            try {
                this.addon.loadSavedPreset(next);
            } catch (IOException exception) {
                notifyUser(Component.translatable("pvvoicechanger.message.load_failed", describe(exception)));
            }
        }

        refreshFromAddon(false);
    }

    private void openPresetFolder() {
        try {
            this.addon.ensurePresetDirectory();
            java.awt.Desktop.getDesktop().open(this.addon.getPresetDirectory().toFile());
        } catch (IOException | UnsupportedOperationException exception) {
            notifyUser(Component.translatable("pvvoicechanger.message.open_folder_failed", describe(exception)));
        }
    }

    private void cycleAutotuneKey() {
        VoiceProfile profile = this.addon.getPlayerProfile();
        int next = (profile.getInt(VoiceParameter.AUTOTUNE_KEY) + 1) % NOTE_NAMES.length;
        applyCustomProfile(profile.with(VoiceParameter.AUTOTUNE_KEY, next));
    }

    private void cycleAutotuneScale() {
        VoiceProfile profile = this.addon.getPlayerProfile();
        int next = (profile.getInt(VoiceParameter.AUTOTUNE_SCALE) + 1) % AutotuneScale.count();
        applyCustomProfile(profile.with(VoiceParameter.AUTOTUNE_SCALE, next));
    }

    // --- Refreshing --------------------------------------------------------

    private void refreshFromAddon(boolean reloadPresetList) {
        if (reloadPresetList) {
            this.addon.reloadSavedPresetNames();
        }

        String savedName = this.addon.getSelectedSavedPresetName();
        this.selectedSavedPreset = savedName == null ? CURRENT_PRESET_OPTION : savedName;

        VoiceProfile profile = this.addon.getPlayerProfile();
        for (ParameterSlider slider : this.parameterSliders) {
            slider.refresh(profile);
        }
        if (this.strengthSlider != null) {
            this.strengthSlider.refresh();
        }

        refreshButtonLabels();
    }

    private void refreshButtonLabels() {
        setMessageIfPresent(this.enabledButton, enabledButtonText());
        applyServerBlock(this.enabledButton);
        setMessageIfPresent(this.selfListenButton, selfListenButtonText());
        setMessageIfPresent(this.savedPresetButton, savedPresetButtonText());
        setMessageIfPresent(this.autotuneKeyButton, autotuneKeyButtonText());
        setMessageIfPresent(this.autotuneScaleButton, autotuneScaleButtonText());

        for (VoiceButton entry : this.voiceButtons) {
            entry.button().setMessage(voiceButtonText(entry.choice()));
        }
    }

    // --- Labels ------------------------------------------------------------

    /**
     * Reads off whenever nothing is actually being sent, which includes a
     * server refusing. The player's own preference is untouched underneath and
     * comes back by itself; a button still reading "on" while the server has
     * them muted is the thing that makes a mute impossible to diagnose.
     */
    private Component enabledButtonText() {
        return Component.translatable(this.addon.getEnabledEntry().value() && this.addon.isAllowedByServer()
                ? "pvvoicechanger.studio.enabled_on"
                : "pvvoicechanger.studio.enabled_off");
    }

    private Component selfListenButtonText() {
        return Component.translatable(this.addon.getSelfListenEntry().value()
                ? "pvvoicechanger.studio.self_listen_on"
                : "pvvoicechanger.studio.self_listen_off");
    }

    private Component savedPresetButtonText() {
        Component value = CURRENT_PRESET_OPTION.equals(this.selectedSavedPreset)
                ? Component.translatable("pvvoicechanger.saved.current")
                : Component.literal(this.selectedSavedPreset);
        return Component.translatable("pvvoicechanger.studio.saved_value", value);
    }

    /** The active voice is marked in the label, since widget skins are version-specific. */
    private Component voiceButtonText(VoiceChoice choice) {
        boolean selected = choice.id().equals(this.addon.getSelectedVoiceId())
                && this.addon.getSelectedSavedPresetName() == null;
        return selected
                ? Component.translatable("pvvoicechanger.studio.preset_selected", choice.name())
                : choice.name();
    }

    private Component autotuneKeyButtonText() {
        int key = this.addon.getPlayerProfile().getInt(VoiceParameter.AUTOTUNE_KEY);
        return Component.translatable("pvvoicechanger.studio.autotune_key_value",
                NOTE_NAMES[Math.floorMod(key, NOTE_NAMES.length)]);
    }

    private Component autotuneScaleButtonText() {
        AutotuneScale scale = AutotuneScale.byIndex(this.addon.getPlayerProfile().getInt(VoiceParameter.AUTOTUNE_SCALE));
        return Component.translatable("pvvoicechanger.studio.autotune_scale_value",
                Component.translatable(scale.translationKey()));
    }

    // --- Helpers -----------------------------------------------------------

    private <T extends AbstractWidget> T addScrollable(T widget) {
        this.scrollPanel.add(widget);
        return addRenderableWidget(widget);
    }

    /**
     * Greys the on/off switch while the server refuses, and says why on hover.
     *
     * <p>Only this one control: everything else still only changes what
     * happens on this machine, so somebody waiting to be unmuted can carry on
     * setting up a voice for when they are.</p>
     */
    private void applyServerBlock(Button button) {
        if (button == null) {
            return;
        }

        boolean allowed = this.addon.isAllowedByServer();
        button.active = allowed;
        button.setTooltip(Tooltip.create(allowed
                ? Component.translatable("pvvoicechanger.tab.enable.desc")
                : serverBlockedText()));
    }

    private static Button withTooltip(Button button, String descriptionKey) {
        button.setTooltip(Tooltip.create(Component.translatable(descriptionKey)));
        return button;
    }

    private static void setMessageIfPresent(Button button, Component message) {
        if (button != null) {
            button.setMessage(message);
        }
    }

    private int centeredLeft(int contentWidth) {
        return Math.max(8, (this.width - contentWidth) / 2);
    }

    private int contentBottom() {
        return Math.max(CONTENT_TOP + 24, this.height - FOOTER_HEIGHT);
    }

    private void notifyUser(Component message) {
        ClientScreens.sendChatMessage(message);
    }

    private static String describe(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message;
    }

    /**
     * One entry in the voice grid, whether it came from this mod or another
     * one. The grid does not care which, so it is not told.
     *
     * @param description tooltip text, or null for none
     */
    private record VoiceChoice(String id, Component name, Component description, Runnable apply) {
    }

    private record VoiceButton(VoiceChoice choice, Button button) {
    }
}
