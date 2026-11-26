package gregtech.datafix.impl;

import gregtech.datafix.migration.lib.MTEDataMigrator;

import org.jetbrains.annotations.NotNull;

import static gregtech.api.GTValues.MODID;
import static gregtech.common.metatileentities.MetaTileEntities.TRANSFORMER;

/**
 * The ID reallocation moved three kinds of machine items to IDs nothing registers: transformers to 5000+, 5015+ and
 * 5030+ while they are registered at 1000+, 1015+ and 1030+; pumps to 5530-5533 while they kept 1530-1533; and the
 * creative energy source to 5640 while it is registered at 5650. Nothing has ever been registered at those targets,
 * so items there are moved to their machine's ID.
 */
public final class V5ReallocatedMachineIdFixer {

    private V5ReallocatedMachineIdFixer() {}

    public static void apply(@NotNull MTEDataMigrator dataMigrator) {
        for (int i = 0; i < TRANSFORMER.length; i++) {
            dataMigrator.migrateMTEMeta(MODID, 5000 + i, 1000 + i);
            dataMigrator.migrateMTEMeta(MODID, 5015 + i, 1015 + i);
            dataMigrator.migrateMTEMeta(MODID, 5030 + i, 1030 + i);
        }
        for (int i = 0; i < 4; i++) {
            dataMigrator.migrateMTEMeta(MODID, 5530 + i, 1530 + i);
        }
        dataMigrator.migrateMTEMeta(MODID, 5640, 5650);
    }
}
