package org.sawiq.client.model;

public record VoiceChangerProfile(
        double mix,
        double gain,
        double pitch,
        double formant,
        double distortion,
        double robotMix,
        int robotFrequency,
        double echoMix,
        int echoDelayMs,
        double echoFeedback,
        double tremoloDepth,
        double tremoloRate,
        double lowEq,
        double midEq,
        double highEq,
        double noise
) {
    public static VoiceChangerProfile defaultsFor(VoiceChangerPreset preset) {
        return switch (preset) {
            case MAN -> new VoiceChangerProfile(0.80D, 1.08D, 0.92D, 0.92D, 0.00D, 0.00D, 40, 0.04D, 90, 0.08D, 0.00D, 2.20D, 2.2D, 0.0D, -1.2D, 0.00D);
            case WOMAN -> new VoiceChangerProfile(0.78D, 1.00D, 1.12D, 1.18D, 0.00D, 0.00D, 65, 0.03D, 84, 0.05D, 0.00D, 2.00D, -2.4D, 1.2D, 2.8D, 0.00D);
            case TITAN -> new VoiceChangerProfile(0.86D, 1.18D, 0.72D, 0.78D, 0.10D, 0.00D, 30, 0.28D, 170, 0.36D, 0.00D, 1.20D, 5.6D, -2.2D, -3.8D, 0.00D);
            case KID -> new VoiceChangerProfile(0.80D, 1.00D, 1.32D, 1.42D, 0.00D, 0.00D, 82, 0.00D, 70, 0.00D, 0.00D, 2.20D, -4.4D, 1.9D, 4.2D, 0.00D);
            case DEMON -> new VoiceChangerProfile(0.86D, 1.14D, 0.84D, 0.72D, 0.14D, 0.08D, 46, 0.24D, 155, 0.26D, 0.03D, 1.40D, 4.2D, -1.6D, -4.4D, 0.00D);
            case RADIO -> new VoiceChangerProfile(0.96D, 0.94D, 1.00D, 1.00D, 0.06D, 0.00D, 98, 0.00D, 70, 0.00D, 0.03D, 2.10D, -9.5D, 5.4D, -8.4D, 0.03D);
            case CUSTOM -> new VoiceChangerProfile(0.90D, 1.00D, 1.00D, 1.00D, 0.00D, 0.00D, 60, 0.00D, 120, 0.10D, 0.00D, 2.50D, 0.0D, 0.0D, 0.0D, 0.00D);
        };
    }
}
