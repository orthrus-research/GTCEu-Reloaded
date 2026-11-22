package gregtech.client.renderer;

import gregtech.api.block.IEmissiveLayerBlock;
import gregtech.api.util.GTLog;
import gregtech.client.model.customtexture.CustomTextureBakedModel;
import gregtech.client.utils.BlockHooks;
import gregtech.common.ConfigHolder;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import dev.orthlib.api.render.Admission;
import dev.orthlib.api.render.DeclaredLayer;
import dev.orthlib.api.render.GameLayer;
import dev.orthlib.api.render.LayerDefinition;
import dev.orthlib.api.render.LayerRole;
import dev.orthlib.api.render.RenderKey;
import dev.orthlib.client.render.RenderInterface;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * GTCEu's emissive layer: the terrain layer its emissive textures are meshed in, declared through OrthLib's render
 * interface. A renderer that provides the interface meshes the layer on its own and draws its glow with the
 * {@link GTBloomKind#EMISSIVE_TEXTURES emissive textures} bloom kind. Everywhere else, without such a renderer and in
 * previews, the layer's geometry is meshed full-bright in its fallback layer, {@link #FALLBACK}, and does not glow.
 * <p>
 * Code that emits geometry per layer asks {@link #isCurrent()} for its emissive geometry and checks Forge's layer as
 * before for the rest; a model that returns every quad when Forge's layer is null asks {@link #isMeshing()} first,
 * since Forge's layer is null while the emissive layer is meshed. {@code Block.canRenderInLayer} answers for its
 * emissive geometry with {@link #gameLayer()}. These answer on any thread.
 */
@SideOnly(Side.CLIENT)
public final class EmissiveLayer {

    /** The layer's key in OrthLib's render interface. */
    public static final RenderKey KEY = new RenderKey("gregtech:bloom");
    /** The game layer the emissive layer's geometry falls back to where no renderer meshes it. */
    public static final BlockRenderLayer FALLBACK = BlockRenderLayer.CUTOUT;

    @Nullable
    private static volatile DeclaredLayer<IBlockState> layer;
    // The settings the live declaration was made from, to tell when changed settings change it. Client thread.
    @Nullable
    private static LayerDefinition declared;
    private static boolean declaredMachinesEmissive;

    private EmissiveLayer() {}

    /**
     * @return whether emissive geometry is to be emitted now, on this thread: while the renderer meshes the emissive
     *         layer, and while Forge's layer is {@link #FALLBACK} where the renderer does not mesh it
     */
    public static boolean isCurrent() {
        DeclaredLayer<IBlockState> current = layer;
        return current != null ? RenderInterface.isCurrent(current) :
                MinecraftForgeClient.getRenderLayer() == FALLBACK;
    }

    /**
     * @return whether a renderer is meshing the emissive layer on this thread; Forge's layer is null then, and only
     *         emissive geometry is to be emitted
     */
    public static boolean isMeshing() {
        DeclaredLayer<IBlockState> current = layer;
        return current != null && RenderInterface.currentDeclaredLayer() == current;
    }

    /**
     * @return the game layer emissive geometry is meshed in on this thread: {@link #FALLBACK} where no renderer meshes
     *         the emissive layer, {@code null} where one does
     */
    @Nullable
    public static BlockRenderLayer gameLayer() {
        DeclaredLayer<IBlockState> current = layer;
        return current != null ? RenderInterface.gameLayerFor(current) : FALLBACK;
    }

    /**
     * The layer's membership: whether a state draws anything in it. Blocks answer through {@link IEmissiveLayerBlock};
     * any other block is in it when its model has textures whose GTCEu texture metadata names the emissive layer.
     *
     * @param state a block state
     * @return whether the state draws anything in the emissive layer
     */
    public static boolean isMember(IBlockState state) {
        Block block = state.getBlock();
        if (block instanceof IEmissiveLayerBlock emissive) {
            return emissive.isInEmissiveLayer(state);
        }
        return BlockHooks.hasEmissiveTextures(state);
    }

    /**
     * The quads a model has in every layer, the emissive layer included, whatever this thread is meshing. Emissive
     * models take their emissive quads from these.
     */
    public static List<BakedQuad> allQuads(IBakedModel model, @Nullable IBlockState state, @Nullable EnumFacing side,
                                           long rand) {
        if (model instanceof CustomTextureBakedModel customTextureModel) {
            return customTextureModel.getAllQuads(state, side, rand);
        }
        // Layered models such as CTM's answer every layer for none.
        BlockRenderLayer forgeLayer = MinecraftForgeClient.getRenderLayer();
        ForgeHooksClient.setRenderLayer(null);
        try {
            return model.getQuads(state, side, rand);
        } finally {
            ForgeHooksClient.setRenderLayer(forgeLayer);
        }
    }

    /** Defines GTCEu's bloom kinds and declares the layer. Client thread, from pre-initialization. */
    public static void init() {
        for (GTBloomKind kind : GTBloomKind.values()) {
            kind.define();
        }
        declare();
    }

    /**
     * Applies changed settings: defines the bloom kinds whose settings changed again, and declares the layer again
     * when they change its role, its kind or its membership, so a renderer meshes it afresh. Client thread.
     */
    public static void refresh() {
        DeclaredLayer<IBlockState> current = layer;
        if (current != null && (GTBloomKind.EMISSIVE_TEXTURES.needsDefinition() || !wanted().equals(declared) ||
                declaredMachinesEmissive != ConfigHolder.client.machinesEmissiveTextures)) {
            layer = null;
            current.close();
        }
        for (GTBloomKind kind : GTBloomKind.values()) {
            kind.define();
        }
        if (layer == null) {
            declare();
        }
    }

    /** Retires the layer, the bloom kinds and the contributions made through them. Client thread. */
    static void retire() {
        DeclaredLayer<IBlockState> current = layer;
        layer = null;
        declared = null;
        if (current != null) {
            current.close();
        }
        for (GTBloomKind kind : GTBloomKind.values()) {
            kind.retire();
        }
    }

    private static void declare() {
        LayerDefinition definition = wanted();
        if (definition.role() == LayerRole.EMISSIVE && !GTBloomKind.EMISSIVE_TEXTURES.isDefined()) {
            // Without its bloom kind the layer is drawn without glow.
            definition = new LayerDefinition(KEY, LayerRole.PLAIN, GameLayer.CUTOUT, null);
        }
        declared = wanted();
        declaredMachinesEmissive = ConfigHolder.client.machinesEmissiveTextures;
        switch (RenderInterface.declareLayer(definition, EmissiveLayer::isMember)) {
            case Admission.Admitted<DeclaredLayer<IBlockState>>(var handle) -> layer = handle;
            case Admission.Exhausted<DeclaredLayer<IBlockState>>(var limit) -> GTLog.logger.warn(
                    "OrthLib's render interface holds its limit of {} layers; emissive textures stay in {}", limit,
                    FALLBACK);
        }
    }

    /** The declaration the settings ask for: with bloom, an emissive layer; without, a plain one, with no glow. */
    private static LayerDefinition wanted() {
        return ConfigHolder.client.shader.emissiveTexturesBloom ?
                new LayerDefinition(KEY, LayerRole.EMISSIVE, GameLayer.CUTOUT, GTBloomKind.EMISSIVE_TEXTURES.key()) :
                new LayerDefinition(KEY, LayerRole.PLAIN, GameLayer.CUTOUT, null);
    }
}
