package gregtech.api.configuration;

import gregtech.Bootstrap;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IDataAccessHatch;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.ILaserContainer;
import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.capability.SimpleCapabilityManager;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.FluidPipeProperties;
import gregtech.api.unification.material.properties.WireProperties;
import gregtech.common.pipelike.cable.BlockCable;
import gregtech.common.pipelike.cable.Insulation;
import gregtech.common.pipelike.cable.net.WorldENet;
import gregtech.common.pipelike.cable.tile.TileEntityCable;
import gregtech.common.pipelike.fluidpipe.BlockFluidPipe;
import gregtech.common.pipelike.fluidpipe.FluidPipeType;
import gregtech.common.pipelike.fluidpipe.net.WorldFluidPipeNet;
import gregtech.common.pipelike.laser.BlockLaserPipe;
import gregtech.common.pipelike.laser.LaserPipeProperties;
import gregtech.common.pipelike.laser.LaserPipeType;
import gregtech.common.pipelike.laser.net.WorldLaserPipeNet;
import gregtech.common.pipelike.laser.tile.TileEntityLaserPipe;
import gregtech.common.pipelike.optical.BlockOpticalPipe;
import gregtech.common.pipelike.optical.OpticalPipeProperties;
import gregtech.common.pipelike.optical.OpticalPipeType;
import gregtech.common.pipelike.optical.net.WorldOpticalPipeNet;
import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fml.common.discovery.ASMDataTable;

import org.objectweb.asm.Type;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;

final class PipeFamilyTestSupport {

    private static boolean initialized;
    private static BlockCable cableBlock;
    private static BlockOpticalPipe opticalBlock;
    private static BlockLaserPipe laserBlock;
    private static BlockFluidPipe fluidBlock;

    private PipeFamilyTestSupport() {}

    static void bootstrap() {
        Bootstrap.perform();
        if (initialized) return;
        ASMDataTable table = new ASMDataTable();
        inject(table, GregtechCapabilities.class, "CAPABILITY_ENERGY_CONTAINER", IEnergyContainer.class);
        inject(table, GregtechTileCapabilities.class, "CAPABILITY_DATA_ACCESS", IDataAccessHatch.class);
        inject(table, GregtechTileCapabilities.class, "CAPABILITY_LASER", ILaserContainer.class);
        inject(table, GregtechTileCapabilities.class, "CABABILITY_COMPUTATION_PROVIDER",
                IOpticalComputationProvider.class);
        CapabilityManager.INSTANCE.injectCapabilities(table);
        if (GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER == null)
            SimpleCapabilityManager.registerCapabilityWithNoDefault(IEnergyContainer.class);
        if (GregtechTileCapabilities.CAPABILITY_DATA_ACCESS == null)
            SimpleCapabilityManager.registerCapabilityWithNoDefault(IDataAccessHatch.class);
        if (GregtechTileCapabilities.CAPABILITY_LASER == null)
            SimpleCapabilityManager.registerCapabilityWithNoDefault(ILaserContainer.class);
        if (GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER == null)
            SimpleCapabilityManager.registerCapabilityWithNoDefault(IOpticalComputationProvider.class);
        assertNotNull(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER);
        assertNotNull(GregtechTileCapabilities.CAPABILITY_DATA_ACCESS);
        assertNotNull(GregtechTileCapabilities.CAPABILITY_LASER);
        assertNotNull(GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER);

        cableBlock = new BlockCable(Insulation.CABLE_SINGLE, Materials.Copper.getRegistry());
        cableBlock.addPipeMaterial(Materials.Copper, new WireProperties(128, 16, 1));
        opticalBlock = new BlockOpticalPipe(OpticalPipeType.NORMAL);
        laserBlock = new BlockLaserPipe(LaserPipeType.NORMAL);
        fluidBlock = new BlockFluidPipe(FluidPipeType.NORMAL, Materials.Copper.getRegistry());
        fluidBlock.setRegistryName("gregtech", "pipe_family_test_fluid");
        fluidBlock.addPipeMaterial(Materials.Copper, new FluidPipeProperties(1000, 1000, true, true, true, true));
        TileEntity.register("gregtech:pipe_family_test_fluid", PipeTestFluidPipe.class);
        initialized = true;
    }

    private static void inject(ASMDataTable table, Class<?> owner, String field, Class<?> type) {
        table.addASMData(null, CapabilityInject.class.getName(), owner.getName(), field,
                Collections.singletonMap("value", Type.getType(type)));
    }

    static TileEntityCable cable(ConfigurationTestWorld world, BlockPos pos) {
        TileEntityCable cable = new TileEntityCable();
        world.put(pos, cable, cableBlock.getDefaultState());
        cable.setPipeData(cableBlock, Insulation.CABLE_SINGLE, Materials.Copper);
        WorldENet.getWorldENet(world).addNode(pos, cable.getNodeData(), 0, 0, false);
        return cable;
    }

    static TileEntityOpticalPipe optical(ConfigurationTestWorld world, BlockPos pos) {
        TileEntityOpticalPipe pipe = new TileEntityOpticalPipe();
        world.put(pos, pipe, opticalBlock.getDefaultState());
        pipe.setPipeData(opticalBlock, OpticalPipeType.NORMAL);
        WorldOpticalPipeNet.getWorldPipeNet(world).addNode(pos, OpticalPipeProperties.INSTANCE, 0, 0, false);
        return pipe;
    }

    static TileEntityLaserPipe laser(ConfigurationTestWorld world, BlockPos pos) {
        TileEntityLaserPipe pipe = new TileEntityLaserPipe();
        world.put(pos, pipe, laserBlock.getDefaultState());
        pipe.setPipeData(laserBlock, LaserPipeType.NORMAL);
        WorldLaserPipeNet.getWorldPipeNet(world).addNode(pos, LaserPipeProperties.INSTANCE, 0, 0, false);
        return pipe;
    }

    static PipeTestFluidPipe fluid(ConfigurationTestWorld world, BlockPos pos) {
        PipeTestFluidPipe pipe = new PipeTestFluidPipe();
        world.put(pos, pipe, fluidBlock.getDefaultState());
        pipe.setPipeData(fluidBlock, FluidPipeType.NORMAL, Materials.Copper);
        WorldFluidPipeNet.getWorldPipeNet(world).addNode(pos, pipe.getNodeData(), 0, 0, false);
        return pipe;
    }
}
