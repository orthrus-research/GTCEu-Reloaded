package gregtech.api.capability.impl;

import gregtech.common.metatileentities.multi.electric.MetaTileEntityCrackingUnit;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityElectricBlastFurnace;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityImplosionCompressor;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeChemicalReactor;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityMultiSmelter;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityProcessingArray;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityPyrolyseOven;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityVacuumFreezer;

/** Exact core admission: inherited addon controllers retain their legacy search/routing unless they opt in. */
public final class PaintGroupSupport {

    private PaintGroupSupport() {}

    public static boolean supports(Class<?> type) {
        return type == MetaTileEntityElectricBlastFurnace.class || type == MetaTileEntityLargeChemicalReactor.class ||
                type == MetaTileEntityVacuumFreezer.class || type == MetaTileEntityImplosionCompressor.class ||
                type == MetaTileEntityPyrolyseOven.class || type == MetaTileEntityCrackingUnit.class ||
                type == MetaTileEntityMultiSmelter.class || type == MetaTileEntityProcessingArray.class;
    }
}
