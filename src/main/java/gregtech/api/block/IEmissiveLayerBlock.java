package gregtech.api.block;

import net.minecraft.block.state.IBlockState;

/**
 * A block whose states may draw in GTCEu's emissive layer, the terrain layer emissive textures are meshed in. A
 * renderer that provides OrthLib's render interface meshes that layer on its own and gives it its glow; without one,
 * the layer's geometry is meshed full-bright in its fallback layer, {@code CUTOUT}, and does not glow.
 * <p>
 * The answer is asked on any thread, often while chunks are meshed, and a renderer may keep it for each state until
 * resources reload, so it is pure, fast and does not block.
 *
 * @see gregtech.client.renderer.EmissiveLayer
 */
public interface IEmissiveLayerBlock {

    /**
     * @param state a state of this block
     * @return whether the state draws anything in the emissive layer
     */
    boolean isInEmissiveLayer(IBlockState state);
}
