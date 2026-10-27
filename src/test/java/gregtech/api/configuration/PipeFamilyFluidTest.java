package gregtech.api.configuration;

import gregtech.common.pipelike.fluidpipe.net.PipeTankList;

import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.discovery.ASMDataTable;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class PipeFamilyFluidTest {

    private static final BlockPos POS = new BlockPos(0, 64, 0);

    @BeforeAll
    static void bootstrap() {
        PipeFamilyTestSupport.bootstrap();
        ASMDataTable capabilities = new ASMDataTable();
        capabilities.addASMData(null, CapabilityInject.class.getName(), CapabilityFluidHandler.class.getName(),
                "FLUID_HANDLER_CAPABILITY", Collections.singletonMap("value", Type.getType(IFluidHandler.class)));
        capabilities.addASMData(null, CapabilityInject.class.getName(), CapabilityFluidHandler.class.getName(),
                "FLUID_HANDLER_ITEM_CAPABILITY",
                Collections.singletonMap("value", Type.getType(IFluidHandlerItem.class)));
        CapabilityManager.INSTANCE.injectCapabilities(capabilities);
        if (CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY == null) CapabilityFluidHandler.register();
        assertNotNull(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY);
        assertNotNull(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
    }

    @Test
    void failedRealDistributionRestoresWithdrawnFluidAndPropagatesTheOriginalException() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        PipeTestFluidPipe pipe = PipeFamilyTestSupport.fluid(world, POS);
        PipeDistributionFailureTarget target = new PipeDistributionFailureTarget(() -> {});
        connect(world, pipe, target);
        assertEquals(100, pipe.getFluidTanks()[0].fill(new FluidStack(FluidRegistry.WATER, 100), true));
        pipe.dirtyNotifications = 0;

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> distribute(pipe));

        assertSame(target.failure, failure);
        assertEquals(1, target.actualFills);
        assertEquals(0, target.getFluidAmount());
        assertNotNull(pipe.getContainedFluid(0));
        assertEquals(100, pipe.getContainedFluid(0).amount);
        assertFalse(pipe.hasRetainedFluidData(1));
        assertTrue(pipe.dirtyNotifications > 0);
    }

    @Test
    void failedDistributionRetainsWithdrawnFluidWhenReentryOccupiesTheSourceTank() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        PipeTestFluidPipe pipe = PipeFamilyTestSupport.fluid(world, POS);
        int capacity = pipe.getCapacityPerTank();
        PipeDistributionFailureTarget target = new PipeDistributionFailureTarget(() -> {
            assertEquals(capacity, pipe.getFluidTanks()[0].fill(new FluidStack(FluidRegistry.LAVA, capacity), true));
        });
        connect(world, pipe, target);
        FluidStack water = new FluidStack(FluidRegistry.WATER, 100);
        water.tag = new NBTTagCompound();
        water.tag.setString("rollback", "still owned");
        assertEquals(100, pipe.getFluidTanks()[0].fill(water, true));
        pipe.dirtyNotifications = 0;

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> distribute(pipe));

        assertSame(target.failure, failure);
        assertEquals(1, target.actualFills);
        assertEquals(0, target.getFluidAmount());
        assertEquals(FluidRegistry.LAVA, pipe.getContainedFluid(0).getFluid());
        assertEquals(capacity, pipe.getContainedFluid(0).amount);
        assertTrue(pipe.hasRetainedFluidData(1));
        NBTTagCompound saved = saved(pipe);
        assertEquals(water.writeToNBT(new NBTTagCompound()), saved.getTagList("Fluids", 10).getCompoundTagAt(1));
        assertTrue(pipe.dirtyNotifications > 0);
        read(pipe, saved);
        assertEquals(water.writeToNBT(new NBTTagCompound()), saved(pipe).getTagList("Fluids", 10).getCompoundTagAt(1));
    }

    @Test
    void actualFillAndDrainDirtyTheTileWhileSimulationDoesNot() {
        PipeTestFluidPipe pipe = pipe();
        PipeTankList tanks = pipe.getTankList(EnumFacing.NORTH);
        pipe.dirtyNotifications = 0;

        assertEquals(100, tanks.fill(new FluidStack(FluidRegistry.WATER, 100), false));
        assertEquals(0, pipe.dirtyNotifications);
        assertNull(pipe.getContainedFluid(0));
        assertEquals(100, tanks.fill(new FluidStack(FluidRegistry.WATER, 100), true));
        assertEquals(1, pipe.dirtyNotifications);
        assertEquals(40, tanks.drain(40, false).amount);
        assertEquals(1, pipe.dirtyNotifications);
        assertEquals(100, pipe.getContainedFluid(0).amount);
        assertEquals(40, tanks.drain(40, true).amount);
        assertEquals(2, pipe.dirtyNotifications);
        assertEquals(60, pipe.getContainedFluid(0).amount);
    }

    @Test
    void missingLegacyFluidListIsAcceptedAsEmpty() {
        PipeTestFluidPipe pipe = pipe();
        NBTTagCompound saved = saved(pipe);
        saved.removeTag("Fluids");

        read(pipe, saved);

        assertNull(pipe.getContainedFluid(0));
        assertFalse(pipe.hasRetainedFluidData(0));
    }

    @Test
    void malformedFluidListTypesAreRejectedWithoutChangingStoredFluid() {
        PipeTestFluidPipe pipe = pipe();
        pipe.getTankList(EnumFacing.NORTH).fill(new FluidStack(FluidRegistry.WATER, 73), true);
        NBTTagCompound wrongTag = saved(pipe);
        wrongTag.setString("Fluids", "invalid");
        assertThrows(IllegalArgumentException.class, () -> read(pipe, wrongTag));
        assertEquals(73, pipe.getContainedFluid(0).amount);
        NBTTagCompound wrongEntry = saved(pipe);
        NBTTagList invalid = new NBTTagList();
        invalid.appendTag(new NBTTagString("invalid"));
        wrongEntry.setTag("Fluids", invalid);

        assertThrows(IllegalArgumentException.class, () -> read(pipe, wrongEntry));
        assertEquals(73, pipe.getContainedFluid(0).amount);
    }

    @Test
    void excessChannelsAndUnknownFluidsRoundTripAtTheirOriginalIndexes() {
        PipeTestFluidPipe pipe = pipe();
        NBTTagCompound saved = saved(pipe);
        NBTTagList list = new NBTTagList();
        list.appendTag(new FluidStack(FluidRegistry.WATER, 100).writeToNBT(new NBTTagCompound()));
        NBTTagCompound excess = new FluidStack(FluidRegistry.LAVA, 200).writeToNBT(new NBTTagCompound());
        NBTTagCompound unknown = unknownFluid();
        list.appendTag(excess);
        list.appendTag(unknown);
        saved.setTag("Fluids", list);

        read(pipe, saved);

        assertEquals(100, pipe.getContainedFluid(0).amount);
        assertTrue(pipe.hasRetainedFluidData(1));
        assertTrue(pipe.hasRetainedFluidData(2));
        NBTTagList written = saved(pipe).getTagList("Fluids", 10);
        assertEquals(3, written.tagCount());
        assertEquals(excess, written.getCompoundTagAt(1));
        assertEquals(unknown, written.getCompoundTagAt(2));
        assertEquals(50, pipe.getTankList(EnumFacing.NORTH).fill(new FluidStack(FluidRegistry.WATER, 50), true));
        written = saved(pipe).getTagList("Fluids", 10);
        assertEquals(excess, written.getCompoundTagAt(1));
        assertEquals(unknown, written.getCompoundTagAt(2));
    }

    @Test
    void unknownFluidReservesItsChannelInsteadOfBeingOverwritten() {
        PipeTestFluidPipe pipe = pipe();
        NBTTagCompound saved = saved(pipe);
        NBTTagCompound unknown = unknownFluid();
        NBTTagList list = new NBTTagList();
        list.appendTag(unknown);
        saved.setTag("Fluids", list);

        read(pipe, saved);

        assertNull(pipe.getContainedFluid(0));
        assertTrue(pipe.hasRetainedFluidData(0));
        assertEquals(0, pipe.getTankList(EnumFacing.NORTH).fill(new FluidStack(FluidRegistry.WATER, 50), true));
        assertEquals(unknown, saved(pipe).getTagList("Fluids", 10).getCompoundTagAt(0));
    }

    @Test
    void oversizedKnownContentsRemainRecoverableInsteadOfBeingClampedAway() {
        PipeTestFluidPipe pipe = pipe();
        int amount = pipe.getCapacityPerTank() + 100;
        NBTTagCompound saved = saved(pipe);
        NBTTagList list = new NBTTagList();
        list.appendTag(new FluidStack(FluidRegistry.WATER, amount).writeToNBT(new NBTTagCompound()));
        saved.setTag("Fluids", list);

        read(pipe, saved);

        assertEquals(amount, pipe.getContainedFluid(0).amount);
        assertEquals(0, pipe.getTankList(EnumFacing.NORTH).fill(new FluidStack(FluidRegistry.WATER, 1), true));
        assertEquals(amount, pipe.getTankList(EnumFacing.NORTH).drain(amount, true).amount);
        assertNull(pipe.getContainedFluid(0));
    }

    @Test
    void blockedIngressKeepsExistingDrainSemantics() {
        PipeTestFluidPipe pipe = pipe();
        PipeTankList tanks = pipe.getTankList(EnumFacing.NORTH);
        assertEquals(80, tanks.fill(new FluidStack(FluidRegistry.WATER, 80), true));
        pipe.setFaceBlocked(EnumFacing.NORTH, true);

        assertEquals(0, tanks.fill(new FluidStack(FluidRegistry.WATER, 10), true));
        assertEquals(20, tanks.drain(20, true).amount);
        assertEquals(60, pipe.getContainedFluid(0).amount);
    }

    private static PipeTestFluidPipe pipe() {
        return PipeFamilyTestSupport.fluid(new ConfigurationTestWorld(), POS);
    }

    private static void connect(ConfigurationTestWorld world, PipeTestFluidPipe pipe, IFluidHandler target) {
        world.put(POS.east(), new PipeTestEndpoint().provide(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, target),
                Blocks.CHEST.getDefaultState());
        pipe.setConnection(EnumFacing.EAST, true, false);
    }

    private static void distribute(PipeTestFluidPipe pipe) {
        for (int tick = 0; tick < 5; tick++) pipe.update();
    }

    private static NBTTagCompound saved(PipeTestFluidPipe pipe) {
        return pipe.writeToNBT(new NBTTagCompound());
    }

    private static void read(PipeTestFluidPipe pipe, NBTTagCompound saved) {
        // The fixture block is test-only and unregistered, while the attached world already owns it.
        saved.removeTag("PipeBlock");
        pipe.readFromNBT(saved);
    }

    private static NBTTagCompound unknownFluid() {
        NBTTagCompound unknown = new NBTTagCompound();
        unknown.setString("FluidName", "pipe_family_test:unregistered_fluid");
        unknown.setInteger("Amount", 123);
        NBTTagCompound metadata = new NBTTagCompound();
        metadata.setString("provenance", "preserve this payload");
        unknown.setTag("Tag", metadata);
        return unknown;
    }
}
