package gregtech.client.renderer;

import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.resources.TextureArea;
import gregtech.api.items.toolitem.ToolAction;
import gregtech.api.items.toolitem.ToolActionHints;
import gregtech.api.items.toolitem.ToolGrid;
import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public final class ToolActionHintRenderer {

    private static final TextureArea ROTATE = TextureArea
            .fullImage("textures/gui/terminal/icon/system/rotate_normal.png");
    private static final TextureArea SOUND = TextureArea.fullImage("textures/gui/terminal/icon/sound_normal.png");
    private static final TextureArea SETTINGS = TextureArea
            .fullImage("textures/gui/overlay/filter_settings_overlay.png");
    static final TextureArea CROSS = TextureArea.fullImage("textures/gui/widget/button_cross.png");
    private static final TextureArea OUTPUT = TextureArea.fullImage("textures/gui/widget/button_output.png");
    private static final TextureArea INPUT = TextureArea
            .fullImage("textures/blocks/overlay/machine/overlay_item_hatch_input.png");
    private static final TextureArea ENABLE = GuiTextures.BUTTON_WORKING_ENABLE.getSubArea(0, 0, 1, 0.5);
    private static final TextureArea DISABLE = GuiTextures.BUTTON_WORKING_ENABLE.getSubArea(0, 0.5, 1, 0.5);

    private ToolActionHintRenderer() {}

    /** Called inside the existing grid renderer, with blending enabled, textures disabled and depth writes disabled. */
    public static void draw(MetaTileEntity machine, EntityPlayer player, BlockPos pos, EnumFacing viewedFace,
                            double cameraX, double cameraY, double cameraZ) {
        ItemStack stack = player.getHeldItemMainhand();
        if (viewedFace == null || player.isSpectator() || stack.isEmpty() ||
                !player.canPlayerEdit(pos, viewedFace, stack))
            return;

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        float lightX = OpenGlHelper.lastBrightnessX;
        float lightY = OpenGlHelper.lastBrightnessY;
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        try {
            Vec3d origin = new Vec3d(pos).subtract(cameraX, cameraY, cameraZ);
            for (int row = -1; row <= 1; row++) {
                for (int column = -1; column <= 1; column++) {
                    EnumFacing side = ToolGrid.sideAtCell(viewedFace, column, row);
                    ToolAction action = ToolActionHints.forMainHand(machine, stack, player.isSneaking(), side);
                    if (action == null) continue;
                    double right = column * 0.375;
                    double up = row * 0.375;
                    drawTexture(textureFor(action), origin, viewedFace, right, up, 0.505);
                    if (action == ToolAction.MUFFLE) {
                        drawTexture(CROSS, origin, viewedFace, right, up, 0.506);
                    }
                }
            }
        } finally {
            GlStateManager.bindTexture(previousTexture);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            if (lighting) GlStateManager.enableLighting();
            GlStateManager.disableTexture2D();
        }
    }

    static TextureArea textureFor(ToolAction action) {
        return switch (action) {
            case ROTATE_FRONT -> ROTATE;
            case ROTATE_OUTPUT -> OUTPUT;
            case TOGGLE_INPUT -> INPUT;
            case ENABLE_WORKING -> ENABLE;
            case DISABLE_WORKING -> DISABLE;
            case MUFFLE, UNMUFFLE -> SOUND;
            case REMOVE_COVER -> CROSS;
            case COVER_SETTINGS -> SETTINGS;
        };
    }

    private static void drawTexture(TextureArea texture, Vec3d origin, EnumFacing face,
                                    double right, double up, double outward) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture.imageLocation);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        vertex(buffer, origin.add(ToolGrid.pointOnFace(face, right - 0.09, up - 0.09, outward)),
                texture.offsetX, texture.offsetY + texture.imageHeight);
        vertex(buffer, origin.add(ToolGrid.pointOnFace(face, right + 0.09, up - 0.09, outward)),
                texture.offsetX + texture.imageWidth, texture.offsetY + texture.imageHeight);
        vertex(buffer, origin.add(ToolGrid.pointOnFace(face, right + 0.09, up + 0.09, outward)),
                texture.offsetX + texture.imageWidth, texture.offsetY);
        vertex(buffer, origin.add(ToolGrid.pointOnFace(face, right - 0.09, up + 0.09, outward)),
                texture.offsetX, texture.offsetY);
        Tessellator.getInstance().draw();
    }

    private static void vertex(BufferBuilder buffer, Vec3d point, double u, double v) {
        buffer.pos(point.x, point.y, point.z).tex(u, v).color(255, 255, 255, 230).endVertex();
    }
}
