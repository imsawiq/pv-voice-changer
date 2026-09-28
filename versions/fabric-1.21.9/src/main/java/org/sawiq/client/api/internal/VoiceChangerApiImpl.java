package org.sawiq.client.api.internal;

import java.util.Optional;
import org.sawiq.client.VoiceChangerAddon;
import org.sawiq.client.api.VoiceChangerApi;
import org.sawiq.client.api.VoiceChangerListener;
import org.sawiq.client.api.VoiceOverride;
import org.sawiq.client.api.VoicePreset;
import org.sawiq.client.model.ActiveVoice;
import org.sawiq.client.model.VoiceChangerPreset;
import org.sawiq.client.model.VoiceProfile;

/**
 * Adapts the addon to the published contract.
 *
 * <p>Deliberately thin: the override stack, the contributed voices and the
 * listeners all live on the addon, because the studio and the audio path need
 * them too. Keeping a second copy of that state behind the API is how the two
 * would drift apart.</p>
 */
public final class VoiceChangerApiImpl implements VoiceChangerApi {
    private final VoiceChangerAddon addon;

    public VoiceChangerApiImpl(VoiceChangerAddon addon) {
        this.addon = addon;
    }

    @Override
    public boolean isEffectEnabled() {
        return this.addon.isEffectEnabled();
    }

    @Override
    public boolean isAllowed() {
        return this.addon.isAllowedByServer();
    }

    @Override
    public int getStrength() {
        return this.addon.getStrength();
    }

    @Override
    public String getSelectedVoiceId() {
        return this.addon.getSelectedVoiceId();
    }

    @Override
    public Optional<VoiceProfile> getActiveProfile() {
        ActiveVoice voice = this.addon.getActiveVoice();
        return voice.active() ? Optional.of(voice.profile()) : Optional.empty();
    }

    @Override
    public VoiceProfile getPlayerProfile() {
        return this.addon.getPlayerProfile();
    }

    @Override
    public VoiceOverride pushOverride(VoiceOverride.Request request) {
        return this.addon.api().overrides().push(request);
    }

    @Override
    public void releaseOverrides(String ownerId) {
        this.addon.api().overrides().releaseOwner(ownerId);
    }

    @Override
    public Optional<VoiceOverride> getActiveOverride() {
        return this.addon.api().overrides().winning();
    }

    @Override
    public void registerPreset(VoicePreset preset) {
        this.addon.api().contributedPresets().register(preset);
    }

    @Override
    public void unregisterPreset(String presetId) {
        this.addon.api().contributedPresets().unregister(presetId);
    }

    @Override
    public Optional<VoiceProfile> builtInProfile(String builtInId) {
        return VoiceChangerPreset.byKey(builtInId).map(VoiceChangerPreset::profile);
    }

    @Override
    public void addListener(VoiceChangerListener listener) {
        this.addon.api().listeners().add(listener);
    }

    @Override
    public void removeListener(VoiceChangerListener listener) {
        this.addon.api().listeners().remove(listener);
    }
}
