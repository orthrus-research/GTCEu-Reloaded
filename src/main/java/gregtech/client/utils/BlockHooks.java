package gregtech.client.utils;

import gregtech.client.model.customtexture.CustomTextureBakedModel;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.WeightedBakedModel;
import net.minecraft.util.BlockRenderLayer;

import org.jetbrains.annotations.NotNull;

public class BlockHooks {

    /** Per thread, since chunk meshing calls the hook from several threads at once. */
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * @param bypass whether {@link #canRenderInLayer} gives no opinion on the calling thread
     */
    public static void setBypass(boolean bypass) {
        BYPASS.set(bypass);
    }

    public static Boolean canRenderInLayer(@NotNull IBlockState state, @NotNull BlockRenderLayer layer) {
        if (!BYPASS.get()) {
            IBakedModel model = Minecraft.getMinecraft().getBlockRendererDispatcher().getModelForState(state);
            if (model instanceof WeightedBakedModel) {
                model = ((WeightedBakedModel) model).baseModel;
            }

            Boolean ret;
            if (model instanceof CustomTextureBakedModel) {
                ret = ((CustomTextureBakedModel) model).getModel().canRenderInLayer(state, layer);
            } else {
                ret = null;
            }
            return ret;
        }
        return null;
    }
}
