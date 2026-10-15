package dev.htcl.mixin;

import dev.htcl.HcSt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class EntRdMxn {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void ext(LivingEntity e, LivingEntityRenderState st, float pt, CallbackInfo ci) {
		float p = e.deathTime > 0 || e.hurtTime <= 0 ? 1f : Math.max(0f, e.hurtTime - pt) / Math.max(1, e.hurtDuration);
		((HcSt) st).hcSet(p, e == Minecraft.getInstance().player);
	}
}
