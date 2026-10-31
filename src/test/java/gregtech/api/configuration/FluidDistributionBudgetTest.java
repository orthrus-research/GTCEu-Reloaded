package gregtech.api.configuration;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.FluidPipeProperties;
import gregtech.common.pipelike.fluidpipe.BlockFluidPipe;
import gregtech.common.pipelike.fluidpipe.FluidPipeType;
import gregtech.common.pipelike.fluidpipe.tile.TileEntityFluidPipeTickable;

import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.discovery.ASMDataTable;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class FluidDistributionBudgetTest {

    @BeforeAll
    static void setup() {
        Bootstrap.perform();
        ASMDataTable data = new ASMDataTable();
        data.addASMData(null, CapabilityInject.class.getName(), CapabilityFluidHandler.class.getName(),
                "FLUID_HANDLER_CAPABILITY", Collections.singletonMap("value", Type.getType(IFluidHandler.class)));
        data.addASMData(null, CapabilityInject.class.getName(), CapabilityFluidHandler.class.getName(),
                "FLUID_HANDLER_ITEM_CAPABILITY",
                Collections.singletonMap("value", Type.getType(IFluidHandlerItem.class)));
        CapabilityManager.INSTANCE.injectCapabilities(data);
        if (CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY == null) CapabilityFluidHandler.register();
        assertNotNull(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY);
    }

    static final class Fixture {

        final ConfigurationTestWorld world = new ConfigurationTestWorld();
        final BlockPos pos = new BlockPos(0, 64, 0);
        final TileEntityFluidPipeTickable pipe = new TileEntityFluidPipeTickable();
        final FluidTank[] targets;

        Fixture(int throughput, int[] capacities, int supply) {
            this(throughput, capacities, supply, -1);
        }

        Fixture(int throughput, int[] capacities, int supply, int firstActualLimit) {
            BlockFluidPipe block = new BlockFluidPipe(FluidPipeType.NORMAL, Materials.Copper.getRegistry());
            block.addPipeMaterial(Materials.Copper,
                    new FluidPipeProperties(1000, throughput, true, false, false, false));
            world.put(pos, pipe, block.getDefaultState());
            pipe.setPipeData(block, FluidPipeType.NORMAL, Materials.Copper);
            targets = new FluidTank[capacities.length];
            for (int i = 0; i < capacities.length; i++) {
                final int limit = i == 0 ? firstActualLimit : -1;
                FluidTank target = new FluidTank(capacities[i]) {

                    @Override
                    public int fill(FluidStack resource, boolean doFill) {
                        if (!doFill || limit < 0) return super.fill(resource, doFill);
                        FluidStack limited = resource.copy();
                        limited.amount = Math.min(limited.amount, limit);
                        return super.fill(limited, true);
                    }
                };
                targets[i] = target;
                EnumFacing facing = EnumFacing.VALUES[i];
                TileEntity neighbor = new TileEntity() {

                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
                        return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY ? (T) target :
                                super.getCapability(capability, side);
                    }
                };
                world.put(pos.offset(facing), neighbor, Blocks.CHEST.getDefaultState());
                pipe.setConnection(facing, true, false);
            }
            assertEquals(supply, pipe.getFluidTanks()[0].fill(new FluidStack(FluidRegistry.WATER, supply), true));
        }

        int distribute(int start) throws Exception {
            long seed = 0;
            for (; seed < 10000; seed++) {
                GTValues.RNG.setSeed(seed);
                if (GTValues.RNG.nextInt(6) == start) break;
            }
            assertTrue(seed < 10000);
            GTValues.RNG.setSeed(seed);
            FluidTank source = pipe.getFluidTanks()[0];
            int before = source.getFluidAmount();
            Method method = TileEntityFluidPipeTickable.class.getDeclaredMethod("distributeFluid", int.class,
                    FluidTank.class, FluidStack.class);
            method.setAccessible(true);
            method.invoke(pipe, 0, source, source.getFluid());
            int received = 0;
            for (FluidTank target : targets) received += target.getFluidAmount();
            assertEquals(before - source.getFluidAmount(), received, "fluid conserved");
            return received;
        }
    }

    @Test
    void partialAndRefusedActualFillsReturnTheRemainder() throws Exception {
        for (int limit : new int[] { 0, 1 }) {
            Fixture fixture = new Fixture(1, new int[] { 120, 120 }, 120, limit);
            int delivered = fixture.distribute(0);
            assertEquals(30 + limit, delivered);
            assertEquals(limit, fixture.targets[0].getFluidAmount());
            assertEquals(30, fixture.targets[1].getFluidAmount());
        }
    }

    @Test
    void originalRoundingTraceConservesInEveryRotation() throws Exception {
        for (int start = 0; start < 6; start++) {
            Fixture f = new Fixture(1000, new int[] { 1, 1, 2 }, 2);
            assertEquals(2, f.distribute(start), "rotation " + start);
        }
    }

    @Test
    void distributionMustRespectPerPassBudgetWithRounding() throws Exception {
        for (int start = 0; start < 6; start++) {
            Fixture f = new Fixture(1, new int[] { 1, 1, 1, 1, 1, 120 }, 120);
            int budget = f.pipe.getCapacityPerTank() / 2;
            int transferred = f.distribute(start);
            System.out.println("start=" + start + " capacity=" + f.pipe.getCapacityPerTank() + " budget=" + budget +
                    " actual=" + transferred);
            assertTrue(transferred <= budget,
                    "rotation " + start + " transferred " + transferred + " over budget " + budget);
        }
    }

    @Test
    void conservationAndBudgetHoldAcrossFanoutsAndVolumes() throws Exception {
        for (int count = 1; count <= 6; count++) {
            for (int supply : new int[] { 1, 2, 59, 60, 61, 120 }) {
                for (int start = 0; start < 6; start++) {
                    int[] capacities = new int[count];
                    for (int i = 0; i < count; i++) capacities[i] = i == count - 1 ? 120 : 1;
                    Fixture f = new Fixture(1, capacities, supply);
                    int transferred = f.distribute(start);
                    assertTrue(transferred <= Math.min(supply, f.pipe.getCapacityPerTank() / 2),
                            "fanout=" + count + " supply=" + supply + " rotation=" + start);
                }
            }
        }
    }
}
