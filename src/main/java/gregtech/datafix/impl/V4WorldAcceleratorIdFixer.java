package gregtech.datafix.impl;

import gregtech.datafix.migration.lib.MTEDataMigrator;

import org.jetbrains.annotations.NotNull;

import static gregtech.api.GTValues.MODID;

/**
 * The ID reallocation moved the LV-UV world accelerators from 1390-1397 to 5390-5397, while they are registered at
 * 1391-1398. Nothing has ever been registered at 5390-5397, so items there are moved to their accelerator's ID.
 */
public final class V4WorldAcceleratorIdFixer {

    private V4WorldAcceleratorIdFixer() {}

    public static void apply(@NotNull MTEDataMigrator dataMigrator) {
        for (int i = 0; i < 8; i++) {
            dataMigrator.migrateMTEMeta(MODID, 5390 + i, 1391 + i);
        }
    }
}
