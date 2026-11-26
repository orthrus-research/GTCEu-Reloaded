package gregtech.datafix.migration.lib;

import gregtech.Bootstrap;
import gregtech.datafix.impl.V2PostMTEReallocFixer;
import gregtech.datafix.impl.V4WorldAcceleratorIdFixer;
import gregtech.datafix.migration.impl.MigrateMTEItems;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.datafix.DataFixer;
import net.minecraftforge.common.util.CompoundDataFixer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldAcceleratorIdMigrationTest {

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
    void acceleratorsMovedToUnregisteredIdsReturnToTheirOwn() {
        var fix = new MigrateMTEItems(migrator(V4WorldAcceleratorIdFixer::apply));
        for (int tier = 1; tier <= 8; tier++) {
            assertEquals(item(1390 + tier), fix.fixTagCompound(item(5389 + tier)));
            // a current world's accelerator stays where it is
            assertEquals(item(1390 + tier), fix.fixTagCompound(item(1390 + tier)));
        }
    }

    private static MTEDataMigrator migrator(Consumer<MTEDataMigrator> fixes) {
        var fixer = new CompoundDataFixer(new DataFixer(1343));
        var migrator = new MTEDataMigrator(fixer.init("gregtech", 4), 4, new MTERegistriesMigrator());
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
