package gregtech.datafix.migration.lib;

import gregtech.Bootstrap;
import gregtech.datafix.impl.V2PostMTEReallocFixer;
import gregtech.datafix.impl.V4WorldAcceleratorIdFixer;
import gregtech.datafix.impl.V5ReallocatedMachineIdFixer;
import gregtech.datafix.migration.impl.MigrateMTEItems;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.datafix.DataFixer;
import net.minecraftforge.common.util.CompoundDataFixer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReallocationIdMigrationTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void reallocationMovesEachAcceleratorToTheIdItIsRegisteredAt() {
        var fix = new MigrateMTEItems(migrator(V2PostMTEReallocFixer::apply));
        for (int tier = 1; tier <= 8; tier++) {
            // before the reallocation, LV was 1390
            assertEquals(item(1390 + tier), fix.fixTagCompound(item(1389 + tier)));
        }
        // the machine hatch held 1398 before the reallocation
        assertEquals(item(11495), fix.fixTagCompound(item(1398)));
    }

    @Test
    void reallocationMovesTransformersPumpsAndCreativeEnergyToTheirRegisteredIds() {
        var fix = new MigrateMTEItems(migrator(V2PostMTEReallocFixer::apply));
        for (int tier = 0; tier < 14; tier++) {
            assertEquals(item(1000 + tier), fix.fixTagCompound(item(1270 + tier)));
            assertEquals(item(1015 + tier), fix.fixTagCompound(item(1730 + tier)));
            assertEquals(item(1030 + tier), fix.fixTagCompound(item(1285 + tier)));
        }
        for (int tier = 0; tier < 4; tier++) {
            // pumps were not moved
            assertEquals(item(1530 + tier), fix.fixTagCompound(item(1530 + tier)));
        }
        assertEquals(item(5650), fix.fixTagCompound(item(1650)));
        // the multiblocks that held 1000+ before the reallocation still leave for 10000+
        assertEquals(item(10000), fix.fixTagCompound(item(1000)));
    }

    @Test
    void acceleratorsMovedToUnregisteredIdsReturnToTheirOwn() {
        var fix = new MigrateMTEItems(migrator(V4WorldAcceleratorIdFixer::apply));
        for (int tier = 1; tier <= 8; tier++) {
            assertEquals(item(1390 + tier), fix.fixTagCompound(item(5389 + tier)));
            // a current world's accelerator stays where it is
            assertEquals(item(1390 + tier), fix.fixTagCompound(item(1390 + tier)));
        }
    }

    @Test
    void machinesMovedToUnregisteredIdsReturnToTheirOwn() {
        var fix = new MigrateMTEItems(migrator(V5ReallocatedMachineIdFixer::apply));
        for (int tier = 0; tier < 14; tier++) {
            assertEquals(item(1000 + tier), fix.fixTagCompound(item(5000 + tier)));
            assertEquals(item(1015 + tier), fix.fixTagCompound(item(5015 + tier)));
            assertEquals(item(1030 + tier), fix.fixTagCompound(item(5030 + tier)));
            // a current world's transformer stays where it is
            assertEquals(item(1000 + tier), fix.fixTagCompound(item(1000 + tier)));
        }
        for (int tier = 0; tier < 4; tier++) {
            assertEquals(item(1530 + tier), fix.fixTagCompound(item(5530 + tier)));
        }
        assertEquals(item(5650), fix.fixTagCompound(item(5640)));
        // the miners registered after the transformer targets are untouched
        assertEquals(item(5045), fix.fixTagCompound(item(5045)));
    }

    private static MTEDataMigrator migrator(Consumer<MTEDataMigrator> fixes) {
        var fixer = new CompoundDataFixer(new DataFixer(1343));
        var migrator = new MTEDataMigrator(fixer.init("gregtech", 5), 5, new MTERegistriesMigrator());
        fixes.accept(migrator);
        return migrator;
    }

    private static NBTTagCompound item(int metadata) {
        var item = new NBTTagCompound();
        item.setString("id", "gregtech:mte");
        item.setShort("Damage", (short) metadata);
        item.setInteger("Count", 1);
        return item;
    }
}
