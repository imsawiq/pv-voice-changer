package org.sawiq.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.sawiq.PvVoiceChanger;
import org.sawiq.client.audio.SelfListenBus;
import org.sawiq.client.audio.SelfListenMonitor;
import org.sawiq.client.audio.VoiceChangerLiveFilter;
import org.sawiq.client.audio.VoiceDiagnostics;
import org.sawiq.client.api.VoiceChangerApi;
import org.sawiq.client.api.VoicePreset;
import org.sawiq.client.api.internal.VoiceChangerApiImpl;
import org.sawiq.client.api.internal.VoiceChangerApiRegistry;
import org.sawiq.client.api.internal.VoiceChangerApiSupport;
import org.sawiq.client.compat.ClientScreens;
import org.sawiq.client.model.ActiveVoice;
import org.sawiq.client.model.VoiceChangerPreset;
import org.sawiq.client.model.VoiceChangerState;
import org.sawiq.client.model.VoiceProfile;
import org.sawiq.client.preset.VoiceChangerPresetStore;
import org.sawiq.client.server.ServerVoiceSession;
import org.sawiq.protocol.PolicyReason;
import org.sawiq.protocol.VoiceSourceRule;
import org.sawiq.client.ui.VoiceChangerStudioScreen;
import su.plo.config.entry.BooleanConfigEntry;
import su.plo.config.entry.EnumConfigEntry;
import su.plo.config.entry.IntConfigEntry;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.audio.device.AudioDevice;
import su.plo.voice.api.client.audio.device.InputDevice;
import su.plo.voice.api.client.audio.filter.AudioFilter.Priority;
import su.plo.voice.api.client.config.hotkey.Hotkey;
import su.plo.voice.client.ModVoiceClient;
import su.plo.voice.client.config.hotkey.ConfigHotkeys;
import su.plo.voice.client.config.hotkey.HotkeyConfigEntry;

/**
 * Client-side state of the voice changer: the current profile, the saved
 * preset library, the Plasmo Voice hooks and the studio's self-listen monitor.
 */
@Addon(
        id = "pv-voice-changer",
        name = "Plasmo Voice Changer",
        scope = AddonLoaderScope.CLIENT,
        version = "1.7",
        authors = {"sawiq_"},
        dependencies = {}
)
public final class VoiceChangerAddon {
    private static final String TOGGLE_HOTKEY_ID = "pvvoicechanger.toggle";
    private static final String TOGGLE_HOTKEY_CATEGORY = "category.pvvoicechanger";

    public static final VoiceChangerAddon INSTANCE = new VoiceChangerAddon();

    private final BooleanConfigEntry enabledEntry = new BooleanConfigEntry(false);
    private final BooleanConfigEntry selfListenEntry = new BooleanConfigEntry(false);
    private final EnumConfigEntry<VoiceChangerPreset> presetEntry =
            new EnumConfigEntry<>(VoiceChangerPreset.class, VoiceChangerPreset.MAN);
    private final IntConfigEntry strengthEntry = new IntConfigEntry(0, 100, 100);

    private final VoiceChangerApiSupport api = new VoiceChangerApiSupport();
    private final ServerVoiceSession serverSession = new ServerVoiceSession(this);
    private final SelfListenBus selfListenBus = new SelfListenBus();
    private final VoiceChangerLiveFilter liveFilter = new VoiceChangerLiveFilter(this, this.selfListenBus);
    private final SelfListenMonitor selfListenMonitor = new SelfListenMonitor(
            this.selfListenBus,
            this::openMonitorDevice,
            this::getActiveVoice,
            this::reportInputLevel,
            failure -> setSelfListenEnabled(false)
    );

    private ModVoiceClient voiceClient;
    private VoiceChangerPresetStore presetStore;
    private List<String> savedPresetNames = List.of();
    private boolean initialized;
    private boolean suppressUiEvents;
    private volatile VoiceProfile playerProfile = VoiceChangerPreset.MAN.profile();
    private volatile ActiveVoice activeVoice = ActiveVoice.INACTIVE;
    private volatile boolean allowedByServer = true;
    private volatile PolicyReason serverDenialReason = PolicyReason.ALLOWED;
    private volatile VoiceSourceRule voiceRule = VoiceSourceRule.ALL;
    private volatile String serverDenialMessage = "";
    private String selectedSavedPresetName;
    /**
     * Volatile because {@code getSelectedVoiceId()} is part of the published
     * API, which promises any thread may call it, while this is written on the
     * client thread whenever the player picks a voice.
     */
    private volatile String selectedContributedVoiceId;
    private boolean autosaveFailureLogged;
    private AudioDevice attachedDevice;
    private HotkeyConfigEntry toggleHotkeyEntry;
    private volatile double inputLevel;

    private VoiceChangerAddon() {
        this.api.onOverrideChanged(this::onOverrideChanged);
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
        refreshActiveVoice();
        // Published last: a mod calling in from its own thread the instant the
        // API appears must find an addon that is already fully wired up.
        VoiceChangerApiRegistry.publish(new VoiceChangerApiImpl(this));
    }

    public void shutdown() {
        if (!this.initialized || this.voiceClient == null) {
            return;
        }

        VoiceChangerApiRegistry.withdraw();
        this.api.clear();
        detachFilter();
        this.selfListenMonitor.stop();
        this.initialized = false;
        this.voiceClient = null;
        refreshActiveVoice();
    }

    public void tick() {
        if (!isInitialized()) {
            return;
        }

        ensureFilterAttached();
        this.serverSession.tick();
        if (!isStudioOpen() && this.selfListenEntry.value()) {
            setSelfListenEnabled(false);
        }
    }

    /** What the current server has said about the voice changer. */
    public ServerVoiceSession serverSession() {
        return this.serverSession;
    }

    public boolean isInitialized() {
        return this.initialized && this.voiceClient != null && this.presetStore != null;
    }

    // --- Live audio surface, called from the capture thread -----------------

    /**
     * What the audio thread should apply right now, resolved in one read so an
     * override cannot land between two separate questions.
     */
    public ActiveVoice getActiveVoice() {
        return this.activeVoice;
    }

    /** Whether the voice is being changed at all, override and server policy included. */
    public boolean isEffectActive() {
        return this.activeVoice.active();
    }

    /** Whether the player has the effect switched on, ignoring anything on top of it. */
    public boolean isEffectEnabled() {
        return this.enabledEntry.value();
    }

    /** The tuning the player chose, ignoring any override on top of it. */
    public VoiceProfile getPlayerProfile() {
        return this.playerProfile;
    }

    public int getStrength() {
        return this.strengthEntry.value();
    }

    // --- Public API surface -------------------------------------------------

    /** The overrides, contributed voices and listeners behind {@link VoiceChangerApi}. */
    public VoiceChangerApiSupport api() {
        return this.api;
    }

    /** Whether the server permits the voice changer here. */
    public boolean isAllowedByServer() {
        return this.allowedByServer;
    }

    /** How much freedom this server gives the player over their voice. */
    public VoiceSourceRule getVoiceRule() {
        return this.voiceRule;
    }

    /** Whether the voice the player currently has selected is permitted here. */
    public boolean isSelectedVoiceAllowed() {
        return this.voiceRule.allows(getSelectedVoiceId());
    }

    /** Why the server refused, or {@link PolicyReason#ALLOWED} when it has not. */
    public PolicyReason getServerDenialReason() {
        return this.serverDenialReason;
    }

    /**
     * The server operator's own wording for the refusal, or empty when they
     * gave none and {@link #getServerDenialReason()} should be shown instead.
     */
    public String getServerDenialMessage() {
        return this.serverDenialMessage;
    }

    /**
     * Identifier of the voice the player selected: a built-in id such as
     * {@code man}, a contributed {@code namespace:id}, or {@code custom}.
     */
    public String getSelectedVoiceId() {
        VoiceChangerPreset preset = this.presetEntry.value();
        if (preset != VoiceChangerPreset.CUSTOM) {
            return preset.id();
        }
        return this.selectedContributedVoiceId != null ? this.selectedContributedVoiceId : "custom";
    }

    public String getSelectedContributedVoiceId() {
        return this.selectedContributedVoiceId;
    }

    /** Reported by the microphone filter so the studio can draw a level meter. */
    public void reportInputLevel(double peak) {
        this.inputLevel = peak;
    }

    public double getInputLevel() {
        return this.inputLevel;
    }

    /**
     * What the chain the user is currently hearing is doing. While the studio
     * previews, that is the monitor's own chain; otherwise it is the
     * microphone filter that feeds other players.
     */
    public VoiceDiagnostics getDiagnostics() {
        VoiceDiagnostics preview = this.selfListenMonitor.diagnostics();
        return preview.isRunning() ? preview : this.liveFilter.diagnostics();
    }

    public SelfListenMonitor.Mode getSelfListenMode() {
        return this.selfListenMonitor.mode();
    }

    // --- Config entries used by the Plasmo Voice settings tab ---------------

    public BooleanConfigEntry getEnabledEntry() {
        return this.enabledEntry;
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

    public String getSelectedSavedPresetName() {
        return this.selectedSavedPresetName;
    }

    // --- Commands from the UI ----------------------------------------------

    public void setEnabled(boolean enabled) {
        setSilently(() -> this.enabledEntry.set(enabled));
        refreshActiveVoice();
        persistAutosave();
        showToggleStatus();
        this.api.notifyEach(listener -> listener.onEnabledChanged(enabled));
    }

    public void toggleEnabled() {
        setEnabled(!this.enabledEntry.value());
    }

    public void setStrength(int strength) {
        setSilently(() -> this.strengthEntry.set(Math.max(0, Math.min(100, strength))));
        refreshActiveVoice();
        persistAutosave();
        this.api.notifyEach(listener -> listener.onStrengthChanged(this.strengthEntry.value()));
    }

    public void setSelfListenEnabled(boolean enabled) {
        boolean allowed = enabled && isStudioOpen();
        setSilently(() -> this.selfListenEntry.set(allowed));

        if (allowed) {
            this.selfListenMonitor.start();
        } else {
            this.selfListenMonitor.stop();
        }
        persistAutosave();
    }

    public void applyBuiltInPreset(VoiceChangerPreset preset) {
        if (preset == VoiceChangerPreset.CUSTOM || !this.voiceRule.allows(preset.id())) {
            return;
        }

        applyState(new VoiceChangerState(
                this.enabledEntry.value(),
                this.selfListenEntry.value(),
                preset,
                this.strengthEntry.value(),
                null,
                null,
                preset.profile()
        ));
    }

    /** Selects a voice another mod contributed, remembering it by id. */
    public void applyContributedPreset(VoicePreset preset) {
        if (!this.voiceRule.allows(preset.id())) {
            return;
        }

        applyState(new VoiceChangerState(
                this.enabledEntry.value(),
                this.selfListenEntry.value(),
                VoiceChangerPreset.CUSTOM,
                this.strengthEntry.value(),
                null,
                preset.id(),
                preset.profile()
        ));
    }

    /**
     * Applies a tuning the player made themselves. Refused outright while the
     * server allows only ready-made voices, so the studio's sliders cannot be
     * the way around that policy even if a widget is left enabled by mistake.
     */
    public void applyCustomProfile(VoiceProfile profile) {
        if (!this.voiceRule.allowsHandTuning()) {
            return;
        }

        applyState(new VoiceChangerState(
                this.enabledEntry.value(),
                this.selfListenEntry.value(),
                VoiceChangerPreset.CUSTOM,
                this.strengthEntry.value(),
                null,
                null,
                profile
        ));
    }

    public void loadSavedPreset(String rawName) throws IOException {
        if (!this.voiceRule.allowsPersonalPresets()) {
            return;
        }

        String sanitized = VoiceChangerPresetStore.sanitizeName(rawName);
        VoiceProfile profile = this.presetStore.loadPreset(sanitized);
        applyState(new VoiceChangerState(
                this.enabledEntry.value(),
                this.selfListenEntry.value(),
                VoiceChangerPreset.CUSTOM,
                this.strengthEntry.value(),
                sanitized,
                null,
                profile
        ));
    }

    public String saveCurrentPreset(String rawName) throws IOException {
        String sanitized = VoiceChangerPresetStore.sanitizeName(rawName);
        this.presetStore.savePreset(sanitized, this.playerProfile);
        reloadSavedPresetNames();
        this.selectedSavedPresetName = sanitized;
        persistAutosave();
        return sanitized;
    }

    public boolean deleteSavedPreset(String rawName) throws IOException {
        boolean deleted = this.presetStore.deletePreset(rawName);
        reloadSavedPresetNames();

        if (deleted && rawName.equalsIgnoreCase(this.selectedSavedPresetName)) {
            this.selectedSavedPresetName = null;
        }

        persistAutosave();
        return deleted;
    }

    // --- Preset library ----------------------------------------------------

    public Path getPresetDirectory() {
        return this.voiceClient.getConfigFolder().toPath().resolve("pv-voice-changer-presets");
    }

    public void ensurePresetDirectory() throws IOException {
        this.presetStore.ensureDirectory();
    }

    public List<String> listSavedPresetNames() throws IOException {
        reloadSavedPresetNames();
        return this.savedPresetNames;
    }

    public List<String> getSavedPresetNamesCached() {
        return this.savedPresetNames;
    }

    public void reloadSavedPresetNames() {
        try {
            this.savedPresetNames = this.presetStore.listPresetNames();
        } catch (IOException exception) {
            this.savedPresetNames = List.of();
            PvVoiceChanger.LOGGER.warn("Could not read the saved preset folder", exception);
        }
    }

    // --- Internals ---------------------------------------------------------

    private void ensureHotkeyRegistered() {
        ConfigHotkeys hotkeys = (ConfigHotkeys) this.voiceClient.getHotkeys();
        if (hotkeys.getConfigHotkey(TOGGLE_HOTKEY_ID).isEmpty()) {
            hotkeys.register(
                    TOGGLE_HOTKEY_ID,
                    List.of(new Hotkey.Key(Hotkey.Type.KEYSYM, InputConstants.KEY_J)),
                    TOGGLE_HOTKEY_CATEGORY,
                    true
            );
        }

        this.toggleHotkeyEntry = hotkeys.getConfigHotkey(TOGGLE_HOTKEY_ID).orElseThrow();
        Hotkey hotkey = this.toggleHotkeyEntry.value();
        hotkey.clearPressListener();
        hotkey.addPressListener(action -> {
            if (action == Hotkey.Action.DOWN && ClientScreens.current() == null) {
                toggleEnabled();
            }
        });
    }

    private void bindListeners() {
        this.enabledEntry.clearChangeListeners();
        this.selfListenEntry.clearChangeListeners();
        this.presetEntry.clearChangeListeners();
        this.strengthEntry.clearChangeListeners();

        // Recomputed here rather than only in setEnabled(): Plasmo Voice's own
        // activation tab binds its toggle straight to this entry, so flipping
        // the switch there never reaches setEnabled() and the audio thread
        // would keep reading the snapshot taken before the switch moved.
        this.enabledEntry.addChangeListener(value -> {
            refreshActiveVoice();
            persistIfInteractive();
        });
        this.selfListenEntry.addChangeListener(value -> {
            if (!this.suppressUiEvents && !value) {
                this.selfListenMonitor.stop();
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
        // Strength is part of that same snapshot, and the studio's slider writes
        // the entry directly for the same reason.
        this.strengthEntry.addChangeListener(value -> {
            refreshActiveVoice();
            persistIfInteractive();
        });
    }

    private void loadAutosaveOrDefault() {
        VoiceChangerState state;
        try {
            state = this.presetStore.loadAutosaveState();
        } catch (IOException exception) {
            state = VoiceChangerState.defaults();
            PvVoiceChanger.LOGGER.warn("Could not read the saved settings; starting from defaults", exception);
        }

        applyState(state);
    }

    private void applyState(VoiceChangerState state) {
        this.suppressUiEvents = true;
        try {
            this.playerProfile = state.profile();
            this.selectedSavedPresetName = normalizeSavedPresetName(state.savedPresetName());
            this.selectedContributedVoiceId = state.contributedVoiceId();
            this.enabledEntry.set(state.enabled());
            this.selfListenEntry.set(state.selfListen());
            this.presetEntry.set(state.preset());
            this.strengthEntry.set(Math.max(0, Math.min(100, state.strength())));
        } finally {
            this.suppressUiEvents = false;
        }

        refreshActiveVoice();
        if (this.selfListenEntry.value() && isStudioOpen()) {
            this.selfListenMonitor.start();
        } else {
            this.selfListenMonitor.stop();
        }
        persistAutosave();
        this.api.notifyEach(listener -> listener.onVoiceChanged(getSelectedVoiceId()));
    }

    /**
     * Applies what the server says about the voice changer here.
     *
     * <p>Advisory by nature: the voice is changed on this machine before the
     * audio is encoded, so a server can ask an honest client to stop and has no
     * way to make a modified one comply.</p>
     */
    public void applyServerPolicy(
            boolean allowed, PolicyReason reason, String message, VoiceSourceRule voices) {
        this.serverDenialReason = allowed ? PolicyReason.ALLOWED : reason;
        this.serverDenialMessage = allowed || message == null ? "" : message;

        boolean ruleChanged = this.voiceRule != voices;
        this.voiceRule = voices;
        if (ruleChanged) {
            refreshActiveVoice();
        }

        if (this.allowedByServer == allowed) {
            return;
        }

        this.allowedByServer = allowed;
        refreshActiveVoice();

        // Listeners get something a mod can branch on, preferring the
        // operator's wording only when there is one to show a player.
        String described = allowed
                ? null
                : (this.serverDenialMessage.isBlank() ? reason.name() : this.serverDenialMessage);
        this.api.notifyEach(listener -> listener.onAllowedChanged(allowed, described));
    }

    /** Recomputes the audio thread's snapshot. Cheap, so it is done eagerly. */
    private void refreshActiveVoice() {
        this.activeVoice = this.initialized
                ? this.api.resolve(this.enabledEntry.value(), this.allowedByServer,
                        isSelectedVoiceAllowed(), this.playerProfile, this.strengthEntry.value())
                : ActiveVoice.INACTIVE;
    }

    /** A mod pushed an override or released one. */
    private void onOverrideChanged() {
        refreshActiveVoice();
        String owner = this.api.winningOverrideOwner();
        this.api.notifyEach(listener -> listener.onOverrideChanged(owner));
    }

    private String normalizeSavedPresetName(String savedPresetName) {
        if (savedPresetName == null || savedPresetName.isBlank()) {
            return null;
        }
        return this.savedPresetNames.contains(savedPresetName) ? savedPresetName : null;
    }

    private void persistIfInteractive() {
        if (!this.suppressUiEvents) {
            persistAutosave();
        }
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
                    this.selectedContributedVoiceId,
                    this.playerProfile
            ));
            this.autosaveFailureLogged = false;
        } catch (IOException exception) {
            // Runs on every change a player makes, so a broken disk would
            // otherwise fill the log with the same line.
            if (!this.autosaveFailureLogged) {
                this.autosaveFailureLogged = true;
                PvVoiceChanger.LOGGER.warn("Could not save the voice changer settings", exception);
            }
        }
    }

    /** Runs a config write without triggering the listeners that persist state. */
    private void setSilently(Runnable change) {
        this.suppressUiEvents = true;
        try {
            change.run();
        } finally {
            this.suppressUiEvents = false;
        }
    }

    private void ensureFilterAttached() {
        this.voiceClient.getDeviceManager().getInputDevice().ifPresentOrElse(this::attachFilter, this::detachFilter);
    }

    private void attachFilter(AudioDevice device) {
        if (this.attachedDevice == device) {
            return;
        }

        detachFilter();
        device.removeFilter(this.liveFilter);
        device.addFilter(this.liveFilter, Priority.HIGHEST);
        this.attachedDevice = device;
        // A new device means a new stream: the chain's delay lines and vocoder
        // phase describe the old one and would be heard as a click.
        this.liveFilter.resetStream();
    }

    private void detachFilter() {
        if (this.attachedDevice != null) {
            this.attachedDevice.removeFilter(this.liveFilter);
            this.attachedDevice = null;
        }
    }

    /**
     * Opens a second handle on the microphone Plasmo Voice is configured to
     * use, for the studio's self-listen preview.
     *
     * @return {@code null} when the device cannot be opened, usually because
     *         Plasmo Voice is capturing from it at that moment
     */
    private InputDevice openMonitorDevice() {
        if (this.voiceClient == null) {
            return null;
        }

        try {
            return this.voiceClient.getDeviceManager().openInputDevice(SelfListenMonitor.CAPTURE_FORMAT);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isStudioOpen() {
        return ClientScreens.current() instanceof VoiceChangerStudioScreen;
    }

    private void showToggleStatus() {
        ClientScreens.sendActionBarMessage(Component.translatable(this.enabledEntry.value()
                ? "pvvoicechanger.actionbar.enabled"
                : "pvvoicechanger.actionbar.disabled"));
    }
}
