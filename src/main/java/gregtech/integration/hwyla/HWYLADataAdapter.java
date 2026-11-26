package gregtech.integration.hwyla;

import gregtech.api.GTValues;
import gregtech.integration.probe.ProbeContext;
import gregtech.integration.probe.ProbeInfoProducer;
import gregtech.integration.probe.ProbeLine;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows a shared producer's lines in HWYLA. Lines about a tile entity are produced on the server, sent in HWYLA's NBT
 * data and drawn on the client; lines that need only the block state are produced on the client.
 */
public final class HWYLADataAdapter implements IWailaDataProvider {

    private final String configKey;
    private final Class<?> target;
    private final boolean fromServer;
    private final ProbeInfoProducer producer;
    private final String nbtKey;

    private HWYLADataAdapter(String configKey, Class<?> target, boolean fromServer, ProbeInfoProducer producer) {
        this.configKey = configKey;
        this.target = target;
        this.fromServer = fromServer;
        this.producer = producer;
        this.nbtKey = "gregtech.probe." + configKey;
    }

    /** Information about a tile entity, which the server produces for the client to draw. */
    public static HWYLADataAdapter fromServer(String configKey, Class<?> target, ProbeInfoProducer producer) {
        return new HWYLADataAdapter(configKey, target, true, producer);
    }

    /** Information the block state holds, which the client produces itself. */
    public static HWYLADataAdapter fromClient(String configKey, Class<?> target, ProbeInfoProducer producer) {
        return new HWYLADataAdapter(configKey, target, false, producer);
    }

    public void register(@NotNull IWailaRegistrar registrar) {
        registrar.registerBodyProvider(this, target);
        if (fromServer) registrar.registerNBTProvider(this, target);
        registrar.addConfig(GTValues.MOD_NAME, configKey);
    }

    @Override
    public @NotNull NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world,
                                              BlockPos pos) {
        if (!fromServer || te == null) return tag;
        List<ProbeLine> lines = new ArrayList<>();
        producer.addInfo(new ProbeContext(world, pos, world.getBlockState(pos), te, player.isSneaking()), lines);
        if (!lines.isEmpty()) tag.setTag(nbtKey, ProbeLine.writeList(lines));
        return tag;
    }

    @Override
    public @NotNull List<String> getWailaBody(ItemStack itemStack, List<String> tooltip, IWailaDataAccessor accessor,
                                              IWailaConfigHandler config) {
        if (!config.getConfig(configKey)) return tooltip;

        List<ProbeLine> lines;
        if (fromServer) {
            NBTTagCompound data = accessor.getNBTData();
            if (data == null || !data.hasKey(nbtKey)) return tooltip;
            lines = ProbeLine.readList(data.getTagList(nbtKey, Constants.NBT.TAG_COMPOUND));
        } else {
            lines = new ArrayList<>();
            producer.addInfo(new ProbeContext(accessor.getWorld(), accessor.getPosition(), accessor.getBlockState(),
                    accessor.getTileEntity(), accessor.getPlayer().isSneaking()), lines);
        }

        for (ProbeLine line : lines) {
            if (line.isVisibleFrom(accessor.getSide())) tooltip.add(HWYLAProbeRenderer.INSTANCE.render(line));
        }
        return tooltip;
    }
}
