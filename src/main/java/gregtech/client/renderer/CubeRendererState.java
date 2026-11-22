package gregtech.client.renderer;

import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.vec.Cuboid6;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * What a block renderer is drawing: Forge's layer, the faces of the block that can be seen, and whether the block is
 * in GTCEu's {@link EmissiveLayer emissive layer}. {@link gregtech.client.renderer.texture.Textures#renderFace} and
 * {@link gregtech.client.renderer.texture.Textures#renderEmissiveFace} draw only the faces of what is being meshed.
 */
@SideOnly(Side.CLIENT)
public class CubeRendererState {

    public final BlockRenderLayer layer;
    public final boolean[] sideMask;
    public final IBlockAccess world;
    /**
     * Whether the block is in the emissive layer: its emissive faces are drawn where that layer is current. The
     * emissive faces of a block outside it are drawn with its {@code CUTOUT} faces.
     */
    public final boolean emissiveLayer;
    public static boolean[] PASS_MASK = new boolean[EnumFacing.VALUES.length];

    static {
        Arrays.fill(PASS_MASK, true);
    }

    /**
     * A state for a block outside the emissive layer.
     */
    public CubeRendererState(BlockRenderLayer layer, boolean[] sideMask, IBlockAccess world) {
        this(layer, sideMask, world, false);
    }

    public CubeRendererState(BlockRenderLayer layer, boolean[] sideMask, IBlockAccess world, boolean emissiveLayer) {
        this.layer = layer;
        this.sideMask = sideMask;
        this.world = world;
        this.emissiveLayer = emissiveLayer;
    }

    /**
     * @return this state with another side mask
     */
    public CubeRendererState withSideMask(boolean[] sideMask) {
        return new CubeRendererState(this.layer, sideMask, this.world, this.emissiveLayer);
    }

    /**
     * @param faceLayer the game layer of a face, or {@code null} for a face of no particular layer
     * @return whether the face is drawn now: in its layer, or in any layer while no layer is set; never while the
     *         emissive layer is meshed
     */
    public boolean drawsLayer(@Nullable BlockRenderLayer faceLayer) {
        if (EmissiveLayer.isMeshing()) return false;
        return faceLayer == null || this.layer == null || this.layer == faceLayer;
    }

    /**
     * @return whether an emissive face is drawn now: while the emissive layer is current for a block in it, with the
     *         {@code CUTOUT} faces for a block outside it, and in any layer while no layer is set
     */
    public boolean drawsEmissive() {
        if (EmissiveLayer.isMeshing()) return this.emissiveLayer;
        if (this.layer == null) return true;
        return this.layer == (this.emissiveLayer ? EmissiveLayer.gameLayer() : EmissiveLayer.FALLBACK);
    }

    public boolean shouldSideBeRendered(EnumFacing face, Cuboid6 bounds) {
        if (!sideMask[face.getIndex()]) { // check if the side is unnecessary be rendered
            if (bounds == Cuboid6.full) {
                return false;
            }
            switch (face) {
                case DOWN:
                    if (bounds.min.y <= 0) return false;
                    break;
                case UP:
                    if (bounds.max.y >= 1) return false;
                    break;
                case NORTH:
                    if (bounds.min.z <= 0) return false;
                    break;
                case SOUTH:
                    if (bounds.max.z >= 1) return false;
                    break;
                case WEST:
                    if (bounds.min.x <= 0) return false;
                    break;
                case EAST:
                    if (bounds.max.x >= 1) return false;
                    break;
            }
        }
        return true;
    }
}
