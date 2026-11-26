package gregtech.datafix.migration.lib;

import gregtech.Bootstrap;
import gregtech.api.metatileentity.multiblock.IBatteryData;
import gregtech.common.metatileentities.electric.MetaTileEntityHull;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityPowerSubstation.PowerStationEnergyBank;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMaintenanceHatch;
import gregtech.common.metatileentities.storage.MetaTileEntityCreativeChest;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumTank;
import gregtech.datafix.impl.LegacyMachineData;
import gregtech.datafix.impl.V6LegacyDataFixer;

import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.datafix.DataFixer;
import net.minecraftforge.common.util.CompoundDataFixer;
import net.minecraftforge.common.util.ModFixs;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static gregtech.api.util.GTUtility.gregtechId;
import static gregtech.common.metatileentities.multi.electric.MetaTileEntityPowerSubstation.PASSIVE_DRAIN_DIVISOR;
import static org.junit.jupiter.api.Assertions.*;

/** Feeds machine NBT in the formats loading used to convert through the V6 fixes, then loads the result. */
class LegacyMachineDataMigrationTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void tankContainerSlotsMoveToTheMachineInventories() {
        NBTTagCompound machine = new NBTTagCompound();
        machine.setTag("ImportInventory", new NBTTagList());
        machine.setTag("ExportInventory", new NBTTagList());
        machine.setTag("ContainerInventory",
                handler(2, item(0, "minecraft:bucket", 1), item(1, "minecraft:water_bucket", 1)));
        LegacyMachineData.tankContainerInventory(machine);
        assertFalse(machine.hasKey("ContainerInventory"));
        assertUnchangedBy(LegacyMachineData::tankContainerInventory, machine);

        MetaTileEntityQuantumTank tank = new MetaTileEntityQuantumTank(gregtechId("legacy_tank"), 1, 4000);
        tank.readFromNBT(machine);
        assertEquals(Items.BUCKET, tank.getImportItems().getStackInSlot(0).getItem());
        assertEquals(Items.WATER_BUCKET, tank.getExportItems().getStackInSlot(0).getItem());
    }

    @Test
    void partialVoidingBecomesVoiding() {
        NBTTagCompound partial = new NBTTagCompound();
        partial.setBoolean("IsVoiding", false);
        partial.setBoolean("IsPartialVoiding", true);
        LegacyMachineData.quantumPartialVoiding(partial);
        assertTrue(partial.getBoolean("IsVoiding"));
        assertFalse(partial.hasKey("IsPartialVoiding"));

        NBTTagCompound off = new NBTTagCompound();
        off.setBoolean("IsVoiding", false);
        off.setBoolean("IsPartialVoiding", false);
        LegacyMachineData.quantumPartialVoiding(off);
        assertEquals(Set.of("IsVoiding"), off.getKeySet());
        assertFalse(off.getBoolean("IsVoiding"));
    }

    @Test
    void creativeChestKeepsItsItem() {
        NBTTagCompound machine = new NBTTagCompound();
        machine.setTag("ItemStackHandler", handler(1, item(0, "minecraft:diamond", 1)));
        LegacyMachineData.creativeChestItem(machine);
        assertFalse(machine.hasKey("ItemStackHandler"));
        assertEquals(1L, machine.getLong("ItemAmount"));
        assertUnchangedBy(LegacyMachineData::creativeChestItem, machine);

        MetaTileEntityCreativeChest chest = new MetaTileEntityCreativeChest(gregtechId("legacy_creative_chest"));
        chest.readFromNBT(machine);
        assertEquals(Items.DIAMOND, chest.getTypeValue().getStackInSlot(0).getItem());

        // a chest that also holds an item in the current format keeps that one
        NBTTagCompound both = machine.copy();
        both.setTag("ItemStackHandler", handler(1, item(0, "minecraft:emerald", 1)));
        LegacyMachineData.creativeChestItem(both);
        assertEquals(machine, both);
    }

    @Test
    void powerSubstationBankKeepsItsStoredEnergyAndDrain() {
        long[] capacities = { 1000L, PASSIVE_DRAIN_DIVISOR * 3 + 7, Long.MAX_VALUE };
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setInteger("Size", capacities.length);
        for (int i = 0; i < capacities.length; i++) {
            NBTTagCompound battery = new NBTTagCompound();
            battery.setLong("Max", capacities[i]);
            // an empty battery saved no Stored
            if (i != 1) battery.setLong("Stored", i == 0 ? 100L : Long.MAX_VALUE);
            legacy.setTag(String.valueOf(i), battery);
        }
        NBTTagCompound machine = new NBTTagCompound();
        machine.setTag("EnergyBank", legacy);
        LegacyMachineData.powerSubstationBank(machine);

        NBTTagCompound fixed = machine.getCompoundTag("EnergyBank");
        assertEquals(Set.of("Stored0", "Stored1", "drain", "drainMod"), fixed.getKeySet());
        assertUnchangedBy(LegacyMachineData::powerSubstationBank, machine);
        PowerStationEnergyBank bank = new PowerStationEnergyBank(fixed);
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(100)), bank.getStored());
        PowerStationEnergyBank expected = new PowerStationEnergyBank(
                List.of(battery(capacities[0]), battery(capacities[1]), battery(capacities[2])));
        assertEquals(expected.getPassiveDrainPerTick(), bank.getPassiveDrainPerTick());
    }

    @Test
    void maintenanceHatchTapeMovesToItsOwnInventory() {
        NBTTagCompound machine = new NBTTagCompound();
        NBTTagList imported = new NBTTagList();
        imported.appendTag(item(0, "minecraft:slime_ball", 3));
        machine.setTag("ImportInventory", imported);
        LegacyMachineData.maintenanceTape(machine);
        assertTrue(machine.getTagList("ImportInventory", 10).isEmpty());
        assertUnchangedBy(LegacyMachineData::maintenanceTape, machine);

        MetaTileEntityMaintenanceHatch hatch = new MetaTileEntityMaintenanceHatch(gregtechId("legacy_maintenance"),
                false);
        hatch.readFromNBT(machine);
        assertEquals(Items.SLIME_BALL, hatch.getItemInventory().getStackInSlot(0).getItem());
        assertEquals(3, hatch.getItemInventory().getStackInSlot(0).getCount());

        // a hatch saved since its tape had an inventory keeps that inventory
        NBTTagCompound current = machine.copy();
        current.setTag("ImportInventory", imported.copy());
        LegacyMachineData.maintenanceTape(current);
        assertEquals(imported, current.getTagList("ImportInventory", 10));
    }

    @Test
    void fixesAreChosenByMachineClassAndRegisteredByMachineId() {
        assertNull(V6LegacyDataFixer.machineFix(new MetaTileEntityHull(gregtechId("legacy_hull"), 1)));
        MetaTileEntityQuantumTank tank = new MetaTileEntityQuantumTank(gregtechId("legacy_routed_tank"), 1, 4000);
        ModFixs fixes = new CompoundDataFixer(new DataFixer(1343)).init("gregtech", 6);
        MTEDataMigrator migrator = new MTEDataMigrator(fixes, 6, new MTERegistriesMigrator());
        V6LegacyDataFixer.apply(migrator, fixes, List.of(tank));

        NBTTagCompound machine = new NBTTagCompound();
        machine.setBoolean("IsPartialVoiding", true);
        machine.setTag("ContainerInventory", handler(2, item(1, "minecraft:bucket", 1)));
        NBTTagCompound untouched = machine.copy();
        migrator.fixMTEData(gregtechId("legacy_hull"), untouched);
        assertEquals(machine, untouched);

        migrator.fixMTEData(tank.metaTileEntityId, machine);
        assertEquals(Set.of("IsVoiding", "ExportInventory"), machine.getKeySet());
        assertTrue(machine.getBoolean("IsVoiding"));
        assertEquals(1, machine.getTagList("ExportInventory", 10).tagCount());
    }

    private static void assertUnchangedBy(@NotNull Consumer<NBTTagCompound> fix, @NotNull NBTTagCompound current) {
        NBTTagCompound again = current.copy();
        fix.accept(again);
        assertEquals(current, again, "a tag in the current format is left alone");
    }

    static @NotNull NBTTagCompound item(int slot, @NotNull String id, int count) {
        NBTTagCompound item = new NBTTagCompound();
        item.setInteger("Slot", slot);
        item.setString("id", id);
        item.setByte("Count", (byte) count);
        item.setShort("Damage", (short) 0);
        return item;
    }

    static @NotNull NBTTagCompound handler(int size, @NotNull NBTTagCompound... items) {
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound item : items) {
            list.appendTag(item);
        }
        NBTTagCompound handler = new NBTTagCompound();
        handler.setTag("Items", list);
        handler.setInteger("Size", size);
        return handler;
    }

    private static @NotNull IBatteryData battery(long capacity) {
        return new IBatteryData() {

            @Override
            public int getTier() {
                return 1;
            }

            @Override
            public long getCapacity() {
                return capacity;
            }

            @Override
            public @NotNull String getBatteryName() {
                return "legacy";
            }
        };
    }
}
