package gregtech.client.utils;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.client.particle.GTParticle;
import gregtech.client.renderer.EmissiveLayer;
import gregtech.client.renderer.IRenderSetup;
import gregtech.client.shader.postprocessing.BloomType;

import net.minecraft.util.BlockRenderLayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * GTCEu's former bloom layer and bloom pass. Emissive textures are now meshed in GTCEu's
 * {@link EmissiveLayer emissive layer}, which is no game layer, and bloom and emissive geometry reach the screen
 * through OrthLib's render interface, drawn by a renderer that provides it (see {@code docs/emissive-rendering.md}).
 * <p>
 * These members are kept for addons built against them, and answer as GTCEu did under a renderer that draws terrain
 * without its bloom pass: the bloom layer getters answer the layer emissive textures fall back to, and bloom renders
 * are not registered. Their emissive textures are drawn full-bright in that layer and do not glow.
 *
 * @deprecated declare emissive geometry through {@link EmissiveLayer} and
 *             {@link gregtech.client.renderer.GTBloomKind#contribute}
 */
@Deprecated
@SideOnly(Side.CLIENT)
public class BloomEffectUtil {

    /**
     * @return {@link BlockRenderLayer#CUTOUT}, the layer emissive textures fall back to
     */
    @NotNull
    public static BlockRenderLayer getBloomLayer() {
        return EmissiveLayer.FALLBACK;
    }

    /**
     * @return {@link BlockRenderLayer#CUTOUT}, the layer emissive textures fall back to
     */
    @NotNull
    public static BlockRenderLayer getEffectiveBloomLayer() {
        return EmissiveLayer.FALLBACK;
    }

    /**
     * @param fallback Block render layer to be returned
     * @return {@code fallback}
     */
    @Contract("null -> _; !null -> !null")
    public static BlockRenderLayer getEffectiveBloomLayer(BlockRenderLayer fallback) {
        return fallback;
    }

    /**
     * @param isBloomActive ignored
     * @return {@link BlockRenderLayer#CUTOUT}, the layer emissive textures fall back to
     */
    @NotNull
    public static BlockRenderLayer getEffectiveBloomLayer(boolean isBloomActive) {
        return EmissiveLayer.FALLBACK;
    }

    /**
     * @param isBloomActive ignored
     * @param fallback      Block render layer to be returned
     * @return {@code fallback}
     */
    @Contract("_, null -> _; _, !null -> !null")
    public static BlockRenderLayer getEffectiveBloomLayer(boolean isBloomActive, BlockRenderLayer fallback) {
        return fallback;
    }

    /**
     * Registers nothing: the render callback is never called.
     *
     * @return {@link BloomRenderTicket#INVALID}
     * @throws NullPointerException if {@code metaTileEntity == null}
     */
    @NotNull
    public static BloomRenderTicket registerBloomRender(@Nullable IRenderSetup setup,
                                                        @NotNull BloomType bloomType,
                                                        @NotNull IBloomEffect render,
                                                        @NotNull MetaTileEntity metaTileEntity) {
        Objects.requireNonNull(metaTileEntity, "metaTileEntity == null");
        return BloomRenderTicket.INVALID;
    }

    /**
     * Registers nothing: the render callback is never called.
     *
     * @return {@link BloomRenderTicket#INVALID}
     * @throws NullPointerException if {@code particle == null}
     */
    @NotNull
    public static BloomRenderTicket registerBloomRender(@Nullable IRenderSetup setup,
                                                        @NotNull BloomType bloomType,
                                                        @NotNull IBloomEffect render,
                                                        @NotNull GTParticle particle) {
        Objects.requireNonNull(particle, "particle == null");
        return BloomRenderTicket.INVALID;
    }

    /**
     * Registers nothing: the render callback is never called.
     *
     * @return {@link BloomRenderTicket#INVALID}
     */
    @NotNull
    public static BloomRenderTicket registerBloomRender(@Nullable IRenderSetup setup,
                                                        @NotNull BloomType bloomType,
                                                        @NotNull IBloomEffect render,
                                                        @Nullable Predicate<BloomRenderTicket> validityChecker) {
        return BloomRenderTicket.INVALID;
    }

    /**
     * Registers nothing: the render callback is never called.
     *
     * @return {@link BloomRenderTicket#INVALID}
     */
    @NotNull
    public static BloomRenderTicket registerBloomRender(@Nullable IRenderSetup setup,
                                                        @NotNull BloomType bloomType,
                                                        @NotNull IBloomEffect render,
                                                        @Nullable Predicate<BloomRenderTicket> validityChecker,
                                                        @Nullable Supplier<World> worldContext) {
        return BloomRenderTicket.INVALID;
    }

    /**
     * A bloom render registration. None is made any more: every ticket is {@link #INVALID}.
     */
    public static final class BloomRenderTicket {

        public static final BloomRenderTicket INVALID = new BloomRenderTicket();

        private BloomRenderTicket() {}

        /**
         * @return {@code false}
         */
        public boolean isValid() {
            return false;
        }

        public void invalidate() {}
    }
}
