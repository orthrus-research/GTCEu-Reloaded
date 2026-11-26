package gregtech.loaders.recipe;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.recipes.ModHandler;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;
import gregtech.api.recipes.ingredients.GTRecipeOreInput;
import gregtech.api.unification.material.MarkerMaterials.Tier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.UnificationEntry;

import net.minecraft.init.Items;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;

import static gregtech.api.GTValues.*;
import static gregtech.api.recipes.RecipeMaps.ASSEMBLER_RECIPES;
import static gregtech.api.recipes.RecipeMaps.ASSEMBLY_LINE_RECIPES;
import static gregtech.api.unification.material.Materials.*;
import static gregtech.api.unification.ore.OrePrefix.*;
import static gregtech.common.items.MetaItems.*;
import static gregtech.loaders.recipe.RecipeTiers.circuitTier;
import static gregtech.loaders.recipe.RecipeTiers.tierName;

/**
 * The electric components: motors, conveyors, pumps, fluid regulators, pistons, robot arms, field generators, sensors
 * and emitters. The LV to IV components are crafted by hand and in the assembler, each tier from its entries in the
 * tables below; the LuV to UV ones are assembly line recipes, written out because each family steps its amounts and
 * research differently.
 * <p>
 * The tables are this loader's own, indexed by tier, rather than {@link CraftingComponent}'s, which addons may change.
 */
public class ComponentRecipes {

    /** The single cable of a tier's conveyors, pumps, pistons, robot arms and emitters. */
    private static final Material[] CABLES = { null, Tin, Copper, Gold, Aluminium, Tungsten };
    /** The metal of a tier's pistons, robot arms, field generators and sensors. */
    private static final Material[] METALS = { null, Steel, Aluminium, StainlessSteel, Titanium, TungstenSteel };
    /** The screws and rotor of a tier's pumps. */
    private static final Material[] PUMP_ROTORS = { null, Tin, Bronze, Steel, StainlessSteel, TungstenSteel };
    /** The fluid pipe of a tier's pumps. */
    private static final Material[] PUMP_PIPES = { null, Bronze, Steel, StainlessSteel, Titanium, TungstenSteel };
    /** The rods of a tier's sensors and emitters. */
    private static final Material[] EMITTER_RODS = { null, Brass, Electrum, Chrome, Platinum, Iridium };
    /** The gem of a tier's sensors and emitters: an ore dictionary entry or a meta item. */
    private static final Object[] EMITTER_GEMS = { null, new UnificationEntry(gem, Quartzite),
            new UnificationEntry(gemFlawless, Emerald), new UnificationEntry(gem, EnderEye), QUANTUM_EYE,
            QUANTUM_STAR };
    /** The quadruple wire of a tier's field generators. */
    private static final Material[] FIELD_WIRES = { null, ManganesePhosphide, MagnesiumDiboride,
            MercuryBariumCalciumCuprate, UraniumTriplatinum, SamariumIronArsenicOxide };
    /** The plate of a tier's field generators, of the tier's metal. */
    private static final OrePrefix[] FIELD_PLATES = { null, plate, plate, plate, plateDouble, plateDouble };
    /** The core of a tier's field generators: an ore dictionary entry or a meta item. */
    private static final Object[] FIELD_CORES = { null, new UnificationEntry(gem, EnderPearl),
            new UnificationEntry(gem, EnderEye), QUANTUM_EYE, new UnificationEntry(gem, NetherStar), QUANTUM_STAR };

    private static final MetaItem<?>.MetaValueItem[] MOTORS = { null, ELECTRIC_MOTOR_LV, ELECTRIC_MOTOR_MV,
            ELECTRIC_MOTOR_HV, ELECTRIC_MOTOR_EV, ELECTRIC_MOTOR_IV };
    private static final MetaItem<?>.MetaValueItem[] CONVEYORS = { null, CONVEYOR_MODULE_LV, CONVEYOR_MODULE_MV,
            CONVEYOR_MODULE_HV, CONVEYOR_MODULE_EV, CONVEYOR_MODULE_IV };
    private static final MetaItem<?>.MetaValueItem[] PUMPS = { null, ELECTRIC_PUMP_LV, ELECTRIC_PUMP_MV,
            ELECTRIC_PUMP_HV, ELECTRIC_PUMP_EV, ELECTRIC_PUMP_IV, ELECTRIC_PUMP_LuV, ELECTRIC_PUMP_ZPM,
            ELECTRIC_PUMP_UV };
    private static final MetaItem<?>.MetaValueItem[] FLUID_REGULATORS = { null, FLUID_REGULATOR_LV,
            FLUID_REGULATOR_MV, FLUID_REGULATOR_HV, FLUID_REGULATOR_EV, FLUID_REGULATOR_IV, FLUID_REGULATOR_LUV,
            FLUID_REGULATOR_ZPM, FLUID_REGULATOR_UV };
    private static final MetaItem<?>.MetaValueItem[] PISTONS = { null, ELECTRIC_PISTON_LV, ELECTRIC_PISTON_MV,
            ELECTRIC_PISTON_HV, ELECTRIC_PISTON_EV, ELECTRIC_PISTON_IV };
    private static final MetaItem<?>.MetaValueItem[] ROBOT_ARMS = { null, ROBOT_ARM_LV, ROBOT_ARM_MV, ROBOT_ARM_HV,
            ROBOT_ARM_EV, ROBOT_ARM_IV };
    private static final MetaItem<?>.MetaValueItem[] FIELD_GENERATORS = { null, FIELD_GENERATOR_LV,
            FIELD_GENERATOR_MV, FIELD_GENERATOR_HV, FIELD_GENERATOR_EV, FIELD_GENERATOR_IV };
    private static final MetaItem<?>.MetaValueItem[] SENSORS = { null, SENSOR_LV, SENSOR_MV, SENSOR_HV, SENSOR_EV,
            SENSOR_IV };
    private static final MetaItem<?>.MetaValueItem[] EMITTERS = { null, EMITTER_LV, EMITTER_MV, EMITTER_HV,
            EMITTER_EV, EMITTER_IV };

    public static void register() {
        motors();
        conveyorsAndPumps();
        fluidRegulators();
        voidingCovers();
        pistons();
        robotArms();
        fieldGenerators();
        sensors();
        emitters();
    }

    private static void motors() {
        // the LV motor is made of steel or of iron; only the iron recipe gives the motor its recycling data
        motor(LV, "electric_motor_lv_steel", false, cableGtSingle, Tin, wireGtSingle, Copper, Steel, SteelMagnetic);
        motor(LV, "electric_motor_lv_iron", true, cableGtSingle, Tin, wireGtSingle, Copper, Iron, IronMagnetic);
        motor(MV, "electric_motor_mv", true, cableGtSingle, Copper, wireGtDouble, Cupronickel, Aluminium,
                SteelMagnetic);
        motor(HV, "electric_motor_hv", true, cableGtDouble, Silver, wireGtDouble, Electrum, StainlessSteel,
                SteelMagnetic);
        motor(EV, "electric_motor_ev", true, cableGtDouble, Aluminium, wireGtDouble, Kanthal, Titanium,
                NeodymiumMagnetic);
        motor(IV, "electric_motor_iv", true, cableGtDouble, Tungsten, wireGtDouble, Graphene, TungstenSteel,
                NeodymiumMagnetic);

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(stickLong, SamariumMagnetic)
                .input(stickLong, HSSS, 2)
                .input(ring, HSSS, 2)
                .input(round, HSSS, 4)
                .input(wireFine, Ruridit, 64)
                .input(cableGtSingle, NiobiumTitanium, 2)
                .fluidInputs(SolderingAlloy.getFluid(L))
                .fluidInputs(Lubricant.getFluid(250))
                .output(ELECTRIC_MOTOR_LuV)
                .scannerResearch(ELECTRIC_MOTOR_IV)
                .duration(600).EUt(6000).buildAndRegister();

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(stickLong, SamariumMagnetic)
                .input(stickLong, Osmiridium, 4)
                .input(ring, Osmiridium, 4)
                .input(round, Osmiridium, 8)
                .input(wireFine, Europium, 64)
                .input(wireFine, Europium, 32)
                .input(cableGtSingle, VanadiumGallium, 2)
                .fluidInputs(SolderingAlloy.getFluid(L * 2))
                .fluidInputs(Lubricant.getFluid(500))
                .output(ELECTRIC_MOTOR_ZPM)
                .scannerResearch(b -> b
                        .researchStack(ELECTRIC_MOTOR_LuV)
                        .duration(1200)
                        .EUt(VA[IV]))
                .duration(600).EUt(24000).buildAndRegister();

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(stickLong, SamariumMagnetic)
                .input(stickLong, Tritanium, 4)
                .input(ring, Tritanium, 4)
                .input(round, Tritanium, 8)
                .input(wireFine, Americium, 64)
                .input(wireFine, Americium, 64)
                .input(cableGtSingle, YttriumBariumCuprate, 2)
                .fluidInputs(SolderingAlloy.getFluid(L * 4))
                .fluidInputs(Lubricant.getFluid(1000))
                .fluidInputs(Naquadria.getFluid(L * 4))
                .output(ELECTRIC_MOTOR_UV)
                .stationResearch(b -> b
                        .researchStack(ELECTRIC_MOTOR_ZPM)
                        .CWUt(32)
                        .EUt(VA[ZPM]))
                .duration(600).EUt(100000).buildAndRegister();
    }

    /** A motor by hand and in the assembler, from a cable, a wire, two rods and a magnetic rod. */
    private static void motor(int tier, String name, boolean recycling, OrePrefix cablePrefix, Material cable,
                              OrePrefix wirePrefix, Material wire, Material rod, Material magnet) {
        ModHandler.addShapedRecipe(recycling, name, MOTORS[tier].getStackForm(), "CWR", "WMW", "RWC",
                'C', new UnificationEntry(cablePrefix, cable), 'W', new UnificationEntry(wirePrefix, wire),
                'R', new UnificationEntry(stick, rod), 'M', new UnificationEntry(stick, magnet));

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(cablePrefix, cable, 2)
                .input(stick, rod, 2)
                .input(stick, magnet)
                .input(wirePrefix, wire, 4)
                .outputs(MOTORS[tier].getStackForm())
                .duration(100).EUt(VA[LV]).buildAndRegister();
    }

    private static void conveyorsAndPumps() {
        final Map<String, Material> rubberMaterials = new Object2ObjectOpenHashMap<>();
        rubberMaterials.put("rubber", Rubber);
        rubberMaterials.put("silicone_rubber", SiliconeRubber);
        rubberMaterials.put("styrene_butadiene_rubber", StyreneButadieneRubber);

        for (Map.Entry<String, Material> materialEntry : rubberMaterials.entrySet()) {
            // natural rubber reaches EV; the rubber recipe gives the recycling data, at IV the silicone rubber one
            Material material = materialEntry.getValue();
            int maxTier = material.equals(Rubber) ? EV : IV;
            for (int tier = LV; tier <= maxTier; tier++) {
                boolean recycling = material.equals(tier == IV ? SiliconeRubber : Rubber);
                conveyor(tier, materialEntry.getKey(), material, recycling);
            }
            for (int tier = LV; tier <= maxTier; tier++) {
                boolean recycling = material.equals(tier == IV ? SiliconeRubber : Rubber);
                pump(tier, materialEntry.getKey(), material, recycling);
            }
        }

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(ELECTRIC_MOTOR_LuV, 2)
                .input(plate, HSSS, 2)
                .input(ring, HSSS, 4)
                .input(round, HSSS, 16)
                .input(screw, HSSS, 4)
                .input(cableGtSingle, NiobiumTitanium, 2)
                .fluidInputs(SolderingAlloy.getFluid(L))
                .fluidInputs(Lubricant.getFluid(250))
                .fluidInputs(StyreneButadieneRubber.getFluid(L * 8))
                .output(CONVEYOR_MODULE_LuV)
                .scannerResearch(CONVEYOR_MODULE_IV)
                .duration(600).EUt(6000).buildAndRegister();

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(ELECTRIC_MOTOR_ZPM, 2)
                .input(plate, Osmiridium, 2)
                .input(ring, Osmiridium, 4)
                .input(round, Osmiridium, 16)
                .input(screw, Osmiridium, 4)
                .input(cableGtSingle, VanadiumGallium, 2)
                .fluidInputs(SolderingAlloy.getFluid(L * 2))
                .fluidInputs(Lubricant.getFluid(500))
                .fluidInputs(StyreneButadieneRubber.getFluid(L * 16))
                .output(CONVEYOR_MODULE_ZPM)
                .scannerResearch(b -> b
                        .researchStack(CONVEYOR_MODULE_LuV)
                        .duration(1200)
                        .EUt(VA[IV]))
                .duration(600).EUt(24000).buildAndRegister();

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(ELECTRIC_MOTOR_UV, 2)
                .input(plate, Tritanium, 2)
                .input(ring, Tritanium, 4)
                .input(round, Tritanium, 16)
                .input(screw, Tritanium, 4)
                .input(cableGtSingle, YttriumBariumCuprate, 2)
                .fluidInputs(SolderingAlloy.getFluid(L * 4))
                .fluidInputs(Lubricant.getFluid(1000))
                .fluidInputs(StyreneButadieneRubber.getFluid(L * 24))
                .fluidInputs(Naquadria.getFluid(L * 4))
                .output(CONVEYOR_MODULE_UV)
                .stationResearch(b -> b
                        .researchStack(CONVEYOR_MODULE_ZPM)
                        .CWUt(32)
                        .EUt(VA[ZPM]))
                .duration(600).EUt(100000).buildAndRegister();

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(ELECTRIC_MOTOR_LuV)
                .input(pipeSmallFluid, NiobiumTitanium)
                .input(plate, HSSS, 2)
                .input(screw, HSSS, 8)
                .input(ring, SiliconeRubber, 4)
                .input(rotor, HSSS)
                .input(cableGtSingle, NiobiumTitanium, 2)
                .fluidInputs(SolderingAlloy.getFluid(L))
                .fluidInputs(Lubricant.getFluid(250))
                .output(ELECTRIC_PUMP_LuV)
                .scannerResearch(ELECTRIC_PUMP_IV)
                .duration(600).EUt(6000).buildAndRegister();

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(ELECTRIC_MOTOR_ZPM)
                .input(pipeNormalFluid, Polybenzimidazole)
                .input(plate, Osmiridium, 2)
                .input(screw, Osmiridium, 8)
                .input(ring, SiliconeRubber, 8)
                .input(rotor, Osmiridium)
                .input(cableGtSingle, VanadiumGallium, 2)
                .fluidInputs(SolderingAlloy.getFluid(L * 2))
                .fluidInputs(Lubricant.getFluid(500))
                .output(ELECTRIC_PUMP_ZPM)
                .scannerResearch(b -> b
                        .researchStack(ELECTRIC_PUMP_LuV)
                        .duration(1200)
                        .EUt(VA[IV]))
                .duration(600).EUt(24000).buildAndRegister();

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(ELECTRIC_MOTOR_UV)
                .input(pipeLargeFluid, Naquadah)
                .input(plate, Tritanium, 2)
                .input(screw, Tritanium, 8)
                .input(ring, SiliconeRubber, 16)
                .input(rotor, NaquadahAlloy)
                .input(cableGtSingle, YttriumBariumCuprate, 2)
                .fluidInputs(SolderingAlloy.getFluid(L * 4))
                .fluidInputs(Lubricant.getFluid(1000))
                .fluidInputs(Naquadria.getFluid(L * 4))
                .output(ELECTRIC_PUMP_UV)
                .stationResearch(b -> b
                        .researchStack(ELECTRIC_PUMP_ZPM)
                        .CWUt(32)
                        .EUt(VA[ZPM]))
                .duration(600).EUt(100000).buildAndRegister();
    }

    /** A conveyor by hand from rubber plates, and in the assembler from liquid rubber. */
    private static void conveyor(int tier, String rubberName, Material rubber, boolean recycling) {
        ModHandler.addShapedRecipe(recycling, "conveyor_module_" + tierName(tier) + "_" + rubberName,
                CONVEYORS[tier].getStackForm(), "RRR", "MCM", "RRR", 'R', new UnificationEntry(plate, rubber),
                'C', new UnificationEntry(cableGtSingle, CABLES[tier]), 'M', MOTORS[tier].getStackForm());

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(cableGtSingle, CABLES[tier])
                .inputs(MOTORS[tier].getStackForm(2))
                .fluidInputs(rubber.getFluid(L * 6))
                .circuitMeta(1)
                .outputs(CONVEYORS[tier].getStackForm())
                .duration(100).EUt(VA[LV]).buildAndRegister();
    }

    /** A pump by hand and in the assembler, sealed with rubber rings. */
    private static void pump(int tier, String rubberName, Material rubber, boolean recycling) {
        ModHandler.addShapedRecipe(recycling, "electric_pump_" + tierName(tier) + "_" + rubberName,
                PUMPS[tier].getStackForm(), "SXR", "dPw", "RMC", 'S', new UnificationEntry(screw, PUMP_ROTORS[tier]),
                'X', new UnificationEntry(rotor, PUMP_ROTORS[tier]),
                'P', new UnificationEntry(pipeNormalFluid, PUMP_PIPES[tier]), 'R', new UnificationEntry(ring, rubber),
                'C', new UnificationEntry(cableGtSingle, CABLES[tier]), 'M', MOTORS[tier].getStackForm());

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(cableGtSingle, CABLES[tier])
                .input(pipeNormalFluid, PUMP_PIPES[tier])
                .input(screw, PUMP_ROTORS[tier])
                .input(rotor, PUMP_ROTORS[tier])
                .input(ring, rubber, 2)
                .inputs(MOTORS[tier].getStackForm())
                .outputs(PUMPS[tier].getStackForm())
                .duration(100).EUt(VA[LV]).buildAndRegister();
    }

    private static void fluidRegulators() {
        for (int tier = LV; tier <= UV; tier++) {
            // only the LV to IV regulators carry recycling data
            var builder = ASSEMBLER_RECIPES.recipeBuilder()
                    .inputs(PUMPS[tier].getStackForm())
                    .input(circuit, circuitTier(tier), 2)
                    .circuitMeta(1)
                    .outputs(FLUID_REGULATORS[tier].getStackForm())
                    .EUt(VA[tier])
                    .duration(450 - 50 * tier);
            if (tier <= IV) builder.withRecycling();
            builder.buildAndRegister();
        }
    }

    private static void voidingCovers() {
        ModHandler.addShapedRecipe(true, "cover_item_voiding", COVER_ITEM_VOIDING.getStackForm(), "SDS", "dPw", " E ",
                'S', new UnificationEntry(screw, Steel), 'D', COVER_ITEM_DETECTOR.getStackForm(), 'P',
                new UnificationEntry(pipeNormalItem, Brass), 'E', Items.ENDER_PEARL);

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(screw, Steel, 2)
                .inputs(COVER_ITEM_DETECTOR.getStackForm())
                .input(pipeNormalItem, Brass)
                .input(Items.ENDER_PEARL)
                .outputs(COVER_ITEM_VOIDING.getStackForm())
                .duration(100).EUt(VA[LV]).buildAndRegister();

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(COVER_ITEM_VOIDING)
                .input(circuit, Tier.MV, 1)
                .outputs(COVER_ITEM_VOIDING_ADVANCED.getStackForm())
                .duration(100).EUt(VA[LV]).buildAndRegister();

        ModHandler.addShapedRecipe(true, "cover_fluid_voiding", COVER_FLUID_VOIDING.getStackForm(), "SDS", "dPw", " E ",
                'S', new UnificationEntry(screw, Steel), 'D', COVER_FLUID_DETECTOR.getStackForm(), 'P',
                new UnificationEntry(pipeNormalFluid, Bronze), 'E', Items.ENDER_PEARL);

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(screw, Steel, 2)
                .inputs(COVER_FLUID_DETECTOR.getStackForm())
                .input(pipeNormalFluid, Bronze)
                .input(Items.ENDER_PEARL)
                .outputs(COVER_FLUID_VOIDING.getStackForm())
                .duration(100).EUt(VA[LV]).buildAndRegister();

        ASSEMBLER_RECIPES.recipeBuilder()
                .input(COVER_FLUID_VOIDING)
                .input(circuit, Tier.MV, 1)
                .outputs(COVER_FLUID_VOIDING_ADVANCED.getStackForm())
                .duration(100).EUt(VA[LV]).buildAndRegister();
    }

    private static void pistons() {
        for (int tier = LV; tier <= IV; tier++) {
            Material metal = METALS[tier];
            ModHandler.addShapedRecipe(true, "electric_piston_" + tierName(tier), PISTONS[tier].getStackForm(), "PPP",
                    "CRR", "CMG", 'P', new UnificationEntry(plate, metal),
                    'C', new UnificationEntry(cableGtSingle, CABLES[tier]), 'R', new UnificationEntry(stick, metal),
                    'G', new UnificationEntry(gearSmall, metal), 'M', MOTORS[tier].getStackForm());

            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(stick, metal, 2)
                    .input(cableGtSingle, CABLES[tier], 2)
                    .input(plate, metal, 3)
                    .input(gearSmall, metal)
                    .inputs(MOTORS[tier].getStackForm())
                    .outputs(PISTONS[tier].getStackForm())
                    .duration(100).EUt(VA[LV]).buildAndRegister();
        }

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(ELECTRIC_MOTOR_LuV)
                .input(plate, HSSS, 4)
                .input(ring, HSSS, 4)
                .input(round, HSSS, 16)
                .input(stick, HSSS, 4)
                .input(gear, HSSS)
                .input(gearSmall, HSSS, 2)
                .input(cableGtSingle, NiobiumTitanium, 2)
                .fluidInputs(SolderingAlloy.getFluid(L))
                .fluidInputs(Lubricant.getFluid(250))
                .output(ELECTRIC_PISTON_LUV)
                .scannerResearch(ELECTRIC_PISTON_IV)
                .duration(600).EUt(6000).buildAndRegister();

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(ELECTRIC_MOTOR_ZPM)
                .input(plate, Osmiridium, 4)
                .input(ring, Osmiridium, 4)
                .input(round, Osmiridium, 16)
                .input(stick, Osmiridium, 4)
                .input(gear, Osmiridium)
                .input(gearSmall, Osmiridium, 2)
                .input(cableGtSingle, VanadiumGallium, 2)
                .fluidInputs(SolderingAlloy.getFluid(L * 2))
                .fluidInputs(Lubricant.getFluid(500))
                .output(ELECTRIC_PISTON_ZPM)
                .scannerResearch(b -> b
                        .researchStack(ELECTRIC_PISTON_LUV)
                        .duration(1200)
                        .EUt(VA[IV]))
                .duration(600).EUt(24000).buildAndRegister();

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(ELECTRIC_MOTOR_UV)
                .input(plate, Tritanium, 4)
                .input(ring, Tritanium, 4)
                .input(round, Tritanium, 16)
                .input(stick, Tritanium, 4)
                .input(gear, NaquadahAlloy)
                .input(gearSmall, NaquadahAlloy, 2)
                .input(cableGtSingle, YttriumBariumCuprate, 2)
                .fluidInputs(SolderingAlloy.getFluid(L * 4))
                .fluidInputs(Lubricant.getFluid(1000))
                .fluidInputs(Naquadria.getFluid(L * 4))
                .output(ELECTRIC_PISTON_UV)
                .stationResearch(b -> b
                        .researchStack(ELECTRIC_PISTON_ZPM)
                        .CWUt(32)
                        .EUt(VA[ZPM]))
                .duration(600).EUt(100000).buildAndRegister();
    }

    private static void robotArms() {
        for (int tier = LV; tier <= IV; tier++) {
            ModHandler.addShapedRecipe(true, "robot_arm_" + tierName(tier), ROBOT_ARMS[tier].getStackForm(), "CCC",
                    "MRM", "PXR", 'C', new UnificationEntry(cableGtSingle, CABLES[tier]),
                    'R', new UnificationEntry(stick, METALS[tier]), 'M', MOTORS[tier].getStackForm(),
                    'P', PISTONS[tier].getStackForm(), 'X', new UnificationEntry(circuit, circuitTier(tier)));

            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(cableGtSingle, CABLES[tier], 3)
                    .input(stick, METALS[tier], 2)
                    .inputs(MOTORS[tier].getStackForm(2))
                    .inputs(PISTONS[tier].getStackForm())
                    .input(circuit, circuitTier(tier))
                    .outputs(ROBOT_ARMS[tier].getStackForm())
                    .duration(100).EUt(VA[LV]).buildAndRegister();
        }

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(stickLong, HSSS, 4)
                .input(gear, HSSS)
                .input(gearSmall, HSSS, 3)
                .input(ELECTRIC_MOTOR_LuV, 2)
                .input(ELECTRIC_PISTON_LUV)
                .input(circuit, Tier.LuV)
                .input(circuit, Tier.IV, 2)
                .input(circuit, Tier.EV, 4)
                .input(cableGtSingle, NiobiumTitanium, 4)
                .fluidInputs(SolderingAlloy.getFluid(L * 4))
                .fluidInputs(Lubricant.getFluid(250))
                .output(ROBOT_ARM_LuV)
                .scannerResearch(ROBOT_ARM_IV)
                .duration(600).EUt(6000).buildAndRegister();

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(stickLong, Osmiridium, 4)
                .input(gear, Osmiridium)
                .input(gearSmall, Osmiridium, 3)
                .input(ELECTRIC_MOTOR_ZPM, 2)
                .input(ELECTRIC_PISTON_ZPM)
                .input(circuit, Tier.ZPM)
                .input(circuit, Tier.LuV, 2)
                .input(circuit, Tier.IV, 4)
                .input(cableGtSingle, VanadiumGallium, 4)
                .fluidInputs(SolderingAlloy.getFluid(L * 8))
                .fluidInputs(Lubricant.getFluid(500))
                .output(ROBOT_ARM_ZPM)
                .scannerResearch(b -> b
                        .researchStack(ROBOT_ARM_LuV)
                        .duration(1200)
                        .EUt(VA[IV]))
                .duration(600).EUt(24000).buildAndRegister();

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(stickLong, Tritanium, 4)
                .input(gear, Tritanium)
                .input(gearSmall, Tritanium, 3)
                .input(ELECTRIC_MOTOR_UV, 2)
                .input(ELECTRIC_PISTON_UV)
                .input(circuit, Tier.UV)
                .input(circuit, Tier.ZPM, 2)
                .input(circuit, Tier.LuV, 4)
                .input(cableGtSingle, YttriumBariumCuprate, 4)
                .fluidInputs(SolderingAlloy.getFluid(L * 12))
                .fluidInputs(Lubricant.getFluid(1000))
                .fluidInputs(Naquadria.getFluid(L * 4))
                .output(ROBOT_ARM_UV)
                .stationResearch(b -> b
                        .researchStack(ROBOT_ARM_ZPM)
                        .CWUt(32)
                        .EUt(VA[ZPM]))
                .duration(600).EUt(100000).buildAndRegister();
    }

    private static void fieldGenerators() {
        for (int tier = LV; tier <= IV; tier++) {
            ModHandler.addShapedRecipe(true, "field_generator_" + tierName(tier), FIELD_GENERATORS[tier].getStackForm(),
                    "WPW", "XGX", "WPW", 'W', new UnificationEntry(wireGtQuadruple, FIELD_WIRES[tier]),
                    'P', new UnificationEntry(FIELD_PLATES[tier], METALS[tier]), 'G', FIELD_CORES[tier],
                    'X', new UnificationEntry(circuit, circuitTier(tier)));

            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(input(FIELD_CORES[tier], 1))
                    .input(FIELD_PLATES[tier], METALS[tier], 2)
                    .input(circuit, circuitTier(tier), 2)
                    .input(wireGtQuadruple, FIELD_WIRES[tier], 4)
                    .outputs(FIELD_GENERATORS[tier].getStackForm())
                    .duration(100).EUt(VA[LV]).buildAndRegister();
        }

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(frameGt, HSSS)
                .input(plate, HSSS, 6)
                .input(QUANTUM_STAR)
                .input(EMITTER_LuV, 2)
                .input(circuit, Tier.LuV, 2)
                .input(wireFine, IndiumTinBariumTitaniumCuprate, 64)
                .input(wireFine, IndiumTinBariumTitaniumCuprate, 64)
                .input(cableGtSingle, NiobiumTitanium, 4)
                .fluidInputs(SolderingAlloy.getFluid(L * 4))
                .output(FIELD_GENERATOR_LuV)
                .scannerResearch(b -> b
                        .researchStack(FIELD_GENERATOR_IV)
                        .duration(2400))
                .duration(600).EUt(6000).buildAndRegister();

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(frameGt, NaquadahAlloy)
                .input(plate, NaquadahAlloy, 6)
                .input(QUANTUM_STAR)
                .input(EMITTER_ZPM, 2)
                .input(circuit, Tier.ZPM, 2)
                .input(wireFine, UraniumRhodiumDinaquadide, 64)
                .input(wireFine, UraniumRhodiumDinaquadide, 64)
                .input(cableGtSingle, VanadiumGallium, 4)
                .fluidInputs(SolderingAlloy.getFluid(L * 8))
                .output(FIELD_GENERATOR_ZPM)
                .stationResearch(b -> b
                        .researchStack(FIELD_GENERATOR_LuV)
                        .CWUt(4))
                .duration(600).EUt(24000).buildAndRegister();

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(frameGt, Tritanium)
                .input(plate, Tritanium, 6)
                .input(GRAVI_STAR)
                .input(EMITTER_UV, 2)
                .input(circuit, Tier.UV, 2)
                .input(wireFine, EnrichedNaquadahTriniumEuropiumDuranide, 64)
                .input(wireFine, EnrichedNaquadahTriniumEuropiumDuranide, 64)
                .input(cableGtSingle, YttriumBariumCuprate, 4)
                .fluidInputs(SolderingAlloy.getFluid(L * 12))
                .fluidInputs(Naquadria.getFluid(L * 4))
                .output(FIELD_GENERATOR_UV)
                .stationResearch(b -> b
                        .researchStack(FIELD_GENERATOR_ZPM)
                        .CWUt(48)
                        .EUt(VA[ZPM]))
                .duration(600).EUt(100000).buildAndRegister();
    }

    private static void sensors() {
        for (int tier = LV; tier <= IV; tier++) {
            ModHandler.addShapedRecipe(true, "sensor_" + tierName(tier), SENSORS[tier].getStackForm(), "P G", "PR ",
                    "XPP", 'P', new UnificationEntry(plate, METALS[tier]),
                    'R', new UnificationEntry(stick, EMITTER_RODS[tier]), 'G', EMITTER_GEMS[tier],
                    'X', new UnificationEntry(circuit, circuitTier(tier)));

            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(stick, EMITTER_RODS[tier])
                    .input(plate, METALS[tier], 4)
                    .input(circuit, circuitTier(tier))
                    .input(input(EMITTER_GEMS[tier], 1))
                    .outputs(SENSORS[tier].getStackForm())
                    .duration(100).EUt(VA[LV]).buildAndRegister();
        }

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(frameGt, HSSS)
                .input(ELECTRIC_MOTOR_LuV)
                .input(plate, Ruridit, 4)
                .input(QUANTUM_STAR)
                .input(circuit, Tier.LuV, 2)
                .input(foil, Palladium, 64)
                .input(foil, Palladium, 32)
                .input(cableGtSingle, NiobiumTitanium, 4)
                .fluidInputs(SolderingAlloy.getFluid(L * 2))
                .output(SENSOR_LuV)
                .scannerResearch(b -> b
                        .researchStack(SENSOR_IV)
                        .duration(2400))
                .duration(600).EUt(6000).buildAndRegister();

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(frameGt, NaquadahAlloy)
                .input(ELECTRIC_MOTOR_ZPM)
                .input(plate, Osmiridium, 4)
                .input(QUANTUM_STAR, 2)
                .input(circuit, Tier.ZPM, 2)
                .input(foil, Trinium, 64)
                .input(foil, Trinium, 32)
                .input(cableGtSingle, VanadiumGallium, 4)
                .fluidInputs(SolderingAlloy.getFluid(L * 4))
                .output(SENSOR_ZPM)
                .stationResearch(b -> b
                        .researchStack(SENSOR_LuV)
                        .CWUt(4))
                .duration(600).EUt(24000).buildAndRegister();

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(frameGt, Tritanium)
                .input(ELECTRIC_MOTOR_UV)
                .input(plate, Tritanium, 4)
                .input(GRAVI_STAR)
                .input(circuit, Tier.UV, 2)
                .input(foil, Naquadria, 64)
                .input(foil, Naquadria, 32)
                .input(cableGtSingle, YttriumBariumCuprate, 4)
                .fluidInputs(SolderingAlloy.getFluid(L * 8))
                .fluidInputs(Naquadria.getFluid(L * 4))
                .output(SENSOR_UV)
                .stationResearch(b -> b
                        .researchStack(SENSOR_ZPM)
                        .CWUt(48)
                        .EUt(VA[ZPM]))
                .duration(600).EUt(100000).buildAndRegister();
    }

    private static void emitters() {
        for (int tier = LV; tier <= IV; tier++) {
            ModHandler.addShapedRecipe(true, "emitter_" + tierName(tier), EMITTERS[tier].getStackForm(), "CRX", "RGR",
                    "XRC", 'R', new UnificationEntry(stick, EMITTER_RODS[tier]),
                    'C', new UnificationEntry(cableGtSingle, CABLES[tier]), 'G', EMITTER_GEMS[tier],
                    'X', new UnificationEntry(circuit, circuitTier(tier)));

            ASSEMBLER_RECIPES.recipeBuilder()
                    .input(stick, EMITTER_RODS[tier], 4)
                    .input(cableGtSingle, CABLES[tier], 2)
                    .input(circuit, circuitTier(tier), 2)
                    .input(input(EMITTER_GEMS[tier], 1))
                    .circuitMeta(1)
                    .outputs(EMITTERS[tier].getStackForm())
                    .duration(100).EUt(VA[LV]).buildAndRegister();
        }

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(frameGt, HSSS)
                .input(ELECTRIC_MOTOR_LuV)
                .input(stickLong, Ruridit, 4)
                .input(QUANTUM_STAR)
                .input(circuit, Tier.LuV, 2)
                .input(foil, Palladium, 64)
                .input(foil, Palladium, 32)
                .input(cableGtSingle, NiobiumTitanium, 4)
                .fluidInputs(SolderingAlloy.getFluid(L * 2))
                .output(EMITTER_LuV)
                .scannerResearch(b -> b
                        .researchStack(EMITTER_IV)
                        .duration(2400))
                .duration(600).EUt(6000).buildAndRegister();

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(frameGt, NaquadahAlloy)
                .input(ELECTRIC_MOTOR_ZPM)
                .input(stickLong, Osmiridium, 4)
                .input(QUANTUM_STAR, 2)
                .input(circuit, Tier.ZPM, 2)
                .input(foil, Trinium, 64)
                .input(foil, Trinium, 32)
                .input(cableGtSingle, VanadiumGallium, 4)
                .fluidInputs(SolderingAlloy.getFluid(L * 4))
                .output(EMITTER_ZPM)
                .stationResearch(b -> b
                        .researchStack(EMITTER_LuV)
                        .CWUt(8))
                .duration(600).EUt(24000).buildAndRegister();

        ASSEMBLY_LINE_RECIPES.recipeBuilder()
                .input(frameGt, Tritanium)
                .input(ELECTRIC_MOTOR_UV)
                .input(stickLong, Tritanium, 4)
                .input(GRAVI_STAR)
                .input(circuit, Tier.UV, 2)
                .input(foil, Naquadria, 64)
                .input(foil, Naquadria, 32)
                .input(cableGtSingle, YttriumBariumCuprate, 4)
                .fluidInputs(SolderingAlloy.getFluid(L * 8))
                .fluidInputs(Naquadria.getFluid(L * 4))
                .output(EMITTER_UV)
                .stationResearch(b -> b
                        .researchStack(EMITTER_ZPM)
                        .CWUt(48)
                        .EUt(VA[ZPM]))
                .duration(600).EUt(100000).buildAndRegister();
    }

    /** A table entry as a recipe input: an ore dictionary entry, or a meta item. */
    private static GTRecipeInput input(Object ingredient, int count) {
        if (ingredient instanceof UnificationEntry entry) {
            return new GTRecipeOreInput(entry.orePrefix, entry.material, count);
        }
        return new GTRecipeItemInput(((MetaItem<?>.MetaValueItem) ingredient).getStackForm(count));
    }
}
