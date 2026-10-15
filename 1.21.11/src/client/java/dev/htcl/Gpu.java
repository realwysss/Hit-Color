package dev.htcl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

public final class Gpu {
    public static final int NONE = 0;
    public static final int PRE = 1;
    private static final RenderPipeline[] UI = new RenderPipeline[5];
    private static Tex screenTex;
    private static Tex wrapped;

    public static final class Tex {
        private final GpuTexture texture;
        private final GpuTextureView view;

        Tex(GpuTexture texture, GpuTextureView view) {
            this.texture = texture;
            this.view = view;
        }

        public GpuTextureView view() {
            return view;
        }

        public int width() {
            return view.texture().getWidth(0);
        }

        public int height() {
            return view.texture().getHeight(0);
        }

        public void close() {
            if (texture == null) return;
            view.close();
            texture.close();
        }
    }

    public static final class Buf {
        private final GpuBuffer buffer;

        Buf(GpuBuffer buffer) {
            this.buffer = buffer;
        }
    }

    public static final class Pipe {
        private final RenderPipeline pipeline;

        Pipe(RenderPipeline pipeline) {
            this.pipeline = pipeline;
        }

        public RenderPipeline get() {
            return pipeline;
        }
    }

    private Gpu() {
    }
    public static Tex screen() {
        GpuTextureView v = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        if (screenTex == null || screenTex.view != v) screenTex = new Tex(null, v);
        return screenTex;
    }

    public static Tex wrap(GpuTextureView v) {
        if (wrapped == null || wrapped.view != v) wrapped = new Tex(null, v);
        return wrapped;
    }

    public static boolean redirected() {
        return RenderSystem.outputColorTextureOverride != null;
    }

    public static Pipe post(String frag, String block, int blend, boolean masked) {
        RenderPipeline.Builder b = RenderPipeline.builder()
            .withLocation(Glow.id("pipeline/" + frag + "_" + blend))
            .withVertexShader(Glow.id("core/quad"))
            .withFragmentShader(Glow.id("core/" + frag))
            .withSampler("InSampler")
            .withUniform(block, UniformType.UNIFORM_BUFFER)
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES);
        if (masked) b.withSampler("MaskSampler");
        blend(b, blend);
        return new Pipe(b.build());
    }

    public static RenderPipeline ui(int mode) {
        if (UI[mode] == null) {
            UI[mode] = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Glow.id("pipeline/ui_" + mode))
                .withVertexShader(Glow.id("core/ui"))
                .withFragmentShader(Glow.id("core/ui"))
                .withShaderDefine("MODE", mode)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .build();
        }
        return UI[mode];
    }
    private static void blend(RenderPipeline.Builder b, int blend) {
        if (blend == NONE) {
            b.withoutBlend();
        } else {
            b.withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA).withColorWrite(true, false);
        }
    }
    public static Tex texture(String label, int w, int h) {
        GpuDevice device = RenderSystem.getDevice();
        GpuTexture t = device.createTexture(label, 15, TextureFormat.RGBA8, w, h, 1, 1);
        return new Tex(t, device.createTextureView(t));
    }

    public static void draw(Pipe p, Tex out, String block, Buf ubo, Tex in, Tex mask) {
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "hitcolor", out.view, OptionalInt.empty())) {
            pass.setPipeline(p.pipeline);
            pass.setUniform(block, ubo.buffer);
            pass.bindTexture("InSampler", in.view, linear());
            if (mask != null) pass.bindTexture("MaskSampler", mask.view, nearest());
            pass.draw(0, 3);
        }
    }

    public static Buf ubo(String label, int size) {
        return new Buf(RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, size));
    }

    public static void write(Buf ubo, float... values) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.malloc(values.length * 4);
            for (float v : values) data.putFloat(v);
            data.flip();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(ubo.buffer.slice(), data);
        }
    }

    public static int guiScale() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }

    public static GpuSampler linear() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    public static GpuSampler nearest() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }
}
