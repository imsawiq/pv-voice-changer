package org.sawiq.client.audio;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.sawiq.client.ai.audio.AudioFrames;
import org.sawiq.client.ai.runtime.AiVoiceRuntime;
import org.sawiq.client.ai.runtime.AiVoiceStream;
import su.plo.voice.api.client.audio.capture.ClientActivation;

/**
 * Fits the AI voice into Plasmo Voice's capture loop.
 *
 * <p>Plasmo Voice decides per activation and per 20 ms tick whether to send,
 * from the push-to-talk key or the voice level, and only then runs the
 * microphone filters on what it sends. An AI voice needs that decision to be
 * its own: it lags behind the microphone, keeps speaking after the key is let
 * go and, for phrase engines, sends nothing while the player talks. So:</p>
 * <ol>
 *     <li>at the start of each tick ({@link #beginTick}) the runtime gets the
 *     raw frame, if the player was talking, and says what to send this tick;</li>
 *     <li>each activation's own decision is then rewritten ({@link #translate})
 *     to send exactly when the runtime has a frame;</li>
 *     <li>the microphone filter swaps the frame in ({@link #takeOutput}).</li>
 * </ol>
 *
 * <p>Whether the player is talking is read from the previous tick's
 * decisions, since this tick's are made after the frame has to be handed
 * over. The 20 ms this costs is lost in the engine's own delay.</p>
 *
 * <p>All of this runs on Plasmo Voice's capture thread.</p>
 */
public final class AiCaptureBridge {
    private final AiVoiceRuntime runtime;

    private final Set<UUID> speakingActivations = new HashSet<>();
    private final Set<UUID> sendingActivations = new HashSet<>();
    private boolean talkingLastTick;
    private boolean talkingThisTick;
    private short[] output;
    private boolean bypassed;
    private volatile long lastTickNanos;

    /** Ticks come every 20 ms; a filter call this long after the last one belongs to no tick. */
    private static final long TICK_STALE_NANOS = 60_000_000L;

    private final UnaryOperator<short[]> preview;

    /** @param preview converts a frame for a preview; returns {@code null} for silence */
    public AiCaptureBridge(AiVoiceRuntime runtime, UnaryOperator<short[]> preview) {
        this.runtime = runtime;
        this.preview = preview;
    }

    /**
     * Start of a capture tick, before any activation has decided anything.
     * Plasmo Voice asks the parent activation first, with no parent result,
     * which is what marks the start of a tick from inside the activation.
     */
    public void beginTick(short[] samples) {
        this.lastTickNanos = System.nanoTime();
        this.talkingLastTick = this.talkingThisTick;
        this.talkingThisTick = false;

        // The engines work at 48 kHz in 20 ms frames, which is Plasmo Voice's
        // default, so a frame is 960 samples per channel. A server configured
        // for another rate gets the effects only.
        int channels = samples.length / AudioFrames.FRAME_SAMPLES;
        this.bypassed = !this.runtime.isActive()
                || samples.length % AudioFrames.FRAME_SAMPLES != 0
                || channels < 1 || channels > 2;
        if (this.bypassed) {
            this.output = null;
            this.speakingActivations.clear();
            this.sendingActivations.clear();
            return;
        }

        short[] raw = this.talkingLastTick ? AudioFrames.toMono(samples, channels) : null;
        this.output = this.runtime.onFrame(raw);

        if (this.output == null && !this.talkingLastTick && this.runtime.activity() == AiVoiceStream.Activity.IDLE) {
            this.speakingActivations.clear();
        }
    }

    /**
     * Rewrites one activation's decision for this tick.
     *
     * <p>Inheriting activations are left alone: they follow their parent's
     * already rewritten result. Everything else sends while the runtime has
     * audio, if it is the activation the player is using or was using when
     * the audio was spoken, and ends its stream as soon as the audio stops.</p>
     */
    public ClientActivation.Result translate(ClientActivation activation, ClientActivation.Result natural) {
        if (this.bypassed || activation.getType() == ClientActivation.Type.INHERIT) {
            return natural;
        }

        UUID id = activation.getId();
        if (natural == ClientActivation.Result.ACTIVATED) {
            this.talkingThisTick = true;
            this.speakingActivations.add(id);
        }

        if (this.output != null && this.speakingActivations.contains(id)) {
            this.sendingActivations.add(id);
            return ClientActivation.Result.ACTIVATED;
        }
        if (this.sendingActivations.remove(id)) {
            return ClientActivation.Result.END;
        }
        return ClientActivation.Result.NOT_ACTIVATED;
    }

    /**
     * The frame to send this tick, written into every channel of
     * {@code samples}. Silence when an activation is closing its stream with
     * nothing left to say.
     *
     * @return false when the AI voice is not in charge of this tick
     */
    public boolean takeOutput(short[] samples, int channels) {
        if (!this.runtime.isActive()) {
            return false;
        }
        if (System.nanoTime() - this.lastTickNanos > TICK_STALE_NANOS) {
            return takePreview(samples, channels);
        }
        if (this.bypassed) {
            return false;
        }

        short[] frame = this.output != null ? this.output : AudioFrames.silence();
        AudioFrames.copyToAllChannels(frame, samples, channels);
        return true;
    }

    /**
     * The filter runs without a capture tick when nothing is being sent:
     * Plasmo Voice's own microphone test stops the activations and plays the
     * filtered microphone back. That is a preview, so it gets the preview
     * stream's AI voice instead of the stale frame of the last real tick.
     */
    private boolean takePreview(short[] samples, int channels) {
        if (samples.length != AudioFrames.FRAME_SAMPLES * channels) {
            return false;
        }
        short[] converted = this.preview.apply(AudioFrames.toMono(samples, channels));
        AudioFrames.copyToAllChannels(converted != null ? converted : AudioFrames.silence(), samples, channels);
        return true;
    }
}
