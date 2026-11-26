package gregtech.loaders.recipe;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.recipes.ModHandler;
import gregtech.api.recipes.ingredients.nbtmatch.NBTCondition;
import gregtech.api.recipes.ingredients.nbtmatch.NBTMatcher;
import gregtech.api.unification.material.MarkerMaterials.Color;
import gregtech.api.unification.material.MarkerMaterials.Tier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.UnificationEntry;
import gregtech.common.blocks.BlockBatteryPart.BatteryPartType;

import net.minecraftforge.fluids.FluidStack;

import static gregtech.api.GTValues.*;
import static gregtech.api.recipes.RecipeMaps.*;
import static gregtech.api.unification.material.Materials.*;
import static gregtech.api.unification.ore.OrePrefix.*;
import static gregtech.common.blocks.MetaBlocks.BATTERY_BLOCK;
import static gregtech.common.items.MetaItems.*;

public class BatteryRecipes {

    // a crystal grows from water, faster from distilled water, then from two molten steels, the better one fastest
    private static final int[] CRYSTAL_DURATIONS = { 1800, 1200, 300, 150 };
    private static final int[] CRYSTAL_VOLTAGES = { VA[HV], 320, 256, 192 };

    public static void init() {
        standardBatteries();
        gemBatteries();
        batteryBlocks();
    }

    private static void standardBatteries() {
        // Tantalum Battery (since it doesn't fit elsewhere)
        ASSEMBLER_RECIPES.recipeBuilder()
                .input(dust, Tantalum)
                .input(foil, Manganese)
                .fluidInputs(Polyethylene.getFluid(L))
                .output(BATTERY_ULV_TANTALUM, 8)
                .duration(30).EUt(4).buildAndRegister();

        // :trol:
        ModHandler.addShapedRecipe("tantalum_capacitor", BATTERY_ULV_TANTALUM.getStackForm(2),
                " F ", "FDF", "B B",
                'F', new UnificationEntry(foil, Manganese),
                'D', new UnificationEntry(dust, Tantalum),
                'B', new UnificationEntry(bolt, Iron));

        // Battery Hull Recipes: the LV and MV hulls by hand, and each hull in the assembler from its cables, plates
        // and plastic
        ModHandler.addShapedRecipe("battery_hull_lv", BATTERY_HULL_LV.getStackForm(),
                "C", "P", "P",
                'C', new UnificationEntry(cableGtSingle, Tin),
                'P', new UnificationEntry(plate, BatteryAlloy));
        ModHandler.addShapedRecipe("battery_hull_mv", BATTERY_HULL_MV.getStackForm(),
                "C C", "PPP", "PPP",
                'C', new UnificationEntry(cableGtSingle, Copper),
                'P', new UnificationEntry(plate, BatteryAlloy));

        batteryHull(BATTERY_HULL_LV, Tin, 1, BatteryAlloy, 1, Polyethylene, L, 400, 1);
        batteryHull(BATTERY_HULL_MV, Copper, 2, BatteryAlloy, 3, Polyethylene, L * 3, 200, 2);
        batteryHull(BATTERY_HULL_MV, AnnealedCopper, 2, BatteryAlloy, 3, Polyethylene, L * 3, 200, 2);
        batteryHull(BATTERY_HULL_HV, Gold, 4, BatteryAlloy, 9, Polyethylene, 1296, 300, 4);
        batteryHull(BATTERY_HULL_SMALL_VANADIUM, Aluminium, 2, RedSteel, 2, Polytetrafluoroethylene, 144, 100, VA[HV]);
        batteryHull(BATTERY_HULL_MEDIUM_VANADIUM, Platinum, 2, RoseGold, 6, Polytetrafluoroethylene, 288, 200, VA[EV]);
        batteryHull(BATTERY_HULL_LARGE_VANADIUM, NiobiumTitanium, 2, BlueSteel, 18, Polybenzimidazole, 144, 300,
                VA[IV]);
        batteryHull(BATTERY_HULL_MEDIUM_NAQUADRIA, Naquadah, 2, Europium, 6, Polybenzimidazole, 288, 200, VA[LuV]);
        batteryHull(BATTERY_HULL_LARGE_NAQUADRIA, YttriumBariumCuprate, 2, Americium, 18, Polybenzimidazole, 576, 300,
                VA[ZPM]);

        // Battery Filling Recipes, each with its recycling back to the hull: the LV to HV batteries of each
        // chemistry, then the vanadium and naquadria ones
        battery(BATTERY_HULL_LV, BATTERY_LV_CADMIUM, Cadmium, 2, 100, 2);
        battery(BATTERY_HULL_LV, BATTERY_LV_LITHIUM, Lithium, 2, 100, 2);
        battery(BATTERY_HULL_LV, BATTERY_LV_SODIUM, Sodium, 2, 100, 2);
        battery(BATTERY_HULL_MV, BATTERY_MV_CADMIUM, Cadmium, 8, 400, 2);
        battery(BATTERY_HULL_MV, BATTERY_MV_LITHIUM, Lithium, 8, 400, 2);
        battery(BATTERY_HULL_MV, BATTERY_MV_SODIUM, Sodium, 8, 400, 2);
        battery(BATTERY_HULL_HV, BATTERY_HV_CADMIUM, Cadmium, 16, 1600, 2);
        battery(BATTERY_HULL_HV, BATTERY_HV_LITHIUM, Lithium, 16, 1600, 2);
        battery(BATTERY_HULL_HV, BATTERY_HV_SODIUM, Sodium, 16, 1600, 2);
        battery(BATTERY_HULL_SMALL_VANADIUM, BATTERY_EV_VANADIUM, Vanadium, 2, 100, VA[HV]);
        battery(BATTERY_HULL_MEDIUM_VANADIUM, BATTERY_IV_VANADIUM, Vanadium, 8, 150, 1024);
        battery(BATTERY_HULL_LARGE_VANADIUM, BATTERY_LUV_VANADIUM, Vanadium, 16, 200, VA[EV]);
        battery(BATTERY_HULL_MEDIUM_NAQUADRIA, BATTERY_ZPM_NAQUADRIA, Naquadria, 8, 250, 4096);
        battery(BATTERY_HULL_LARGE_NAQUADRIA, BATTERY_UV_NAQUADRIA, Naquadria, 16, 300, VA[IV]);
    }

    private static void gemBatteries() {
        // Energy Crystal
        MIXER_RECIPES.recipeBuilder().duration(600).EUt(VA[MV])
                .input(dust, Redstone, 5)
                .input(dust, Ruby, 4)
                .circuitMeta(1)
                .output(ENERGIUM_DUST, 9)
                .buildAndRegister();

        FluidStack[] energiumFluids = { Water.getFluid(1000), DistilledWater.getFluid(1000), BlackSteel.getFluid(L * 2),
                RedSteel.getFluid(L / 2) };
        for (int i = 0; i < energiumFluids.length; i++) {
            AUTOCLAVE_RECIPES.recipeBuilder()
                    .input(ENERGIUM_DUST, 9)
                    .fluidInputs(energiumFluids[i])
                    .output(ENERGIUM_CRYSTAL)
                    .duration(CRYSTAL_DURATIONS[i]).EUt(CRYSTAL_VOLTAGES[i]).buildAndRegister();
        }

        // Lapotron Crystal
        MIXER_RECIPES.recipeBuilder()
                .input(ENERGIUM_DUST, 3)
                .input(dust, Lapis, 2)
                .circuitMeta(2)
                .output(dust, Lapotron, 5)
                .duration(200).EUt(VA[HV]).buildAndRegister();

        FluidStack[] lapotronFluids = { Water.getFluid(1000), DistilledWater.getFluid(1000), RedSteel.getFluid(L * 2),
                BlueSteel.getFluid(L / 2) };
        for (int i = 0; i < lapotronFluids.length; i++) {
            AUTOCLAVE_RECIPES.recipeBuilder()
                    .input(dust, Lapotron, 15)
                    .fluidInputs(lapotronFluids[i])
                    .output(gem, Lapotron)
                    .duration(CRYSTAL_DURATIONS[i]).EUt(CRYSTAL_VOLTAGES[i]).buildAndRegister();
        }

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(gem, Lapotron)
                .input(circuit, Tier.HV, 2)
                .output(LAPOTRON_CRYSTAL)
                .duration(600).EUt(VA[EV]).buildAndRegister();

        // Lapotronic Energy Orb
        LASER_ENGRAVER_RECIPES.recipeBuilder()
                .inputNBT(LAPOTRON_CRYSTAL, NBTMatcher.ANY, NBTCondition.ANY)
                .notConsumable(craftingLens, Color.Blue)
                .output(ENGRAVED_LAPOTRON_CHIP, 3)
                .cleanroom(CleanroomType.CLEANROOM)
                .duration(256).EUt(VA[HV]).buildAndRegister();

        CIRCUIT_ASSEMBLER_RECIPES.recipeBuilder().duration(512).EUt(1024)
                .input(EXTREME_CIRCUIT_BOARD)
                .input(POWER_INTEGRATED_CIRCUIT, 4)
                .input(ENGRAVED_LAPOTRON_CHIP, 24)
                .input(NANO_CENTRAL_PROCESSING_UNIT, 2)
                .input(wireFine, Platinum, 16)
                .input(plate, Platinum, 8)
                .output(ENERGY_LAPOTRONIC_ORB)
                .solderMultiplier(2)
                .cleanroom(CleanroomType.CLEANROOM)
                .buildAndRegister();

        // Lapotronic Energy Cluster
        ASSEMBLY_LINE_RECIPES.recipeBuilder().EUt(80000).duration(1000)
                .input(EXTREME_CIRCUIT_BOARD)
                .input(plate, Europium, 8)
                .input(circuit, Tier.LuV, 4)
                .inputNBT(ENERGY_LAPOTRONIC_ORB, NBTMatcher.ANY, NBTCondition.ANY)
                .input(FIELD_GENERATOR_IV)
                .input(HIGH_POWER_INTEGRATED_CIRCUIT, 16)
                .input(ADVANCED_SMD_DIODE, 8)
                .input(ADVANCED_SMD_CAPACITOR, 8)
                .input(ADVANCED_SMD_RESISTOR, 8)
                .input(ADVANCED_SMD_TRANSISTOR, 8)
                .input(ADVANCED_SMD_INDUCTOR, 8)
                .input(wireFine, Platinum, 64)
                .input(bolt, Naquadah, 16)
                .fluidInputs(SolderingAlloy.getFluid(L * 5))
                .output(ENERGY_LAPOTRONIC_ORB_CLUSTER)
                .scannerResearch(ENERGY_LAPOTRONIC_ORB)
                .buildAndRegister();

        // Energy Module
        ASSEMBLY_LINE_RECIPES.recipeBuilder().EUt(100000).duration(1200)
                .input(ELITE_CIRCUIT_BOARD)
                .input(plateDouble, Europium, 8)
                .input(circuit, Tier.ZPM, 4)
                .inputNBT(ENERGY_LAPOTRONIC_ORB_CLUSTER, NBTMatcher.ANY, NBTCondition.ANY)
                .input(FIELD_GENERATOR_LuV)
                .input(HIGH_POWER_INTEGRATED_CIRCUIT, 32)
                .input(ADVANCED_SMD_DIODE, 12)
                .input(ADVANCED_SMD_CAPACITOR, 12)
                .input(ADVANCED_SMD_RESISTOR, 12)
                .input(ADVANCED_SMD_TRANSISTOR, 12)
                .input(ADVANCED_SMD_INDUCTOR, 12)
                .input(wireFine, Ruridit, 64)
                .input(bolt, Trinium, 16)
                .fluidInputs(SolderingAlloy.getFluid(L * 10))
                .output(ENERGY_MODULE)
                .stationResearch(b -> b
                        .researchStack(ENERGY_LAPOTRONIC_ORB_CLUSTER)
                        .CWUt(16))
                .buildAndRegister();

        // Energy Cluster
        ASSEMBLY_LINE_RECIPES.recipeBuilder().EUt(200000).duration(1400)
                .input(WETWARE_CIRCUIT_BOARD)
                .input(plate, Americium, 16)
                .input(WETWARE_SUPER_COMPUTER_UV, 4)
                .input(ENERGY_MODULE)
                .input(FIELD_GENERATOR_ZPM)
                .input(ULTRA_HIGH_POWER_INTEGRATED_CIRCUIT, 32)
                .input(ADVANCED_SMD_DIODE, 16)
                .input(ADVANCED_SMD_CAPACITOR, 16)
                .input(ADVANCED_SMD_RESISTOR, 16)
                .input(ADVANCED_SMD_TRANSISTOR, 16)
                .input(ADVANCED_SMD_INDUCTOR, 16)
                .input(wireFine, Osmiridium, 64)
                .input(bolt, Naquadria, 16)
                .fluidInputs(SolderingAlloy.getFluid(L * 20))
                .fluidInputs(Polybenzimidazole.getFluid(L * 4))
                .output(ENERGY_CLUSTER)
                .stationResearch(b -> b
                        .researchStack(ENERGY_MODULE)
                        .CWUt(96)
                        .EUt(VA[ZPM]))
                .buildAndRegister();

        // Ultimate Battery
        ASSEMBLY_LINE_RECIPES.recipeBuilder().EUt(300000).duration(2000)
                .input(plateDouble, Darmstadtium, 16)
                .input(circuit, Tier.UHV, 4)
                .input(ENERGY_CLUSTER, 16)
                .input(FIELD_GENERATOR_UV, 4)
                .input(ULTRA_HIGH_POWER_INTEGRATED_CIRCUIT_WAFER, 64)
                .input(ULTRA_HIGH_POWER_INTEGRATED_CIRCUIT_WAFER, 64)
                .input(ADVANCED_SMD_DIODE, 64)
                .input(ADVANCED_SMD_CAPACITOR, 64)
                .input(ADVANCED_SMD_RESISTOR, 64)
                .input(ADVANCED_SMD_TRANSISTOR, 64)
                .input(ADVANCED_SMD_INDUCTOR, 64)
                .input(wireGtSingle, EnrichedNaquadahTriniumEuropiumDuranide, 64)
                .input(bolt, Neutronium, 64)
                .fluidInputs(SolderingAlloy.getFluid(L * 40))
                .fluidInputs(Polybenzimidazole.getFluid(2304))
                .fluidInputs(Naquadria.getFluid(L * 18))
                .output(ULTIMATE_BATTERY)
                .stationResearch(b -> b
                        .researchStack(ENERGY_CLUSTER)
                        .CWUt(144)
                        .EUt(VA[UHV]))
                .buildAndRegister();
    }

    private static void batteryBlocks() {
        // the empty battery casing of each grade, then the cells filled from it and emptied back in the packer
        batteryCasing(BatteryPartType.EMPTY_TIER_I, Ultimet, VA[HV]);
        batteryCell(BatteryPartType.EMPTY_TIER_I, BatteryPartType.LAPOTRONIC_EV, LAPOTRON_CRYSTAL, 200, VA[HV]);
        batteryCell(BatteryPartType.EMPTY_TIER_I, BatteryPartType.LAPOTRONIC_IV, ENERGY_LAPOTRONIC_ORB, 400, VA[HV]);

        batteryCasing(BatteryPartType.EMPTY_TIER_II, Ruridit, VA[IV]);
        batteryCell(BatteryPartType.EMPTY_TIER_II, BatteryPartType.LAPOTRONIC_LuV, ENERGY_LAPOTRONIC_ORB_CLUSTER, 200,
                VA[EV]);
        batteryCell(BatteryPartType.EMPTY_TIER_II, BatteryPartType.LAPOTRONIC_ZPM, ENERGY_MODULE, 400, VA[EV]);

        batteryCasing(BatteryPartType.EMPTY_TIER_III, Neutronium, VA[ZPM]);
        batteryCell(BatteryPartType.EMPTY_TIER_III, BatteryPartType.LAPOTRONIC_UV, ENERGY_CLUSTER, 200, VA[IV]);
        batteryCell(BatteryPartType.EMPTY_TIER_III, BatteryPartType.ULTIMATE_UHV, ULTIMATE_BATTERY, 400, VA[IV]);
    }

    private static void batteryHull(MetaItem<?>.MetaValueItem hull, Material cable, int cables, Material plate,
                                    int plates, Material plastic, int plasticAmount, int duration, int voltage) {
        ASSEMBLER_RECIPES.recipeBuilder()
                .input(cableGtSingle, cable, cables)
                .input(OrePrefix.plate, plate, plates)
                .fluidInputs(plastic.getFluid(plasticAmount))
                .output(hull)
                .duration(duration).EUt(voltage).buildAndRegister();
    }

    /** A battery canned from its hull and dust, and extracted back to the hull whatever its charge. */
    private static void battery(MetaItem<?>.MetaValueItem hull, MetaItem<?>.MetaValueItem battery, Material dust,
                                int dusts, int duration, int voltage) {
        CANNER_RECIPES.recipeBuilder().duration(duration).EUt(voltage)
                .input(hull)
                .input(OrePrefix.dust, dust, dusts)
                .output(battery)
                .buildAndRegister();

        EXTRACTOR_RECIPES.recipeBuilder().inputNBT(battery, NBTMatcher.ANY, NBTCondition.ANY)
                .output(hull).buildAndRegister();
    }

    private static void batteryCasing(BatteryPartType casing, Material material, int voltage) {
        ASSEMBLER_RECIPES.recipeBuilder()
                .input(frameGt, material)
                .input(plate, material, 6)
                .input(screw, material, 24)
                .outputs(BATTERY_BLOCK.getItemVariant(casing))
                .duration(400).EUt(voltage).buildAndRegister();
    }

    /** A battery cell canned from an empty casing and a charged item, which the packer gives back. */
    private static void batteryCell(BatteryPartType casing, BatteryPartType cell, MetaItem<?>.MetaValueItem item,
                                    int duration, int voltage) {
        CANNER_RECIPES.recipeBuilder()
                .inputs(BATTERY_BLOCK.getItemVariant(casing))
                .inputNBT(item, NBTMatcher.ANY, NBTCondition.ANY)
                .outputs(BATTERY_BLOCK.getItemVariant(cell))
                .duration(duration).EUt(voltage).buildAndRegister();

        PACKER_RECIPES.recipeBuilder()
                .inputs(BATTERY_BLOCK.getItemVariant(cell))
                .outputs(BATTERY_BLOCK.getItemVariant(casing))
                .output(item)
                .circuitMeta(2)
                .duration(200).EUt(VA[LV]).buildAndRegister();
    }
}
