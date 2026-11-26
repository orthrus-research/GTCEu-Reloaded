package gregtech.common.mui.widget.prospector;

import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.ore.StoneType;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.util.GTUtility;
import gregtech.api.util.TextFormattingUtil;
import gregtech.api.worldgen.bedrockFluids.BedrockFluidVeinHandler;
import gregtech.core.network.packets.PacketProspecting;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fluids.Fluid;

import com.cleanroommc.modularui.value.sync.SyncHandler;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Scans the chunks around the player one per tick on the server and sends each result to the prospector screen.
 * The client sends nothing back.
 */
public class ProspectingSyncHandler extends SyncHandler {

    private static final int SCAN = 0;

    private final int chunkRadius;
    private final ProspectorMode mode;
    private int chunkIndex;
    private Consumer<PacketProspecting> receiver;

    public ProspectingSyncHandler(int chunkRadius, @NotNull ProspectorMode mode) {
        this.chunkRadius = chunkRadius;
        this.mode = mode;
    }

    /** Client: where scanned chunks go. */
    public void setReceiver(Consumer<PacketProspecting> receiver) {
        this.receiver = receiver;
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        int side = chunkRadius * 2 - 1;
        if (chunkIndex >= side * side) return;
        EntityPlayer player = getSyncManager().getPlayer();
        PacketProspecting packet = scanChunk(player.world, player, chunkIndex / side - chunkRadius + 1,
                chunkIndex % side - chunkRadius + 1);
        syncToClient(SCAN, packet::writePacketData);
        chunkIndex++;
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) {
        if (id == SCAN) {
            PacketProspecting packet = PacketProspecting.readPacketData(buf);
            if (packet != null && receiver != null) {
                receiver.accept(packet);
            }
        }
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) {}

    /** Scans the chunk at the given row and column offset from the player's chunk. */
    private PacketProspecting scanChunk(World world, EntityPlayer player, int oz, int ox) {
        int playerChunkX = player.chunkCoordX;
        int playerChunkZ = player.chunkCoordZ;
        Chunk chunk = world.getChunk(playerChunkX + ox, playerChunkZ + oz);
        PacketProspecting packet = new PacketProspecting(playerChunkX + ox, playerChunkZ + oz, playerChunkX,
                playerChunkZ, (int) player.posX, (int) player.posZ, this.mode);

        switch (mode) {
            case ORE -> scanOres(chunk, packet);
            case FLUID -> {
                BedrockFluidVeinHandler.FluidVeinWorldEntry fStack = BedrockFluidVeinHandler
                        .getFluidVeinWorldEntry(world, chunk.x, chunk.z);
                if (fStack != null && fStack.getDefinition() != null) {
                    packet.addBlock(0, 3, 0, TextFormattingUtil.formatNumbers(100.0 *
                            BedrockFluidVeinHandler.getOperationsRemaining(world, chunk.x, chunk.z) /
                            BedrockFluidVeinHandler.MAXIMUM_VEIN_OPERATIONS));
                    packet.addBlock(0, 2, 0,
                            String.valueOf(BedrockFluidVeinHandler.getFluidYield(world, chunk.x, chunk.z)));
                    Fluid fluid = BedrockFluidVeinHandler.getFluidInChunk(world, chunk.x, chunk.z);
                    if (fluid != null) {
                        packet.addBlock(0, 1, 0, fluid.getName());
                    }
                }
            }
        }
        return packet;
    }

    private static void scanOres(Chunk chunk, PacketProspecting packet) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int ySize = chunk.getHeightValue(x, z);
                for (int y = 1; y < ySize; y++) {
                    pos.setPos(x, y, z);
                    IBlockState state = chunk.getBlockState(pos);
                    ItemStack itemBlock = GTUtility.toItem(state);
                    if (GTUtility.isOre(itemBlock)) {
                        packet.addBlock(x, y, z, oreName(itemBlock));
                    }
                }
            }
        }
    }

    /** The ore dictionary name an ore block is listed under: its stone type's ore name where it has one. */
    private static String oreName(ItemStack itemBlock) {
        String oreDictString = OreDictUnifier.getOreDictionaryNames(itemBlock).stream().findFirst().orElse("");
        OrePrefix prefix = OreDictUnifier.getPrefix(itemBlock);
        if (prefix != null) {
            for (StoneType type : StoneType.STONE_TYPE_REGISTRY) {
                if (type.processingPrefix == prefix && type.shouldBeDroppedAsItem) {
                    return oreDictString;
                } else if (type.processingPrefix == prefix) {
                    MaterialStack materialStack = OreDictUnifier.getMaterial(itemBlock);
                    if (materialStack != null) {
                        return "ore" + oreDictString.replaceFirst(prefix.name(), "");
                    }
                }
            }
        }
        // probably another mod's ore
        return oreDictString;
    }
}
