package org.sawiq.client.audio;

import org.sawiq.client.VoiceChangerAddon;
import org.sawiq.client.model.ActiveVoice;
import su.plo.voice.api.client.audio.filter.AudioFilter;
import su.plo.voice.api.client.audio.filter.AudioFilterContext;

/**
 * The Plasmo Voice microphone filter: the one place where the effect is
 * applied to audio that actually leaves the machine.
 *
 * <p>The processed block is also published to the {@link SelfListenBus}, so
 * the studio preview can monitor precisely what listeners receive rather than
 * re-capturing from a second device.</p>
 */
public final class VoiceChangerLiveFilter implements AudioFilter {
    private final VoiceChangerAddon addon;
    private final SelfListenBus selfListenBus;
    private final VoiceProcessor processor = new VoiceProcessor();

    private int lastChannelCount;
    private boolean wasEnabled;

    private volatile int lastBlockFrames;
    private volatile int blocksPerSecond;
    private int blocksThisSecond;
    private volatile long secondStartedAt = System.currentTimeMillis();

    public VoiceChangerLiveFilter(VoiceChangerAddon addon, SelfListenBus selfListenBus) {
        this.addon = addon;
        this.selfListenBus = selfListenBus;
    }

    @Override
    public String getName() {
        return "pv-voice-changer";
    }

    @Override
    public short[] process(AudioFilterContext context, short[] samples) {
        if (!this.addon.isInitialized()) {
            this.wasEnabled = false;
            return samples;
        }

        int channels = resolveChannelCount(context);
        countBlock(samples.length / Math.max(1, channels));

        ActiveVoice voice = this.addon.getActiveVoice();
        if (!voice.active()) {
            // Still measure, so the studio's level meter works while the
            // effect is off. Otherwise someone opening the studio to set up a
            // voice sees a dead meter and concludes their microphone is broken.
            this.addon.reportInputLevel(peakOf(samples));
            publishForMonitoring(samples, channels);
            this.wasEnabled = false;
            return samples;
        }

        // Filter memory, delay lines and vocoder phase all describe a
        // continuous stream. Carrying them across a gap, or across a channel
        // layout change, plays back stale audio as a click.
        if (!this.wasEnabled || channels != this.lastChannelCount) {
            this.processor.reset();
            this.wasEnabled = true;
            this.lastChannelCount = channels;
        }

        this.processor.process(samples, channels, voice.profile(), voice.strength());
        this.addon.reportInputLevel(this.processor.takePeakLevel());

        publishForMonitoring(samples, channels);
        return samples;
    }

    /**
     * Feeds the self-listen monitor. Done on the disabled path too, so turning
     * the effect off while monitoring keeps playing back the Plasmo Voice
     * microphone rather than dropping the monitor onto the system default.
     */
    private void publishForMonitoring(short[] samples, int channels) {
        if (this.selfListenBus.isActive()) {
            this.selfListenBus.write(samples, channels);
        }
    }

    /**
     * A snapshot of what the microphone filter is actually seeing and doing.
     * The studio shows it, which is the only way to tell a chain that is not
     * running apart from one that is running and inaudible.
     */
    public VoiceDiagnostics diagnostics() {
        return new VoiceDiagnostics(
                this.processor.speakerPitchHz(),
                this.processor.appliedPitchRatio(),
                this.lastBlockFrames,
                this.lastChannelCount,
                recentBlocksPerSecond());
    }

    /**
     * The count only moves when a block arrives, so once blocks stop coming it
     * would go on showing the last busy second. Push-to-talk stops them every
     * time the key is let go, and the studio then claimed audio was flowing.
     */
    private int recentBlocksPerSecond() {
        return System.currentTimeMillis() - this.secondStartedAt > 2_000L ? 0 : this.blocksPerSecond;
    }

    /** Counts blocks so the studio can show whether audio is arriving at all. */
    private void countBlock(int frames) {
        this.lastBlockFrames = frames;
        this.blocksThisSecond++;

        long now = System.currentTimeMillis();
        if (now - this.secondStartedAt >= 1_000L) {
            this.blocksPerSecond = this.blocksThisSecond;
            this.blocksThisSecond = 0;
            this.secondStartedAt = now;
        }
    }

    /** Discards filter state so the next block starts a fresh stream. */
    public void resetStream() {
        this.wasEnabled = false;
    }

    /**
     * Always on once the addon is up, so the level meter keeps reading even
     * when the effect itself is off. A disabled effect costs one pass over the
     * block to measure its peak and nothing else.
     */
    @Override
    public boolean isEnabled() {
        return this.addon.isInitialized();
    }

    private static double peakOf(short[] samples) {
        int peak = 0;
        for (short sample : samples) {
            int magnitude = Math.abs(sample);
            if (magnitude > peak) {
                peak = magnitude;
            }
        }
        return peak / 32_768.0D;
    }

    /**
     * The context reports one channel for some device layouts even when the
     * device itself is stereo, so the device format is the more reliable
     * source once the context says mono.
     */
    private static int resolveChannelCount(AudioFilterContext context) {
        int channels = Math.max(1, context.getChannels());
        if (channels > 1) {
            return channels;
        }
        return Math.max(1, context.getDevice().getFormat().getChannels());
    }
}
