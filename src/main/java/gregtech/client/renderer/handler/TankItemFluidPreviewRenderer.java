package gregtech.client.renderer.handler;

import gregtech.api.util.TankItemFluidPreview;
import gregtech.client.utils.RenderUtil;
import gregtech.common.ConfigHolder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.nio.FloatBuffer;

@SideOnly(Side.CLIENT)
public final class TankItemFluidPreviewRenderer {

    // LWJGL2 validates capacity 16 for glGetFloat even when this query returns only four components.
    private static final FloatBuffer CURRENT_COLOR = BufferUtils.createFloatBuffer(16);

    private TankItemFluidPreviewRenderer() {}

    public static void render(ItemStack stack, int x, int y) {
        FluidStack contents = TankItemFluidPreview.getFluid(stack, ConfigHolder.client.drumFluidBadges,
                ConfigHolder.client.quantumTankFluidBadges);
        if (contents == null) return;
        ResourceLocation still = contents.getFluid().getStill(contents);
        if (still == null) return;
        // Resolve the current atlas sprite each draw so resource reloads and animated textures keep working.
        TextureAtlasSprite sprite = RenderUtil.getTexture(still);
        int tint = contents.getFluid().getColor(contents);
        draw(sprite, tint, x + TankItemFluidPreview.BADGE_OFFSET, y + TankItemFluidPreview.BADGE_OFFSET);
    }

    private static void draw(TextureAtlasSprite sprite, int tint, int x, int y) {
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean textureEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        float lightX = OpenGlHelper.lastBrightnessX;
        float lightY = OpenGlHelper.lastBrightnessY;
        CURRENT_COLOR.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, CURRENT_COLOR);
        float red = CURRENT_COLOR.get(0);
        float green = CURRENT_COLOR.get(1);
        float blue = CURRENT_COLOR.get(2);
        float opacity = CURRENT_COLOR.get(3);

        try {
            GlStateManager.enableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE,
                    GL11.GL_ZERO);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            drawQuad(sprite, tint, x, y);
        } finally {
            GlStateManager.bindTexture(texture);
            if (!textureEnabled) GlStateManager.disableTexture2D();
            if (lighting) GlStateManager.enableLighting();
            if (depth) GlStateManager.enableDepth();
            if (alpha) GlStateManager.enableAlpha();
            GlStateManager.tryBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            if (!blend) GlStateManager.disableBlend();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            GlStateManager.resetColor();
            GlStateManager.color(red, green, blue, opacity);
            GlStateManager.setActiveTexture(activeTexture);
        }
    }

    private static void drawQuad(TextureAtlasSprite sprite, int tint, int x, int y) {
        int endX = x + TankItemFluidPreview.BADGE_SIZE;
        int endY = y + TankItemFluidPreview.BADGE_SIZE;
        int red = (tint >> 16) & 255;
        int green = (tint >> 8) & 255;
        int blue = tint & 255;
        int alpha = (tint >>> 24) & 255;
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        buffer.pos(x, endY, 0).tex(sprite.getMinU(), sprite.getMaxV()).color(red, green, blue, alpha).endVertex();
        buffer.pos(endX, endY, 0).tex(sprite.getMaxU(), sprite.getMaxV()).color(red, green, blue, alpha).endVertex();
        buffer.pos(endX, y, 0).tex(sprite.getMaxU(), sprite.getMinV()).color(red, green, blue, alpha).endVertex();
        buffer.pos(x, y, 0).tex(sprite.getMinU(), sprite.getMinV()).color(red, green, blue, alpha).endVertex();
        Tessellator.getInstance().draw();
    }
}
