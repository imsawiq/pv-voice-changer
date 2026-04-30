package org.sawiq.client.audio;

import org.sawiq.client.model.VoiceChangerProfile;

/**
 * Real-time autotune: detects the fundamental frequency with a YIN-style
 * autocorrelation, snaps it to the nearest note in the chosen scale, and
 * applies a continuously updated PSOLA-style pitch shift to drive the
 * detected pitch toward that target. Mono and stereo inputs are processed
 * with independent state per channel.
 */
final class VoiceChangerAutotune {
    private static final double SAMPLE_RATE = 48_000.0D;

    static final int FRAME_SIZE = 1024;
    static final int HOP_SIZE = 256;
    static final int MIN_PERIOD = 50;   // ~960 Hz
    static final int MAX_PERIOD = 600;  // ~80 Hz
    static final int PSOLA_BUFFER = 4096;
    static final int PSOLA_DELAY = 1440;
    static final int PSOLA_WINDOW = 960;

    private static final double YIN_THRESHOLD = 0.15D;
    private static final double MIN_RATIO = 0.50D;
    private static final double MAX_RATIO = 2.00D;
    private static final double SILENCE_RMS = 0.0025D;

    private static final int[] SCALE_CHROMATIC = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    private static final int[] SCALE_MAJOR = {0, 2, 4, 5, 7, 9, 11};
    private static final int[] SCALE_MINOR = {0, 2, 3, 5, 7, 8, 10};

    private VoiceChangerAutotune() {
    }

    static void process(short[] samples, int channels, VoiceChangerProfile profile, double amount, AutotuneState state) {
        double mix = clamp01(profile.autotuneMix() * amount);
        if (mix <= 0.005D || samples.length == 0) {
            return;
        }

        state.prepare(channels);
        int[] scale = scaleFor(profile.autotuneScale());
        int rootPc = ((profile.autotuneKey() % 12) + 12) % 12;
        double snapAlpha = 0.005D + clamp01(profile.autotuneStrength()) * 0.45D;

        for (int i = 0; i < samples.length; i++) {
            int channel = i % channels;
            double dry = samples[i] / 32768.0D;

            // feed analysis ring buffer
            float[] analysis = state.analysis[channel];
            int aw = state.analysisWrite[channel];
            analysis[aw] = (float) dry;
            state.analysisWrite[channel] = (aw + 1) % analysis.length;
            state.hopCounter[channel]++;

            if (state.hopCounter[channel] >= HOP_SIZE) {
                state.hopCounter[channel] = 0;
                double detected = detectPitch(analysis, state.analysisWrite[channel], state.yinDiff[channel]);
                if (detected > 0.0D) {
                    double targetHz = snapToScale(detected, rootPc, scale);
                    double rawRatio = clamp(targetHz / detected, MIN_RATIO, MAX_RATIO);
                    state.targetRatio[channel] = rawRatio;
                    state.hasTarget[channel] = true;
                } else {
                    state.targetRatio[channel] = 1.0D;
                }
            }

            double target = state.hasTarget[channel] ? state.targetRatio[channel] : 1.0D;
            state.smoothedRatio[channel] += snapAlpha * (target - state.smoothedRatio[channel]);
            double ratio = clamp(state.smoothedRatio[channel], MIN_RATIO, MAX_RATIO);

            // PSOLA-style dual-grain shifter driven by the dynamic ratio
            float[] grainBuf = state.grainBuffer[channel];
            int gw = state.grainWrite[channel];
            grainBuf[gw] = (float) dry;

            double phaseA = wrap01(state.grainPhase[channel]);
            double phaseB = wrap01(phaseA + 0.5D);
            double delayA = PSOLA_DELAY + phaseA * PSOLA_WINDOW;
            double delayB = PSOLA_DELAY + phaseB * PSOLA_WINDOW;

            double sampleA = readDelayed(grainBuf, gw, delayA);
            double sampleB = readDelayed(grainBuf, gw, delayB);
            double envA = triangleWindow(phaseA);
            double envB = triangleWindow(phaseB);
            double envSum = Math.max(0.0001D, envA + envB);
            double shifted = (sampleA * envA + sampleB * envB) / envSum;

            state.grainWrite[channel] = (gw + 1) % grainBuf.length;
            state.grainPhase[channel] = wrap01(phaseA + (1.0D - ratio) / PSOLA_WINDOW);

            // gate output to dry on near-silence to avoid hiss artefacts
            double rms = state.rms[channel];
            rms = rms * 0.995D + dry * dry * 0.005D;
            state.rms[channel] = rms;
            double gate = Math.sqrt(rms) < SILENCE_RMS ? 0.0D : 1.0D;

            double wet = clamp(shifted, -1.0D, 1.0D);
            double blended = dry * (1.0D - mix * gate) + wet * (mix * gate);
            samples[i] = (short) Math.round(clamp(blended, -1.0D, 1.0D) * 32767.0D);
        }
    }

    private static int[] scaleFor(int scaleId) {
        return switch (scaleId) {
            case VoiceChangerProfile.SCALE_MAJOR -> SCALE_MAJOR;
            case VoiceChangerProfile.SCALE_MINOR -> SCALE_MINOR;
            default -> SCALE_CHROMATIC;
        };
    }

    private static double snapToScale(double freqHz, int rootPc, int[] scale) {
        if (freqHz <= 0.0D) {
            return freqHz;
        }
        double midi = 69.0D + 12.0D * (Math.log(freqHz / 440.0D) / Math.log(2.0D));
        int baseMidi = (int) Math.floor(midi);
        double bestDelta = Double.MAX_VALUE;
        int bestMidi = baseMidi;
        for (int octaveOffset = -1; octaveOffset <= 1; octaveOffset++) {
            for (int degree : scale) {
                int candidatePc = (rootPc + degree) % 12;
                int candidate = nearestMidiWithPc(baseMidi + octaveOffset * 12, candidatePc);
                double delta = Math.abs(midi - candidate);
                if (delta < bestDelta) {
                    bestDelta = delta;
                    bestMidi = candidate;
                }
            }
        }
        return 440.0D * Math.pow(2.0D, (bestMidi - 69) / 12.0D);
    }

    private static int nearestMidiWithPc(int aroundMidi, int pc) {
        int aroundPc = ((aroundMidi % 12) + 12) % 12;
        int diff = pc - aroundPc;
        if (diff > 6) {
            diff -= 12;
        } else if (diff < -6) {
            diff += 12;
        }
        return aroundMidi + diff;
    }

    /**
     * YIN-style fundamental frequency detector over the latest FRAME_SIZE
     * samples in the ring buffer. Returns 0 when the signal is too quiet
     * or no clear period is found.
     */
    private static double detectPitch(float[] ring, int writeCursor, float[] diffBuffer) {
        int n = ring.length;
        // pre-compute RMS on the analysis window to suppress silence
        double energy = 0.0D;
        for (int i = 0; i < FRAME_SIZE; i++) {
            int idx = (writeCursor - FRAME_SIZE + i + n) % n;
            float v = ring[idx];
            energy += v * v;
        }
        double rms = Math.sqrt(energy / FRAME_SIZE);
        if (rms < SILENCE_RMS) {
            return 0.0D;
        }

        int maxTau = Math.min(MAX_PERIOD, FRAME_SIZE / 2);
        // difference function
        for (int tau = 0; tau <= maxTau; tau++) {
            double sum = 0.0D;
            for (int i = 0; i < FRAME_SIZE - tau; i++) {
                int aIdx = (writeCursor - FRAME_SIZE + i + n) % n;
                int bIdx = (writeCursor - FRAME_SIZE + i + tau + n) % n;
                double delta = ring[aIdx] - ring[bIdx];
                sum += delta * delta;
            }
            diffBuffer[tau] = (float) sum;
        }
        // cumulative mean normalized difference
        diffBuffer[0] = 1.0F;
        double running = 0.0D;
        for (int tau = 1; tau <= maxTau; tau++) {
            running += diffBuffer[tau];
            diffBuffer[tau] = (float) (diffBuffer[tau] * tau / (running > 1e-9 ? running : 1e-9));
        }
        // absolute threshold
        int chosen = -1;
        for (int tau = MIN_PERIOD; tau <= maxTau; tau++) {
            if (diffBuffer[tau] < YIN_THRESHOLD) {
                while (tau + 1 <= maxTau && diffBuffer[tau + 1] < diffBuffer[tau]) {
                    tau++;
                }
                chosen = tau;
                break;
            }
        }
        if (chosen < 0) {
            // fallback: take global minimum within voice range
            double best = Double.MAX_VALUE;
            for (int tau = MIN_PERIOD; tau <= maxTau; tau++) {
                if (diffBuffer[tau] < best) {
                    best = diffBuffer[tau];
                    chosen = tau;
                }
            }
            if (chosen < 0 || best > 0.6D) {
                return 0.0D;
            }
        }
        // parabolic interpolation around chosen
        double refined = chosen;
        if (chosen > MIN_PERIOD && chosen < maxTau) {
            double s0 = diffBuffer[chosen - 1];
            double s1 = diffBuffer[chosen];
            double s2 = diffBuffer[chosen + 1];
            double denom = (s0 + s2 - 2.0D * s1);
            if (Math.abs(denom) > 1e-9) {
                refined = chosen + 0.5D * (s0 - s2) / denom;
            }
        }
        if (refined <= 0.0D) {
            return 0.0D;
        }
        return SAMPLE_RATE / refined;
    }

    private static double readDelayed(float[] buffer, int write, double delaySamples) {
        double readIndex = write - delaySamples;
        while (readIndex < 0.0D) {
            readIndex += buffer.length;
        }
        int i0 = floorMod((int) Math.floor(readIndex), buffer.length);
        int i1 = floorMod(i0 + 1, buffer.length);
        double delta = readIndex - Math.floor(readIndex);
        return buffer[i0] + (buffer[i1] - buffer[i0]) * delta;
    }

    private static double triangleWindow(double phase) {
        return 1.0D - Math.abs(phase * 2.0D - 1.0D);
    }

    private static double wrap01(double value) {
        double v = value;
        while (v >= 1.0D) v -= 1.0D;
        while (v < 0.0D) v += 1.0D;
        return v;
    }

    private static int floorMod(int value, int mod) {
        int r = value % mod;
        return r < 0 ? r + mod : r;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0D, 1.0D);
    }

    static final class AutotuneState {
        float[][] analysis = new float[1][FRAME_SIZE];
        float[][] yinDiff = new float[1][MAX_PERIOD + 1];
        int[] analysisWrite = new int[1];
        int[] hopCounter = new int[1];
        double[] targetRatio = new double[]{1.0D};
        double[] smoothedRatio = new double[]{1.0D};
        boolean[] hasTarget = new boolean[1];
        double[] rms = new double[1];
        float[][] grainBuffer = new float[1][PSOLA_BUFFER];
        int[] grainWrite = new int[1];
        double[] grainPhase = new double[1];

        void prepare(int channels) {
            if (this.analysis.length == channels) {
                return;
            }
            this.analysis = resize2D(this.analysis, channels, FRAME_SIZE);
            this.yinDiff = resize2D(this.yinDiff, channels, MAX_PERIOD + 1);
            this.grainBuffer = resize2D(this.grainBuffer, channels, PSOLA_BUFFER);
            this.analysisWrite = resizeInt(this.analysisWrite, channels);
            this.hopCounter = resizeInt(this.hopCounter, channels);
            this.grainWrite = resizeInt(this.grainWrite, channels);
            this.targetRatio = resizeDouble(this.targetRatio, channels, 1.0D);
            this.smoothedRatio = resizeDouble(this.smoothedRatio, channels, 1.0D);
            this.grainPhase = resizeDouble(this.grainPhase, channels, 0.0D);
            this.rms = resizeDouble(this.rms, channels, 0.0D);
            boolean[] flags = new boolean[channels];
            for (int i = 0; i < Math.min(channels, this.hasTarget.length); i++) {
                flags[i] = this.hasTarget[i];
            }
            this.hasTarget = flags;
        }

        private static float[][] resize2D(float[][] arr, int channels, int innerLen) {
            float[][] r = new float[channels][];
            for (int i = 0; i < channels; i++) {
                if (i < arr.length && arr[i] != null && arr[i].length == innerLen) {
                    r[i] = arr[i];
                } else {
                    r[i] = new float[innerLen];
                }
            }
            return r;
        }

        private static int[] resizeInt(int[] arr, int size) {
            int[] r = new int[size];
            System.arraycopy(arr, 0, r, 0, Math.min(arr.length, size));
            return r;
        }

        private static double[] resizeDouble(double[] arr, int size, double fill) {
            double[] r = new double[size];
            for (int i = 0; i < size; i++) {
                r[i] = i < arr.length ? arr[i] : fill;
            }
            return r;
        }
    }
}
