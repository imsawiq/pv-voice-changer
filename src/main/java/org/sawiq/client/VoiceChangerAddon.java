package org.sawiq.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.sawiq.client.audio.VoiceChangerLiveFilter;
import org.sawiq.client.audio.VoiceChangerSelfListenPreview;
import org.sawiq.client.model.VoiceChangerPreset;
import org.sawiq.client.model.VoiceChangerProfile;
import org.sawiq.client.model.VoiceChangerState;
import org.sawiq.client.preset.VoiceChangerPresetStore;
import org.sawiq.client.ui.VoiceChangerStudioScreen;
import su.plo.config.entry.BooleanConfigEntry;
import su.plo.config.entry.EnumConfigEntry;
import su.plo.config.entry.IntConfigEntry;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.audio.device.AudioDevice;
import su.plo.voice.api.client.audio.filter.AudioFilter.Priority;
import su.plo.voice.api.client.config.hotkey.Hotkey;
import su.plo.voice.client.ModVoiceClient;
import su.plo.voice.client.config.hotkey.ConfigHotkeys;
import su.plo.voice.client.config.hotkey.HotkeyConfigEntry;

@Addon(
        id = "pv-voice-changer",
        name = "Plasmo Voice Changer",
        scope = AddonLoaderScope.CLIENT,
        version = "1.0.0",
        authors = {"sawiq"},
        dependencies = {}
)
public final class VoiceChangerAddon {
    private static final String TOGGLE_HOTKEY_ID = "pvvoicechanger.toggle";
    private static final String TOGGLE_HOTKEY_CATEGORY = "category.pvvoicechanger";

    public static final VoiceChangerAddon INSTANCE = new VoiceChangerAddon();

    private final BooleanConfigEntry enabledEntry = new BooleanConfigEntry(false);
    private final BooleanConfigEntry selfListenEntry = new BooleanConfigEntry(false);
    private final EnumConfigEntry<VoiceChangerPreset> presetEntry = new EnumConfigEntry<>(VoiceChangerPreset.class, VoiceChangerPreset.MAN);
    private final IntConfigEntry strengthEntry = new IntConfigEntry(0, 100, 100);
    private final VoiceChangerLiveFilter liveFilter = new VoiceChangerLiveFilter(this);
    private final VoiceChangerSelfListenPreview selfListenPreview = new VoiceChangerSelfListenPreview(
            () -> this.selfListenEntry.value(),
            this::isStudioPreviewActive,
            () -> this.enabledEntry.value(),
            () -> this.currentProfile,
            () -> this.strengthEntry.value(),
            () -> setSelfListenSilently(false)
    );

    private ModVoiceClient voiceClient;
    private VoiceChangerPresetStore presetStore;
    private List<String> savedPresetNames = List.of();
    private boolean initialized;
    private boolean suppressUiEvents;
    private VoiceChangerProfile currentProfile = VoiceChangerProfile.defaultsFor(VoiceChangerPreset.MAN);
    private String selectedSavedPresetName;
    private AudioDevice attachedDevice;
    private HotkeyConfigEntry toggleHotkeyEntry;

    private VoiceChangerAddon() {
    }

    public void initialize(ModVoiceClient voiceClient) {
        if (this.initialized) {
            return;
        }

        this.voiceClient = voiceClient;
        this.presetStore = new VoiceChangerPresetStore(getPresetDirectory());
        ensureHotkeyRegistered();
        reloadSavedPresetNames();
        bindListeners();
        loadAutosaveOrDefault();
        ensureFilterAttached();
        this.initialized = true;
    }

    public void shutdown() {
        if (!this.initialized || this.voiceClient == null) {
            return;
        }

        if (this.attachedDevice != null) {
            this.attachedDevice.removeFilter(this.liveFilter);
            this.attachedDevice = null;
        }

        this.selfListenPreview.stop();
        this.initialized = false;
        this.voiceClient = null;
    }

    public void tick() {
        if (!isInitialized()) {
            return;
        }

        ensureFilterAttached();
        if (!isStudioPreviewActive()) {
            setSelfListenEnabled(false);
        }
    }

    public boolean isInitialized() {
        return this.initialized && this.voiceClient != null && this.presetStore != null;
    }

    public BooleanConfigEntry getEnabledEntry() {
        return this.enabledEntry;
    }

    public boolean isEffectEnabled() {
        return this.enabledEntry.value();
    }

    public EnumConfigEntry<VoiceChangerPreset> getPresetControl() {
        return this.presetEntry;
    }

    public BooleanConfigEntry getSelfListenEntry() {
        return this.selfListenEntry;
    }

    public IntConfigEntry getStrengthEntry() {
        return this.strengthEntry;
    }

    public HotkeyConfigEntry getToggleHotkeyEntry() {
        return this.toggleHotkeyEntry;
    }

    public VoiceChangerPreset getSelectedPreset() {
        return this.presetEntry.value();
    }

    public VoiceChangerProfile getCurrentProfileSnapshot() {
        return this.currentProfile;
    }

    public String getSelectedSavedPresetName() {
        return this.selectedSavedPresetName;
    }

    public Path getPresetDirectory() {
        return this.voiceClient.getConfigFolder().toPath().resolve("pv-voice-changer-presets");
    }

    public List<String> listSavedPresetNames() throws IOException {
        reloadSavedPresetNames();
        return this.savedPresetNames;
    }

    public List<String> getSavedPresetNamesCached() {
        return this.savedPresetNames;
    }

    public void setEnabled(boolean enabled) {
        setEnabledSilently(enabled);
        persistAutosave();
        showToggleStatus();
    }

    public void toggleEnabled() {
        setEnabled(!this.enabledEntry.value());
    }

    public void setStrength(int strength) {
        setStrengthSilently(clampInt(strength, 0, 100));
        persistAutosave();
    }

    public void setSelfListenEnabled(boolean enabled) {
        setSelfListenSilently(enabled && isStudioPreviewActive());
        if (this.selfListenEntry.value()) {
            this.selfListenPreview.start();
        } else {
            this.selfListenPreview.stop();
        }
        persistAutosave();
    }

    public void applyBuiltInPreset(VoiceChangerPreset preset) {
        if (preset == VoiceChangerPreset.CUSTOM) {
            return;
        }

        applyState(new VoiceChangerState(
                this.enabledEntry.value(),
                false,
                preset,
                this.strengthEntry.value(),
                null,
                VoiceChangerProfile.defaultsFor(preset)
        ));
    }

    public void applyCustomProfile(VoiceChangerProfile profile) {
        applyState(new VoiceChangerState(
                this.enabledEntry.value(),
                this.selfListenEntry.value(),
                VoiceChangerPreset.CUSTOM,
                this.strengthEntry.value(),
                null,
                profile
        ));
    }

    public void loadSavedPreset(String rawName) throws IOException {
        String sanitized = VoiceChangerPresetStore.sanitizeName(rawName);
        VoiceChangerProfile profile = this.presetStore.loadPreset(sanitized);
        applyState(new VoiceChangerState(
                this.enabledEntry.value(),
                this.selfListenEntry.value(),
                VoiceChangerPreset.CUSTOM,
                this.strengthEntry.value(),
                sanitized,
                profile
        ));
    }

    public void ensurePresetDirectory() throws IOException {
        this.presetStore.ensureDirectory();
    }

    public String saveCurrentPreset(String rawName) throws IOException {
        String sanitized = VoiceChangerPresetStore.sanitizeName(rawName);
        this.presetStore.savePreset(sanitized, this.currentProfile);
        reloadSavedPresetNames();
        this.selectedSavedPresetName = sanitized;
        persistAutosave();
        return sanitized;
    }

    public boolean deleteSavedPreset(String rawName) throws IOException {
        boolean deleted = this.presetStore.deletePreset(rawName);
        reloadSavedPresetNames();

        if (deleted && rawName.equalsIgnoreCase(String.valueOf(this.selectedSavedPresetName))) {
            this.selectedSavedPresetName = null;
        }

        persistAutosave();
        return deleted;
    }

    public void reloadSavedPresetNames() {
        try {
            this.savedPresetNames = this.presetStore.listPresetNames();
        } catch (IOException ignored) {
            this.savedPresetNames = List.of();
        }
    }

    private void ensureHotkeyRegistered() {
        ConfigHotkeys hotkeys = (ConfigHotkeys) this.voiceClient.getHotkeys();
        if (hotkeys.getConfigHotkey(TOGGLE_HOTKEY_ID).isEmpty()) {
            hotkeys.register(
                    TOGGLE_HOTKEY_ID,
                    List.of(new Hotkey.Key(Hotkey.Type.KEYSYM, GLFW.GLFW_KEY_J)),
                    TOGGLE_HOTKEY_CATEGORY,
                    true
            );
        }

        this.toggleHotkeyEntry = hotkeys.getConfigHotkey(TOGGLE_HOTKEY_ID).orElseThrow();
        Hotkey hotkey = this.toggleHotkeyEntry.value();
        hotkey.clearPressListener();
        hotkey.addPressListener(action -> {
            if (action == Hotkey.Action.DOWN && Minecraft.getInstance().gui.screen() == null) {
                toggleEnabled();
            }
        });
    }

    private void bindListeners() {
        this.enabledEntry.clearChangeListeners();
        this.selfListenEntry.clearChangeListeners();
        this.presetEntry.clearChangeListeners();
        this.strengthEntry.clearChangeListeners();

        this.enabledEntry.addChangeListener(value -> persistIfInteractive());
        this.selfListenEntry.addChangeListener(value -> {
            if (!this.suppressUiEvents && !value) {
                this.selfListenPreview.stop();
            }
            persistIfInteractive();
        });
        this.presetEntry.addChangeListener(value -> {
            if (this.suppressUiEvents) {
                return;
            }

            if (value == VoiceChangerPreset.CUSTOM) {
                persistAutosave();
                return;
            }

            applyBuiltInPreset(value);
        });
        this.strengthEntry.addChangeListener(value -> persistIfInteractive());
    }

    private void loadAutosaveOrDefault() {
        VoiceChangerState state;
        try {
            state = this.presetStore.loadAutosaveState();
        } catch (IOException ignored) {
            state = VoiceChangerState.defaults();
        }

        applyState(state);
    }

    private void applyState(VoiceChangerState state) {
        this.suppressUiEvents = true;
        try {
            this.currentProfile = sanitizeProfile(state.profile());
            this.selectedSavedPresetName = normalizeSavedPresetName(state.savedPresetName());
            this.enabledEntry.set(state.enabled());
            this.selfListenEntry.set(state.selfListen());
            this.presetEntry.set(state.preset());
            this.strengthEntry.set(clampInt(state.strength(), 0, 100));
        } finally {
            this.suppressUiEvents = false;
        }

        if (this.selfListenEntry.value()) {
            this.selfListenPreview.start();
        } else {
            this.selfListenPreview.stop();
        }
        persistAutosave();
    }

    private void persistIfInteractive() {
        if (!this.suppressUiEvents) {
            persistAutosave();
        }
    }

    private String normalizeSavedPresetName(String savedPresetName) {
        if (savedPresetName == null || savedPresetName.isBlank()) {
            return null;
        }

        return this.savedPresetNames.contains(savedPresetName) ? savedPresetName : null;
    }

    private void persistAutosave() {
        if (!isInitialized() || this.suppressUiEvents) {
            return;
        }

        try {
            this.presetStore.saveAutosave(new VoiceChangerState(
                    this.enabledEntry.value(),
                    this.selfListenEntry.value(),
                    this.presetEntry.value(),
                    this.strengthEntry.value(),
                    this.selectedSavedPresetName,
                    this.currentProfile
            ));
        } catch (IOException ignored) {
        }
    }

    private void setEnabledSilently(boolean enabled) {
        this.suppressUiEvents = true;
        try {
            this.enabledEntry.set(enabled);
        } finally {
            this.suppressUiEvents = false;
        }
    }

    private void setStrengthSilently(int strength) {
        this.suppressUiEvents = true;
        try {
            this.strengthEntry.set(strength);
        } finally {
            this.suppressUiEvents = false;
        }
    }

    private void setSelfListenSilently(boolean enabled) {
        this.suppressUiEvents = true;
        try {
            this.selfListenEntry.set(enabled);
        } finally {
            this.suppressUiEvents = false;
        }
    }

    private void ensureFilterAttached() {
        this.voiceClient.getDeviceManager().getInputDevice().ifPresentOrElse(
                this::attachFilter,
                this::detachFilter
        );
    }

    private void attachFilter(AudioDevice device) {
        if (this.attachedDevice == device) {
            return;
        }

        detachFilter();
        device.removeFilter(this.liveFilter);
        device.addFilter(this.liveFilter, Priority.HIGHEST);
        this.attachedDevice = device;
    }

    private void detachFilter() {
        if (this.attachedDevice != null) {
            this.attachedDevice.removeFilter(this.liveFilter);
            this.attachedDevice = null;
        }
    }

    private boolean isStudioPreviewActive() {
        return Minecraft.getInstance().gui.screen() instanceof VoiceChangerStudioScreen;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static VoiceChangerProfile sanitizeProfile(VoiceChangerProfile profile) {
        return new VoiceChangerProfile(
                sanitize(profile.mix(), 0.90D, 0.0D, 1.0D),
                sanitize(profile.gain(), 1.00D, 0.40D, 2.50D),
                sanitize(profile.pitch(), 1.00D, 0.55D, 2.20D),
                sanitize(profile.formant(), 1.00D, 0.45D, 2.00D),
                sanitize(profile.distortion(), 0.00D, 0.0D, 0.75D),
                sanitize(profile.robotMix(), 0.00D, 0.0D, 0.85D),
                clampInt(profile.robotFrequency(), 20, 140),
                sanitize(profile.echoMix(), 0.00D, 0.0D, 0.65D),
                clampInt(profile.echoDelayMs(), 40, 260),
                sanitize(profile.echoFeedback(), 0.10D, 0.0D, 0.80D),
                sanitize(profile.tremoloDepth(), 0.00D, 0.0D, 0.75D),
                sanitize(profile.tremoloRate(), 2.50D, 0.20D, 9.00D),
                sanitize(profile.lowEq(), 0.00D, -10.0D, 10.0D),
                sanitize(profile.midEq(), 0.00D, -10.0D, 10.0D),
                sanitize(profile.highEq(), 0.00D, -10.0D, 10.0D),
                sanitize(profile.noise(), 0.00D, 0.0D, 0.60D),
                sanitize(profile.autotuneMix(), 0.00D, 0.0D, 1.0D),
                sanitize(profile.autotuneStrength(), 0.60D, 0.0D, 1.0D),
                clampInt(profile.autotuneKey(), 0, 11),
                clampInt(profile.autotuneScale(), 0, 2)
        );
    }

    private static double sanitize(double value, double fallback, double min, double max) {
        if (!Double.isFinite(value)) {
            return fallback;
        }

        return Math.max(min, Math.min(max, value));
    }

    private void showToggleStatus() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        client.player.sendOverlayMessage(
                this.enabledEntry.value()
                        ? Component.translatable("pvvoicechanger.actionbar.enabled")
                        : Component.translatable("pvvoicechanger.actionbar.disabled")
        );
    }
}
