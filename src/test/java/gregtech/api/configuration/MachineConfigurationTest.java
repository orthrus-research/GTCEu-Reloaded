package gregtech.api.configuration;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.cover.Cover;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.SimpleMachineMetaTileEntity;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.common.covers.CoverConveyor;
import gregtech.common.covers.CoverPump;
import gregtech.common.covers.filter.SimpleFluidFilter;
import gregtech.common.covers.filter.SimpleItemFilter;
import gregtech.common.items.MetaItems;
import gregtech.common.metatileentities.electric.SimpleMachineMetaTileEntityResizable;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class MachineConfigurationTest {

    private static RecipeMap<SimpleRecipeBuilder> recipeMap;
    private static CoverDefinition conveyorDefinition;
    private static CoverDefinition pumpDefinition;
    private static ConfigurationTestItems testItems;

    @BeforeAll
    public static void bootstrap() {
        testItems = ConfigurationTestItems.setup();
        recipeMap = new RecipeMapBuilder<>("configuration_card_test", new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1).fluidInputs(1).fluidOutputs(1).build();
        conveyorDefinition = new CoverDefinition(GTUtility.gregtechId("configuration_conveyor"),
                (definition, holder, side) -> new CoverConveyor(definition, holder, side, 1, 64), ItemStack.EMPTY);
        pumpDefinition = new CoverDefinition(GTUtility.gregtechId("configuration_pump"),
                (definition, holder, side) -> new CoverPump(definition, holder, side, 1, 64000), ItemStack.EMPTY);
    }

    @AfterAll
    public static void restoreItems() {
        testItems.restore();
    }

    @Test
    public void roundTripPreservesActiveRecipeEnergyInventoriesAndCapabilityObjects() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        SimpleMachineMetaTileEntity source = machine(world, new BlockPos(0, 64, 0));
        SimpleMachineMetaTileEntity target = machine(world, new BlockPos(2, 64, 0));
        NBTTagCompound desired = clipboard(source);
        configureMachine(desired.getCompoundTag("Settings"));
        assertTrue(paste(world, source, desired));
        NBTTagCompound copied = clipboard(source);

        Object itemHandler = target.getImportItems();
        Object fluidHandler = target.getImportFluids();
        target.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 7));
        target.getExportItems().setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 3));
        target.getImportFluids().fill(new FluidStack(FluidRegistry.WATER, 1234), true);
        EnergyContainerHandler energy = (EnergyContainerHandler) target
                .getMTETrait(GregtechDataCodes.ENERGY_CONTAINER_TRAIT);
        energy.setEnergyStored(1024);
        NBTTagCompound active = target.getRecipeLogic().serializeNBT();
        active.setInteger("Progress", 37);
        active.setInteger("MaxProgress", 200);
        active.setLong("RecipeEUt", 8);
        target.getRecipeLogic().deserializeNBT(active);
        NBTTagCompound beforeRecipe = target.getRecipeLogic().serializeNBT();

        assertTrue(paste(world, target, copied));
        assertEquals(copied, clipboard(target));
        assertEquals(beforeRecipe, target.getRecipeLogic().serializeNBT());
        assertTrue(target.getRecipeLogic().isActive());
        assertEquals(1024, energy.getEnergyStored());
        assertSame(itemHandler, target.getImportItems());
        assertSame(fluidHandler, target.getImportFluids());
        assertEquals(7, target.getImportItems().getStackInSlot(0).getCount());
        assertEquals(3, target.getExportItems().getStackInSlot(0).getCount());
        assertEquals(1234, target.getImportFluids().getTankAt(0).getFluidAmount());
    }

    @Test
    public void pairedOutputsHandleUninitializedFieldsAndSwappedFrontWithoutResettingMachine() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        SimpleMachineMetaTileEntity target = machine(world, new BlockPos(0, 64, 0));
        NBTTagCompound data = clipboard(target);
        NBTTagCompound settings = data.getCompoundTag("Settings");
        settings.setInteger("Front", EnumFacing.SOUTH.getIndex());
        settings.setInteger("ItemOutput", EnumFacing.NORTH.getIndex());
        settings.setInteger("FluidOutput", EnumFacing.UP.getIndex());
        assertTrue(paste(world, target, data));
        assertEquals(EnumFacing.SOUTH, target.getFrontFacing());
        assertEquals(EnumFacing.NORTH, target.getOutputFacingItems());
        assertEquals(EnumFacing.UP, target.getOutputFacingFluids());
    }

    @Test
    public void freshSnapshotClearsFalseZeroAndUnsetValuesAndPreparedPayloadCannotChange() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        SimpleMachineMetaTileEntity target = machine(world, new BlockPos(0, 64, 0));
        NBTTagCompound defaults = clipboard(target);
        NBTTagCompound populated = defaults.copy();
        configureMachine(populated.getCompoundTag("Settings"));
        assertTrue(paste(world, target, populated));
        NBTTagCompound cleared = defaults.copy();
        cleared.getCompoundTag("Settings").setInteger("Circuit", 0);
        assertTrue(paste(world, target, cleared));
        assertEquals(0, clipboard(target).getCompoundTag("Settings").getInteger("Circuit"));
        ConfigurationTransaction transaction = prepare(world, target, defaults);
        defaults.getCompoundTag("Settings").setInteger("Circuit", 32);
        assertTrue(transaction.execute());
        assertEquals(-1, clipboard(target).getCompoundTag("Settings").getInteger("Circuit"));
        assertFalse(target.isAutoOutputItems());
        assertFalse(target.isAutoOutputFluids());
        assertFalse(target.isAllowInputFromOutputSideItems());
        assertFalse(target.isAllowInputFromOutputSideFluids());
        assertFalse(target.isMuffled());
    }

    @Test
    public void strictPayloadRejectsUnexpectedTypesKeysRangesAndFamiliesBeforeMutation() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        SimpleMachineMetaTileEntity target = machine(world, new BlockPos(0, 64, 0));
        NBTTagCompound before = clipboard(target);
        List<Consumer<NBTTagCompound>> corruptions = Arrays.asList(
                data -> data.setInteger("Version", 2),
                data -> data.setLong("Version", 1),
                data -> data.setString("Kind", "pipe"),
                data -> data.setString("Schema", "addon:anything"),
                data -> data.setTag("Inventory", new NBTTagCompound()),
                data -> data.getCompoundTag("Settings").setInteger("Circuit", 33),
                data -> data.getCompoundTag("Settings").setInteger("Circuit", -2),
                data -> data.getCompoundTag("Settings").setByte("AutoItems", (byte) 2),
                data -> data.getCompoundTag("Settings").setInteger("AutoFluids", 1),
                data -> data.getCompoundTag("Settings").setInteger("Front", 6),
                data -> data.getCompoundTag("Settings").setInteger("Front", EnumFacing.UP.getIndex()),
                data -> data.getCompoundTag("Settings").setInteger("ItemOutput", EnumFacing.NORTH.getIndex()),
                data -> data.getCompoundTag("Settings").removeTag("InputFluids"),
                data -> data.getCompoundTag("Covers").removeTag("5"));
        for (Consumer<NBTTagCompound> corruption : corruptions) {
            NBTTagCompound malformed = before.copy();
            corruption.accept(malformed);
            assertThrows(IllegalArgumentException.class, () -> prepare(world, target, malformed));
            assertEquals(before, clipboard(target));
        }
    }

    @Test
    public void matchedCoverScalarsCopyWhileFiltersStayWithDestination() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        SimpleMachineMetaTileEntity source = machine(world, new BlockPos(0, 64, 0));
        SimpleMachineMetaTileEntity target = machine(world, new BlockPos(2, 64, 0));
        addCover(source, conveyorDefinition, EnumFacing.WEST);
        addCover(source, pumpDefinition, EnumFacing.EAST);
        CoverConveyor conveyor = (CoverConveyor) addCover(target, conveyorDefinition, EnumFacing.WEST);
        CoverPump pump = (CoverPump) addCover(target, pumpDefinition, EnumFacing.EAST);
        conveyor.getItemFilterContainer().setFilterStack(MetaItems.ITEM_FILTER.getStackForm());
        pump.getFluidFilterContainer().setFilterStack(MetaItems.FLUID_FILTER.getStackForm());
        conveyor.getItemFilterContainer().setBlacklistFilter(true);
        pump.getFluidFilterContainer().setBlacklistFilter(true);
        ((SimpleItemFilter) conveyor.getItemFilterContainer().getFilter()).getFilterReader()
                .setStackInSlot(0, new ItemStack(Items.GOLD_INGOT));
        ((SimpleFluidFilter) pump.getFluidFilterContainer().getFilter()).getFilterReader().getFluidTank(0)
                .setFluid(new FluidStack(FluidRegistry.LAVA, 1));
        NBTTagCompound itemFilter = conveyor.getItemFilterContainer().serializeNBT().copy();
        NBTTagCompound fluidFilter = pump.getFluidFilterContainer().serializeNBT().copy();
        // Filter getters lazily create NBT; confirm the configured snapshots are stable before the paste.
        assertEquals(itemFilter, conveyor.getItemFilterContainer().serializeNBT());
        assertEquals(fluidFilter, pump.getFluidFilterContainer().serializeNBT());
        NBTTagCompound data = clipboard(source);
        configureMachine(data.getCompoundTag("Settings"));
        for (EnumFacing side : new EnumFacing[] { EnumFacing.WEST, EnumFacing.EAST }) {
            NBTTagCompound cover = coverSettings(data, side);
            cover.setInteger("Rate", side == EnumFacing.WEST ? 7 : 12345);
            cover.setInteger("Mode", 0);
            cover.setInteger("Manual", 1);
            cover.setInteger("Distribution", 1);
            cover.setBoolean("Working", false);
        }
        // Preserve the exact mB rate even when the presentation unit is buckets.
        coverSettings(data, EnumFacing.EAST).setInteger("Bucket", CoverPump.BucketMode.BUCKET.ordinal());
        assertTrue(paste(world, target, data));
        assertEquals(data, clipboard(target));
        assertEquals(itemFilter, conveyor.getItemFilterContainer().serializeNBT());
        assertEquals(fluidFilter, pump.getFluidFilterContainer().serializeNBT());
        coverSettings(data, EnumFacing.EAST).setInteger("Bucket", CoverPump.BucketMode.MILLI_BUCKET.ordinal());
        coverSettings(data, EnumFacing.EAST).setInteger("Rate", 2001);
        assertTrue(paste(world, target, data));
        assertEquals(2001, pump.getTransferRate());
        assertEquals(fluidFilter, pump.getFluidFilterContainer().serializeNBT());
    }

    @Test
    public void lastCoverRejectionAndDeniedFaceLeaveWholeMachineUnchanged() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        SimpleMachineMetaTileEntity target = machine(world, new BlockPos(0, 64, 0));
        addCover(target, conveyorDefinition, EnumFacing.WEST);
        addCover(target, pumpDefinition, EnumFacing.EAST);
        NBTTagCompound before = clipboard(target);
        NBTTagCompound bad = before.copy();
        configureMachine(bad.getCompoundTag("Settings"));
        coverSettings(bad, EnumFacing.EAST).setInteger("Rate", 64001);
        assertThrows(IllegalArgumentException.class, () -> prepare(world, target, bad));
        assertEquals(before, clipboard(target));
        NBTTagCompound wrongLayout = before.copy();
        wrongLayout.getCompoundTag("Covers").setTag("5", new NBTTagCompound());
        assertThrows(IllegalArgumentException.class, () -> prepare(world, target, wrongLayout));
        ConfigurationContext denied = new ConfigurationContext(world, (pos, face) -> face != EnumFacing.EAST);
        assertThrows(IllegalArgumentException.class, () -> ConfigurationClipboard.prepare(before, "machine",
                target.getConfigurationAdapter(), target, denied, new ConfigurationTransaction()));
        assertEquals(before, clipboard(target));
    }

    @Test
    public void unknownMachineAndCoverSubclassesMustExplicitlyOptIn() {
        SimpleMachineMetaTileEntity unknown = new SimpleMachineMetaTileEntity(
                GTUtility.gregtechId("configuration_unknown"), recipeMap, null, 1, true) {};
        assertNull(unknown.getConfigurationAdapter());
        SimpleMachineMetaTileEntityResizable resizable = new SimpleMachineMetaTileEntityResizable(
                GTUtility.gregtechId("configuration_resizable"), recipeMap, 2, 2, null, 2);
        assertNotNull(resizable.getConfigurationAdapter());
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        SimpleMachineMetaTileEntity machine = machine(world, new BlockPos(0, 64, 0));
        CoverConveyor unknownCover = new CoverConveyor(conveyorDefinition, machine, EnumFacing.EAST, 1, 64) {};
        assertNull(unknownCover.getConfigurationAdapter());
        machine.addCover(EnumFacing.EAST, unknownCover);
        assertThrows(IllegalArgumentException.class, () -> clipboard(machine));
    }

    private static SimpleMachineMetaTileEntity machine(ConfigurationTestWorld world, BlockPos pos) {
        SimpleMachineMetaTileEntity machine = new SimpleMachineMetaTileEntity(
                GTUtility.gregtechId("configuration_machine"), recipeMap, null, 1, true);
        MetaTileEntityHolder holder = new MetaTileEntityHolder() {

            {
                setRawMetaTileEntity(machine);
            }
        };
        world.put(pos, holder, Blocks.AIR.getDefaultState());
        return machine;
    }

    private static NBTTagCompound clipboard(SimpleMachineMetaTileEntity machine) {
        return ConfigurationClipboard.capture("machine", machine.getConfigurationAdapter(), machine);
    }

    private static Cover addCover(SimpleMachineMetaTileEntity machine, CoverDefinition definition, EnumFacing side) {
        Cover cover = definition.createCover(machine, side);
        machine.addCover(side, cover);
        return cover;
    }

    private static NBTTagCompound coverSettings(NBTTagCompound clipboard, EnumFacing side) {
        return clipboard.getCompoundTag("Covers").getCompoundTag(Integer.toString(side.getIndex()))
                .getCompoundTag("Settings");
    }

    private static void configureMachine(NBTTagCompound data) {
        data.setInteger("Front", EnumFacing.EAST.getIndex());
        data.setInteger("ItemOutput", EnumFacing.UP.getIndex());
        data.setInteger("FluidOutput", EnumFacing.DOWN.getIndex());
        data.setBoolean("AutoItems", true);
        data.setBoolean("AutoFluids", true);
        data.setBoolean("InputItems", true);
        data.setBoolean("InputFluids", true);
        data.setBoolean("Muffled", true);
        data.setInteger("Circuit", 32);
    }

    private static ConfigurationTransaction prepare(ConfigurationTestWorld world, SimpleMachineMetaTileEntity target,
                                                    NBTTagCompound data) {
        ConfigurationTransaction transaction = new ConfigurationTransaction();
        ConfigurationClipboard.prepare(data, "machine", target.getConfigurationAdapter(), target,
                new ConfigurationContext(world, (pos, side) -> true), transaction);
        return transaction;
    }

    private static boolean paste(ConfigurationTestWorld world, SimpleMachineMetaTileEntity target,
                                 NBTTagCompound data) {
        return prepare(world, target, data).execute();
    }
}
