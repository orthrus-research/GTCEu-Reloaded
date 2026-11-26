package gregtech.integration.theoneprobe;

import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeInfoProducer;
import gregtech.integration.probe.ProbeLine;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ProbeMode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Shows a shared producer's lines in The One Probe, which asks for them on the server. */
public final class TOPInfoAdapter implements IProbeInfoProvider {

    private final String id;
    private final ProbeInfoProducer producer;

    public TOPInfoAdapter(String id, ProbeInfoProducer producer) {
        this.id = id;
        this.producer = producer;
    }

    @Override
    public String getID() {
        return id;
    }

    @Override
    public void addProbeInfo(@NotNull ProbeMode mode, @NotNull IProbeInfo probeInfo, @NotNull EntityPlayer player,
                             @NotNull World world, @NotNull IBlockState blockState, @NotNull IProbeHitData data) {
        TileEntity tileEntity = null;
        if (blockState.getBlock().hasTileEntity(blockState)) tileEntity = world.getTileEntity(data.getPos());
        List<ProbeLine> lines = new ArrayList<>();
        producer.addInfo(new ProbeContext(world, data.getPos(), blockState, tileEntity, player.isSneaking()), lines);
        if (lines.isEmpty()) return;

        TOPProbeRenderer renderer = new TOPProbeRenderer(player.isSneaking());
        for (ProbeLine line : lines) {
            if (line.isVisibleFrom(data.getSideHit())) renderer.render(line, probeInfo);
        }
    }
}
