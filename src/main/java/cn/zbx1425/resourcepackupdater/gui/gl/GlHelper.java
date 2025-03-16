package cn.zbx1425.resourcepackupdater.gui.gl;

import cn.zbx1425.resourcepackupdater.ResourcePackUpdater;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.fonts.TrueTypeFont; // 导入 TrueTypeFont 类
import net.minecraft.client.gui.fonts.FontManager; // 导入 FontManager 类

public class GlHelper {

    public static void clearScreen(float r, float g, float b) {
        RenderSystem.clearColor(r, g, b, 1f);
        RenderSystem.clear(16640, Minecraft.ON_OSX);
    }

    private static ShaderInstance previousShader;
    private static Matrix4f lastProjectionMat;

    public static void initGlStates() {
        previousShader = RenderSystem.getShader();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().setIdentity();
        RenderSystem.applyModelViewMatrix();
        lastProjectionMat = RenderSystem.getProjectionMatrix();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableTexture();
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        initFont(); // 初始化 TTF 字体
    }

    public static void resetGlStates() {
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(() -> previousShader);
        RenderSystem.setProjectionMatrix(lastProjectionMat);
    }

    // 定义 TTF 字体资源
    public static final ResourceLocation TTF_FONT_RESOURCE = new ResourceLocation(ResourcePackUpdater.MOD_ID, "textures/fonts/dyfont.ttf");
    public static TrueTypeFont ttfFont;

    // 创建 TrueTypeFont 实例
    public static void initFont() {
        FontManager fontManager = Minecraft.getInstance().fontManager;
        ttfFont = new TrueTypeFont(fontManager, TTF_FONT_RESOURCE, 16); // 16 是字体大小，可以根据需要调整
    }

    private static BufferBuilder bufferBuilder;

    public static void begin(ResourceLocation texture) {
        bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Minecraft.getInstance().getTextureManager().getTexture(texture).setFilter(true, false);
        RenderSystem.setShaderTexture(0, texture);
    }

    public static void end() {
        Tesselator.getInstance().end();
    }

    public static void swapBuffer() throws MinecraftStoppingException {
        Window window = Minecraft.getInstance().getWindow();
        if (window.shouldClose()) {
            throw new MinecraftStoppingException();
        } else {
            window.updateDisplay();
        }
    }

    public static void blit(float x1, float y1, float width, float height, float u1, float v1, float u2, float v2, int color) {
        float x2 = x1 + width;
        float y2 = y1 + height;
        withColor(bufferBuilder.vertex(x1, y1, 1f).uv(u1, v1), color).endVertex();
        withColor(bufferBuilder.vertex(x2, y1, 1f).uv(u2, v1), color).endVertex();
        withColor(bufferBuilder.vertex(x2, y2, 1f).uv(u2, v2), color).endVertex();
        withColor(bufferBuilder.vertex(x1, y2, 1f).uv(u1, v2), color).endVertex();
    }

    public static void blit(float x1, float y1, float width, float height, int color) {
        float x2 = x1 + width;
        float y2 = y1 + height;
        withColor(bufferBuilder.vertex(x1, y1, 1f).uv(preloadFont.whiteU, preloadFont.whiteV), color).endVertex();
        withColor(bufferBuilder.vertex(x2, y1, 1f).uv(preloadFont.whiteU, preloadFont.whiteV), color).endVertex();
        withColor(bufferBuilder.vertex(x2, y2, 1f).uv(preloadFont.whiteU, preloadFont.whiteV), color).endVertex();
        withColor(bufferBuilder.vertex(x1, y2, 1f).uv(preloadFont.whiteU, preloadFont.whiteV), color).endVertex();
    }

    // 修改 drawString 方法以使用 TTF 字体
    public static void drawString(float x1, float y1, float width, float height, float fontSize,
                                  String text, int color, boolean monospace, boolean noWrap) {
        ttfFont.draw(text, x1, y1, fontSize, color); // 使用 TTF 字体渲染文本
    }

    // 修改 drawShadowString 方法
    public static void drawShadowString(float x1, float y1, float width, float height, float fontSize,
                                        String text, int color, boolean monospace, boolean noWrap) {
        ttfFont.draw(text, x1 + fontSize / 16, y1 + fontSize / 16, fontSize, 0xFF222222); // 绘制阴影
        ttfFont.draw(text, x1, y1, fontSize, color); // 绘制文本
    }

    public static float getStringWidth(String text, float fontSize) {
        float CHAR_SPACING = 0f;
        float width = 0;
        float x = 0;
        for (char chr : text.toCharArray()) {
            if (chr == '\n') {
                width = Math.max(width, x);
                x = 0;
            } else if (chr == '\r') {
                // Ignore CR
            } else if (chr == '\t') {
                // Align to 8 spaces
                float alignToPixels = (preloadFont.spaceWidthPl + CHAR_SPACING) * 8 * fontSize;
                x = (float) (Math.ceil(x / alignToPixels) * alignToPixels);
            } else if (chr == ' ') {
                x += (preloadFont.spaceWidthPl + CHAR_SPACING) * fontSize;
            } else {
                SimpleFont.GlyphProperty glyph = preloadFont.getGlyph(chr);
                x += glyph.advancePl * fontSize + CHAR_SPACING * fontSize;
            }
        }
        return Math.max(width, x);
    }

    public static void setMatIdentity() {
        RenderSystem.getModelViewStack().setIdentity();
    }

    public static void setMatPixel() {
        Matrix4f matrix = new Matrix4f();
        matrix.setIdentity();
        matrix.multiply(Matrix4f.createScaleMatrix(2, -2, 1));
        matrix.multiply(Matrix4f.createTranslateMatrix(-0.5f, -0.5f, 0));
        float rawWidth = Minecraft.getInstance().getWindow().getWidth();
        float rawHeight = Minecraft.getInstance().getWindow().getHeight();
        matrix.multiply(Matrix4f.createScaleMatrix(1 / rawWidth, 1 / rawHeight, 1));
        RenderSystem.setProjectionMatrix(matrix);
    }

    public static void setMatScaledPixel() {
        Matrix4f matrix = new Matrix4f();
        matrix.setIdentity();
        matrix.multiply(Matrix4f.createScaleMatrix(2, -2, 1));
        matrix.multiply(Matrix4f.createTranslateMatrix(-0.5f, -0.5f, 0));
        matrix.multiply(Matrix4f.createScaleMatrix(1f / getWidth(), 1f / getHeight(), 1));
        RenderSystem.setProjectionMatrix(matrix);
    }

    public static void enableScissor(float x, float y, float width, float height) {
        Matrix4f posMap = RenderSystem.getProjectionMatrix();
        Vector4f bottomLeft = new Vector4f(x, y + height, 0, 1);
        bottomLeft.transform(posMap);
        Vector4f topRight = new Vector4f(x + width, y, 0, 1);
        topRight.transform(posMap);
        float x1 = (float)Mth.map(bottomLeft.x(), -1, 1, 0, Minecraft.getInstance().getWindow().getWidth());
        float y1 = (float)Mth.map(bottomLeft.y(), -1, 1, 0, Minecraft.getInstance().getWindow().getHeight());
        float x2 = (float)Mth.map(topRight.x(), -1, 1, 0, Minecraft.getInstance().getWindow().getWidth());
        float y2 = (float)Mth.map(topRight.y(), -1, 1, 0, Minecraft.getInstance().getWindow().getHeight());
        RenderSystem.enableScissor((int)x1, (int)y1, (int)(x2 - x1), (int)(y2 - y1));
    }

    public static void disableScissor() {
        RenderSystem.disableScissor();
    }

    public static int getWidth() {
        int rawWidth = Minecraft.getInstance().getWindow().getWidth();
        if (rawWidth < 854) {
            return rawWidth;
        } else if (rawWidth < 1920) {
            return (int)((rawWidth - 854) * 1f / (1920 - 854) * (1366 - 854) + 854);
        } else {
            return 1366;
        }
    }

    public static int getHeight() {
        int rawWidth = Minecraft.getInstance().getWindow().getWidth();
        int rawHeight = Minecraft.getInstance().getWindow().getHeight();
        return (int)(rawHeight * (getWidth() * 1f / rawWidth));
    }

    public static void setMatCenterForm(float width, float height, float widthPercent) {
        Matrix4f matrix = new Matrix4f();
        matrix.setIdentity();
        matrix.multiply(Matrix4f.createScaleMatrix(2, -2, 1));
        matrix.multiply(Matrix4f.createTranslateMatrix(-0.5f, -0.5f, 0));
        float rawWidth = Minecraft.getInstance().getWindow().getWidth();
        float rawHeight = Minecraft.getInstance().getWindow().getHeight();
        matrix.multiply(Matrix4f.createScaleMatrix(1 / rawWidth, 1 / rawHeight, 1));
        float formRawWidth = rawWidth * widthPercent;
        float formRawHeight = height / width * formRawWidth;
        matrix.multiply(Matrix4f.createTranslateMatrix((rawWidth - formRawWidth) / 2f, (rawHeight - formRawHeight) / 2f, 0));
        matrix.multiply(Matrix4f.createScaleMatrix(formRawWidth / width, formRawHeight / height, 1));
        RenderSystem.setProjectionMatrix(matrix);
    }

    private static VertexConsumer withColor(VertexConsumer vc, int color) {
        int a = color >>> 24 & 0xFF;
        int r = color >>> 16 & 0xFF;
        int g = color >>> 8 & 0xFF;
        int b = color & 0xFF;
        return vc.color(r, g, b, a);
    }

    public static class MinecraftStoppingException extends RuntimeException {
        public MinecraftStoppingException() {
            super("Minecraft is now stopping.");
        }
    }
}
