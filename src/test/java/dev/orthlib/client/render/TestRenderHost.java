package dev.orthlib.client.render;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;

import dev.orthlib.api.render.InterfaceVersion;
import dev.orthlib.api.render.ProviderInfo;
import dev.orthlib.api.render.RenderCapability;
import dev.orthlib.api.render.RenderSnapshot;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An OrthLib render host for one test, with the test's thread as the client thread and Forge's own current layer,
 * so that a test can declare through the render interface and open a provider's scopes. OrthLib's own tests install
 * their hosts this way; it offers consumers none, so this lives in its package. Closing it restores the game's host.
 */
public final class TestRenderHost implements AutoCloseable {

    private final RenderHost game = RenderInterface.host;
    private final RenderHost host;
    private final List<String> failures = new CopyOnWriteArrayList<>();

    public TestRenderHost() {
        Thread client = Thread.currentThread();
        this.host = new RenderHost(() -> Thread.currentThread() == client, () -> false, new RenderHost.ForgeLayer() {

            @Override
            public BlockRenderLayer get() {
                return MinecraftForgeClient.getRenderLayer();
            }

            @Override
            public void set(BlockRenderLayer layer) {
                ForgeHooksClient.setRenderLayer(layer);
            }
        }, new RenderHost.GameDrawing() {

            // A test has no world pass: OrthLib's drawing of contributions without a provider finds no frame.
            @Override
            public FrameView frame(float partialTicks) {
                return null;
            }

            @Override
            public BufferBuilder begin(PlainState state) {
                throw new UnsupportedOperationException("A test has no world pass to draw in");
            }

            @Override
            public void end(boolean draw) {}

            @Override
            public void restore() {}
        }, (message, failure) -> this.failures.add(message));
        RenderInterface.host = this.host;
    }

    /**
     * @return the handle of a provider stating every capability
     */
    public ProviderHandle registerProvider() {
        var info = new ProviderInfo("Test renderer", new InterfaceVersion(1, 0), Set.of(
                RenderCapability.DECLARED_LAYERS, RenderCapability.EMISSIVE_CONTRIBUTIONS, RenderCapability.BLOOM));
        return RenderInterface.registerProvider(() -> info);
    }

    public RenderSnapshot<IBlockState, EmissiveSource> snapshot() {
        return this.host.registry.snapshot();
    }

    /**
     * @return what OrthLib reported of a consumer's failures
     */
    public List<String> failures() {
        return this.failures;
    }

    @Override
    public void close() {
        RenderInterface.host = this.game;
        ForgeHooksClient.setRenderLayer(null);
    }
}
