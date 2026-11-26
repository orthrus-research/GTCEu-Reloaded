package gregtech.api.configuration;

import gregtech.api.cover.Cover;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.cover.CoverSetting;
import gregtech.api.cover.CoverSettingType;
import gregtech.api.cover.CoverSettings;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.SimpleMachineMetaTileEntity;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.common.covers.CoverConveyor;
import gregtech.common.covers.CoverPump;
import gregtech.common.covers.DistributionMode;
import gregtech.common.covers.ManualImportExportMode;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import com.cleanroommc.modularui.value.sync.EnumSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Every conveyor and pump setting round-trips through NBT and the card from its single declaration. */
public class CoverSettingsTest {

    private static RecipeMap<SimpleRecipeBuilder> recipeMap;
    private static CoverDefinition conveyorDefinition;
    private static CoverDefinition pumpDefinition;
    private static ConfigurationTestItems testItems;

    @BeforeAll
    public static void bootstrap() {
        testItems = ConfigurationTestItems.setup();
        recipeMap = new RecipeMapBuilder<>("cover_settings_test", new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1).build();
        conveyorDefinition = new CoverDefinition(GTUtility.gregtechId("cover_settings_conveyor"),
                (definition, holder, side) -> new CoverConveyor(definition, holder, side, 1, 64), ItemStack.EMPTY);
        pumpDefinition = new CoverDefinition(GTUtility.gregtechId("cover_settings_pump"),
                (definition, holder, side) -> new CoverPump(definition, holder, side, 1, 64000), ItemStack.EMPTY);
    }

    @AfterAll
    public static void restoreItems() {
        testItems.restore();
    }

    @Test
    public void conveyorSettingsRoundTripThroughNBTAndTheCard() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        SimpleMachineMetaTileEntity machine = machine(world, new BlockPos(0, 64, 0));
        CoverConveyor source = (CoverConveyor) addCover(machine, conveyorDefinition, EnumFacing.WEST);
        source.setTransferRate(7);
        source.setConveyorMode(CoverConveyor.ConveyorMode.IMPORT);
        CoverConveyor.MANUAL_IMPORT_EXPORT_MODE.set(source, ManualImportExportMode.UNFILTERED);
        source.setDistributionMode(DistributionMode.ROUND_ROBIN_PRIO);
        source.setWorkingEnabled(false);

        NBTTagCompound saved = new NBTTagCompound();
        source.writeToNBT(saved);
        // the keys saved worlds already have
        assertEquals(7, saved.getInteger("TransferRate"));
        assertEquals(CoverConveyor.ConveyorMode.IMPORT.ordinal(), saved.getInteger("ConveyorMode"));
        assertEquals(ManualImportExportMode.UNFILTERED.ordinal(), saved.getInteger("ManualImportExportMode"));
        assertEquals(DistributionMode.ROUND_ROBIN_PRIO.ordinal(), saved.getInteger("DistributionMode"));
        assertFalse(saved.getBoolean("WorkingAllowed"));
        CoverConveyor loaded = new CoverConveyor(conveyorDefinition, machine, EnumFacing.EAST, 1, 64);
        loaded.readFromNBT(saved);
        assertSameSettings(CoverConveyor.SETTINGS, source, loaded);

        // the keys saved cards already have
        NBTTagCompound card = source.getConfigurationAdapter().capture();
        assertEquals(Set.of("Rate", "Mode", "Manual", "Distribution", "Working"), card.getKeySet());
        CoverConveyor target = (CoverConveyor) addCover(machine, conveyorDefinition, EnumFacing.EAST);
        assertTrue(paste(world, target.getConfigurationAdapter(), card));
        assertSameSettings(CoverConveyor.SETTINGS, source, target);
        assertEquals(card, target.getConfigurationAdapter().capture());
    }

    @Test
    public void pumpSettingsRoundTripThroughNBTAndTheCard() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        SimpleMachineMetaTileEntity machine = machine(world, new BlockPos(0, 64, 0));
        CoverPump source = (CoverPump) addCover(machine, pumpDefinition, EnumFacing.WEST);
        source.setBucketMode(CoverPump.BucketMode.BUCKET);
        source.setTransferRateMilliBuckets(12345);
        source.setPumpMode(CoverPump.PumpMode.IMPORT);
        CoverPump.MANUAL_IMPORT_EXPORT_MODE.set(source, ManualImportExportMode.FILTERED);
        CoverPump.DISTRIBUTION_MODE.set(source, DistributionMode.ROUND_ROBIN_GLOBAL);
        source.setWorkingEnabled(false);

        NBTTagCompound saved = new NBTTagCompound();
        source.writeToNBT(saved);
        assertEquals(12345, saved.getInteger("TransferRate"));
        assertEquals(CoverPump.PumpMode.IMPORT.ordinal(), saved.getInteger("PumpMode"));
        assertEquals(CoverPump.BucketMode.BUCKET.ordinal(), saved.getInteger("BucketMode"));
        assertEquals(ManualImportExportMode.FILTERED.ordinal(), saved.getInteger("ManualImportExportMode"));
        assertEquals(DistributionMode.ROUND_ROBIN_GLOBAL.ordinal(), saved.getInteger("DistributionMode"));
        assertFalse(saved.getBoolean("WorkingAllowed"));
        CoverPump loaded = new CoverPump(pumpDefinition, machine, EnumFacing.EAST, 1, 64000);
        loaded.readFromNBT(saved);
        assertSameSettings(CoverPump.SETTINGS, source, loaded);

        NBTTagCompound card = source.getConfigurationAdapter().capture();
        assertEquals(Set.of("Rate", "Mode", "Manual", "Distribution", "Working", "Bucket"), card.getKeySet());
        // the card keeps the exact mB rate, whatever unit the screen shows
        assertEquals(12345, card.getInteger("Rate"));
        CoverPump target = (CoverPump) addCover(machine, pumpDefinition, EnumFacing.EAST);
        assertTrue(paste(world, target.getConfigurationAdapter(), card));
        assertSameSettings(CoverPump.SETTINGS, source, target);
    }

    @Test
    public void unusableSavedValuesLoadAsDefaults() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        SimpleMachineMetaTileEntity machine = machine(world, new BlockPos(0, 64, 0));
        NBTTagCompound conveyorData = new NBTTagCompound();
        conveyorData.setInteger("TransferRate", 65);
        conveyorData.setInteger("ConveyorMode", 7);
        conveyorData.setInteger("DistributionMode", -1);
        conveyorData.setString("WorkingAllowed", "yes");
        CoverConveyor conveyor = new CoverConveyor(conveyorDefinition, machine, EnumFacing.WEST, 1, 64);
        NBTTagCompound configured = new NBTTagCompound();
        configured.setInteger("TransferRate", 3);
        configured.setInteger("ConveyorMode", CoverConveyor.ConveyorMode.IMPORT.ordinal());
        configured.setBoolean("WorkingAllowed", false);
        conveyor.readFromNBT(configured);
        assertEquals(CoverConveyor.ConveyorMode.IMPORT, conveyor.getConveyorMode());
        conveyor.readFromNBT(conveyorData);
        assertDefaults(CoverConveyor.SETTINGS, conveyor);
        assertEquals(64, conveyor.getTransferRate());

        NBTTagCompound pumpData = new NBTTagCompound();
        pumpData.setInteger("TransferRate", 0);
        pumpData.setInteger("PumpMode", -3);
        pumpData.setInteger("BucketMode", 9);
        pumpData.setInteger("ManualImportExportMode", ManualImportExportMode.VALUES.length);
        CoverPump pump = new CoverPump(pumpDefinition, machine, EnumFacing.EAST, 1, 64000);
        pump.readFromNBT(pumpData);
        assertDefaults(CoverPump.SETTINGS, pump);
        assertEquals(64000, pump.getTransferRateMilliBuckets());
    }

    @Test
    public void cardRejectsMissingUnknownMistypedAndOutOfRangeValues() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        SimpleMachineMetaTileEntity machine = machine(world, new BlockPos(0, 64, 0));
        CoverPump pump = (CoverPump) addCover(machine, pumpDefinition, EnumFacing.WEST);
        ConfigurationAdapter adapter = pump.getConfigurationAdapter();
        NBTTagCompound before = adapter.capture();
        NBTTagCompound missing = before.copy();
        missing.removeTag("Bucket");
        NBTTagCompound unknown = before.copy();
        unknown.setInteger("Extra", 1);
        NBTTagCompound mistyped = before.copy();
        mistyped.setLong("Mode", 0);
        NBTTagCompound outOfRange = before.copy();
        outOfRange.setInteger("Rate", 64001);
        NBTTagCompound badEnum = before.copy();
        badEnum.setInteger("Distribution", DistributionMode.VALUES.length);
        for (NBTTagCompound bad : new NBTTagCompound[] { missing, unknown, mistyped, outOfRange, badEnum }) {
            assertThrows(IllegalArgumentException.class, () -> adapter.prepare(bad,
                    new ConfigurationContext(world, (pos, side) -> true), new ConfigurationTransaction()));
        }
        assertEquals(before, adapter.capture());
    }

    @Test
    public void syncValuesReadAndChangeTheCoverThroughItsSetters() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        SimpleMachineMetaTileEntity machine = machine(world, new BlockPos(0, 64, 0));
        CoverConveyor conveyor = (CoverConveyor) addCover(machine, conveyorDefinition, EnumFacing.WEST);
        IntSyncValue rate = CoverConveyor.TRANSFER_RATE.syncValue(conveyor);
        EnumSyncValue<CoverConveyor.ConveyorMode> mode = CoverConveyor.CONVEYOR_MODE.syncValue(conveyor);
        rate.updateCacheFromSource(true);
        assertEquals(64, rate.getIntValue());
        rate.setIntValue(9, true, false);
        assertEquals(9, conveyor.getTransferRate());
        mode.setValue(CoverConveyor.ConveyorMode.IMPORT, true, false);
        assertEquals(CoverConveyor.ConveyorMode.IMPORT, conveyor.getConveyorMode());
        assertEquals("manual_io", CoverConveyor.MANUAL_IMPORT_EXPORT_MODE.getName());
    }

    @Test
    public void settingsRejectSharedNamesAndKeys() {
        CoverSetting<CoverConveyor, Boolean> working = CoverSetting
                .of("working", CoverSettingType.<CoverConveyor>bool(), conveyor -> true,
                        CoverConveyor::isWorkingEnabled, CoverConveyor::setWorkingEnabled)
                .saved("WorkingAllowed", CoverConveyor::setWorkingEnabled)
                .card("Working");
        CoverSetting<CoverConveyor, Boolean> sameNbtKey = CoverSetting
                .of("other", CoverSettingType.<CoverConveyor>bool(), conveyor -> true,
                        CoverConveyor::isWorkingEnabled, CoverConveyor::setWorkingEnabled)
                .saved("WorkingAllowed", CoverConveyor::setWorkingEnabled);
        assertThrows(IllegalArgumentException.class, () -> CoverSettings.of(working, sameNbtKey));
        assertThrows(IllegalArgumentException.class, () -> CoverSettings.of(working, working));
        assertThrows(IllegalArgumentException.class,
                () -> CoverSettings.of(CoverConveyor.TRANSFER_RATE, CoverConveyor.WORKING_ENABLED.card("Rate")));
    }

    private static <C extends Cover> void assertSameSettings(CoverSettings<C> settings, C expected, C actual) {
        for (CoverSetting<C, ?> setting : settings.getSettings()) {
            assertEquals(setting.get(expected), setting.get(actual), setting.getName());
        }
    }

    private static <C extends Cover> void assertDefaults(CoverSettings<C> settings, C cover) {
        for (CoverSetting<C, ?> setting : settings.getSettings()) {
            assertEquals(setting.getDefault(cover), setting.get(cover), setting.getName());
        }
    }

    private static SimpleMachineMetaTileEntity machine(ConfigurationTestWorld world, BlockPos pos) {
        SimpleMachineMetaTileEntity machine = new SimpleMachineMetaTileEntity(
                GTUtility.gregtechId("cover_settings_machine"), recipeMap, null, 1, true);
        MetaTileEntityHolder holder = new MetaTileEntityHolder() {

            {
                setRawMetaTileEntity(machine);
            }
        };
        world.put(pos, holder, Blocks.AIR.getDefaultState());
        return machine;
    }

    private static Cover addCover(SimpleMachineMetaTileEntity machine, CoverDefinition definition, EnumFacing side) {
        Cover cover = definition.createCover(machine, side);
        machine.addCover(side, cover);
        return cover;
    }

    private static boolean paste(ConfigurationTestWorld world, ConfigurationAdapter adapter, NBTTagCompound data) {
        ConfigurationTransaction transaction = new ConfigurationTransaction();
        adapter.prepare(data, new ConfigurationContext(world, (pos, side) -> true), transaction);
        return transaction.execute();
    }
}
