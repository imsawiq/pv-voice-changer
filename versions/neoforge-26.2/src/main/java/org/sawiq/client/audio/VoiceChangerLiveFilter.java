package org.sawiq.client.audio;

import org.sawiq.client.VoiceChangerAddon;
import org.sawiq.client.model.VoiceChangerProfile;
import su.plo.voice.api.client.audio.filter.AudioFilter;
import su.plo.voice.api.client.audio.filter.AudioFilterContext;

public final class VoiceChangerLiveFilter implements AudioFilter {
    private final VoiceChangerAddon addon;
    private final VoiceChangerAudioEngine.VoiceChangerState monoState = new VoiceChangerAudioEngine.VoiceChangerState();
    private final VoiceChangerAudioEngine.VoiceChangerState stereoState = new VoiceChangerAudioEngine.VoiceChangerState();

    public VoiceChangerLiveFilter(VoiceChangerAddon addon) {
        this.addon = addon;
    }

    @Override
    public String getName() {
        return "pv-voice-changer";
    }

    @Override
    public short[] process(AudioFilterContext context, short[] samples) {
        if (!this.addon.isInitialized()) {
            return samples;
        }

        int channels = Math.max(1, context.getChannels());
        if (channels == 1) {
            channels = Math.max(1, context.getDevice().getFormat().getChannels());
        }

        if (!this.addon.isEffectEnabled()) {
            return samples;
        }

        VoiceChangerProfile profile = this.addon.getCurrentProfileSnapshot();
        int strength = this.addon.getStrengthEntry().value();
        VoiceChangerAudioEngine.VoiceChangerState state = channels > 1 ? this.stereoState : this.monoState;
        VoiceChangerAudioEngine.process(samples, channels, profile, strength, state);
        return samples;
    }

    @Override
    public boolean isEnabled() {
        return this.addon.isInitialized() && this.addon.isEffectEnabled();
    }
}
