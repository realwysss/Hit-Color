package dev.htcl.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.htcl.Hc;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelFeatureRenderer.class)
public abstract class MdlFtMxn {
	@Inject(method = "renderModel", at = @At("HEAD"))
	private <S> void a(SubmitNodeStorage.ModelSubmit<S> ms, RenderType rt, VertexConsumer vc, OutlineBufferSource ob, MultiBufferSource.BufferSource bs, CallbackInfo ci) {
		Hc.begin(ms, rt);
	}

	@Inject(method = "renderModel", at = @At("RETURN"))
	private <S> void b(SubmitNodeStorage.ModelSubmit<S> ms, RenderType rt, VertexConsumer vc, OutlineBufferSource ob, MultiBufferSource.BufferSource bs, CallbackInfo ci) {
		Hc.end();
	}

	@Redirect(method = "renderModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeStorage$ModelSubmit;overlayCoords()I"))
	private int ov(SubmitNodeStorage.ModelSubmit<?> ms) {
		return Hc.ov(ms.overlayCoords());
	}

	@Redirect(method = "renderModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeStorage$ModelSubmit;tintedColor()I"))
	private int tc(SubmitNodeStorage.ModelSubmit<?> ms) {
		return Hc.tc(ms.tintedColor());
	}
}
