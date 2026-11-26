package gregtech.loaders.recipe;

import gregtech.api.GTValues;
import gregtech.api.items.OreDictNames;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.recipes.ModHandler;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;
import gregtech.api.recipes.ingredients.GTRecipeOreInput;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.UnificationEntry;
import gregtech.api.util.GTUtility;
import gregtech.api.util.Mods;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import static gregtech.api.GTValues.*;
import static gregtech.api.recipes.RecipeMaps.ASSEMBLER_RECIPES;
import static gregtech.api.recipes.RecipeMaps.ASSEMBLY_LINE_RECIPES;
import static gregtech.api.unification.material.MarkerMaterials.Tier;
import static gregtech.api.unification.material.Materials.*;
import static gregtech.api.unification.ore.OrePrefix.*;
import static gregtech.common.blocks.MetaBlocks.LD_FLUID_PIPE;
import static gregtech.common.blocks.MetaBlocks.LD_ITEM_PIPE;
import static gregtech.common.items.MetaItems.*;
import static gregtech.common.metatileentities.MetaTileEntities.*;
import static gregtech.loaders.recipe.RecipeTiers.*;

public class MetaTileEntityMachineRecipeLoader {

    /** The conductor of each tier's springs, multi-amp hatch wires and transformer cables, ULV to UHV. */
    private static final Material[] CONDUCTORS = { Lead, Tin, Copper, Gold, Aluminium, Tungsten, NiobiumTitanium,
            VanadiumGallium, YttriumBariumCuprate, Europium };
    /** The cable of each tier's energy hatches, ULV to UHV: the conductor, but red alloy at ULV. */
    private static final Material[] ENERGY_HATCH_CABLES = { RedAlloy, Tin, Copper, Gold, Aluminium, Tungsten,
            NiobiumTitanium, VanadiumGallium, YttriumBariumCuprate, Europium };
    /** The integrated circuit of each tier's energy and dynamo hatches, from MV. */
    private static final MetaItem<?>.MetaValueItem[] CHIPS = { null, null, ULTRA_LOW_POWER_INTEGRATED_CIRCUIT,
            LOW_POWER_INTEGRATED_CIRCUIT, POWER_INTEGRATED_CIRCUIT, HIGH_POWER_INTEGRATED_CIRCUIT,
            HIGH_POWER_INTEGRATED_CIRCUIT, ULTRA_HIGH_POWER_INTEGRATED_CIRCUIT, ULTRA_HIGH_POWER_INTEGRATED_CIRCUIT,
            ULTRA_HIGH_POWER_INTEGRATED_CIRCUIT };
    private static final MetaItem<?>.MetaValueItem[] VOLTAGE_COILS = { VOLTAGE_COIL_ULV, VOLTAGE_COIL_LV,
            VOLTAGE_COIL_MV, VOLTAGE_COIL_HV, VOLTAGE_COIL_EV, VOLTAGE_COIL_IV, VOLTAGE_COIL_LuV, VOLTAGE_COIL_ZPM,
            VOLTAGE_COIL_UV };
    /** The sodium potassium, in millibuckets, of each tier's energy and dynamo hatches, from HV. */
    private static final int[] SODIUM_POTASSIUM = { 0, 0, 0, 1000, 2000, 3000, 6000, 8000, 10000, 12000 };
    /** The soldering alloy, in millibuckets, of each tier's energy and dynamo hatches, from LuV. */
    private static final int[] SOLDER = { 0, 0, 0, 0, 0, 0, 720, 1440, 2880, 5760 };

    public static void init() {
        registerDualHatches();
        // Fluid Hatches
        registerHatchBusRecipe(ULV, FLUID_IMPORT_HATCH[ULV], FLUID_EXPORT_HATCH[ULV], new ItemStack(Blocks.GLASS));
        registerHatchBusRecipe(LV, FLUID_IMPORT_HATCH[LV], FLUID_EXPORT_HATCH[LV], new ItemStack(Blocks.GLASS));
        registerHatchBusRecipe(MV, FLUID_IMPORT_HATCH[MV], FLUID_EXPORT_HATCH[MV], BRONZE_DRUM.getStackForm());
        registerHatchBusRecipe(HV, FLUID_IMPORT_HATCH[HV], FLUID_EXPORT_HATCH[HV], STEEL_DRUM.getStackForm());
        registerHatchBusRecipe(EV, FLUID_IMPORT_HATCH[EV], FLUID_EXPORT_HATCH[EV], ALUMINIUM_DRUM.getStackForm());
        registerHatchBusRecipe(IV, FLUID_IMPORT_HATCH[IV], FLUID_EXPORT_HATCH[IV], STAINLESS_STEEL_DRUM.getStackForm());
        registerHatchBusRecipe(LuV, FLUID_IMPORT_HATCH[LuV], FLUID_EXPORT_HATCH[LuV], TITANIUM_DRUM.getStackForm());
        registerHatchBusRecipe(ZPM, FLUID_IMPORT_HATCH[ZPM], FLUID_EXPORT_HATCH[ZPM],
                TUNGSTENSTEEL_DRUM.getStackForm());
        registerHatchBusRecipe(UV, FLUID_IMPORT_HATCH[UV], FLUID_EXPORT_HATCH[UV], QUANTUM_TANK[0].getStackForm());
        registerHatchBusRecipe(UHV, FLUID_IMPORT_HATCH[UHV], FLUID_EXPORT_HATCH[UHV], QUANTUM_TANK[1].getStackForm());

        // Quadruple and Nonuple Fluid Hatches
        multiFluidHatches(FLUID_IMPORT_HATCH, QUADRUPLE_IMPORT_HATCH, pipeQuadrupleFluid, 4, 300);
        multiFluidHatches(FLUID_IMPORT_HATCH, NONUPLE_IMPORT_HATCH, pipeNonupleFluid, 9, 600);
        multiFluidHatches(FLUID_EXPORT_HATCH, QUADRUPLE_EXPORT_HATCH, pipeQuadrupleFluid, 4, 300);
        multiFluidHatches(FLUID_EXPORT_HATCH, NONUPLE_EXPORT_HATCH, pipeNonupleFluid, 9, 600);

        // Reservoir Hatch
        ASSEMBLER_RECIPES.recipeBuilder()
                .input(COVER_INFINITE_WATER)
                .input(FLUID_IMPORT_HATCH[EV])
                .input(ELECTRIC_PUMP_EV)
                .output(RESERVOIR_HATCH)
                .duration(300).EUt(VA[EV]).buildAndRegister();

        // Item Buses
        registerHatchBusRecipe(ULV, ITEM_IMPORT_BUS[ULV], ITEM_EXPORT_BUS[ULV], OreDictNames.chestWood.toString());
        registerHatchBusRecipe(LV, ITEM_IMPORT_BUS[LV], ITEM_EXPORT_BUS[LV], OreDictNames.chestWood.toString());
        registerHatchBusRecipe(MV, ITEM_IMPORT_BUS[MV], ITEM_EXPORT_BUS[MV], BRONZE_CRATE.getStackForm());
        registerHatchBusRecipe(HV, ITEM_IMPORT_BUS[HV], ITEM_EXPORT_BUS[HV], STEEL_CRATE.getStackForm());
        registerHatchBusRecipe(EV, ITEM_IMPORT_BUS[EV], ITEM_EXPORT_BUS[EV], ALUMINIUM_CRATE.getStackForm());
        registerHatchBusRecipe(IV, ITEM_IMPORT_BUS[IV], ITEM_EXPORT_BUS[IV], STAINLESS_STEEL_CRATE.getStackForm());
        registerHatchBusRecipe(LuV, ITEM_IMPORT_BUS[LuV], ITEM_EXPORT_BUS[LuV], TITANIUM_CRATE.getStackForm());
        registerHatchBusRecipe(ZPM, ITEM_IMPORT_BUS[ZPM], ITEM_EXPORT_BUS[ZPM], TUNGSTENSTEEL_CRATE.getStackForm());
        registerHatchBusRecipe(UV, ITEM_IMPORT_BUS[UV], ITEM_EXPORT_BUS[UV], QUANTUM_CHEST[0].getStackForm());
        registerHatchBusRecipe(UHV, ITEM_IMPORT_BUS[UHV], ITEM_EXPORT_BUS[UHV], QUANTUM_CHEST[1].getStackForm());

        // Laser Hatches
        registerLaserRecipes();

        // Energy Output Hatches, wired with springs, and Energy Input Hatches, wired with cables
        energyHatches("dynamo_hatch", ENERGY_OUTPUT_HATCH, spring, CONDUCTORS);
        energyHatches("energy_hatch", ENERGY_INPUT_HATCH, cableGtSingle, ENERGY_HATCH_CABLES);

        // Power Transformers: the tier's hi-amp transformer with a pump and lubricant, wired between the tier's
        // conductor and the one above it
        int[] pumpTiers = { LV, LV, MV, MV, HV, HV, EV, EV, IV };
        for (int tier = ULV; tier <= UV; tier++) {
            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(HI_AMP_TRANSFORMER[tier])
                    .input(pump(pumpTiers[tier]))
                    .input(cableGtOctal, CONDUCTORS[tier + 1])
                    .input(cableGtHex, CONDUCTORS[tier], 2)
                    .input(springSmall, CONDUCTORS[tier])
                    .input(spring, CONDUCTORS[tier + 1])
                    .fluidInputs(Lubricant.getFluid(2000))
                    .output(POWER_TRANSFORMER[tier])
                    .duration(200).EUt(VA[tier]).buildAndRegister();
        }

        // 4A, 16A and 64A Substation Energy Hatches, then the same Dynamo Hatches
        multiAmpHatches(ENERGY_INPUT_HATCH, ENERGY_INPUT_HATCH_4A, ENERGY_INPUT_HATCH_16A,
                SUBSTATION_ENERGY_INPUT_HATCH, false);
        multiAmpHatches(ENERGY_OUTPUT_HATCH, ENERGY_OUTPUT_HATCH_4A, ENERGY_OUTPUT_HATCH_16A,
                SUBSTATION_ENERGY_OUTPUT_HATCH, true);

        // Maintenance Hatch

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(HULL[LV])
                .circuitMeta(8)
                .output(MAINTENANCE_HATCH)
                .duration(100).EUt(VA[LV]).buildAndRegister();

        // Multiblock Miners

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(HULL[EV])
                .input(frameGt, Titanium, 4)
                .input(circuit, Tier.EV, 4)
                .input(ELECTRIC_MOTOR_EV, 4)
                .input(ELECTRIC_PUMP_EV, 4)
                .input(CONVEYOR_MODULE_EV, 4)
                .input(gear, Tungsten, 4)
                .circuitMeta(2)
                .output(BASIC_LARGE_MINER)
                .duration(400).EUt(VA[EV]).buildAndRegister();

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(HULL[IV])
                .input(frameGt, TungstenSteel, 4)
                .input(circuit, Tier.IV, 4)
                .input(ELECTRIC_MOTOR_IV, 4)
                .input(ELECTRIC_PUMP_IV, 4)
                .input(CONVEYOR_MODULE_IV, 4)
                .input(gear, Iridium, 4)
                .circuitMeta(2)
                .output(LARGE_MINER)
                .duration(400).EUt(VA[IV]).buildAndRegister();

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(HULL[LuV])
                .input(frameGt, HSSS, 4)
                .input(circuit, Tier.LuV, 4)
                .input(ELECTRIC_MOTOR_LuV, 4)
                .input(ELECTRIC_PUMP_LuV, 4)
                .input(CONVEYOR_MODULE_LuV, 4)
                .input(gear, Ruridit, 4)
                .circuitMeta(2)
                .output(ADVANCED_LARGE_MINER)
                .duration(400).EUt(VA[LuV]).buildAndRegister();

        // Multiblock Fluid Drills

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(HULL[MV])
                .input(frameGt, Steel, 4)
                .input(circuit, Tier.MV, 4)
                .input(ELECTRIC_MOTOR_MV, 4)
                .input(ELECTRIC_PUMP_MV, 4)
                .input(gear, VanadiumSteel, 4)
                .circuitMeta(2)
                .output(BASIC_FLUID_DRILLING_RIG)
                .duration(400).EUt(VA[MV]).buildAndRegister();

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(HULL[EV])
                .input(frameGt, Titanium, 4)
                .input(circuit, Tier.EV, 4)
                .input(ELECTRIC_MOTOR_EV, 4)
                .input(ELECTRIC_PUMP_EV, 4)
                .input(gear, TungstenCarbide, 4)
                .circuitMeta(2)
                .output(FLUID_DRILLING_RIG)
                .duration(400).EUt(VA[EV]).buildAndRegister();

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(HULL[LuV])
                .input(frameGt, TungstenSteel, 4)
                .input(circuit, Tier.LuV, 4)
                .input(ELECTRIC_MOTOR_LuV, 4)
                .input(ELECTRIC_PUMP_LuV, 4)
                .input(gear, Osmiridium, 4)
                .circuitMeta(2)
                .output(ADVANCED_FLUID_DRILLING_RIG)
                .duration(400).EUt(VA[LuV]).buildAndRegister();

        // Long Distance Pipes
        ASSEMBLER_RECIPES.recipeBuilder()
                .input(pipeLargeItem, Tin, 2)
                .input(plate, Steel, 8)
                .input(gear, Steel, 2)
                .circuitMeta(1)
                .fluidInputs(SolderingAlloy.getFluid(L / 2))
                .output(LONG_DIST_ITEM_ENDPOINT, 2)
                .duration(400).EUt(16).buildAndRegister();

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(pipeLargeFluid, Bronze, 2)
                .input(plate, Steel, 8)
                .input(gear, Steel, 2)
                .circuitMeta(1)
                .fluidInputs(SolderingAlloy.getFluid(L / 2))
                .output(LONG_DIST_FLUID_ENDPOINT, 2)
                .duration(400).EUt(16).buildAndRegister();

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(pipeLargeItem, Tin, 2)
                .input(plate, Steel, 8)
                .circuitMeta(2)
                .fluidInputs(SolderingAlloy.getFluid(L / 2))
                .output(LD_ITEM_PIPE, 64)
                .duration(600).EUt(24).buildAndRegister();

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(pipeLargeFluid, Bronze, 2)
                .input(plate, Steel, 8)
                .circuitMeta(2)
                .fluidInputs(SolderingAlloy.getFluid(L / 2))
                .output(LD_FLUID_PIPE, 64)
                .duration(600).EUt(24).buildAndRegister();

        // ME Parts

        if (Mods.AppliedEnergistics2.isModLoaded()) {

            ItemStack fluidInterface = Mods.AppliedEnergistics2.getItem("fluid_interface");
            ItemStack normalInterface = Mods.AppliedEnergistics2.getItem("interface");
            ItemStack accelerationCard = Mods.AppliedEnergistics2.getItem("material", 30, 2);

            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(ITEM_IMPORT_BUS[IV]).input(FLUID_IMPORT_HATCH[IV])
                    .inputs(normalInterface.copy()).inputs(fluidInterface.copy()).input(SENSOR_IV)
                    .output(PATTERN_BUFFER_ME).duration(400).EUt(VA[IV]).buildAndRegister();

            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(ITEM_IMPORT_BUS[IV]).input(SENSOR_IV).input(EMITTER_IV)
                    .output(PATTERN_BUFFER_PROXY_ME).duration(300).EUt(VA[IV]).buildAndRegister();

            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(FLUID_EXPORT_HATCH[EV])
                    .inputs(fluidInterface.copy())
                    .inputs(accelerationCard.copy())
                    .output(FLUID_EXPORT_HATCH_ME)
                    .duration(300).EUt(VA[HV]).buildAndRegister();

            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(FLUID_IMPORT_HATCH[EV])
                    .inputs(fluidInterface.copy())
                    .inputs(accelerationCard.copy())
                    .output(FLUID_IMPORT_HATCH_ME)
                    .duration(300).EUt(VA[HV]).buildAndRegister();

            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(ITEM_EXPORT_BUS[EV])
                    .inputs(normalInterface.copy())
                    .inputs(accelerationCard.copy())
                    .output(ITEM_EXPORT_BUS_ME)
                    .duration(300).EUt(VA[HV]).buildAndRegister();

            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(ITEM_IMPORT_BUS[EV])
                    .inputs(normalInterface.copy())
                    .inputs(accelerationCard.copy())
                    .output(ITEM_IMPORT_BUS_ME)
                    .duration(300).EUt(VA[HV]).buildAndRegister();

            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(ITEM_IMPORT_BUS[IV])
                    .inputs(normalInterface.copy())
                    .input(CONVEYOR_MODULE_IV)
                    .input(SENSOR_IV)
                    .inputs(GTUtility.copy(4, accelerationCard))
                    .output(STOCKING_BUS_ME)
                    .duration(300).EUt(VA[IV]).buildAndRegister();

            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(FLUID_IMPORT_HATCH[IV])
                    .inputs(fluidInterface.copy())
                    .input(ELECTRIC_PUMP_IV)
                    .input(SENSOR_IV)
                    .inputs(GTUtility.copy(4, accelerationCard))
                    .output(STOCKING_HATCH_ME)
                    .duration(300).EUt(VA[IV]).buildAndRegister();
        }
    }

    private static void registerDualHatches() {
        Material[] pipes = { NiobiumTitanium, Iridium, Naquadah, Neutronium };
        Material[] frames = { Ruridit, Iridium, NaquadahAlloy, NaquadahAlloy };
        for (int tier = LuV; tier <= UHV; tier++) {
            ItemStack pipe = OreDictUnifier.get(pipeNonupleFluid, pipes[tier - LuV]);
            ItemStack frame = OreDictUnifier.get(frameGt, frames[tier - LuV], 3);
            if (pipe.isEmpty() || frame.isEmpty()) {
                throw new IllegalStateException("Missing combined hatch ingredients for tier " + tier);
            }
            for (boolean output : new boolean[] { false, true }) {
                ASSEMBLER_RECIPES.recipeBuilder()
                        .input(output ? ITEM_EXPORT_BUS[tier] : ITEM_IMPORT_BUS[tier])
                        .input(output ? FLUID_EXPORT_HATCH[tier] : FLUID_IMPORT_HATCH[tier])
                        .inputs(pipe.copy(), frame.copy())
                        .fluidInputs(Polybenzimidazole.getFluid(L * (tier + 1)))
                        .circuitMeta(output ? 2 : 1)
                        .output(output ? DUAL_EXPORT_HATCH[tier] : DUAL_IMPORT_HATCH[tier])
                        .duration(300).EUt(VA[Math.min(tier, UV)]).buildAndRegister();
            }
        }
    }

    private static void registerHatchBusRecipe(int tier, MetaTileEntity inputBus, MetaTileEntity outputBus,
                                               Object extraInput) {
        GTRecipeInput extra;
        if (extraInput instanceof ItemStack stack) {
            extra = new GTRecipeItemInput(stack);
        } else if (extraInput instanceof String oreName) {
            extra = new GTRecipeOreInput(oreName);
        } else {
            throw new IllegalArgumentException("extraInput must be ItemStack or GTRecipeInput");
        }

        // Glue up to LV: 250L for ULV, 500L for LV
        if (tier <= GTValues.LV) {
            hatchBusPair(tier, inputBus, outputBus, extra, Glue, tier == GTValues.ULV ? 250 : 500, VA[tier], false);
        }
        // Polyethylene up to HV: 72L for ULV, 144L for LV, 288L for MV, 432L for HV
        if (tier <= GTValues.HV) {
            hatchBusPair(tier, inputBus, outputBus, extra, Polyethylene, getFluidAmount(tier + 4), VA[tier], false);
        }
        // Polytetrafluoroethylene up to LuV: 36L for ULV, 72L for LV, 144L for MV, 288L for HV, 432L for EV,
        // 576L for IV, 720L for LuV
        if (tier <= GTValues.LuV) {
            hatchBusPair(tier, inputBus, outputBus, extra, Polytetrafluoroethylene, getFluidAmount(tier + 3),
                    VA[tier], false);
        }
        // PBI for all, the only recipe that carries recycling data: 4L for ULV, 9L for LV, 18L for MV, 36L for HV,
        // 72L for EV, 144L for IV, 288L for LuV, 432L for ZPM, 576L for UV, 720L for UHV, at UV voltage for UHV
        hatchBusPair(tier, inputBus, outputBus, extra, Polybenzimidazole, getFluidAmount(tier),
                VA[Math.min(GTValues.UV, tier)], true);
    }

    /** The input bus or hatch on circuit 1 and the output one on circuit 2, from the tier's hull and a plastic. */
    private static void hatchBusPair(int tier, MetaTileEntity inputBus, MetaTileEntity outputBus, GTRecipeInput extra,
                                     Material fluid, int amount, int voltage, boolean recycling) {
        for (int configuration = 1; configuration <= 2; configuration++) {
            var builder = ASSEMBLER_RECIPES.recipeBuilder()
                    .input(HULL[tier])
                    .inputs(extra)
                    .fluidInputs(fluid.getFluid(amount))
                    .circuitMeta(configuration)
                    .output(configuration == 1 ? inputBus : outputBus);
            if (recycling) builder.withRecycling();
            builder.duration(300).EUt(voltage).buildAndRegister();
        }
    }

    private static int getFluidAmount(int offsetTier) {
        return switch (offsetTier) {
            case 0 -> 4;
            case 1 -> 9;
            case 2 -> 18;
            case 3 -> 36;
            case 4 -> 72;
            case 5 -> 144;
            case 6 -> 288;
            case 7 -> 432;
            case 8 -> 576;
            default -> 720;
        };
    }

    private static void registerLaserRecipes() {
        // 256A, 1024A and 4096A Laser Source and Target Hatches: the tier's hull with an emitter or a sensor, a
        // diamond lens and a pump, one, two or four of each, and the tier's cable
        laserHatches(LASER_OUTPUT_HATCH_256, true, 1, cableGtSingle, 1, 300);
        laserHatches(LASER_INPUT_HATCH_256, false, 1, cableGtSingle, 1, 300);
        laserHatches(LASER_OUTPUT_HATCH_1024, true, 2, cableGtDouble, 2, 600);
        laserHatches(LASER_INPUT_HATCH_1024, false, 2, cableGtDouble, 2, 600);
        laserHatches(LASER_OUTPUT_HATCH_4096, true, 4, cableGtQuadruple, 3, 1200);
        laserHatches(LASER_INPUT_HATCH_4096, false, 4, cableGtQuadruple, 3, 1200);
    }

    private static void laserHatches(MetaTileEntity[] hatches, boolean source, int parts, OrePrefix cable,
                                     int configuration, int duration) {
        for (int tier = IV; tier <= UV; tier++) {
            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(HULL[tier])
                    .input(lens, Diamond, parts)
                    .input(source ? emitter(tier) : sensor(tier), parts)
                    .input(pump(tier), parts)
                    .input(cable, material(CraftingComponent.CABLE, tier), 4)
                    .circuitMeta(configuration)
                    .output(hatches[tier])
                    .duration(duration).EUt(VA[tier]).buildAndRegister();
        }
    }

    /** The quadruple or nonuple fluid hatches, EV to UHV, from the tier's fluid hatch, a pipe and a plastic. */
    private static void multiFluidHatches(MetaTileEntity[] hatches, MetaTileEntity[] multiHatches, OrePrefix pipe,
                                          int size, int duration) {
        Material[] pipes = { Titanium, TungstenSteel, NiobiumTitanium, Iridium, Naquadah, Neutronium };
        for (int tier = EV; tier <= UHV; tier++) {
            Material plastic = tier <= LuV ? Polytetrafluoroethylene : Polybenzimidazole;
            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(hatches[tier])
                    .input(pipe, pipes[tier - EV])
                    .fluidInputs(plastic.getFluid(L * size))
                    .circuitMeta(size)
                    .output(multiHatches[tier])
                    .duration(duration).EUt(VA[Math.min(tier, UV)]).buildAndRegister();
        }
    }

    /**
     * The energy or dynamo hatch of each tier: the tier's hull and voltage coil wired with its conductor, crafted by
     * hand up to MV, assembled up to IV, with sodium potassium from HV, and assembled in the assembly line from LuV.
     */
    private static void energyHatches(String name, MetaTileEntity[] hatches, OrePrefix wiring, Material[] wires) {
        for (int tier = ULV; tier <= MV; tier++) {
            String recipeName = name + "." + tierName(tier);
            ItemStack hatch = hatches[tier].getStackForm();
            UnificationEntry wire = new UnificationEntry(wiring, wires[tier]);
            if (tier == MV) {
                ModHandler.addShapedRecipe(true, recipeName, hatch, " V ", "WHW", " P ",
                        'P', ULTRA_LOW_POWER_INTEGRATED_CIRCUIT.getStackForm(), 'W', wire,
                        'V', VOLTAGE_COILS[tier].getStackForm(), 'H', HULL[tier].getStackForm());
            } else {
                ModHandler.addShapedRecipe(true, recipeName, hatch, " V ", "WHW", "   ", 'W', wire,
                        'V', VOLTAGE_COILS[tier].getStackForm(), 'H', HULL[tier].getStackForm());
            }
        }

        for (int tier = ULV; tier <= IV; tier++) {
            var builder = ASSEMBLER_RECIPES.recipeBuilder()
                    .input(HULL[tier])
                    .input(wiring, wires[tier], 2);
            if (tier >= MV) builder.input(CHIPS[tier], tier == MV ? 1 : 2);
            builder.input(VOLTAGE_COILS[tier]);
            if (tier >= HV) builder.fluidInputs(SodiumPotassium.getFluid(SODIUM_POTASSIUM[tier]));
            builder.output(hatches[tier])
                    .duration(200).EUt(VA[tier]).buildAndRegister();
        }

        for (int tier = LuV; tier <= UHV; tier++) {
            var builder = ASSEMBLY_LINE_RECIPES.recipeBuilder()
                    .input(HULL[tier])
                    .input(wiring, wires[tier], 4)
                    .input(CHIPS[tier], 2)
                    .input(circuit, circuitTier(tier));
            // there is no UHV voltage coil
            if (tier == UHV) builder.input(wireGtDouble, RutheniumTriniumAmericiumNeutronate, 2);
            else builder.input(VOLTAGE_COILS[tier], 2);
            builder.fluidInputs(SodiumPotassium.getFluid(SODIUM_POTASSIUM[tier]))
                    .fluidInputs(SolderingAlloy.getFluid(SOLDER[tier]))
                    .output(hatches[tier]);
            MetaTileEntity previous = hatches[tier - 1];
            switch (tier) {
                case LuV -> builder.scannerResearch(b -> b.researchStack(previous).EUt(VA[EV]));
                case ZPM -> builder.stationResearch(b -> b.researchStack(previous).CWUt(8));
                case UV -> builder.stationResearch(b -> b.researchStack(previous).CWUt(64).EUt(VA[ZPM]));
                default -> builder.stationResearch(b -> b.researchStack(previous).CWUt(128).EUt(VA[UV]));
            }
            builder.duration(400 + 200 * (tier - LuV)).EUt(VA[tier]).buildAndRegister();
        }
    }

    /**
     * The 4A, 16A and 64A hatches of one kind, ULV to UHV, each from the hatch of the amperage below it, the tier's
     * conductor and plates, and for 16A and 64A a transformer. A few recipes break the pattern and are kept as they
     * are: the ULV 4A energy hatch takes 2 EU/t where the dynamo takes VH[ULV], the ZPM 4A dynamo takes a
     * transformer as well, the ULV 16A dynamo recipe makes the LuV 16A dynamo, and the UHV 16A energy hatch takes
     * two 4A hatches where the dynamo takes one.
     */
    private static void multiAmpHatches(MetaTileEntity[] hatches, MetaTileEntity[] hatches4A,
                                        MetaTileEntity[] hatches16A, MetaTileEntity[] hatches64A, boolean dynamo) {
        for (int tier = ULV; tier <= UHV; tier++) {
            var builder = ASSEMBLER_RECIPES.recipeBuilder();
            if (dynamo && tier == ZPM) builder.input(TRANSFORMER[ZPM]);
            builder.input(hatches[tier])
                    .input(wireGtQuadruple, CONDUCTORS[tier], 2)
                    .input(plate, material(CraftingComponent.PLATE, tier), 2)
                    .output(hatches4A[tier])
                    .duration(100).EUt(tier > ULV ? VA[tier - 1] : dynamo ? VH[ULV] : 2).buildAndRegister();
        }

        for (int tier = ULV; tier <= UHV; tier++) {
            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(tier < UHV ? TRANSFORMER[tier] : HI_AMP_TRANSFORMER[UV])
                    .input(hatches4A[tier], tier == UHV && !dynamo ? 2 : 1)
                    .input(wireGtOctal, CONDUCTORS[tier], 2)
                    .input(plate, material(CraftingComponent.PLATE, tier), 4)
                    .output(hatches16A[tier])
                    .duration(200).EUt(tier > ULV ? VA[tier - 1] : VH[ULV]).buildAndRegister();
        }

        for (int tier = ULV; tier <= UHV; tier++) {
            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(POWER_TRANSFORMER[Math.min(tier, UV)])
                    .input(hatches16A[tier])
                    .input(wireGtHex, CONDUCTORS[tier], 2)
                    .input(plate, material(CraftingComponent.PLATE, tier), 6)
                    .output(hatches64A[tier])
                    .duration(400).EUt(tier > ULV ? VA[tier - 1] : VH[ULV]).buildAndRegister();
        }
    }
}
