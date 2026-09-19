package org.sawiq.client.audio;

import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import org.sawiq.client.model.ActiveVoice;
import su.plo.voice.api.client.audio.device.InputDevice;

/**
 * Plays your own voice back to you while the studio is open.
 *
 * <p>Both paths capture from the microphone Plasmo Voice is configured to use.
 * A preview from some other device is worse than no preview, because it looks
 * like the mod is misbehaving.</p>
 *
 * <ul>
 *   <li><b>Own device.</b> Plasmo Voice only captures while it is connected to
 *       a voice server, so most of the time the studio has to open its own
 *       handle on the same microphone and run the chain itself. The handle is a
 *       separate device, not the one Plasmo Voice reads, so this never takes
 *       samples away from what is being transmitted.</li>
 *   <li><b>Shared stream.</b> When Plasmo Voice is capturing, the microphone
 *       filter has already processed the audio and published it to a
 *       {@link SelfListenBus}. Playing that back is exactly what listeners
 *       hear, and it must not be processed a second time.</li>
 * </ul>
 *
 * <p>Whichever path is used, the effect is applied exactly once, and not at all
 * while the voice changer is switched off.</p>
 */
public final class SelfListenMonitor {
    /** What the monitor asks for. The device may hand back a different layout. */
    public static final AudioFormat CAPTURE_FORMAT = new AudioFormat(48_000f, 16, 1, true, false);

    private static final AudioFormat PLAYBACK_FORMAT = new AudioFormat(48_000f, 16, 1, true, false);
    private static final int PLAYBACK_BUFFER_BYTES = 8_192;
    private static final long IDLE_SLEEP_MILLIS = 2L;
    private static final long SHARED_STREAM_TIMEOUT_MILLIS = 1_500L;

    /** Where the monitored audio is coming from. */
    public enum Mode {
        STOPPED,
        /** Reading the Plasmo Voice microphone through our own handle on it. */
        OWN_DEVICE,
        /** Playing back the microphone filter's output while Plasmo Voice captures. */
        SHARED_STREAM,
        /** Neither path could be established. */
        UNAVAILABLE
    }

    private final SelfListenBus bus;
    private final Supplier<InputDevice> deviceOpener;
    private final Supplier<ActiveVoice> activeVoice;
    private final Consumer<Double> levelReporter;
    private final Consumer<Exception> failureHandler;
    private final VoiceProcessor processor = new VoiceProcessor();

    private volatile int lastBlockFrames;
    private volatile int lastChannels;
    private volatile int blocksPerSecond;
    private int blocksThisSecond;
    private long secondStartedAt = System.currentTimeMillis();

    private Thread playbackThread;
    private volatile boolean running;
    private volatile Mode mode = Mode.STOPPED;

    public SelfListenMonitor(
            SelfListenBus bus,
            Supplier<InputDevice> deviceOpener,
            Supplier<ActiveVoice> activeVoice,
            Consumer<Double> levelReporter,
            Consumer<Exception> failureHandler
    ) {
        this.bus = bus;
        this.deviceOpener = deviceOpener;
        this.activeVoice = activeVoice;
        this.levelReporter = levelReporter;
        this.failureHandler = failureHandler;
    }

    public Mode mode() {
        return this.mode;
    }

    /**
     * What the preview chain is doing. While the studio is previewing this is
     * the chain the user is actually hearing, so it is what the studio should
     * report rather than the microphone filter, which is usually idle then.
     */
    public VoiceDiagnostics diagnostics() {
        if (!this.running) {
            return VoiceDiagnostics.IDLE;
        }

        return new VoiceDiagnostics(
                this.processor.speakerPitchHz(),
                this.processor.appliedPitchRatio(),
                this.lastBlockFrames,
                this.lastChannels,
                this.blocksPerSecond);
    }

    /** Counts blocks so the studio can show whether audio is arriving at all. */
    private void countBlock(int frames, int channels) {
        this.lastBlockFrames = frames;
        this.lastChannels = channels;
        this.blocksThisSecond++;

        long now = System.currentTimeMillis();
        if (now - this.secondStartedAt >= 1_000L) {
            this.blocksPerSecond = this.blocksThisSecond;
            this.blocksThisSecond = 0;
            this.secondStartedAt = now;
        }
    }

    public synchronized void start() {
        if (this.running) {
            return;
        }

        this.running = true;
        this.bus.open();
        this.playbackThread = new Thread(this::runPlayback, "pv-voice-changer-self-listen");
        this.playbackThread.setDaemon(true);
        this.playbackThread.start();
    }

    public synchronized void stop() {
        this.running = false;
        this.bus.close();
        this.mode = Mode.STOPPED;
        this.blocksPerSecond = 0;
        this.playbackThread = null;
    }

    /**
     * Owns every line and device it opens and releases them on the way out, so
     * a failure anywhere cannot leak an audio handle. Nothing else touches
     * them, which is what keeps stopping the monitor free of races.
     */
    private void runPlayback() {
        SourceDataLine output = null;
        InputDevice device = null;

        try {
            output = openPlayback();
            device = openOwnDevice();
            this.mode = device != null ? Mode.OWN_DEVICE : Mode.SHARED_STREAM;

            // The device decides its own layout: Plasmo Voice can be set to
            // capture in stereo, in which case a read returns interleaved
            // frames. Treating those as mono duplicates every sample, which
            // combs the spectrum and drops the pitch an octave.
            int channels = device != null ? Math.max(1, device.getFormat().getChannels()) : 1;
            int frameSize = device != null ? Math.max(1, device.getFrameSize()) : 960;
            int capacity = frameSize * channels;

            short[] captured = new short[capacity];
            short[] mono = new short[frameSize];
            byte[] playbackBytes = new byte[frameSize * 2];
            long lastAudioAt = System.currentTimeMillis();

            while (this.running) {
                int frames = device != null
                        ? readOwnDevice(device, captured, frameSize, channels)
                        : this.bus.read(mono);

                if (frames <= 0) {
                    if (device == null && System.currentTimeMillis() - lastAudioAt > SHARED_STREAM_TIMEOUT_MILLIS) {
                        this.mode = Mode.UNAVAILABLE;
                    }
                    Thread.sleep(IDLE_SLEEP_MILLIS);
                    continue;
                }

                lastAudioAt = System.currentTimeMillis();
                this.mode = device != null ? Mode.OWN_DEVICE : Mode.SHARED_STREAM;
                countBlock(frames, device != null ? channels : 1);

                if (device != null) {
                    this.levelReporter.accept(peakOf(captured, frames * channels));
                    ActiveVoice voice = this.activeVoice.get();
                    if (voice.active()) {
                        this.processor.process(captured, frames * channels, channels,
                                voice.profile(), voice.strength());
                    }
                    downmix(captured, mono, frames, channels);
                }

                writeSamples(output, mono, frames, playbackBytes);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            this.mode = Mode.UNAVAILABLE;
            this.failureHandler.accept(exception);
        } finally {
            closeQuietly(device);
            closeQuietly(output);
            this.running = false;
            this.mode = Mode.STOPPED;
        }
    }

    /**
     * @return a handle on the Plasmo Voice microphone, or {@code null} when it
     *         cannot be opened
     */
    private InputDevice openOwnDevice() {
        try {
            InputDevice device = this.deviceOpener.get();
            if (device == null) {
                return null;
            }
            device.start();
            return device;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** @return the number of frames read, not the number of samples */
    private int readOwnDevice(InputDevice device, short[] destination, int frameSize, int channels) {
        if (device.available() < frameSize) {
            return 0;
        }

        short[] block = device.read(frameSize);
        if (block == null || block.length == 0) {
            return 0;
        }

        int samples = Math.min(block.length, destination.length);
        System.arraycopy(block, 0, destination, 0, samples);
        return samples / channels;
    }

    /** Averages interleaved channels down to the mono playback line. */
    private static void downmix(short[] interleaved, short[] mono, int frames, int channels) {
        if (channels == 1) {
            System.arraycopy(interleaved, 0, mono, 0, frames);
            return;
        }

        for (int frame = 0; frame < frames; frame++) {
            int sum = 0;
            for (int channel = 0; channel < channels; channel++) {
                sum += interleaved[frame * channels + channel];
            }
            mono[frame] = (short) (sum / channels);
        }
    }

    private static double peakOf(short[] samples, int count) {
        int peak = 0;
        for (int i = 0; i < count; i++) {
            int magnitude = Math.abs(samples[i]);
            if (magnitude > peak) {
                peak = magnitude;
            }
        }
        return peak / 32_768.0D;
    }

    private static void writeSamples(SourceDataLine output, short[] samples, int count, byte[] scratch) {
        for (int i = 0; i < count; i++) {
            scratch[i * 2] = (byte) (samples[i] & 0xFF);
            scratch[i * 2 + 1] = (byte) ((samples[i] >>> 8) & 0xFF);
        }
        output.write(scratch, 0, count * 2);
    }

    private static SourceDataLine openPlayback() throws LineUnavailableException {
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(
                new DataLine.Info(SourceDataLine.class, PLAYBACK_FORMAT));
        line.open(PLAYBACK_FORMAT, PLAYBACK_BUFFER_BYTES);
        line.start();
        return line;
    }

    private static void closeQuietly(SourceDataLine line) {
        if (line == null) {
            return;
        }
        line.stop();
        line.flush();
        line.close();
    }

    private static void closeQuietly(InputDevice device) {
        if (device == null) {
            return;
        }
        try {
            device.stop();
            device.close();
        } catch (Exception ignored) {
            // Closing a device that already failed is not worth reporting.
        }
    }
}
