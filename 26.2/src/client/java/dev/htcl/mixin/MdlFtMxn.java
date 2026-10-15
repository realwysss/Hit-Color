package dev.htcl.mixin;

import dev.htcl.Hc;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelFeatureRenderer.class)
public abstract class MdlFtMxn {
	@Inject(method = "prepareModel", at = @At("HEAD"))
	private <S> void a(ModelFeatureRenderer.Submit<S> ms, CallbackInfo ci) {
		Hc.begin(ms.state(), ms.renderType());
	}

	@Inject(method = "prepareModel", at = @At("RETURN"))
	private <S> void b(ModelFeatureRenderer.Submit<S> ms, CallbackInfo ci) {
		Hc.end();
	}

	@Redirect(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$Submit;overlayCoords()I"))
	private int ov(ModelFeatureRenderer.Submit<?> ms) {
		return Hc.ov(ms.overlayCoords());
	}

	@Redirect(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$Submit;tintedColor()I"))
	private int tc(ModelFeatureRenderer.Submit<?> ms) {
		return Hc.tc(ms.tintedColor());
	}
}
