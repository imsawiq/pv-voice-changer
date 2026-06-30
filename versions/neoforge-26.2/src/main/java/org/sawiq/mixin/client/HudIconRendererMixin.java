package org.sawiq.mixin.client;

import org.sawiq.client.VoiceChangerAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import su.plo.voice.api.client.audio.capture.ClientActivation;
import su.plo.voice.client.render.voice.HudIconRenderer;

@Mixin(HudIconRenderer.class)
public abstract class HudIconRendererMixin {
    @Redirect(
            method = "onRender",
            at = @At(
                    value = "INVOKE",
                    target = "Lsu/plo/voice/api/client/audio/capture/ClientActivation;getIcon()Ljava/lang/String;"
            )
    )
    private String pvvoicechanger$replaceActivationIcon(ClientActivation activation) {
        if (VoiceChangerAddon.INSTANCE.isInitialized() && VoiceChangerAddon.INSTANCE.isEffectEnabled()) {
            return "pv-voice-changer:micro.png";
        }

        return activation.getIcon();
    }
}
