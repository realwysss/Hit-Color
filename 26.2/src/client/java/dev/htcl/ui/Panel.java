package dev.htcl.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.htcl.Frost;
import dev.htcl.Gpu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

public final class Panel extends Screen implements Canvas {
    private final Screen parent;
    private final Menu menu;
    private GuiGraphicsExtractor g;

    public Panel(Screen parent) {
        super(Component.literal("Hit Color"));
        this.parent = parent;
        this.menu = new Menu(this::back, () -> minecraft == null ? null : minecraft.keyboardHandler.getClipboard());
    }

    private void back() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) extractPanorama(g, delta);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        this.g = g;
        menu.draw(this, width, height, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        return menu.click(e.x(), e.y(), e.button()) || super.mouseClicked(e, twice);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        return menu.release(e.x(), e.y(), e.button()) || super.mouseReleased(e);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        return menu.dragged(e.x(), e.y()) || super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        return menu.scroll(x, y, sy != 0 ? sy : sx) || super.mouseScrolled(x, y, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (dev.htcl.HtCl.key != null && dev.htcl.HtCl.key.matches(e) && !menu.typing()) {
            onClose();
            return true;
        }
        int action = switch (e.key()) {
            case InputConstants.KEY_BACKSPACE -> Menu.BACK;
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> Menu.ENTER;
            case InputConstants.KEY_ESCAPE -> Menu.ESCAPE;
            case InputConstants.KEY_TAB -> Menu.TAB;
            case InputConstants.KEY_V -> e.hasControlDown() ? Menu.PASTE : 0;
            case InputConstants.KEY_C -> e.hasControlDown() ? Menu.COPY : 0;
            default -> 0;
        };
        if (action != 0 && menu.key(action)) return true;
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        return menu.type(e.codepoint()) || super.charTyped(e);
    }

    @Override
    public void onClose() {
        menu.save();
        back();
    }

    @Override
    public void fill(float x, float y, float w, float h, float r, int top, int bottom) {
        quad(Gpu.ui(0), x, y, w, h, code(r, h), 0, top, bottom);
    }

    @Override
    public void ring(float x, float y, float w, float h, float r, float t, int top, int bottom) {
        quad(Gpu.ui(1), x, y, w, h, code(r, h), code(t, h), top, bottom);
    }

    @Override
    public void shadow(float x, float y, float w, float h, float r, float blur, int argb) {
        float eh = h + blur * 2;
        quad(Gpu.ui(2), x - blur, y - blur, w + blur * 2, eh, code(r, eh), code(blur, eh), argb, argb);
    }

    @Override
    public void hue(float x, float y, float w, float h, float r, int argb) {
        quad(Gpu.ui(3), x, y, w, h, code(r, h), 0, argb, argb);
    }

    @Override
    public void sv(float x, float y, float w, float h, float r, int argb) {
        quad(Gpu.ui(4), x, y, w, h, code(r, h), 0, argb, argb);
    }

    @Override
    public void text(String s, float x, float y, int argb) {
        if ((argb >>> 24) < 4) return;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.text(font, s, 0, 0, argb, false);
        g.pose().popMatrix();
    }

    @Override
    public int width(String s) {
        return font.width(s);
    }

    @Override
    public void item(String id, float x, float y, float size) {
        net.minecraft.world.item.Item it = id.equals("anchor") ? net.minecraft.world.item.Items.RESPAWN_ANCHOR : net.minecraft.world.item.Items.END_CRYSTAL;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 16f, size / 16f);
        g.item(new net.minecraft.world.item.ItemStack(it), 0, 0);
        g.pose().popMatrix();
    }

    @Override
    public boolean player(float x, float y, float w, float h) {
        if (minecraft.player == null) return false;
        net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(g, (int) x, (int) y, (int) (x + w), (int) (y + h), (int) (h * 0.42f), 0.0625f, x + w / 2f, y + h * 0.3f, minecraft.player);
        return true;
    }

    @Override
    public void hurt() {
        if (minecraft.player != null) {
            minecraft.player.hurtDuration = 10;
            minecraft.player.hurtTime = 10;
        }
    }

    private void quad(RenderPipeline p, float x, float y, float w, float h, int cu, int cv, int top, int bottom) {
        if (w <= 0 || h <= 0 || ((top | bottom) >>> 24) == 0) return;
        element(p, TextureSetup.noTexture(), x, y, w, h, 4 * cu + 1, 4 * cu + 3, 4 * cv + 1, 4 * cv + 3, top, bottom);
    }

    private void element(RenderPipeline p, TextureSetup t, float x, float y, float w, float h, float u0, float u1, float v0, float v1, int top, int bottom) {
        Matrix3x2f pose = new Matrix3x2f(g.pose()).translate(x, y).scale(w, h);
        g.guiRenderState.addGuiElement(new Quad(p, t, pose, u0, u1, v0, v1, top, bottom, new ScreenRectangle(0, 0, 1, 1).transformMaxBounds(pose)));
    }

    private static int code(float v, float h) {
        return Math.max(0, Math.min(4000, Math.round(v / Math.max(h, 0.001f) * 500f)));
    }

    private record Quad(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, float u0, float u1, float v0, float v1,
                        int top, int bottom, ScreenRectangle bounds) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer c) {
            c.addVertexWith2DPose(pose, 0, 0).setUv(u0, v0).setColor(top);
            c.addVertexWith2DPose(pose, 0, 1).setUv(u0, v1).setColor(bottom);
            c.addVertexWith2DPose(pose, 1, 1).setUv(u1, v1).setColor(bottom);
            c.addVertexWith2DPose(pose, 1, 0).setUv(u1, v0).setColor(top);
        }

        @Override
        public ScreenRectangle scissorArea() {
            return null;
        }
    }
}
