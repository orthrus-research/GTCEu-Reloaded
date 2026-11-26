package gregtech.datafix.impl;

import gregtech.api.GTValues;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.metatileentities.multi.MetaTileEntityPumpHatch;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityPowerSubstation;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityFluidHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMaintenanceHatch;
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntitySteamHatch;
import gregtech.common.metatileentities.storage.MetaTileEntityCreativeChest;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumStorage;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumTank;
import gregtech.datafix.migration.impl.MigrateCoverData;
import gregtech.datafix.migration.lib.MTEDataMigrator;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.datafix.FixTypes;
import net.minecraftforge.common.util.ModFixs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Rewrites, once, machine and cover data that loading used to convert from older formats every time: tank item
 * slots, quantum tank partial voiding, power substation banks, maintenance hatch tape, creative chest items, cover
 * filters and fluid regulator amounts. Machine fixes are chosen by class, so they also reach addon subclasses that
 * inherited the old conversions; cover fixes run on the covers of every machine and pipe.
 * <p>
 * Item stacks are not converted: the datafixers do not reach the inventories most of them are kept in, so the few
 * item-data conversions stay where the item data is read.
 */
public final class V6LegacyDataFixer {

    private V6LegacyDataFixer() {}

    /**
     * @param dataMigrator a migrator for this version, made with {@code fixer}
     * @param fixer        GTCEu's fixer
     * @param machines     every registered MTE
     */
    public static void apply(@NotNull MTEDataMigrator dataMigrator, @NotNull ModFixs fixer,
                             @NotNull Iterable<? extends MetaTileEntity> machines) {
        for (MetaTileEntity machine : machines) {
            Consumer<NBTTagCompound> fix = machineFix(machine);
            if (fix != null) {
                dataMigrator.migrateMTEData(machine.metaTileEntityId, fix);
            }
        }
        fixer.registerFix(FixTypes.CHUNK,
                new MigrateCoverData(dataMigrator.fixVersion(), V6LegacyDataFixer::fixCover));
    }

    /**
     * @param machine a registered MTE
     * @return the fixes its saved data needs, or null when it needs none
     */
    public static @Nullable Consumer<@NotNull NBTTagCompound> machineFix(@NotNull MetaTileEntity machine) {
        Consumer<NBTTagCompound> fix = null;
        if (machine instanceof MetaTileEntityQuantumTank || machine instanceof MetaTileEntityFluidHatch ||
                machine instanceof MetaTileEntitySteamHatch || machine instanceof MetaTileEntityPumpHatch) {
            fix = and(fix, LegacyMachineData::tankContainerInventory);
        }
        if (machine instanceof MetaTileEntityQuantumStorage<?>) {
            fix = and(fix, LegacyMachineData::quantumPartialVoiding);
        }
        if (machine instanceof MetaTileEntityCreativeChest) {
            fix = and(fix, LegacyMachineData::creativeChestItem);
        }
        if (machine instanceof MetaTileEntityPowerSubstation) {
            fix = and(fix, LegacyMachineData::powerSubstationBank);
        }
        if (machine instanceof MetaTileEntityMaintenanceHatch) {
            fix = and(fix, LegacyMachineData::maintenanceTape);
        }
        return fix;
    }

    private static @NotNull Consumer<NBTTagCompound> and(@Nullable Consumer<NBTTagCompound> first,
                                                         @NotNull Consumer<NBTTagCompound> second) {
        return first == null ? second : first.andThen(second);
    }

    /**
     * Fixes one saved cover.
     *
     * @param coverId the cover's ID
     * @param cover   the cover's tag
     */
    public static void fixCover(@NotNull ResourceLocation coverId, @NotNull NBTTagCompound cover) {
        LegacyFilterData.fixCover(coverId, cover);
        if (GTValues.MODID.equals(coverId.getNamespace()) && coverId.getPath().startsWith("fluid.regulator.")) {
            LegacyFilterData.fixRegulatorAmount(cover);
        }
    }
}
