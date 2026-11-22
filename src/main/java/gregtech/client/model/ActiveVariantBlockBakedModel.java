package gregtech.client.model;

import gregtech.api.GTValues;
import gregtech.api.block.VariantActiveBlock;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.EmissiveLayer;
import gregtech.client.utils.RenderUtil;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

@Mod.EventBusSubscriber(modid = GTValues.MODID, value = Side.CLIENT)
public class ActiveVariantBlockBakedModel implements IBakedModel {

    private static final Map<ModelResourceLocation, ActiveVariantBlockBakedModel> INSTANCES = new Object2ObjectOpenHashMap<>();
    private static final String[] BLOOM_TEXTURE_SUFFIX = { "_bloom", "_emissive", "_bloom_ctm", "_emissive_ctm" };

    private final ModelResourceLocation inactiveModelLocation;
    private final ModelResourceLocation activeModelLocation;
    @Nullable
    private final BooleanSupplier bloomConfig;

    private final ModelResourceLocation modelLocation;

    public ActiveVariantBlockBakedModel(ModelResourceLocation inactiveModelLocation,
                                        ModelResourceLocation activeModelLocation,
                                        @Nullable BooleanSupplier bloomConfig) {
        this.inactiveModelLocation = inactiveModelLocation;
        this.activeModelLocation = activeModelLocation;
        this.bloomConfig = bloomConfig;
        this.modelLocation = new ModelResourceLocation(
                GTUtility.gregtechId("active_variant_block_" + inactiveModelLocation.getNamespace() + "_" +
                        inactiveModelLocation.getPath()),
                inactiveModelLocation.getVariant().replaceAll(",active=(?:true|false)|active=(?:true|false),?", ""));
        INSTANCES.put(modelLocation, this);
    }

    public ModelResourceLocation getModelLocation() {
        return modelLocation;
    }

    public ModelResourceLocation getActiveModelLocation() {
        return activeModelLocation;
    }

    public ModelResourceLocation getInactiveModelLocation() {
        return inactiveModelLocation;
    }

    protected boolean getBloomConfig() {
        return bloomConfig == null || bloomConfig.getAsBoolean();
    }

    protected IBakedModel getModel(IBlockState state) {
        // Some mods like to call this without getting the extendedBlockState leading to a NPE crash since the
        // unlisted ACTIVE property is null.
        return getModel(Boolean.TRUE.equals(((IExtendedBlockState) state).getValue(VariantActiveBlock.ACTIVE)));
    }

    protected IBakedModel getModel(boolean active) {
        return Minecraft.getMinecraft().blockRenderDispatcher.getBlockModelShapes().getModelManager()
                .getModel(active ? activeModelLocation : inactiveModelLocation);
    }

    @NotNull
    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        if (state == null) return Collections.emptyList();

        IBakedModel m = getModel(state);

        // While a renderer meshes the emissive layer, Forge's layer is null, and only bloom textures are drawn.
        if (EmissiveLayer.isMeshing()) {
            return getBloomConfig() ? getBloomQuads(m, state, side, rand) : Collections.emptyList();
        }
        // If bloom is enabled, bloom textures are drawn where the emissive layer is current: in its own pass with a
        // renderer that meshes it, in CUTOUT without one. If bloom is disabled by the model specific bloom config,
        // they are drawn in CUTOUT.
        boolean bloomTextures = getBloomConfig() ? EmissiveLayer.isCurrent() :
                MinecraftForgeClient.getRenderLayer() == BlockRenderLayer.CUTOUT;
        List<BakedQuad> quads = withoutBloomQuads(m.getQuads(state, side, rand));
        if (bloomTextures) {
            quads = new ArrayList<>(quads);
            quads.addAll(getBloomQuads(m, state, side, rand));
        }
        return quads;
    }

    private static boolean isBloomQuad(BakedQuad quad) {
        String iconName = quad.getSprite().getIconName();
        for (String bloomTextureSuffix : BLOOM_TEXTURE_SUFFIX) {
            if (iconName.endsWith(bloomTextureSuffix)) {
                return true;
            }
        }
        return false;
    }

    private static List<BakedQuad> withoutBloomQuads(List<BakedQuad> quads) {
        for (BakedQuad q : quads) {
            if (isBloomQuad(q)) {
                List<BakedQuad> list = new ArrayList<>(quads);
                list.removeIf(ActiveVariantBlockBakedModel::isBloomQuad);
                return list;
            }
        }
        return quads;
    }

    private static List<BakedQuad> getBloomQuads(IBakedModel model, @Nullable IBlockState state,
                                                 @Nullable EnumFacing side, long rand) {
        List<BakedQuad> list = new ArrayList<>();
        for (BakedQuad q : EmissiveLayer.allQuads(model, state, side, rand)) {
            if (isBloomQuad(q)) {
                list.add(RenderUtil.makeEmissive(q));
            }
        }
        return list;
    }

    @Override
    public boolean isAmbientOcclusion() {
        return getModel(false).isAmbientOcclusion();
    }

    @Override
    public boolean isAmbientOcclusion(@NotNull IBlockState state) {
        return getModel(state).isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return getModel(false).isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return false;
    }

    @NotNull
    @Override
    public TextureAtlasSprite getParticleTexture() {
        return getModel(false).getParticleTexture();
    }

    @NotNull
    @Override
    public ItemOverrideList getOverrides() {
        return ItemOverrideList.NONE;
    }

    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        INSTANCES.forEach(event.getModelRegistry()::putObject);
    }
}
