package gregtech.client.renderer;

import gregtech.Bootstrap;
import gregtech.api.block.IEmissiveLayerBlock;
import gregtech.common.ConfigHolder;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;

import dev.orthlib.api.render.Blend;
import dev.orthlib.api.render.ContributionShape;
import dev.orthlib.api.render.DeclaredLayer;
import dev.orthlib.api.render.LayerRole;
import dev.orthlib.api.render.Primitive;
import dev.orthlib.api.render.VertexLayout;
import dev.orthlib.client.render.EmissiveSource;
import dev.orthlib.client.render.FrameView;
import dev.orthlib.client.render.ProviderHandle;
import dev.orthlib.client.render.ProviderScope;
import dev.orthlib.client.render.TestRenderHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Which faces the block renderers draw, by what is being meshed: Forge's layer without a renderer that meshes the
 * emissive layer, the renderer's own meshing of the game's layers, and its meshing of the emissive layer.
 */
class EmissiveLayerTest {

    private static final ContributionShape QUADS = new ContributionShape(VertexLayout.POSITION_COLOR,
            Primitive.QUADS, Blend.OPAQUE);
    private static final EmissiveSource SOURCE = new EmissiveSource() {

        @Override
        public boolean valid() {
            return true;
        }

        @Override
        public World world() {
            return null;
        }

        @Override
        public boolean shouldDraw(FrameView view) {
            return true;
        }

        @Override
        public void write(BufferBuilder buffer, FrameView view) {}
    };

    private TestRenderHost host;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @BeforeEach
    void declare() {
        this.host = new TestRenderHost();
        EmissiveLayer.init();
    }

    @AfterEach
    void retire() {
        try {
            EmissiveLayer.retire();
            assertEquals(0, this.host.snapshot().layers().size(), "the layer is retired");
            assertEquals(0, this.host.snapshot().kinds().size(), "the bloom kinds are retired");
            assertTrue(this.host.failures().isEmpty(), () -> String.join("\n", this.host.failures()));
        } finally {
            this.host.close();
        }
    }

    @Test
    void theLayerIsEmissiveAndFallsBackToCutout() {
        var layers = this.host.snapshot().layers();
        assertEquals(1, layers.size());
        var definition = layers.getFirst().definition();
        assertEquals(EmissiveLayer.KEY, definition.key());
        assertEquals(LayerRole.EMISSIVE, definition.role());
        assertEquals("CUTOUT", definition.fallback().name());
        assertEquals(GTBloomKind.EMISSIVE_TEXTURES.key(), definition.bloomKind());
        assertEquals(3, this.host.snapshot().kinds().size());
    }

    @Test
    void withoutBloomTheLayerIsPlain() {
        boolean bloom = ConfigHolder.client.shader.emissiveTexturesBloom;
        try {
            ConfigHolder.client.shader.emissiveTexturesBloom = false;
            EmissiveLayer.refresh();
            var definition = this.host.snapshot().layers().getFirst().definition();
            assertEquals(LayerRole.PLAIN, definition.role());
            assertNull(definition.bloomKind());
            for (var kind : this.host.snapshot().kinds()) {
                if (!kind.definition().key().equals(GTBloomKind.EMISSIVE_TEXTURES.key())) {
                    assertEquals(0, kind.definition().strength(), "contributions draw without glow too");
                }
            }
        } finally {
            ConfigHolder.client.shader.emissiveTexturesBloom = bloom;
        }
    }

    @Test
    void withoutARendererEmissiveFacesJoinCutout() {
        ForgeHooksClient.setRenderLayer(BlockRenderLayer.CUTOUT);
        assertTrue(EmissiveLayer.isCurrent());
        assertFalse(EmissiveLayer.isMeshing());
        assertEquals(BlockRenderLayer.CUTOUT, EmissiveLayer.gameLayer());
        assertTrue(member(BlockRenderLayer.CUTOUT).drawsEmissive());
        assertTrue(member(BlockRenderLayer.CUTOUT).drawsLayer(BlockRenderLayer.CUTOUT));
        assertFalse(member(BlockRenderLayer.CUTOUT).drawsLayer(BlockRenderLayer.CUTOUT_MIPPED));
        assertTrue(outsider(BlockRenderLayer.CUTOUT).drawsEmissive());

        ForgeHooksClient.setRenderLayer(BlockRenderLayer.CUTOUT_MIPPED);
        assertFalse(EmissiveLayer.isCurrent());
        assertFalse(member(BlockRenderLayer.CUTOUT_MIPPED).drawsEmissive());
        assertTrue(member(BlockRenderLayer.CUTOUT_MIPPED).drawsLayer(BlockRenderLayer.CUTOUT_MIPPED));
    }

    @Test
    void withoutALayerEveryFaceIsDrawn() {
        ForgeHooksClient.setRenderLayer(null);
        assertTrue(member(null).drawsEmissive());
        assertTrue(member(null).drawsLayer(BlockRenderLayer.CUTOUT_MIPPED));
        assertTrue(outsider(null).drawsEmissive());
    }

    @Test
    void whileARendererMeshesGameLayersEmissiveFacesStayOutOfThem() {
        ProviderHandle provider = this.host.registerProvider();
        ForgeHooksClient.setRenderLayer(BlockRenderLayer.CUTOUT);
        try (ProviderScope ignored = provider.providing()) {
            assertFalse(EmissiveLayer.isCurrent());
            assertFalse(EmissiveLayer.isMeshing());
            assertNull(EmissiveLayer.gameLayer());
            assertFalse(member(BlockRenderLayer.CUTOUT).drawsEmissive());
            assertTrue(member(BlockRenderLayer.CUTOUT).drawsLayer(BlockRenderLayer.CUTOUT));
            assertTrue(outsider(BlockRenderLayer.CUTOUT).drawsEmissive(), "an addon's emissive faces stay in CUTOUT");
        }
    }

    @Test
    void whileARendererMeshesTheEmissiveLayerOnlyEmissiveFacesAreDrawn() {
        ProviderHandle provider = this.host.registerProvider();
        DeclaredLayer<?> layer = this.host.snapshot().layers().getFirst();
        ForgeHooksClient.setRenderLayer(BlockRenderLayer.CUTOUT);
        try (ProviderScope ignored = provider.meshing(layer)) {
            assertTrue(EmissiveLayer.isMeshing());
            assertTrue(EmissiveLayer.isCurrent());
            assertNull(EmissiveLayer.gameLayer());
            CubeRendererState state = member(null);
            assertTrue(state.drawsEmissive());
            for (BlockRenderLayer gameLayer : BlockRenderLayer.values()) {
                assertFalse(state.drawsLayer(gameLayer));
            }
            assertFalse(state.drawsLayer(null), "a face of no layer is no emissive face");
            assertFalse(outsider(null).drawsEmissive());
            assertTrue(state.withSideMask(CubeRendererState.PASS_MASK).drawsEmissive());
        }
        assertEquals(BlockRenderLayer.CUTOUT, MinecraftForgeClient.getRenderLayer());
    }

    @Test
    void membershipIsTheBlocksAnswer() {
        assertTrue(EmissiveLayer.isMember(new EmissiveBlock(true).getDefaultState()));
        assertFalse(EmissiveLayer.isMember(new EmissiveBlock(false).getDefaultState()));
        DeclaredLayer<IBlockState> layer = this.host.snapshot().layers().getFirst();
        assertTrue(layer.membership().test(new EmissiveBlock(true).getDefaultState()));
    }

    @Test
    void contributionsFollowTheirKindsSettings() {
        double strength = ConfigHolder.client.shader.fusionBloom.strength;
        try {
            EmissiveContribution contribution = GTBloomKind.FUSION_REACTOR.contribute(QUADS, SOURCE);
            assertTrue(contribution.isLive());
            var contributions = this.host.snapshot().contributions();
            assertEquals(1, contributions.size());
            assertEquals(GTBloomKind.FUSION_REACTOR.key(), contributions.getFirst().kind().definition().key());

            ConfigHolder.client.shader.fusionBloom.strength = 2;
            EmissiveLayer.refresh();
            assertTrue(contribution.isLive(), "a new definition keeps the contribution");
            contributions = this.host.snapshot().contributions();
            assertEquals(1, contributions.size());
            assertEquals(2 * 1.3f, contributions.getFirst().kind().definition().strength(), 1e-6f);

            contribution.close();
            assertFalse(contribution.isLive());
            assertEquals(0, this.host.snapshot().contributions().size());
        } finally {
            ConfigHolder.client.shader.fusionBloom.strength = strength;
        }
    }

    private static CubeRendererState member(BlockRenderLayer layer) {
        return new CubeRendererState(layer, CubeRendererState.PASS_MASK, null, true);
    }

    private static CubeRendererState outsider(BlockRenderLayer layer) {
        return new CubeRendererState(layer, CubeRendererState.PASS_MASK, null);
    }

    private static final class EmissiveBlock extends Block implements IEmissiveLayerBlock {

        private final boolean emissive;

        EmissiveBlock(boolean emissive) {
            super(Material.ROCK);
            this.emissive = emissive;
        }

        @Override
        public boolean isInEmissiveLayer(IBlockState state) {
            return this.emissive;
        }
    }
}
