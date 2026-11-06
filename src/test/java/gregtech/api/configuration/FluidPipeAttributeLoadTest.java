package gregtech.api.configuration;

import gregtech.Bootstrap;
import gregtech.api.fluids.attribute.FluidAttribute;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.FluidPipeProperties;
import gregtech.common.pipelike.fluidpipe.BlockFluidPipe;
import gregtech.common.pipelike.fluidpipe.FluidPipeType;
import gregtech.common.pipelike.fluidpipe.net.WorldFluidPipeNet;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FluidPipeAttributeLoadTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void loadedTileRestoresNamedAttributesWithoutReplacingTanksAndStaleCallbacksCannotRepair() {
        var attribute = new FluidAttribute(new ResourceLocation("test", "base"), lines -> {}, lines -> {});
        var block = new BlockFluidPipe(FluidPipeType.NORMAL, Materials.Copper.getRegistry());
        block.addPipeMaterial(Materials.Copper,
                new FluidPipeProperties(1000, 100, true, false, false, false).setCanContain(attribute, true));
        var world = new ConfigurationTestWorld();
        var position = new BlockPos(0, 64, 0);
        var tile = new RecoverableFluidPipe();
        world.put(position, tile, block.getDefaultState());
        tile.setPipeData(block, FluidPipeType.NORMAL, Materials.Copper);
        var tank = tile.getFluidTanks()[0];
        tank.fill(new FluidStack(FluidRegistry.WATER, 731), true);
        var graph = WorldFluidPipeNet.getWorldPipeNet(world);
        graph.addNode(position, tile.getNodeData(), 17, 0, false);
        var net = graph.getNetFromPos(position);
        var saved = net.serializeNBT();
        saved.getCompoundTag("Nodes").getTagList("WireProperties", 10).getCompoundTagAt(0).removeTag("attributes");
        net.deserializeNBT(saved);
        assertFalse(net.getNodeAt(position).data.canContain(attribute));
        world.unloaded.add(position);
        tile.recover(world, position);
        assertFalse(net.getNodeAt(position).data.canContain(attribute));
        world.unloaded.clear();
        tile.recover(world, position.up());
        tile.recover(new ConfigurationTestWorld(), position);
        assertFalse(net.getNodeAt(position).data.canContain(attribute));
        tile.recover(world, position);
        assertEquals(tile.getNodeData(), net.getNodeAt(position).data);
        assertSame(tank, tile.getFluidTanks()[0]);
        assertEquals(731, tank.getFluidAmount());
        assertEquals(17, net.getNodeAt(position).mark);
        assertFalse(net.getNodeAt(position).isActive);
        net.deserializeNBT(saved);
        world.put(position, new RecoverableFluidPipe(), block.getDefaultState());
        tile.recover(world, position);
        assertFalse(net.getNodeAt(position).data.canContain(attribute));
    }
}
