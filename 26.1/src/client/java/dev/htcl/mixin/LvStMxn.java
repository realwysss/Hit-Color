package dev.htcl.mixin;

import dev.htcl.HcSt;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class LvStMxn implements HcSt {
	@Unique
	private float hcp = 1f;
	@Unique
	private boolean hcs;

	@Override
	public float hcP() {
		return hcp;
	}

	@Override
	public boolean hcSelf() {
		return hcs;
	}

	@Override
	public void hcSet(float p, boolean s) {
		hcp = p;
		hcs = s;
	}
}
