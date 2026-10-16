package dev.htcl.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.htcl.Hc;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ModelPart.Cube.class)
public abstract class CubeMxn {
	@Redirect(method = "compile", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;addVertex(FFFIFFIIFFF)V"))
	private void av(VertexConsumer vc, float x, float y, float z, int color, float u, float v, int ov, int light, float nx, float ny, float nz) {
		vc.addVertex(x, y, z, Hc.col(color, y), u, v, Hc.ovv(ov, y), Hc.lit(light), nx, ny, nz);
	}
}
