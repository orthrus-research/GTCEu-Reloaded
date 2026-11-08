package gregtech.api.configuration;

import gregtech.api.util.GTLog;
import gregtech.common.pipelike.fluidpipe.BlockFluidPipe;
import gregtech.common.pipelike.fluidpipe.net.PipeTankList;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.discovery.ASMDataTable;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class PipeFamilyFluidTest {

    private static final BlockPos POS = new BlockPos(0, 64, 0);
    /** Distinctive prefix of the warning {@link BlockFluidPipe#breakBlock} logs for an unresolved record. */
    private static final String REMOVAL_WARNING = "Removing fluid pipe with an unresolved transfer";

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
    void failedRealDistributionQuarantinesUncertainFluidAndPropagatesTheOriginalException() {
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
        assertNull(pipe.getContainedFluid(0));
        NBTTagCompound record = (NBTTagCompound) pipe.getUncertainFluidTransfer();
        assertNotNull(record);
        assertEquals(100, record.getCompoundTag("Fluid").getInteger("Amount"));
        assertEquals(EnumFacing.EAST.getIndex(), record.getInteger("Side"));
        assertFalse(pipe.hasRetainedFluidData(1));
        assertTrue(pipe.dirtyNotifications > 0);
    }

    @Test
    void failedDistributionQuarantinesEvenWhenReentryOccupiesTheSourceTank() {
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
        assertNotNull(pipe.getUncertainFluidTransfer());
        NBTTagCompound saved = saved(pipe);
        assertEquals(water.writeToNBT(new NBTTagCompound()),
                saved.getCompoundTag("UncertainFluidTransfer").getCompoundTag("Fluid"));
        assertTrue(pipe.dirtyNotifications > 0);
        read(pipe, saved);
        assertEquals(water.writeToNBT(new NBTTagCompound()),
                saved(pipe).getCompoundTag("UncertainFluidTransfer").getCompoundTag("Fluid"));
    }

    @Test
    void unserializableWithdrawnFluidStillQuarantinesAndPausesDistribution() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        PipeTestFluidPipe pipe = PipeFamilyTestSupport.fluid(world, POS);
        IllegalStateException serializationFailure = new IllegalStateException("Injected serialization failure");
        // The pipe's own drain hands out a stack that cannot be written, forcing the record fallback
        FluidTank source = new FluidTank(pipe.getCapacityPerTank()) {

            @Override
            public FluidStack drain(FluidStack resource, boolean doDrain) {
                FluidStack drained = super.drain(resource, doDrain);
                if (!doDrain || drained == null) return drained;
                return new FluidStack(drained.getFluid(), drained.amount, drained.tag) {

                    @Override
                    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
                        throw serializationFailure;
                    }
                };
            }
        };
        pipe.tanksOverride = new FluidTank[] { source };
        PipeDistributionFailureTarget target = new PipeDistributionFailureTarget(() -> {});
        connect(world, pipe, target);
        assertEquals(100, source.fill(new FluidStack(FluidRegistry.WATER, 100), true));

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> distribute(pipe));

        assertSame(target.failure, failure);
        assertEquals(1, target.actualFills);
        assertTrue(List.of(failure.getSuppressed()).contains(serializationFailure));
        NBTTagCompound record = (NBTTagCompound) pipe.getUncertainFluidTransfer();
        assertNotNull(record);
        assertFalse(record.hasKey("Fluid"));
        assertTrue(record.getBoolean("FluidUnrecorded"));
        assertEquals(100, record.getInteger("Amount"));
        assertEquals(0, record.getInteger("Channel"));
        assertEquals(EnumFacing.EAST.getIndex(), record.getInteger("Side"));

        // The target throws on every real fill, so a resumed distribution would fail here instead of no-op
        assertEquals(50, source.fill(new FluidStack(FluidRegistry.WATER, 50), true));
        distribute(pipe);
        assertEquals(1, target.actualFills);
        assertEquals(0, target.getFluidAmount());
        assertEquals(50, source.getFluidAmount());
    }

    @Test
    void uncertainTransferSurvivesReloadPausesDistributionAndCanBeAcknowledged() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        PipeTestFluidPipe pipe = PipeFamilyTestSupport.fluid(world, POS);
        PipeDistributionFailureTarget badTarget = new PipeDistributionFailureTarget(() -> {});
        connect(world, pipe, badTarget);
        pipe.getFluidTanks()[0].fill(new FluidStack(FluidRegistry.WATER, 100), true);
        assertThrows(IllegalStateException.class, () -> distribute(pipe));
        NBTTagCompound record = (NBTTagCompound) pipe.getUncertainFluidTransfer();
        NBTTagCompound snapshot = saved(pipe);
        read(pipe, snapshot);
        assertEquals(record, pipe.getUncertainFluidTransfer());
        pipe.getFluidTanks()[0].fill(new FluidStack(FluidRegistry.WATER, 25), true);
        distribute(pipe);
        assertEquals(25, pipe.getContainedFluid(0).amount);
        assertEquals(1, badTarget.actualFills);
        pipe.acknowledgeUncertainFluidTransfer();
        assertNull(pipe.getUncertainFluidTransfer());
        assertFalse(saved(pipe).hasKey("UncertainFluidTransfer"));
        assertThrows(IllegalStateException.class, () -> distribute(pipe));
        assertEquals(2, badTarget.actualFills);
    }

    @Test
    void undeclaredCheckedFailureQuarantinesUncertainFluidAndPropagates() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        PipeTestFluidPipe pipe = PipeFamilyTestSupport.fluid(world, POS);
        IOException checked = new IOException("Injected undeclared checked failure");
        int[] actualFills = { 0 };
        connect(world, pipe, new FluidTank(1000) {

            @Override
            public int fill(FluidStack resource, boolean doFill) {
                if (!doFill) return super.fill(resource, false);
                actualFills[0]++;
                throw PipeFamilyFluidTest.<RuntimeException>sneakyThrow(checked);
            }
        });
        assertEquals(100, pipe.getFluidTanks()[0].fill(new FluidStack(FluidRegistry.WATER, 100), true));

        IOException failure = assertThrows(IOException.class, () -> distribute(pipe));

        assertSame(checked, failure);
        assertEquals(1, actualFills[0]);
        assertNull(pipe.getContainedFluid(0));
        NBTTagCompound record = (NBTTagCompound) pipe.getUncertainFluidTransfer();
        assertNotNull(record);
        assertEquals(100, record.getCompoundTag("Fluid").getInteger("Amount"));
        assertEquals(IOException.class.getName(), record.getString("Failure"));
    }

    @Test
    void unknownUncertainRecordRemainsBlockedAndRoundTrips() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        PipeTestFluidPipe pipe = PipeFamilyTestSupport.fluid(world, POS);
        FluidTank target = new FluidTank(1000);
        connect(world, pipe, target);
        NBTTagCompound snapshot = saved(pipe);
        snapshot.setString("UncertainFluidTransfer", "future format");
        read(pipe, snapshot);
        assertEquals(new NBTTagString("future format"), pipe.getUncertainFluidTransfer());
        pipe.getFluidTanks()[0].fill(new FluidStack(FluidRegistry.WATER, 100), true);
        distribute(pipe);
        assertEquals(0, target.getFluidAmount());
        assertEquals(100, pipe.getContainedFluid(0).amount);

        NBTTagCompound reloaded = saved(pipe);
        assertEquals(new NBTTagString("future format"), reloaded.getTag("UncertainFluidTransfer"));
        read(pipe, reloaded);
        assertEquals(new NBTTagString("future format"), pipe.getUncertainFluidTransfer());
        distribute(pipe);
        assertEquals(0, target.getFluidAmount());
        assertEquals(100, pipe.getContainedFluid(0).amount);

        // The same fixture does deliver once the record is acknowledged, so the pause above is the record's doing.
        pipe.acknowledgeUncertainFluidTransfer();
        distribute(pipe);
        assertEquals(100, target.getFluidAmount());
    }

    @Test
    void reentrantUpdateDuringDistributionDoesNotDistributeAgain() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        PipeTestFluidPipe pipe = PipeFamilyTestSupport.fluid(world, POS);
        int capacity = pipe.getCapacityPerTank();
        int[] actualFills = { 0 };
        int[] nestedFills = { -1 };
        FluidTank target = new FluidTank(capacity * 4) {

            @Override
            public int fill(FluidStack resource, boolean doFill) {
                if (!doFill) return super.fill(resource, false);
                actualFills[0]++;
                if (nestedFills[0] < 0) {
                    int before = actualFills[0];
                    // Enough ticks for at least one nested pass to land on the distribution frequency
                    for (int tick = 0; tick < 2 * PipeTestFluidPipe.FREQUENCY; tick++) pipe.update();
                    nestedFills[0] = actualFills[0] - before;
                }
                return super.fill(resource, true);
            }
        };
        connect(world, pipe, target);
        assertEquals(capacity, pipe.getFluidTanks()[0].fill(new FluidStack(FluidRegistry.WATER, capacity), true));

        distribute(pipe);

        assertEquals(0, nestedFills[0], "nested update must not start a second distribution");
        assertEquals(1, actualFills[0]);
        assertEquals(capacity / 2, target.getFluidAmount());
        assertEquals(capacity / 2, pipe.getContainedFluid(0).amount);
        // The guard is released afterwards
        distribute(pipe);
        assertEquals(2, actualFills[0]);
        assertEquals(capacity, target.getFluidAmount());
    }

    @Test
    void breakingAPipeWarnsOnlyWhenItHasAnUnresolvedRecord() {
        ConfigurationTestWorld clean = new ConfigurationTestWorld();
        PipeFamilyTestSupport.fluid(clean, POS);
        ConfigurationTestWorld blocked = new ConfigurationTestWorld();
        PipeTestFluidPipe blockedPipe = PipeFamilyTestSupport.fluid(blocked, POS);
        NBTTagCompound record = attachRecord(blockedPipe);

        try (CapturedWarnings warnings = new CapturedWarnings()) {
            breakBlock(clean);
            assertEquals(Collections.emptyList(), warnings.matching(REMOVAL_WARNING));
            assertNull(clean.getTileEntity(POS), "breakBlock must run through to tile removal");

            breakBlock(blocked);
            List<String> removal = warnings.matching(REMOVAL_WARNING);
            assertEquals(1, removal.size(), removal.toString());
            assertTrue(removal.get(0).contains(record.toString()), removal.get(0));
        }
        assertEquals(record, blockedPipe.getUncertainFluidTransfer(), "warning must not consume the record");
        assertNull(BlockFluidPipe.warnUnresolvedTransferOnRemoval(clean, POS, null));
        assertNull(BlockFluidPipe.warnUnresolvedTransferOnRemoval(clean, POS, new PipeTestEndpoint()));
    }

    @Test
    void tricorderLeadsWithTheUncertainTransferWarningOnlyWhileARecordExists() {
        PipeTestFluidPipe pipe = pipe();
        assertFalse(hasTranslation(pipe.getDataInfo(), "gregtech.pipe.uncertain_fluid_transfer"));

        attachRecord(pipe);
        List<ITextComponent> info = pipe.getDataInfo();

        TextComponentTranslation warning = (TextComponentTranslation) info.get(0);
        assertEquals("gregtech.pipe.uncertain_fluid_transfer", warning.getKey());
        assertEquals(TextFormatting.RED, warning.getStyle().getColor());
        pipe.acknowledgeUncertainFluidTransfer();
        assertFalse(hasTranslation(pipe.getDataInfo(), "gregtech.pipe.uncertain_fluid_transfer"));
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

    private static void breakBlock(ConfigurationTestWorld world) {
        IBlockState state = world.getBlockState(POS);
        ((BlockFluidPipe) state.getBlock()).breakBlock(world, POS, state);
    }

    private static NBTTagCompound saved(PipeTestFluidPipe pipe) {
        return pipe.writeToNBT(new NBTTagCompound());
    }

    private static void read(PipeTestFluidPipe pipe, NBTTagCompound saved) {
        // The fixture block is test-only and unregistered, while the attached world already owns it.
        saved.removeTag("PipeBlock");
        pipe.readFromNBT(saved);
    }

    private static NBTTagCompound attachRecord(PipeTestFluidPipe pipe) {
        NBTTagCompound snapshot = saved(pipe);
        NBTTagCompound record = new NBTTagCompound();
        record.setInteger("Amount", 100);
        snapshot.setTag("UncertainFluidTransfer", record);
        read(pipe, snapshot);
        return record;
    }

    private static boolean hasTranslation(List<ITextComponent> info, String key) {
        for (ITextComponent line : info) {
            if (line instanceof TextComponentTranslation translation && key.equals(translation.getKey())) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable failure) throws T {
        throw (T) failure;
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

    /** Records the formatted WARN messages logged through {@link GTLog#logger} while open. */
    private static final class CapturedWarnings implements AutoCloseable {

        private final Logger logger = (Logger) GTLog.logger;
        private final List<String> messages = new CopyOnWriteArrayList<>();
        private final AbstractAppender appender;
        private final Level previousLevel;

        @SuppressWarnings("deprecation")
        CapturedWarnings() {
            this.appender = new AbstractAppender("PipeFamilyFluidTestWarnings", null, null, false) {

                @Override
                public void append(LogEvent event) {
                    if (event.getLevel() == Level.WARN) messages.add(event.getMessage().getFormattedMessage());
                }
            };
            this.appender.start();
            // Without a test configuration log4j falls back to ERROR, which would drop the warning before appenders
            this.previousLevel = logger.isEnabled(Level.WARN) ? null : logger.getLevel();
            if (previousLevel != null) logger.setLevel(Level.WARN);
            logger.addAppender(appender);
        }

        List<String> matching(String substring) {
            List<String> matches = new ArrayList<>();
            for (String message : messages) {
                if (message.contains(substring)) matches.add(message);
            }
            return matches;
        }

        @Override
        public void close() {
            logger.removeAppender(appender);
            if (previousLevel != null) logger.setLevel(previousLevel);
            appender.stop();
        }
    }
}
