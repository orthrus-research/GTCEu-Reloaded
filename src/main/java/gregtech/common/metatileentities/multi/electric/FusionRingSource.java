package gregtech.common.metatileentities.multi.electric;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import dev.orthlib.api.render.Blend;
import dev.orthlib.api.render.ContributionShape;
import dev.orthlib.api.render.Primitive;
import dev.orthlib.api.render.VertexLayout;
import dev.orthlib.client.render.EmissiveSource;
import dev.orthlib.client.render.FrameView;
import org.jetbrains.annotations.Nullable;

/**
 * A working fusion reactor's ring, as emissive geometry: untextured quads, fully lit, opaque, which glow with the
 * fusion reactor bloom kind. The ring's colors are opaque, so it replaces what it covers.
 */
@SideOnly(Side.CLIENT)
final class FusionRingSource implements EmissiveSource {

    static final ContributionShape SHAPE = new ContributionShape(VertexLayout.POSITION_COLOR, Primitive.QUADS,
            Blend.OPAQUE);

    private final MetaTileEntityFusionReactor reactor;

    FusionRingSource(MetaTileEntityFusionReactor reactor) {
        this.reactor = reactor;
    }

    @Override
    public boolean valid() {
        return this.reactor.isValid() && this.reactor.getHolder().getMetaTileEntity() == this.reactor;
    }

    @Override
    public @Nullable World world() {
        return this.reactor.getWorld();
    }

    @Override
    public boolean shouldDraw(FrameView view) {
        return this.reactor.hasFusionRingColor();
    }

    @Override
    public void write(BufferBuilder buffer, FrameView view) {
        this.reactor.writeFusionRing(buffer, view.cameraX(), view.cameraY(), view.cameraZ(), view.partialTicks());
    }
}
