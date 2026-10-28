package org.sawiq.mixin.client;

import org.sawiq.client.VoiceChangerAddon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import su.plo.voice.api.client.audio.capture.ClientActivation;
import su.plo.voice.client.audio.capture.VoiceClientActivation;

/**
 * Lets the AI voice decide when an activation sends, see
 * {@link org.sawiq.client.audio.AiCaptureBridge}. Untouched while the AI voice
 * is off.
 *
 * <p>Plasmo Voice's own capture events cannot be used for the start of a
 * tick: its event bus only takes listeners from addons it loaded itself, and
 * this mod's client half is wired up by hand.</p>
 */
@Mixin(VoiceClientActivation.class)
public abstract class VoiceClientActivationMixin {
    /** The parent activation is asked first in every tick, and it alone without a parent result. */
    @Inject(method = "process", at = @At("HEAD"))
    private void pvvoicechanger$beginAiTick(
            short[] samples,
            ClientActivation.Result parentResult,
            CallbackInfoReturnable<ClientActivation.Result> callback
    ) {
        if (parentResult == null && samples != null) {
            VoiceChangerAddon.INSTANCE.onCaptureTick(samples);
        }
    }

    @Inject(method = "process", at = @At("RETURN"), cancellable = true)
    private void pvvoicechanger$routeThroughAiVoice(
            short[] samples,
            ClientActivation.Result parentResult,
            CallbackInfoReturnable<ClientActivation.Result> callback
    ) {
        ClientActivation activation = (ClientActivation) (Object) this;
        callback.setReturnValue(VoiceChangerAddon.INSTANCE.aiCaptureBridge()
                .translate(activation, callback.getReturnValue()));
    }
}
