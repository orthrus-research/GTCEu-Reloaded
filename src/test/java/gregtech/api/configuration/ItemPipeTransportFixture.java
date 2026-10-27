package gregtech.api.configuration;

import gregtech.Bootstrap;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.ItemPipeProperties;
import gregtech.api.util.GTUtility;
import gregtech.common.covers.CoverRoboticArm;
import gregtech.common.covers.TransferMode;
import gregtech.common.pipelike.itempipe.BlockItemPipe;
import gregtech.common.pipelike.itempipe.ItemPipeType;
import gregtech.common.pipelike.itempipe.net.ItemNetHandler;
import gregtech.common.pipelike.itempipe.net.WorldItemPipeNet;
import gregtech.common.pipelike.itempipe.tile.TileEntityItemPipeTickable;

import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import org.objectweb.asm.Type;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;

final class ItemPipeTransportFixture {

    static final BlockPos POS = new BlockPos(0, 64, 0);
    final ConfigurationTestWorld world = new ConfigurationTestWorld();
    final TileEntityItemPipeTickable pipe = new TileEntityItemPipeTickable();
    final ItemStackHandler source = new ItemStackHandler(1);
    final ItemStackHandler destination = new ItemStackHandler(1);
    final BlockItemPipe block;
    final ItemNetHandler input;
    final CoverRoboticArm arm;
    final Inventory endpoint;

    static void bootstrap() {
        Bootstrap.perform();
        if (CapabilityItemHandler.ITEM_HANDLER_CAPABILITY == null) {
            ASMDataTable capabilities = new ASMDataTable();
            capabilities.addASMData(null, CapabilityInject.class.getName(), CapabilityItemHandler.class.getName(),
                    "ITEM_HANDLER_CAPABILITY", Collections.singletonMap("value", Type.getType(IItemHandler.class)));
            CapabilityManager.INSTANCE.injectCapabilities(capabilities);
            if (CapabilityItemHandler.ITEM_HANDLER_CAPABILITY == null) CapabilityItemHandler.register();
        }
        assertNotNull(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
    }

    ItemPipeTransportFixture(int batch) {
        this(0.5f, batch);
    }

    ItemPipeTransportFixture(float rate, int batch) {
        block = new BlockItemPipe(ItemPipeType.NORMAL, Materials.Tin.getRegistry());
        block.addPipeMaterial(Materials.Tin, new ItemPipeProperties(4096, rate));
        world.put(POS, pipe, block.getDefaultState());
        pipe.setPipeData(block, ItemPipeType.NORMAL, Materials.Tin);
        WorldItemPipeNet.getWorldPipeNet(world).addNode(POS, pipe.getNodeData(), 0, 0, false);
        world.put(POS.west(), new Inventory(source), Blocks.CHEST.getDefaultState());
        endpoint = output(EnumFacing.EAST, destination);
        pipe.setConnection(EnumFacing.WEST, true, false);
        if (batch > 0) {
            CoverDefinition definition = new CoverDefinition(GTUtility.gregtechId("transport_regression_arm"),
                    (def, holder, face) -> new CoverRoboticArm(def, holder, face, 1, 64),
                    net.minecraft.item.ItemStack.EMPTY);
            arm = (CoverRoboticArm) definition.createCover(pipe.getCoverableImplementation(), EnumFacing.EAST);
            arm.setTransferMode(TransferMode.TRANSFER_EXACT);
            arm.getItemFilterContainer().setTransferSize(batch);
            pipe.getCoverableImplementation().addCover(EnumFacing.EAST, arm);
        } else {
            arm = null;
        }
        input = (ItemNetHandler) pipe.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.WEST);
        assertNotNull(input);
    }

    Inventory output(EnumFacing side, IItemHandler inventory) {
        Inventory endpoint = new Inventory(inventory);
        world.put(POS.offset(side), endpoint, Blocks.CHEST.getDefaultState());
        pipe.setConnection(side, true, false);
        return endpoint;
    }

    void time(long tick) {
        world.setTotalWorldTime(tick);
    }

    static final class Inventory extends TileEntity {

        private final IItemHandler items;
        boolean enabled = true;
        int probes;

        Inventory(IItemHandler items) {
            this.items = items;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getCapability(Capability<T> capability, EnumFacing side) {
            if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
                probes++;
                return enabled ? (T) items : null;
            }
            return super.getCapability(capability, side);
        }
    }
}
