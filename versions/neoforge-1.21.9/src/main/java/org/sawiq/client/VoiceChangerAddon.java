package org.sawiq.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;
import org.sawiq.PvVoiceChanger;
import org.sawiq.client.ai.AiVoiceController;
import org.sawiq.client.ai.AiVoiceSettings;
import org.sawiq.client.ai.AiVoiceSettingsStore;
import org.sawiq.client.ai.audio.AudioFrames;
import org.sawiq.client.ai.install.EngineInstaller;
import org.sawiq.client.ai.runtime.AiVoiceRuntime;
import org.sawiq.client.ai.runtime.AiVoiceStream;
import org.sawiq.client.audio.AiCaptureBridge;
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
import org.sawiq.client.ui.AiModelPickerScreen;
import org.sawiq.client.ui.AiTextScreen;
import org.sawiq.client.ui.AiTrainScreen;
import org.sawiq.client.ui.AiVoiceScreen;
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
import su.plo.voice.client.config.hotkey.VoiceHotkey;

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
    private static final String OPEN_STUDIO_HOTKEY_ID = "pvvoicechanger.open_studio";
    /** Plasmo Voice shows these in its Hotkeys tab under this heading. */
    private static final String HOTKEY_CATEGORY = "category.pvvoicechanger";

    public static final VoiceChangerAddon INSTANCE = new VoiceChangerAddon();

    private final BooleanConfigEntry enabledEntry = new BooleanConfigEntry(false);
    private final BooleanConfigEntry selfListenEntry = new BooleanConfigEntry(false);
    private final EnumConfigEntry<VoiceChangerPreset> presetEntry =
            new EnumConfigEntry<>(VoiceChangerPreset.class, VoiceChangerPreset.MAN);
    private final IntConfigEntry strengthEntry = new IntConfigEntry(0, 100, 100);

    private final VoiceChangerApiSupport api = new VoiceChangerApiSupport();
    private final ServerVoiceSession serverSession = new ServerVoiceSession(this);
    private final AiVoiceRuntime aiRuntime = new AiVoiceRuntime();
    private final AiCaptureBridge aiCaptureBridge = new AiCaptureBridge(this.aiRuntime,
            mono -> this.aiVoice != null ? this.aiVoice.previewFrame(mono) : null);
    private final SelfListenBus selfListenBus = new SelfListenBus();
    private final VoiceChangerLiveFilter liveFilter = new VoiceChangerLiveFilter(this, this.selfListenBus);
    private final SelfListenMonitor selfListenMonitor = new SelfListenMonitor(
            this.selfListenBus,
            this::openMonitorDevice,
            this::getActiveVoice,
            this::reportInputLevel,
            failure -> setSelfListenEnabled(false),
            new SelfListenMonitor.AiPreview() {
                @Override
                public boolean isActive() {
                    AiVoiceController ai = VoiceChangerAddon.this.aiVoice;
                    return ai != null && ai.isPreviewing();
                }

                @Override
                public short[] convert(short[] mono) {
                    return VoiceChangerAddon.this.aiVoice.previewFrame(mono);
                }
            }
    );

    private ModVoiceClient voiceClient;
    private VoiceChangerPresetStore presetStore;
    private AiVoiceController aiVoice;
    private AiVoiceStream.Activity lastAiActivity = AiVoiceStream.Activity.IDLE;
    private List<String> savedPresetNames = List.of();
    private boolean initialized;
    private boolean suppressUiEvents;
    private volatile VoiceProfile playerProfile = VoiceChangerPreset.MAN.profile();
    private volatile ActiveVoice activeVoice = ActiveVoice.INACTIVE;
    private volatile boolean allowedByServer = true;
    private volatile PolicyReason serverDenialReason = PolicyReason.ALLOWED;
    private volatile VoiceSourceRule voiceRule = VoiceSourceRule.ALL;
    private volatile boolean aiAllowedByServer = true;
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
    private HotkeyConfigEntry openStudioHotkeyEntry;
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
        this.aiVoice = new AiVoiceController(new AiVoiceSettingsStore(getAiDirectory()), this.aiRuntime);
        this.aiVoice.load();
        ensureHotkeyRegistered();
        reloadSavedPresetNames();
        bindListeners();
        loadAutosaveOrDefault();
        ensureFilterAttached();
        this.initialized = true;
        refreshActiveVoice();
        this.aiVoice.refresh();
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
        this.aiVoice.shutdown();
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
        this.aiVoice.tick();
        this.aiVoice.setPreviewWanted(this.selfListenEntry.value() || isVoiceModMenuOpen());
        this.aiVoice.setStudioOpen(isAiScreenOpen());
        reportAiVoiceEvents();
    }

    /**
     * Tells the player, above the hotbar, what the AI voice is doing when it
     * is not obvious: a phrase engine is silent while it converts, and a
     * failing engine sends nothing at all.
     */
    private void reportAiVoiceEvents() {
        String failure = this.aiVoice.takeFailure();
        if (failure != null) {
            // The engine's own words are for the log; the player only needs
            // to know the voice hiccuped and that it is being dealt with.
            if (!failure.equals(AiVoiceStream.FAILURE_BUSY)) {
                PvVoiceChanger.LOGGER.info("AI voice: {}", failure);
            }
            ClientScreens.sendActionBarMessage(Component.translatable("pvvoicechanger.ai.actionbar.failed",
                    Component.translatable(failure.equals(AiVoiceStream.FAILURE_BUSY)
                            ? failure
                            : "pvvoicechanger.ai.failure.generic")));
        }

        String trained = this.aiVoice.takeFinishedTraining();
        if (trained != null) {
            ClientScreens.sendChatMessage(Component.translatable("pvvoicechanger.ai.train.finished_chat", trained));
        }

        String addedVoice = this.aiVoice.takeAddedVoice();
        if (addedVoice != null) {
            ClientScreens.sendChatMessage(Component.translatable("pvvoicechanger.ai.voices.added_chat", addedVoice));
        }

        // The install runs in the background, often with the studio closed,
        // so its end is said where the player is looking.
        EngineInstaller.Progress installed = this.aiVoice.installer().takeOutcome();
        if (installed != null) {
            ClientScreens.sendChatMessage(installed.state() == EngineInstaller.State.INSTALLED
                    ? Component.translatable("pvvoicechanger.ai.install.done_chat")
                    : Component.translatable("pvvoicechanger.ai.install.failed_chat", installed.detail()));
        }

        AiVoiceStream.Activity activity = this.aiVoice.activity();
        if (activity == AiVoiceStream.Activity.CONVERTING && this.lastAiActivity != activity) {
            ClientScreens.sendActionBarMessage(Component.translatable("pvvoicechanger.ai.actionbar.converting"));
        }
        this.lastAiActivity = activity;
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

    /** Whether the voice is being changed at all, override, AI voice and server policy included. */
    public boolean isEffectActive() {
        return this.activeVoice.active() || this.aiRuntime.isActive();
    }

    /** The capture thread's way into the AI voice. */
    public AiCaptureBridge aiCaptureBridge() {
        return this.aiCaptureBridge;
    }

    /**
     * Start of a Plasmo Voice capture tick. While the AI voice speaks, the
     * filter no longer sees the microphone, so the meter is fed from here.
     */
    public void onCaptureTick(short[] samples) {
        this.aiCaptureBridge.beginTick(samples);
        if (this.aiRuntime.isActive()) {
            reportInputLevel(AudioFrames.peakOf(samples));
        }
    }

    /** Whether the player has the effect switched on, ignoring anything on top of it. */
    public boolean isEffectEnabled() {
        return this.enabledEntry.value();
    }

    public boolean isSelfListenEnabled() {
        return this.selfListenEntry.value();
    }

    /** The tuning the player chose, ignoring any override on top of it. */
    public VoiceProfile getPlayerProfile() {
        return this.playerProfile;
    }

    public int getStrength() {
        return this.strengthEntry.value();
    }

    // --- AI voice -----------------------------------------------------------

    /** Engines, settings and status of the AI voice, for the studio's AI tab. */
    public AiVoiceController aiVoice() {
        return this.aiVoice;
    }

    /** Changes the AI voice settings; the voice source switch also changes what is active. */
    public void updateAiSettings(UnaryOperator<AiVoiceSettings> change) {
        this.aiVoice.update(change);
        refreshActiveVoice();
    }

    /** Whether this server lets players speak with an AI voice. */
    public boolean isAiAllowedByServer() {
        return this.aiAllowedByServer && this.voiceRule != VoiceSourceRule.SERVER_ONLY;
    }

    /**
     * The AI voice runs when the voice changer is on, the player picked the AI
     * voice as the source, and the server allows both. A server that allows
     * only its own shared voices is taken to mean no AI voices either.
     */
    private boolean isAiWanted() {
        return this.initialized
                && this.enabledEntry.value()
                && this.allowedByServer
                && isAiAllowedByServer()
                && this.aiVoice.isSelected();
    }

    public Path getAiDirectory() {
        return this.voiceClient.getConfigFolder().toPath().resolve("pv-voice-changer-ai");
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

    public HotkeyConfigEntry getOpenStudioHotkeyEntry() {
        return this.openStudioHotkeyEntry;
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

    /**
     * Both keys live in Plasmo Voice's own hotkey list rather than Minecraft's
     * key binds, next to its push-to-talk: that is where a Plasmo Voice player
     * looks for anything voice-related. Opening the studio is unbound by
     * default, as it is in the Simple Voice Chat build.
     */
    private void ensureHotkeyRegistered() {
        ConfigHotkeys hotkeys = (ConfigHotkeys) this.voiceClient.getHotkeys();
        Runnable save = () -> this.voiceClient.getConfig().save(true);
        this.toggleHotkeyEntry = registerHotkey(hotkeys, TOGGLE_HOTKEY_ID,
                List.of(new Hotkey.Key(Hotkey.Type.KEYSYM, InputConstants.KEY_J)),
                this::toggleEnabled, save);
        this.openStudioHotkeyEntry = registerHotkey(hotkeys, OPEN_STUDIO_HOTKEY_ID, List.of(),
                () -> ClientScreens.open(new VoiceChangerStudioScreen(null, this)), save);
    }

    /**
     * Registers the key once, keeping whatever the player rebound it to, and runs the action in game only.
     *
     * <ul>
     *     <li>A key read back from Plasmo Voice's config is registered there
     *     with no default keys, so its reset button cleared the key to "None"
     *     instead of restoring it. The real default is set here.</li>
     *     <li>Plasmo Voice writes its config only when its settings close or
     *     the player leaves a world, so a key bound and followed by quitting
     *     the game was lost. A new key is saved as soon as it is set.</li>
     * </ul>
     */
    private static HotkeyConfigEntry registerHotkey(
            ConfigHotkeys hotkeys, String id, List<Hotkey.Key> defaultKeys, Runnable action, Runnable save) {
        if (hotkeys.getConfigHotkey(id).isEmpty()) {
            hotkeys.register(id, defaultKeys, HOTKEY_CATEGORY, true);
        }

        HotkeyConfigEntry entry = hotkeys.getConfigHotkey(id).orElseThrow();
        entry.setDefault(new VoiceHotkey(hotkeys, id, defaultKeys, true));
        entry.addChangeListener(changed -> save.run());
        Hotkey hotkey = entry.value();
        hotkey.clearPressListener();
        hotkey.addPressListener(pressed -> {
            if (pressed == Hotkey.Action.DOWN && ClientScreens.current() == null) {
                action.run();
            }
        });
        return entry;
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
            boolean allowed, PolicyReason reason, String message, VoiceSourceRule voices, boolean aiAllowed) {
        this.serverDenialReason = allowed ? PolicyReason.ALLOWED : reason;
        this.serverDenialMessage = allowed || message == null ? "" : message;

        boolean ruleChanged = this.voiceRule != voices || this.aiAllowedByServer != aiAllowed;
        this.voiceRule = voices;
        this.aiAllowedByServer = aiAllowed;
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
        if (this.aiVoice != null) {
            this.aiVoice.setWanted(isAiWanted());
        }
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

    /**
     * Plasmo Voice's own settings, where its microphone test plays the
     * filtered microphone back; with the AI voice on, that has to be the AI
     * preview. Its screens are recognised by package, which is Plasmo Voice's
     * and the same in every environment.
     */
    private static boolean isVoiceModMenuOpen() {
        Object screen = ClientScreens.current();
        return screen != null && screen.getClass().getName().startsWith("su.plo.");
    }

    /** The AI tab and the screens it opens, which want the engine running for previews and training. */
    private static boolean isAiScreenOpen() {
        Object screen = ClientScreens.current();
        return screen instanceof AiVoiceScreen || screen instanceof AiTrainScreen
                || screen instanceof AiModelPickerScreen || screen instanceof AiTextScreen;
    }

    private boolean isStudioOpen() {
        return ClientScreens.current() instanceof VoiceChangerStudioScreen
                || ClientScreens.current() instanceof AiVoiceScreen;
    }

    private void showToggleStatus() {
        ClientScreens.sendActionBarMessage(Component.translatable(this.enabledEntry.value()
                ? "pvvoicechanger.actionbar.enabled"
                : "pvvoicechanger.actionbar.disabled"));
    }
}
