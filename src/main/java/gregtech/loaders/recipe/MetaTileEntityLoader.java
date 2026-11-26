package gregtech.loaders.recipe;

import gregtech.api.GTValues;
import gregtech.api.items.OreDictNames;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.recipes.ModHandler;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.MarkerMaterials;
import gregtech.api.unification.material.MarkerMaterials.Tier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.UnificationEntry;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockBoilerCasing.BoilerCasingType;
import gregtech.common.blocks.BlockFireboxCasing.FireboxCasingType;
import gregtech.common.blocks.BlockGlassCasing;
import gregtech.common.blocks.BlockHermeticCasing.HermeticCasingsType;
import gregtech.common.blocks.BlockMachineCasing.MachineCasingType;
import gregtech.common.blocks.BlockMetalCasing.MetalCasingType;
import gregtech.common.blocks.BlockMultiblockCasing;
import gregtech.common.blocks.BlockMultiblockCasing.MultiblockCasingType;
import gregtech.common.blocks.BlockTurbineCasing.TurbineCasingType;
import gregtech.common.blocks.BlockWarningSign;
import gregtech.common.blocks.BlockWarningSign1;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.blocks.wood.BlockGregPlanks;
import gregtech.common.items.MetaItems;
import gregtech.common.metatileentities.MetaTileEntities;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidUtil;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.List;

import static gregtech.common.blocks.BlockBoilerCasing.BoilerCasingType.*;
import static gregtech.common.blocks.BlockFireboxCasing.FireboxCasingType.*;
import static gregtech.common.blocks.BlockHermeticCasing.HermeticCasingsType.*;
import static gregtech.common.blocks.BlockMachineCasing.MachineCasingType.*;
import static gregtech.common.blocks.BlockMetalCasing.MetalCasingType.*;
import static gregtech.common.blocks.BlockMultiblockCasing.MultiblockCasingType.*;
import static gregtech.common.blocks.BlockSteamCasing.SteamCasingType.*;
import static gregtech.common.blocks.BlockTurbineCasing.TurbineCasingType.*;
import static gregtech.common.blocks.BlockWarningSign.SignType.*;
import static gregtech.common.blocks.BlockWarningSign1.SignType.*;
import static gregtech.common.blocks.BlockWireCoil.CoilType.CUPRONICKEL;
import static gregtech.loaders.recipe.CraftingComponent.*;
import static gregtech.loaders.recipe.RecipeTiers.*;

public class MetaTileEntityLoader {

    public static void init() {
        for (int tier = GTValues.ULV; tier <= GTValues.UHV; tier++) {
            ModHandler.addShapedRecipe(true, "casing_" + tierName(tier),
                    MetaBlocks.MACHINE_CASING.getItemVariant(MachineCasingType.values()[tier]), "PPP", "PwP", "PPP",
                    'P', PLATE.getIngredient(tier));
        }

        // If these recipes are changed, change the values in MaterialInfoLoader.java
        registerMachineRecipe(false, MetaTileEntities.HULL, "PLP", "CHC", 'P', HULL_PLATE, 'L', PLATE, 'C', CABLE, 'H',
                CASING);

        if (ConfigHolder.recipes.harderBrickRecipes) {
            ModHandler.addFluidReplaceRecipe("bucket_of_concrete",
                    FluidUtil.getFilledBucket(Materials.Concrete.getFluid(1000)),
                    "C S", "CWQ", " L ",
                    'C', new UnificationEntry(OrePrefix.dust, Materials.Calcite),
                    'S', new UnificationEntry(OrePrefix.dust, Materials.Stone),
                    'W', new ItemStack(Items.WATER_BUCKET),
                    'Q', new UnificationEntry(OrePrefix.dust, Materials.QuartzSand),
                    'L', new UnificationEntry(OrePrefix.dust, Materials.Clay));

            ModHandler.addShapedRecipe("casing_primitive_bricks",
                    MetaBlocks.METAL_CASING.getItemVariant(PRIMITIVE_BRICKS),
                    "BGB", "BCB", "BGB",
                    'B', MetaItems.FIRECLAY_BRICK.getStackForm(),
                    'G', new UnificationEntry(OrePrefix.dust, Materials.Gypsum),
                    'C', FluidUtil.getFilledBucket(Materials.Concrete.getFluid(1000)));
        } else {
            ModHandler.addShapedRecipe("casing_primitive_bricks",
                    MetaBlocks.METAL_CASING.getItemVariant(PRIMITIVE_BRICKS, 1), "XX", "XX", 'X',
                    MetaItems.FIRECLAY_BRICK);
        }

        ModHandler.addShapedRecipe(true, "casing_coke_bricks", MetaBlocks.METAL_CASING.getItemVariant(COKE_BRICKS, 1),
                "XX", "XX", 'X', MetaItems.COKE_OVEN_BRICK);
        ModHandler.addShapedRecipe(true, "casing_bronze_bricks",
                MetaBlocks.METAL_CASING.getItemVariant(BRONZE_BRICKS, ConfigHolder.recipes.casingsPerCraft), "PhP",
                "PBP", "PwP", 'P', new UnificationEntry(OrePrefix.plate, Materials.Bronze), 'B',
                new ItemStack(Blocks.BRICK_BLOCK, 1));
        metalCasing("casing_steel_solid", STEEL_SOLID, Materials.Steel, Materials.Steel);
        metalCasing("casing_titanium_stable", TITANIUM_STABLE, Materials.Titanium, Materials.Titanium);
        metalCasing("casing_invar_heatproof", INVAR_HEATPROOF, Materials.Invar, Materials.Invar);
        metalCasing("casing_aluminium_frostproof", ALUMINIUM_FROSTPROOF, Materials.Aluminium, Materials.Aluminium);
        metalCasing("casing_stainless_clean", STAINLESS_CLEAN, Materials.StainlessSteel, Materials.StainlessSteel);
        metalCasing("casing_tungstensteel_robust", TUNGSTENSTEEL_ROBUST, Materials.TungstenSteel,
                Materials.TungstenSteel);
        metalCasing("casing_hssg_robust", HSSE_STURDY, Materials.HSSE, Materials.Europium);
        metalCasing("casing_palladium_substation", PALLADIUM_SUBSTATION, Materials.Palladium, Materials.Iridium);

        // the steel turbine casing is built on a frame, the others on a steel turbine casing
        turbineCasing("casing_steel_turbine_casing", STEEL_TURBINE_CASING, Materials.Magnalium,
                new UnificationEntry(OrePrefix.frameGt, Materials.BlueSteel));
        turbineCasing("casing_stainless_turbine_casing", STAINLESS_TURBINE_CASING, Materials.StainlessSteel,
                MetaBlocks.TURBINE_CASING.getItemVariant(STEEL_TURBINE_CASING));
        turbineCasing("casing_titanium_turbine_casing", TITANIUM_TURBINE_CASING, Materials.Titanium,
                MetaBlocks.TURBINE_CASING.getItemVariant(STEEL_TURBINE_CASING));
        turbineCasing("casing_tungstensteel_turbine_casing", TUNGSTENSTEEL_TURBINE_CASING, Materials.TungstenSteel,
                MetaBlocks.TURBINE_CASING.getItemVariant(STEEL_TURBINE_CASING));

        pipeCasing("casing_bronze_pipe", BRONZE_PIPE, Materials.Bronze);
        pipeCasing("casing_steel_pipe", STEEL_PIPE, Materials.Steel);
        pipeCasing("casing_titanium_pipe", TITANIUM_PIPE, Materials.Titanium);
        pipeCasing("casing_tungstensteel_pipe", TUNGSTENSTEEL_PIPE, Materials.TungstenSteel);
        pipeCasing("casing_ptfe_pipe", POLYTETRAFLUOROETHYLENE_PIPE, Materials.Polytetrafluoroethylene);
        fireboxCasing("casing_bronze_firebox", BRONZE_FIREBOX, Materials.Bronze);
        fireboxCasing("casing_steel_firebox", STEEL_FIREBOX, Materials.Steel);
        fireboxCasing("casing_titanium_firebox", TITANIUM_FIREBOX, Materials.Titanium);
        fireboxCasing("casing_tungstensteel_firebox", TUNGSTENSTEEL_FIREBOX, Materials.TungstenSteel);

        gearboxCasing("casing_bronze_gearbox", BRONZE_GEARBOX, Materials.Bronze);
        gearboxCasing("casing_steel_gearbox", STEEL_GEARBOX, Materials.Steel);
        gearboxCasing("casing_stainless_steel_gearbox", STAINLESS_STEEL_GEARBOX, Materials.StainlessSteel);
        gearboxCasing("casing_titanium_gearbox", TITANIUM_GEARBOX, Materials.Titanium);
        gearboxCasing("casing_tungstensteel_gearbox", TUNGSTENSTEEL_GEARBOX, Materials.TungstenSteel);

        ModHandler.addShapedRecipe(true, "casing_grate_casing",
                MetaBlocks.MULTIBLOCK_CASING.getItemVariant(GRATE_CASING, ConfigHolder.recipes.casingsPerCraft), "PVP",
                "PFP", "PMP", 'P', new ItemStack(Blocks.IRON_BARS, 1), 'F',
                new UnificationEntry(OrePrefix.frameGt, Materials.Steel), 'M', MetaItems.ELECTRIC_MOTOR_MV, 'V',
                new UnificationEntry(OrePrefix.rotor, Materials.Steel));
        ModHandler.addShapedRecipe(true, "casing_assembly_control",
                MetaBlocks.MULTIBLOCK_CASING.getItemVariant(ASSEMBLY_CONTROL, ConfigHolder.recipes.casingsPerCraft),
                "CPC", "SFE", "CMC", 'C', new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.LuV), 'P',
                MetaItems.HIGH_POWER_INTEGRATED_CIRCUIT, 'S', MetaItems.SENSOR_IV.getStackForm(), 'F',
                new UnificationEntry(OrePrefix.frameGt, Materials.TungstenSteel), 'E',
                MetaItems.EMITTER_IV.getStackForm(), 'M', MetaItems.ELECTRIC_MOTOR_IV);
        ModHandler.addShapedRecipe(true, "casing_assembly_line",
                MetaBlocks.MULTIBLOCK_CASING.getItemVariant(ASSEMBLY_LINE_CASING, ConfigHolder.recipes.casingsPerCraft),
                "PGP", "AFA", "PGP", 'P', new UnificationEntry(OrePrefix.plate, Materials.Steel), 'G',
                new UnificationEntry(OrePrefix.gear, Materials.Ruridit), 'A', MetaItems.ROBOT_ARM_IV.getStackForm(),
                'F', OreDictUnifier.get(OrePrefix.frameGt, Materials.TungstenSteel));

        // a solid steel casing M with yellow dye Y and black dye B where each sign has them
        warningSign(YELLOW_STRIPES, "Y  ", " M ", "  B");
        warningSign(SMALL_YELLOW_STRIPES, "  Y", " M ", "B  ");
        warningSign(RADIOACTIVE_HAZARD, " YB", " M ", "   ");
        warningSign(BIO_HAZARD, " Y ", " MB", "   ");
        warningSign(EXPLOSION_HAZARD, " Y ", " M ", "  B");
        warningSign(FIRE_HAZARD, " Y ", " M ", " B ");
        warningSign(ACID_HAZARD, " Y ", " M ", "B  ");
        warningSign(MAGIC_HAZARD, " Y ", "BM ", "   ");
        warningSign(FROST_HAZARD, "BY ", " M ", "   ");
        warningSign(NOISE_HAZARD, "   ", " M ", "BY ");
        warningSign(GENERIC_HAZARD, "   ", "BM ", " Y ");
        warningSign(HIGH_VOLTAGE_HAZARD, "B  ", " M ", " Y ");
        warningSign(MAGNETIC_HAZARD, " B ", " M ", " Y ");
        warningSign(ANTIMATTER_HAZARD, "  B", " M ", " Y ");
        warningSign(HIGH_TEMPERATURE_HAZARD, "   ", " MB", " Y ");
        warningSign(VOID_HAZARD, "   ", " M ", " YB");

        warningSign(MOB_SPAWNER_HAZARD, "B  ", "YM ", "   ");
        warningSign(SPATIAL_STORAGE_HAZARD, " B ", "YM ", "   ");
        warningSign(LASER_HAZARD, "  B", "YM ", "   ");
        warningSign(MOB_HAZARD, "   ", "YMB", "   ");
        warningSign(BOSS_HAZARD, "   ", "YM ", "  B");
        warningSign(GREGIFICATION_HAZARD, "   ", "YM ", " B ");
        warningSign(CAUSALITY_HAZARD, "   ", "YM ", "B  ");
        warningSign(AUTOMATED_DEFENSES_HAZARD, "   ", " MY", "  B");
        warningSign(HIGH_PRESSURE_HAZARD, "   ", " MY", " B ");

        // only the first ten signs turn back into a solid steel casing
        for (BlockWarningSign.SignType type : List.of(YELLOW_STRIPES, SMALL_YELLOW_STRIPES, RADIOACTIVE_HAZARD,
                BIO_HAZARD, EXPLOSION_HAZARD, FIRE_HAZARD, ACID_HAZARD, MAGIC_HAZARD, FROST_HAZARD, NOISE_HAZARD)) {
            ModHandler.addShapelessRecipe(type.getName() + "_to_steel_solid_casing",
                    MetaBlocks.METAL_CASING.getItemVariant(STEEL_SOLID), MetaBlocks.WARNING_SIGN.getItemVariant(type));
        }

        // rotor holders: the tier's hull among gears and the small gears of the tier's metal
        Material[] rotorHolderGears = { Materials.BlackSteel, Materials.Ultimet, Materials.HSSG, Materials.Ruthenium,
                Materials.Trinium, Materials.Tritanium };
        for (int tier = GTValues.HV; tier <= GTValues.UV; tier++) {
            ModHandler.addShapedRecipe(true, "rotor_holder_" + tierName(tier),
                    MetaTileEntities.ROTOR_HOLDER[tier].getStackForm(), "SGS", "GHG", "SGS",
                    'H', MetaTileEntities.HULL[tier].getStackForm(),
                    'G', new UnificationEntry(OrePrefix.gear, rotorHolderGears[tier - GTValues.HV]),
                    'S', new UnificationEntry(OrePrefix.gearSmall, material(PLATE, tier)));
        }

        ModHandler.addShapedRecipe(true, "maintenance_hatch", MetaTileEntities.MAINTENANCE_HATCH.getStackForm(), "dwx",
                "hHc", "fsr", 'H', MetaTileEntities.HULL[GTValues.LV].getStackForm());
        ModHandler.addShapedRecipe(true, "maintenance_hatch_configurable",
                MetaTileEntities.CONFIGURABLE_MAINTENANCE_HATCH.getStackForm(), "   ", "CMC", "VHV", 'C',
                CIRCUIT.getIngredient(GTValues.HV), 'M', MetaTileEntities.MAINTENANCE_HATCH.getStackForm(), 'V',
                CONVEYOR.getIngredient(GTValues.HV), 'H', MetaTileEntities.HULL[GTValues.HV].getStackForm());
        ModHandler.addShapedRecipe(true, "maintenance_hatch_automatic",
                MetaTileEntities.AUTO_MAINTENANCE_HATCH.getStackForm(), "CMC", "RHR", "CMC", 'C',
                CIRCUIT.getIngredient(GTValues.HV), 'M', MetaTileEntities.MAINTENANCE_HATCH.getStackForm(), 'R',
                ROBOT_ARM.getIngredient(GTValues.HV), 'H', MetaTileEntities.HULL[GTValues.HV].getStackForm());
        ModHandler.addShapedRecipe(true, "maintenance_hatch_cleaning",
                MetaTileEntities.CLEANING_MAINTENANCE_HATCH.getStackForm(), "CMC", "RHR", "WCW", 'C',
                CIRCUIT.getIngredient(GTValues.UV), 'M', MetaTileEntities.AUTO_MAINTENANCE_HATCH.getStackForm(), 'R',
                ROBOT_ARM.getIngredient(GTValues.UV), 'H', MetaTileEntities.HULL[GTValues.UV].getStackForm(), 'W',
                new UnificationEntry(OrePrefix.cableGtSingle, Materials.YttriumBariumCuprate));

        ModHandler.addShapedRecipe(true, "machine_access_interface", MetaTileEntities.MACHINE_HATCH.getStackForm(),
                "CHS", 'C', new UnificationEntry(OrePrefix.circuit, Tier.IV), 'H',
                MetaTileEntities.HULL[GTValues.IV].getStackForm(), 'S', MetaItems.SENSOR_IV.getStackForm());

        ModHandler.addShapedRecipe(true, "passthrough_hatch_item",
                MetaTileEntities.PASSTHROUGH_HATCH_ITEM.getStackForm(), " C ", "GHG", " S ", 'C',
                MetaItems.CONVEYOR_MODULE_HV.getStackForm(), 'G',
                new UnificationEntry(OrePrefix.gearSmall, Materials.Steel), 'H',
                MetaTileEntities.HULL[GTValues.HV].getStackForm(), 'S', OreDictNames.chestWood);
        ModHandler.addShapedRecipe(true, "passthrough_hatch_fluid",
                MetaTileEntities.PASSTHROUGH_HATCH_FLUID.getStackForm(), " C ", "GHG", " S ", 'C',
                MetaItems.ELECTRIC_PUMP_HV.getStackForm(), 'G',
                new UnificationEntry(OrePrefix.pipeSmallFluid, Materials.Steel), 'H',
                MetaTileEntities.HULL[GTValues.HV].getStackForm(), 'S',
                MetaBlocks.TRANSPARENT_CASING.getItemVariant(BlockGlassCasing.CasingType.TEMPERED_GLASS));

        ModHandler.addShapedRecipe(true, "charcoal_pile_igniter", MetaTileEntities.CHARCOAL_PILE_IGNITER.getStackForm(),
                "ERE", "EHE", "FFF", 'E', new UnificationEntry(OrePrefix.plate, Materials.Bronze), 'R',
                new UnificationEntry(OrePrefix.rotor, Materials.Iron), 'H',
                MetaBlocks.STEAM_CASING.getItemVariant(BRONZE_BRICKS_HULL), 'F', new ItemStack(Items.FLINT));

        // STEAM MACHINES
        ModHandler.addShapedRecipe(true, "bronze_hull", MetaBlocks.STEAM_CASING.getItemVariant(BRONZE_HULL), "PPP",
                "PhP", "PPP", 'P', new UnificationEntry(OrePrefix.plate, Materials.Bronze));
        ModHandler.addShapedRecipe(true, "bronze_bricks_hull",
                MetaBlocks.STEAM_CASING.getItemVariant(BRONZE_BRICKS_HULL), "PPP", "PhP", "BBB", 'P',
                new UnificationEntry(OrePrefix.plate, Materials.Bronze), 'B', new ItemStack(Blocks.BRICK_BLOCK));
        ModHandler.addShapedRecipe(true, "steel_hull", MetaBlocks.STEAM_CASING.getItemVariant(STEEL_HULL), "PPP", "PhP",
                "PPP", 'P', new UnificationEntry(OrePrefix.plate, Materials.Steel));
        ModHandler.addShapedRecipe(true, "steel_bricks_hull", MetaBlocks.STEAM_CASING.getItemVariant(STEEL_BRICKS_HULL),
                "PPP", "PhP", "BBB", 'P', new UnificationEntry(OrePrefix.plate, Materials.WroughtIron), 'B',
                new ItemStack(Blocks.BRICK_BLOCK));

        ModHandler.addShapedRecipe(true, "steam_boiler_coal_bronze",
                MetaTileEntities.STEAM_BOILER_COAL_BRONZE.getStackForm(), "PPP", "PwP", "BFB", 'F',
                OreDictNames.craftingFurnace, 'P', new UnificationEntry(OrePrefix.plate, Materials.Bronze), 'B',
                new ItemStack(Blocks.BRICK_BLOCK));
        ModHandler.addShapedRecipe(true, "steam_boiler_coal_steel",
                MetaTileEntities.STEAM_BOILER_COAL_STEEL.getStackForm(), "PPP", "PwP", "BFB", 'F',
                OreDictNames.craftingFurnace, 'P', new UnificationEntry(OrePrefix.plate, Materials.Steel), 'B',
                new ItemStack(Blocks.BRICK_BLOCK));
        ModHandler.addShapedRecipe(true, "steam_boiler_lava_bronze",
                MetaTileEntities.STEAM_BOILER_LAVA_BRONZE.getStackForm(), "PPP", "PGP", "PMP", 'M',
                MetaBlocks.STEAM_CASING.getItemVariant(BRONZE_BRICKS_HULL), 'P',
                new UnificationEntry(OrePrefix.plate, Materials.Bronze), 'G', new ItemStack(Blocks.GLASS, 1));
        ModHandler.addShapedRecipe(true, "steam_boiler_lava_steel",
                MetaTileEntities.STEAM_BOILER_LAVA_STEEL.getStackForm(), "PPP", "PGP", "PMP", 'M',
                MetaBlocks.STEAM_CASING.getItemVariant(STEEL_BRICKS_HULL), 'P',
                new UnificationEntry(OrePrefix.plate, Materials.Steel), 'G', new ItemStack(Blocks.GLASS, 1));
        ModHandler.addShapedRecipe(true, "steam_boiler_solar_bronze",
                MetaTileEntities.STEAM_BOILER_SOLAR_BRONZE.getStackForm(), "GGG", "SSS", "PMP", 'M',
                MetaBlocks.STEAM_CASING.getItemVariant(BRONZE_BRICKS_HULL), 'P',
                new UnificationEntry(OrePrefix.pipeSmallFluid, Materials.Bronze), 'S',
                new UnificationEntry(OrePrefix.plate, Materials.Silver), 'G', new ItemStack(Blocks.GLASS));
        ModHandler.addShapedRecipe(true, "steam_boiler_solar_steel",
                MetaTileEntities.STEAM_BOILER_SOLAR_STEEL.getStackForm(), "GGG", "SSS", "PMP", 'M',
                MetaBlocks.STEAM_CASING.getItemVariant(STEEL_BRICKS_HULL), 'P',
                new UnificationEntry(OrePrefix.pipeSmallFluid, Materials.Steel), 'S',
                new UnificationEntry(OrePrefix.plateDouble, Materials.Silver), 'G', new ItemStack(Blocks.GLASS));

        ModHandler.addShapedRecipe(true, "steam_furnace_bronze", MetaTileEntities.STEAM_FURNACE_BRONZE.getStackForm(),
                "XXX", "XMX", "XFX", 'M', MetaBlocks.STEAM_CASING.getItemVariant(BRONZE_BRICKS_HULL), 'X',
                new UnificationEntry(OrePrefix.pipeSmallFluid, Materials.Bronze), 'F', OreDictNames.craftingFurnace);
        ModHandler.addShapedRecipe(true, "steam_furnace_steel", MetaTileEntities.STEAM_FURNACE_STEEL.getStackForm(),
                "XSX", "PMP", "XXX", 'M', MetaTileEntities.STEAM_FURNACE_BRONZE.getStackForm(), 'X',
                new UnificationEntry(OrePrefix.pipeSmallFluid, Materials.TinAlloy), 'S',
                new UnificationEntry(OrePrefix.plate, Materials.Steel), 'P',
                new UnificationEntry(OrePrefix.plate, Materials.WroughtIron));
        ModHandler.addShapedRecipe(true, "steam_macerator_bronze",
                MetaTileEntities.STEAM_MACERATOR_BRONZE.getStackForm(), "DXD", "XMX", "PXP", 'M',
                MetaBlocks.STEAM_CASING.getItemVariant(BRONZE_HULL), 'X',
                new UnificationEntry(OrePrefix.pipeSmallFluid, Materials.Bronze), 'P', OreDictNames.craftingPiston, 'D',
                new UnificationEntry(OrePrefix.gem, Materials.Diamond));
        ModHandler.addShapedRecipe(true, "steam_macerator_steel", MetaTileEntities.STEAM_MACERATOR_STEEL.getStackForm(),
                "WSW", "PMP", "WWW", 'M', MetaTileEntities.STEAM_MACERATOR_BRONZE.getStackForm(), 'W',
                new UnificationEntry(OrePrefix.plate, Materials.WroughtIron), 'S',
                new UnificationEntry(OrePrefix.plate, Materials.Steel), 'P',
                new UnificationEntry(OrePrefix.pipeSmallFluid, Materials.TinAlloy));
        ModHandler.addShapedRecipe(true, "steam_extractor_bronze",
                MetaTileEntities.STEAM_EXTRACTOR_BRONZE.getStackForm(), "XXX", "PMG", "XXX", 'M',
                MetaBlocks.STEAM_CASING.getItemVariant(BRONZE_HULL), 'X',
                new UnificationEntry(OrePrefix.pipeSmallFluid, Materials.Bronze), 'P', OreDictNames.craftingPiston, 'G',
                new ItemStack(Blocks.GLASS));
        ModHandler.addShapedRecipe(true, "steam_extractor_steel", MetaTileEntities.STEAM_EXTRACTOR_STEEL.getStackForm(),
                "PSP", "WMW", "PPP", 'M', MetaTileEntities.STEAM_EXTRACTOR_BRONZE.getStackForm(), 'P',
                new UnificationEntry(OrePrefix.pipeSmallFluid, Materials.TinAlloy), 'S',
                new UnificationEntry(OrePrefix.plate, Materials.Steel), 'W',
                new UnificationEntry(OrePrefix.plate, Materials.WroughtIron));
        ModHandler.addShapedRecipe(true, "steam_hammer_bronze", MetaTileEntities.STEAM_HAMMER_BRONZE.getStackForm(),
                "XPX", "XMX", "XAX", 'M', MetaBlocks.STEAM_CASING.getItemVariant(BRONZE_HULL), 'X',
                new UnificationEntry(OrePrefix.pipeSmallFluid, Materials.Bronze), 'P', OreDictNames.craftingPiston, 'A',
                OreDictNames.craftingAnvil);
        ModHandler.addShapedRecipe(true, "steam_hammer_steel", MetaTileEntities.STEAM_HAMMER_STEEL.getStackForm(),
                "WSW", "PMP", "WWW", 'M', MetaTileEntities.STEAM_HAMMER_BRONZE.getStackForm(), 'S',
                new UnificationEntry(OrePrefix.plate, Materials.Steel), 'W',
                new UnificationEntry(OrePrefix.plate, Materials.WroughtIron), 'P',
                new UnificationEntry(OrePrefix.pipeSmallFluid, Materials.TinAlloy));
        ModHandler.addShapedRecipe(true, "steam_compressor_bronze",
                MetaTileEntities.STEAM_COMPRESSOR_BRONZE.getStackForm(), "XXX", "PMP", "XXX", 'M',
                MetaBlocks.STEAM_CASING.getItemVariant(BRONZE_HULL), 'X',
                new UnificationEntry(OrePrefix.pipeSmallFluid, Materials.Bronze), 'P', OreDictNames.craftingPiston);
        ModHandler.addShapedRecipe(true, "steam_compressor_steel",
                MetaTileEntities.STEAM_COMPRESSOR_STEEL.getStackForm(), "PSP", "WMW", "PPP", 'M',
                MetaTileEntities.STEAM_COMPRESSOR_BRONZE.getStackForm(), 'S',
                new UnificationEntry(OrePrefix.plate, Materials.Steel), 'W',
                new UnificationEntry(OrePrefix.plate, Materials.WroughtIron), 'P',
                new UnificationEntry(OrePrefix.pipeSmallFluid, Materials.TinAlloy));
        ModHandler.addShapedRecipe(true, "steam_alloy_smelter_bronze",
                MetaTileEntities.STEAM_ALLOY_SMELTER_BRONZE.getStackForm(), "XXX", "FMF", "XXX", 'M',
                MetaBlocks.STEAM_CASING.getItemVariant(BRONZE_BRICKS_HULL), 'X',
                new UnificationEntry(OrePrefix.pipeSmallFluid, Materials.Bronze), 'F', OreDictNames.craftingFurnace);
        ModHandler.addShapedRecipe(true, "steam_alloy_smelter_steel",
                MetaTileEntities.STEAM_ALLOY_SMELTER_STEEL.getStackForm(), "WSW", "WMW", "WPW", 'M',
                MetaTileEntities.STEAM_ALLOY_SMELTER_BRONZE.getStackForm(), 'S',
                new UnificationEntry(OrePrefix.plate, Materials.Steel), 'W',
                new UnificationEntry(OrePrefix.plate, Materials.WroughtIron), 'P',
                new UnificationEntry(OrePrefix.pipeSmallFluid, Materials.TinAlloy));
        ModHandler.addShapedRecipe(true, "steam_rock_breaker_bronze",
                MetaTileEntities.STEAM_ROCK_BREAKER_BRONZE.getStackForm(), "PXP", "XMX", "DXD", 'M',
                MetaBlocks.STEAM_CASING.getItemVariant(BRONZE_HULL), 'X',
                new UnificationEntry(OrePrefix.pipeSmallFluid, Materials.Bronze), 'P', "craftingPiston", 'D',
                new UnificationEntry(OrePrefix.gem, Materials.Diamond));
        ModHandler.addShapedRecipe(true, "steam_rock_breaker_steel",
                MetaTileEntities.STEAM_ROCK_BREAKER_STEEL.getStackForm(), "WSW", "PMP", "WWW", 'M',
                MetaTileEntities.STEAM_ROCK_BREAKER_BRONZE.getStackForm(), 'W',
                new UnificationEntry(OrePrefix.plate, Materials.WroughtIron), 'S',
                new UnificationEntry(OrePrefix.plate, Materials.Steel), 'P',
                new UnificationEntry(OrePrefix.pipeSmallFluid, Materials.TinAlloy));
        ModHandler.addShapedRecipe(true, "steam_miner", MetaTileEntities.STEAM_MINER.getStackForm(), "DSD", "SMS",
                "GSG", 'M', MetaBlocks.STEAM_CASING.getItemVariant(BRONZE_HULL), 'S',
                new UnificationEntry(OrePrefix.pipeNormalFluid, Materials.Bronze), 'D',
                new UnificationEntry(OrePrefix.gem, Materials.Diamond), 'G',
                new UnificationEntry(OrePrefix.gearSmall, Materials.Bronze));

        // MULTI BLOCK CONTROLLERS
        ModHandler.addShapedRecipe(true, "bronze_primitive_blast_furnace",
                MetaTileEntities.PRIMITIVE_BLAST_FURNACE.getStackForm(), "hRS", "PBR", "dRS", 'R',
                new UnificationEntry(OrePrefix.stick, Materials.Iron), 'S',
                new UnificationEntry(OrePrefix.screw, Materials.Iron), 'P',
                new UnificationEntry(OrePrefix.plate, Materials.Iron), 'B',
                MetaBlocks.METAL_CASING.getItemVariant(PRIMITIVE_BRICKS));
        ModHandler.addShapedRecipe(true, "coke_oven", MetaTileEntities.COKE_OVEN.getStackForm(), "PIP", "IwI", "PIP",
                'P', MetaBlocks.METAL_CASING.getItemVariant(MetalCasingType.COKE_BRICKS), 'I',
                new UnificationEntry(OrePrefix.plate, Materials.Iron));
        ModHandler.addShapedRecipe(true, "coke_oven_hatch", MetaTileEntities.COKE_OVEN_HATCH.getStackForm(), "CD", 'C',
                MetaBlocks.METAL_CASING.getItemVariant(MetalCasingType.COKE_BRICKS), 'D',
                MetaTileEntities.WOODEN_DRUM.getStackForm());
        ModHandler.addShapedRecipe(true, "vacuum_freezer", MetaTileEntities.VACUUM_FREEZER.getStackForm(), "PPP", "CMC",
                "WCW", 'M', MetaBlocks.METAL_CASING.getItemVariant(ALUMINIUM_FROSTPROOF), 'P',
                MetaItems.ELECTRIC_PUMP_HV, 'C', new UnificationEntry(OrePrefix.circuit, Tier.EV), 'W',
                new UnificationEntry(OrePrefix.cableGtSingle, Materials.Gold));
        ModHandler.addShapedRecipe(true, "implosion_compressor", MetaTileEntities.IMPLOSION_COMPRESSOR.getStackForm(),
                "OOO", "CMC", "WCW", 'M', MetaBlocks.METAL_CASING.getItemVariant(STEEL_SOLID), 'O',
                new UnificationEntry(OrePrefix.block, Materials.Obsidian), 'C',
                new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.HV), 'W',
                new UnificationEntry(OrePrefix.cableGtSingle, Materials.Gold));
        ModHandler.addShapedRecipe(true, "distillation_tower", MetaTileEntities.DISTILLATION_TOWER.getStackForm(),
                "CBC", "FMF", "CBC", 'M', MetaTileEntities.HULL[GTValues.HV].getStackForm(), 'B',
                new UnificationEntry(OrePrefix.pipeLargeFluid, Materials.StainlessSteel), 'C',
                new UnificationEntry(OrePrefix.circuit, Tier.EV), 'F', MetaItems.ELECTRIC_PUMP_HV);
        ModHandler.addShapedRecipe(true, "cracking_unit", MetaTileEntities.CRACKER.getStackForm(), "CEC", "PHP", "CEC",
                'C', MetaBlocks.WIRE_COIL.getItemVariant(CUPRONICKEL), 'E', MetaItems.ELECTRIC_PUMP_HV, 'P',
                new UnificationEntry(OrePrefix.circuit, Tier.HV), 'H',
                MetaTileEntities.HULL[GTValues.HV].getStackForm());

        ModHandler.addShapedRecipe(true, "pyrolyse_oven", MetaTileEntities.PYROLYSE_OVEN.getStackForm(), "WEP", "EME",
                "WCP", 'M', MetaTileEntities.HULL[GTValues.MV].getStackForm(), 'W', MetaItems.ELECTRIC_PISTON_MV, 'P',
                new UnificationEntry(OrePrefix.wireGtQuadruple, Materials.Cupronickel), 'E',
                new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.MV), 'C', MetaItems.ELECTRIC_PUMP_MV);
        ModHandler.addShapedRecipe(true, "large_combustion_engine",
                MetaTileEntities.LARGE_COMBUSTION_ENGINE.getStackForm(), "PCP", "EME", "GWG", 'M',
                MetaTileEntities.HULL[GTValues.EV].getStackForm(), 'P', MetaItems.ELECTRIC_PISTON_EV.getStackForm(),
                'E', MetaItems.ELECTRIC_MOTOR_EV.getStackForm(), 'C', new UnificationEntry(OrePrefix.circuit, Tier.IV),
                'W', new UnificationEntry(OrePrefix.cableGtSingle, Materials.Aluminium), 'G',
                new UnificationEntry(OrePrefix.gear, Materials.Titanium));
        ModHandler.addShapedRecipe(true, "extreme_combustion_engine",
                MetaTileEntities.EXTREME_COMBUSTION_ENGINE.getStackForm(), "PCP", "EME", "GWG", 'M',
                MetaTileEntities.HULL[GTValues.IV].getStackForm(), 'P', MetaItems.ELECTRIC_PISTON_IV.getStackForm(),
                'E', MetaItems.ELECTRIC_MOTOR_IV.getStackForm(), 'C', new UnificationEntry(OrePrefix.circuit, Tier.LuV),
                'W', new UnificationEntry(OrePrefix.cableGtSingle, Materials.HSSG), 'G',
                new UnificationEntry(OrePrefix.gear, Materials.TungstenSteel));
        ModHandler.addShapedRecipe(true, "engine_intake_casing",
                MetaBlocks.MULTIBLOCK_CASING.getItemVariant(MultiblockCasingType.ENGINE_INTAKE_CASING,
                        ConfigHolder.recipes.casingsPerCraft),
                "PhP", "RFR", "PwP", 'R', new UnificationEntry(OrePrefix.rotor, Materials.Titanium), 'F',
                MetaBlocks.METAL_CASING.getItemVariant(TITANIUM_STABLE), 'P',
                new UnificationEntry(OrePrefix.pipeNormalFluid, Materials.Titanium));
        ModHandler.addShapedRecipe(true, "extreme_engine_intake_casing",
                MetaBlocks.MULTIBLOCK_CASING.getItemVariant(MultiblockCasingType.EXTREME_ENGINE_INTAKE_CASING,
                        ConfigHolder.recipes.casingsPerCraft),
                "PhP", "RFR", "PwP", 'R', new UnificationEntry(OrePrefix.rotor, Materials.TungstenSteel), 'F',
                MetaBlocks.METAL_CASING.getItemVariant(TUNGSTENSTEEL_ROBUST), 'P',
                new UnificationEntry(OrePrefix.pipeNormalFluid, Materials.TungstenSteel));
        ModHandler.addShapedRecipe(true, "multi_furnace", MetaTileEntities.MULTI_FURNACE.getStackForm(), "PPP", "ASA",
                "CAC", 'P', Blocks.FURNACE, 'A', new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.HV), 'S',
                MetaBlocks.METAL_CASING.getItemVariant(INVAR_HEATPROOF), 'C',
                new UnificationEntry(OrePrefix.cableGtSingle, Materials.Copper));

        ModHandler.addShapedRecipe(true, "large_steam_turbine", MetaTileEntities.LARGE_STEAM_TURBINE.getStackForm(),
                "PSP", "SAS", "CSC", 'S', new UnificationEntry(OrePrefix.gear, Materials.Steel), 'P',
                new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.HV), 'A',
                MetaTileEntities.HULL[GTValues.HV].getStackForm(), 'C',
                new UnificationEntry(OrePrefix.pipeLargeFluid, Materials.Steel));
        ModHandler.addShapedRecipe(true, "large_gas_turbine", MetaTileEntities.LARGE_GAS_TURBINE.getStackForm(), "PSP",
                "SAS", "CSC", 'S', new UnificationEntry(OrePrefix.gear, Materials.StainlessSteel), 'P',
                new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.EV), 'A',
                MetaTileEntities.HULL[GTValues.EV].getStackForm(), 'C',
                new UnificationEntry(OrePrefix.pipeLargeFluid, Materials.StainlessSteel));
        ModHandler.addShapedRecipe(true, "large_plasma_turbine", MetaTileEntities.LARGE_PLASMA_TURBINE.getStackForm(),
                "PSP", "SAS", "CSC", 'S', new UnificationEntry(OrePrefix.gear, Materials.TungstenSteel), 'P',
                new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.LuV), 'A',
                MetaTileEntities.HULL[GTValues.LuV].getStackForm(), 'C',
                new UnificationEntry(OrePrefix.pipeLargeFluid, Materials.TungstenSteel));

        ModHandler.addShapedRecipe(true, "large_bronze_boiler", MetaTileEntities.LARGE_BRONZE_BOILER.getStackForm(),
                "PSP", "SAS", "PSP", 'P', new UnificationEntry(OrePrefix.cableGtSingle, Materials.Tin), 'S',
                new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.LV), 'A',
                MetaBlocks.BOILER_FIREBOX_CASING.getItemVariant(BRONZE_FIREBOX));
        ModHandler.addShapedRecipe(true, "large_steel_boiler", MetaTileEntities.LARGE_STEEL_BOILER.getStackForm(),
                "PSP", "SAS", "PSP", 'P', new UnificationEntry(OrePrefix.cableGtSingle, Materials.Copper), 'S',
                new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.HV), 'A',
                MetaBlocks.BOILER_FIREBOX_CASING.getItemVariant(STEEL_FIREBOX));
        ModHandler.addShapedRecipe(true, "large_titanium_boiler", MetaTileEntities.LARGE_TITANIUM_BOILER.getStackForm(),
                "PSP", "SAS", "PSP", 'P', new UnificationEntry(OrePrefix.cableGtSingle, Materials.Gold), 'S',
                new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.EV), 'A',
                MetaBlocks.BOILER_FIREBOX_CASING.getItemVariant(TITANIUM_FIREBOX));
        ModHandler.addShapedRecipe(true, "large_tungstensteel_boiler",
                MetaTileEntities.LARGE_TUNGSTENSTEEL_BOILER.getStackForm(), "PSP", "SAS", "PSP", 'P',
                new UnificationEntry(OrePrefix.cableGtSingle, Materials.Aluminium), 'S',
                new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.IV), 'A',
                MetaBlocks.BOILER_FIREBOX_CASING.getItemVariant(TUNGSTENSTEEL_FIREBOX));

        ModHandler.addShapedRecipe(true, "assembly_line", MetaTileEntities.ASSEMBLY_LINE.getStackForm(), "CRC", "SAS",
                "CRC", 'A', MetaTileEntities.HULL[GTValues.IV].getStackForm(), 'R', MetaItems.ROBOT_ARM_IV, 'C',
                MetaBlocks.MULTIBLOCK_CASING
                        .getItemVariant(BlockMultiblockCasing.MultiblockCasingType.ASSEMBLY_CONTROL),
                'S', new UnificationEntry(OrePrefix.circuit, Tier.ZPM));

        ModHandler.addShapedRecipe(true, "large_chemical_reactor",
                MetaTileEntities.LARGE_CHEMICAL_REACTOR.getStackForm(), "CRC", "PMP", "CHC", 'C',
                new UnificationEntry(OrePrefix.circuit, Tier.HV), 'R',
                OreDictUnifier.get(OrePrefix.rotor, Materials.StainlessSteel), 'P',
                OreDictUnifier.get(OrePrefix.pipeLargeFluid, Materials.Polytetrafluoroethylene), 'M',
                MetaItems.ELECTRIC_MOTOR_HV.getStackForm(), 'H', MetaTileEntities.HULL[GTValues.HV].getStackForm());

        ModHandler.addShapedRecipe(true, "power_substation", MetaTileEntities.POWER_SUBSTATION.getStackForm(), "LPL",
                "CBC", "LPL", 'L', MetaItems.LAPOTRON_CRYSTAL, 'P', MetaItems.POWER_INTEGRATED_CIRCUIT, 'C',
                new UnificationEntry(OrePrefix.circuit, Tier.LuV), 'B',
                MetaBlocks.METAL_CASING.getItemVariant(PALLADIUM_SUBSTATION));

        if (ConfigHolder.machines.steelSteamMultiblocks) {
            ModHandler.addShapedRecipe(true, "steam_oven", MetaTileEntities.STEAM_OVEN.getStackForm(), "CGC", "FMF",
                    "CGC", 'F', MetaBlocks.BOILER_FIREBOX_CASING.getItemVariant(STEEL_FIREBOX), 'C',
                    MetaBlocks.METAL_CASING.getItemVariant(STEEL_SOLID), 'M',
                    MetaTileEntities.STEAM_FURNACE_STEEL.getStackForm(), 'G',
                    new UnificationEntry(OrePrefix.gear, Materials.Invar));
            ModHandler.addShapedRecipe(true, "steam_grinder", MetaTileEntities.STEAM_GRINDER.getStackForm(), "CGC",
                    "CFC", "CGC", 'G', new UnificationEntry(OrePrefix.gear, Materials.Potin), 'F',
                    MetaTileEntities.STEAM_MACERATOR_STEEL.getStackForm(), 'C',
                    MetaBlocks.METAL_CASING.getItemVariant(STEEL_SOLID));
            ModHandler.addShapedRecipe(true, "steam_hatch", MetaTileEntities.STEAM_HATCH.getStackForm(), "BPB", "BTB",
                    "BPB", 'B', new UnificationEntry(OrePrefix.plate, Materials.Steel), 'P',
                    new UnificationEntry(OrePrefix.pipeNormalFluid, Materials.Steel), 'T',
                    MetaTileEntities.STEEL_DRUM.getStackForm());
            ModHandler.addShapedRecipe(true, "steam_input_bus", MetaTileEntities.STEAM_IMPORT_BUS.getStackForm(), "C",
                    "H", 'H', MetaBlocks.STEAM_CASING.getItemVariant(STEEL_HULL), 'C', OreDictNames.chestWood);
            ModHandler.addShapedRecipe(true, "steam_output_bus", MetaTileEntities.STEAM_EXPORT_BUS.getStackForm(), "H",
                    "C", 'H', MetaBlocks.STEAM_CASING.getItemVariant(STEEL_HULL), 'C', OreDictNames.chestWood);
        } else {
            ModHandler.addShapedRecipe(true, "steam_oven", MetaTileEntities.STEAM_OVEN.getStackForm(), "CGC", "FMF",
                    "CGC", 'F', MetaBlocks.BOILER_FIREBOX_CASING.getItemVariant(BRONZE_FIREBOX), 'C',
                    MetaBlocks.METAL_CASING.getItemVariant(BRONZE_BRICKS), 'M',
                    MetaTileEntities.STEAM_FURNACE_BRONZE.getStackForm(), 'G',
                    new UnificationEntry(OrePrefix.gear, Materials.Invar));
            ModHandler.addShapedRecipe(true, "steam_grinder", MetaTileEntities.STEAM_GRINDER.getStackForm(), "CGC",
                    "CFC", "CGC", 'G', new UnificationEntry(OrePrefix.gear, Materials.Potin), 'F',
                    MetaTileEntities.STEAM_MACERATOR_BRONZE.getStackForm(), 'C',
                    MetaBlocks.METAL_CASING.getItemVariant(BRONZE_BRICKS));
            ModHandler.addShapedRecipe(true, "steam_hatch", MetaTileEntities.STEAM_HATCH.getStackForm(), "BPB", "BTB",
                    "BPB", 'B', new UnificationEntry(OrePrefix.plate, Materials.Bronze), 'P',
                    new UnificationEntry(OrePrefix.pipeNormalFluid, Materials.Bronze), 'T',
                    MetaTileEntities.BRONZE_DRUM.getStackForm());
            ModHandler.addShapedRecipe(true, "steam_input_bus", MetaTileEntities.STEAM_IMPORT_BUS.getStackForm(), "C",
                    "H", 'H', MetaBlocks.STEAM_CASING.getItemVariant(BRONZE_HULL), 'C', OreDictNames.chestWood);
            ModHandler.addShapedRecipe(true, "steam_output_bus", MetaTileEntities.STEAM_EXPORT_BUS.getStackForm(), "H",
                    "C", 'H', MetaBlocks.STEAM_CASING.getItemVariant(BRONZE_HULL), 'C', OreDictNames.chestWood);
        }

        ModHandler.addShapedRecipe(true, "processing_array", MetaTileEntities.PROCESSING_ARRAY.getStackForm(), "COC",
                "RHR", "CPC", 'C', new UnificationEntry(OrePrefix.circuit, Tier.IV), 'O',
                MetaItems.TOOL_DATA_STICK.getStackForm(), 'R', MetaItems.ROBOT_ARM_EV.getStackForm(), 'P',
                OreDictUnifier.get(OrePrefix.pipeLargeFluid, Materials.StainlessSteel), 'H',
                MetaTileEntities.HULL[GTValues.EV].getStackForm());
        ModHandler.addShapedRecipe(true, "advanced_processing_array",
                MetaTileEntities.ADVANCED_PROCESSING_ARRAY.getStackForm(), "RCR", "SPE", "HNH", 'R',
                MetaItems.ROBOT_ARM_LuV, 'C', new UnificationEntry(OrePrefix.circuit, Tier.ZPM), 'S',
                MetaItems.SENSOR_LuV, 'P', MetaTileEntities.PROCESSING_ARRAY.getStackForm(), 'E', MetaItems.EMITTER_LuV,
                'H', new UnificationEntry(OrePrefix.plate, Materials.HSSE), 'N',
                new UnificationEntry(OrePrefix.pipeLargeFluid, Materials.Naquadah));

        // GENERATORS
        ModHandler.addShapedRecipe(true, "diesel_generator_lv", MetaTileEntities.COMBUSTION_GENERATOR[0].getStackForm(),
                "PCP", "EME", "GWG", 'M', MetaTileEntities.HULL[GTValues.LV].getStackForm(), 'P',
                MetaItems.ELECTRIC_PISTON_LV, 'E', MetaItems.ELECTRIC_MOTOR_LV, 'C',
                new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.LV), 'W',
                new UnificationEntry(OrePrefix.cableGtSingle, Materials.Tin), 'G',
                new UnificationEntry(OrePrefix.gear, Materials.Steel));
        ModHandler.addShapedRecipe(true, "diesel_generator_mv", MetaTileEntities.COMBUSTION_GENERATOR[1].getStackForm(),
                "PCP", "EME", "GWG", 'M', MetaTileEntities.HULL[GTValues.MV].getStackForm(), 'P',
                MetaItems.ELECTRIC_PISTON_MV, 'E', MetaItems.ELECTRIC_MOTOR_MV, 'C',
                new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.MV), 'W',
                new UnificationEntry(OrePrefix.cableGtSingle, Materials.Copper), 'G',
                new UnificationEntry(OrePrefix.gear, Materials.Aluminium));
        ModHandler.addShapedRecipe(true, "diesel_generator_hv", MetaTileEntities.COMBUSTION_GENERATOR[2].getStackForm(),
                "PCP", "EME", "GWG", 'M', MetaTileEntities.HULL[GTValues.HV].getStackForm(), 'P',
                MetaItems.ELECTRIC_PISTON_HV, 'E', MetaItems.ELECTRIC_MOTOR_HV, 'C',
                new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.HV), 'W',
                new UnificationEntry(OrePrefix.cableGtSingle, Materials.Gold), 'G',
                new UnificationEntry(OrePrefix.gear, Materials.StainlessSteel));

        ModHandler.addShapedRecipe(true, "gas_turbine_lv", MetaTileEntities.GAS_TURBINE[0].getStackForm(), "CRC", "RMR",
                "EWE", 'M', MetaTileEntities.HULL[GTValues.LV].getStackForm(), 'E', MetaItems.ELECTRIC_MOTOR_LV, 'R',
                new UnificationEntry(OrePrefix.rotor, Materials.Tin), 'C',
                new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.LV), 'W',
                new UnificationEntry(OrePrefix.cableGtSingle, Materials.Tin));
        ModHandler.addShapedRecipe(true, "gas_turbine_mv", MetaTileEntities.GAS_TURBINE[1].getStackForm(), "CRC", "RMR",
                "EWE", 'M', MetaTileEntities.HULL[GTValues.MV].getStackForm(), 'E', MetaItems.ELECTRIC_MOTOR_MV, 'R',
                new UnificationEntry(OrePrefix.rotor, Materials.Bronze), 'C',
                new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.MV), 'W',
                new UnificationEntry(OrePrefix.cableGtSingle, Materials.Copper));
        ModHandler.addShapedRecipe(true, "gas_turbine_hv", MetaTileEntities.GAS_TURBINE[2].getStackForm(), "CRC", "RMR",
                "EWE", 'M', MetaTileEntities.HULL[GTValues.HV].getStackForm(), 'E', MetaItems.ELECTRIC_MOTOR_HV, 'R',
                new UnificationEntry(OrePrefix.rotor, Materials.Steel), 'C',
                new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.HV), 'W',
                new UnificationEntry(OrePrefix.cableGtSingle, Materials.Gold));

        ModHandler.addShapedRecipe(true, "steam_turbine_lv", MetaTileEntities.STEAM_TURBINE[0].getStackForm(), "PCP",
                "RMR", "EWE", 'M', MetaTileEntities.HULL[GTValues.LV].getStackForm(), 'E', MetaItems.ELECTRIC_MOTOR_LV,
                'R', new UnificationEntry(OrePrefix.rotor, Materials.Tin), 'C',
                new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.LV), 'W',
                new UnificationEntry(OrePrefix.cableGtSingle, Materials.Tin), 'P',
                new UnificationEntry(OrePrefix.pipeNormalFluid, Materials.Bronze));
        ModHandler.addShapedRecipe(true, "steam_turbine_mv", MetaTileEntities.STEAM_TURBINE[1].getStackForm(), "PCP",
                "RMR", "EWE", 'M', MetaTileEntities.HULL[GTValues.MV].getStackForm(), 'E', MetaItems.ELECTRIC_MOTOR_MV,
                'R', new UnificationEntry(OrePrefix.rotor, Materials.Bronze), 'C',
                new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.MV), 'W',
                new UnificationEntry(OrePrefix.cableGtSingle, Materials.Copper), 'P',
                new UnificationEntry(OrePrefix.pipeNormalFluid, Materials.Steel));
        ModHandler.addShapedRecipe(true, "steam_turbine_hv", MetaTileEntities.STEAM_TURBINE[2].getStackForm(), "PCP",
                "RMR", "EWE", 'M', MetaTileEntities.HULL[GTValues.HV].getStackForm(), 'E', MetaItems.ELECTRIC_MOTOR_HV,
                'R', new UnificationEntry(OrePrefix.rotor, Materials.Steel), 'C',
                new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.HV), 'W',
                new UnificationEntry(OrePrefix.cableGtSingle, Materials.Gold), 'P',
                new UnificationEntry(OrePrefix.pipeNormalFluid, Materials.StainlessSteel));

        ModHandler.addShapedRecipe(true, "workbench_bronze", MetaTileEntities.WORKBENCH.getStackForm(), "CSC", "PWP",
                "PsP", 'C', OreDictNames.chestWood, 'W', new ItemStack(Blocks.CRAFTING_TABLE), 'S',
                new UnificationEntry(OrePrefix.slab, Materials.Wood), 'P',
                new UnificationEntry(OrePrefix.plank, Materials.Wood));

        ModHandler.addShapedRecipe(true, "magic_energy_absorber", MetaTileEntities.MAGIC_ENERGY_ABSORBER.getStackForm(),
                "PCP", "PMP", "PCP", 'M', MetaTileEntities.HULL[GTValues.EV].getStackForm(), 'P', MetaItems.SENSOR_EV,
                'C', new UnificationEntry(OrePrefix.circuit, Tier.EV));

        ModHandler.addShapedRecipe(true, "primitive_pump", MetaTileEntities.PRIMITIVE_WATER_PUMP.getStackForm(), "RGS",
                "OWd", "CLC", 'R', new UnificationEntry(OrePrefix.ring, Materials.Iron), 'G',
                new UnificationEntry(OrePrefix.pipeNormalFluid, Materials.Wood), 'S',
                new UnificationEntry(OrePrefix.screw, Materials.Iron), 'O',
                new UnificationEntry(OrePrefix.rotor, Materials.Iron), 'W',
                MetaBlocks.PLANKS.getItemVariant(BlockGregPlanks.BlockType.TREATED_PLANK), 'C',
                new ItemStack(Blocks.STONE_SLAB, 1, 3), 'L',
                new UnificationEntry(OrePrefix.pipeLargeFluid, Materials.Wood));
        ModHandler.addShapedRecipe(true, "pump_deck", MetaBlocks.STEAM_CASING.getItemVariant(PUMP_DECK, 2), "SWS",
                "dCh", 'S', new UnificationEntry(OrePrefix.screw, Materials.Iron), 'W',
                MetaBlocks.PLANKS.getItemVariant(BlockGregPlanks.BlockType.TREATED_PLANK), 'C',
                new ItemStack(Blocks.STONE_SLAB, 1, 3));
        ModHandler.addShapedRecipe(true, "pump_hatch", MetaTileEntities.PUMP_OUTPUT_HATCH.getStackForm(), "SRd", "PLP",
                "CRC", 'S', new UnificationEntry(OrePrefix.screw, Materials.Iron), 'R',
                new UnificationEntry(OrePrefix.ring, Materials.Iron), 'P',
                MetaBlocks.PLANKS.getItemVariant(BlockGregPlanks.BlockType.TREATED_PLANK), 'L',
                new UnificationEntry(OrePrefix.pipeLargeFluid, Materials.Wood), 'C',
                new ItemStack(Blocks.STONE_SLAB, 1, 3));

        ModHandler.addShapedRecipe(true, "wood_multiblock_tank", MetaTileEntities.WOODEN_TANK.getStackForm(), " R ",
                "rCs", " R ", 'R', new UnificationEntry(OrePrefix.ring, Materials.Lead), 'C',
                MetaBlocks.STEAM_CASING.getItemVariant(WOOD_WALL));
        ModHandler.addShapedRecipe(true, "steel_multiblock_tank", MetaTileEntities.STEEL_TANK.getStackForm(), " R ",
                "hCw", " R ", 'R', new UnificationEntry(OrePrefix.ring, Materials.Steel), 'C',
                MetaBlocks.METAL_CASING.getItemVariant(STEEL_SOLID));
        ModHandler.addShapedRecipe(true, "wood_tank_valve", MetaTileEntities.WOODEN_TANK_VALVE.getStackForm(), " R ",
                "rCs", " O ", 'O', new UnificationEntry(OrePrefix.rotor, Materials.Lead), 'R',
                new UnificationEntry(OrePrefix.ring, Materials.Lead), 'C',
                MetaBlocks.STEAM_CASING.getItemVariant(WOOD_WALL));
        ModHandler.addShapedRecipe(true, "steel_tank_valve", MetaTileEntities.STEEL_TANK_VALVE.getStackForm(), " R ",
                "hCw", " O ", 'O', new UnificationEntry(OrePrefix.rotor, Materials.Steel), 'R',
                new UnificationEntry(OrePrefix.ring, Materials.Steel), 'C',
                MetaBlocks.METAL_CASING.getItemVariant(STEEL_SOLID));
        ModHandler.addShapedRecipe(true, "wood_wall", MetaBlocks.STEAM_CASING.getItemVariant(WOOD_WALL), "W W", "sPh",
                "W W", 'W', MetaBlocks.PLANKS.getItemVariant(BlockGregPlanks.BlockType.TREATED_PLANK), 'P',
                new UnificationEntry(OrePrefix.plate, Materials.Lead));

        ModHandler.addShapedRecipe(true, "alarm", MetaTileEntities.ALARM.getStackForm(), "WRW", "CHL", "WPW", 'W',
                new UnificationEntry(OrePrefix.cableGtSingle, Materials.Tin), 'P', MetaItems.ELECTRIC_PUMP_LV, 'L',
                new UnificationEntry(OrePrefix.plate, Materials.Steel), 'C',
                new UnificationEntry(OrePrefix.circuit, Tier.LV), 'H',
                MetaTileEntities.HULL[GTValues.LV].getStackForm(), 'R',
                new UnificationEntry(OrePrefix.rotor, Materials.Tin));

        // MACHINES
        registerMachineRecipe(MetaTileEntities.ALLOY_SMELTER, "ECE", "CMC", "WCW", 'M', HULL, 'E', CIRCUIT, 'W', CABLE,
                'C', COIL_HEATING_DOUBLE);
        registerMachineRecipe(MetaTileEntities.ASSEMBLER, "ACA", "VMV", "WCW", 'M', HULL, 'V', CONVEYOR, 'A', ROBOT_ARM,
                'C', CIRCUIT, 'W', CABLE);
        registerMachineRecipe(MetaTileEntities.BENDER, "PWP", "CMC", "EBE", 'M', HULL, 'E', MOTOR, 'P', PISTON, 'C',
                CIRCUIT, 'W', CABLE, 'B', PLATE);
        registerMachineRecipe(MetaTileEntities.CANNER, "WPW", "CMC", "GGG", 'M', HULL, 'P', PUMP, 'C', CIRCUIT, 'W',
                CABLE, 'G', GLASS);
        registerMachineRecipe(MetaTileEntities.COMPRESSOR, " C ", "PMP", "WCW", 'M', HULL, 'P', PISTON, 'C', CIRCUIT,
                'W', CABLE);
        registerMachineRecipe(MetaTileEntities.CUTTER, "WCG", "VMB", "CWE", 'M', HULL, 'E', MOTOR, 'V', CONVEYOR, 'C',
                CIRCUIT, 'W', CABLE, 'G', GLASS, 'B', SAWBLADE);
        registerMachineRecipe(MetaTileEntities.ELECTRIC_FURNACE, "ECE", "CMC", "WCW", 'M', HULL, 'E', CIRCUIT, 'W',
                CABLE, 'C', COIL_HEATING);
        registerMachineRecipe(MetaTileEntities.EXTRACTOR, "GCG", "EMP", "WCW", 'M', HULL, 'E', PISTON, 'P', PUMP, 'C',
                CIRCUIT, 'W', CABLE, 'G', GLASS);
        registerMachineRecipe(MetaTileEntities.EXTRUDER, "CCE", "XMP", "CCE", 'M', HULL, 'X', PISTON, 'E', CIRCUIT, 'P',
                PIPE_NORMAL, 'C', COIL_HEATING_DOUBLE);
        registerMachineRecipe(MetaTileEntities.LATHE, "WCW", "EMD", "CWP", 'M', HULL, 'E', MOTOR, 'P', PISTON, 'C',
                CIRCUIT, 'W', CABLE, 'D', GRINDER);
        registerMachineRecipe(MetaTileEntities.MACERATOR, "PEG", "WWM", "CCW", 'M', HULL, 'E', MOTOR, 'P', PISTON, 'C',
                CIRCUIT, 'W', CABLE, 'G', GRINDER);
        registerMachineRecipe(MetaTileEntities.WIREMILL, "EWE", "CMC", "EWE", 'M', HULL, 'E', MOTOR, 'C', CIRCUIT, 'W',
                CABLE);
        registerMachineRecipe(MetaTileEntities.CENTRIFUGE, "CEC", "WMW", "CEC", 'M', HULL, 'E', MOTOR, 'C', CIRCUIT,
                'W', CABLE);
        registerMachineRecipe(MetaTileEntities.ELECTROLYZER, "IGI", "IMI", "CWC", 'M', HULL, 'C', CIRCUIT, 'W', CABLE,
                'I', WIRE_ELECTRIC, 'G', GLASS);
        registerMachineRecipe(MetaTileEntities.THERMAL_CENTRIFUGE, "CEC", "OMO", "WEW", 'M', HULL, 'E', MOTOR, 'C',
                CIRCUIT, 'W', CABLE, 'O', COIL_HEATING_DOUBLE);
        registerMachineRecipe(MetaTileEntities.ORE_WASHER, "RGR", "CEC", "WMW", 'M', HULL, 'R', ROTOR, 'E', MOTOR, 'C',
                CIRCUIT, 'W', CABLE, 'G', GLASS);
        registerMachineRecipe(MetaTileEntities.PACKER, "BCB", "RMV", "WCW", 'M', HULL, 'R', ROBOT_ARM, 'V', CONVEYOR,
                'C', CIRCUIT, 'W', CABLE, 'B', OreDictNames.chestWood);
        registerMachineRecipe(MetaTileEntities.CHEMICAL_REACTOR, "GRG", "WEW", "CMC", 'M', HULL, 'R', ROTOR, 'E', MOTOR,
                'C', CIRCUIT, 'W', CABLE, 'G', PIPE_REACTOR);
        registerMachineRecipe(MetaTileEntities.BREWERY, "GPG", "WMW", "CBC", 'M', HULL, 'P', PUMP, 'B',
                STICK_DISTILLATION, 'C', CIRCUIT, 'W', CABLE, 'G', GLASS);
        registerMachineRecipe(MetaTileEntities.FERMENTER, "WPW", "GMG", "WCW", 'M', HULL, 'P', PUMP, 'C', CIRCUIT, 'W',
                CABLE, 'G', GLASS);
        registerMachineRecipe(MetaTileEntities.FLUID_SOLIDIFIER, "PGP", "WMW", "CBC", 'M', HULL, 'P', PUMP, 'C',
                CIRCUIT, 'W', CABLE, 'G', GLASS, 'B', OreDictNames.chestWood);
        registerMachineRecipe(MetaTileEntities.DISTILLERY, "GBG", "CMC", "WPW", 'M', HULL, 'P', PUMP, 'B',
                STICK_DISTILLATION, 'C', CIRCUIT, 'W', CABLE, 'G', GLASS);
        registerMachineRecipe(MetaTileEntities.CHEMICAL_BATH, "VGW", "PGV", "CMC", 'M', HULL, 'P', PUMP, 'V', CONVEYOR,
                'C', CIRCUIT, 'W', CABLE, 'G', GLASS);
        registerMachineRecipe(MetaTileEntities.POLARIZER, "ZSZ", "WMW", "ZSZ", 'M', HULL, 'S', STICK_ELECTROMAGNETIC,
                'Z', COIL_ELECTRIC, 'W', CABLE);
        registerMachineRecipe(MetaTileEntities.ELECTROMAGNETIC_SEPARATOR, "VWZ", "WMS", "CWZ", 'M', HULL, 'S',
                STICK_ELECTROMAGNETIC, 'Z', COIL_ELECTRIC, 'V', CONVEYOR, 'C', CIRCUIT, 'W', CABLE);
        registerMachineRecipe(MetaTileEntities.AUTOCLAVE, "IGI", "IMI", "CPC", 'M', HULL, 'P', PUMP, 'C', CIRCUIT, 'I',
                PLATE, 'G', GLASS);
        registerMachineRecipe(MetaTileEntities.MIXER, "GRG", "GEG", "CMC", 'M', HULL, 'E', MOTOR, 'R', ROTOR, 'C',
                CIRCUIT, 'G', GLASS);
        registerMachineRecipe(MetaTileEntities.LASER_ENGRAVER, "PEP", "CMC", "WCW", 'M', HULL, 'E', EMITTER, 'P',
                PISTON, 'C', CIRCUIT, 'W', CABLE);
        registerMachineRecipe(MetaTileEntities.FORMING_PRESS, "WPW", "CMC", "WPW", 'M', HULL, 'P', PISTON, 'C', CIRCUIT,
                'W', CABLE);
        registerMachineRecipe(MetaTileEntities.FORGE_HAMMER, "WPW", "CMC", "WAW", 'M', HULL, 'P', PISTON, 'C', CIRCUIT,
                'W', CABLE, 'A', OreDictNames.craftingAnvil);
        registerMachineRecipe(MetaTileEntities.FLUID_HEATER, "OGO", "PMP", "WCW", 'M', HULL, 'P', PUMP, 'O',
                COIL_HEATING_DOUBLE, 'C', CIRCUIT, 'W', CABLE, 'G', GLASS);
        registerMachineRecipe(MetaTileEntities.SIFTER, "WFW", "PMP", "CFC", 'M', HULL, 'P', PISTON, 'F',
                MetaItems.ITEM_FILTER, 'C', CIRCUIT, 'W', CABLE);
        registerMachineRecipe(MetaTileEntities.ARC_FURNACE, "WGW", "CMC", "PPP", 'M', HULL, 'P', PLATE, 'C', CIRCUIT,
                'W', CABLE_QUAD, 'G', new UnificationEntry(OrePrefix.dust, Materials.Graphite));
        registerMachineRecipe(MetaTileEntities.CIRCUIT_ASSEMBLER, "RIE", "CHC", "WIW", 'R', ROBOT_ARM, 'I',
                BETTER_CIRCUIT, 'E', EMITTER, 'C', CONVEYOR, 'H', HULL, 'W', CABLE);
        // TODO Replication system
        // registerMachineRecipe(MetaTileEntities.MASS_FABRICATOR, "CFC", "QMQ", "CFC", 'M', HULL, 'Q', CABLE_QUAD, 'C',
        // BETTER_CIRCUIT, 'F', FIELD_GENERATOR);
        // registerMachineRecipe(MetaTileEntities.REPLICATOR, "EFE", "CMC", "EQE", 'M', HULL, 'Q', CABLE_QUAD, 'C',
        // BETTER_CIRCUIT, 'F', FIELD_GENERATOR, 'E', EMITTER);
        registerMachineRecipe(MetaTileEntities.SCANNER, "CEC", "WHW", "CSC", 'C', BETTER_CIRCUIT, 'E', EMITTER, 'W',
                CABLE, 'H', HULL, 'S', SENSOR);
        registerMachineRecipe(MetaTileEntities.GAS_COLLECTOR, "WFW", "PHP", "WCW", 'W', Blocks.IRON_BARS, 'F',
                MetaItems.FLUID_FILTER, 'P', PUMP, 'H', HULL, 'C', CIRCUIT);
        registerMachineRecipe(MetaTileEntities.ROCK_BREAKER, "PMW", "CHC", "GGG", 'P', PISTON, 'M', MOTOR, 'W', GRINDER,
                'C', CABLE, 'H', HULL, 'G', GLASS);

        registerMachineRecipe(MetaTileEntities.PUMP, "WGW", "GMG", "TGT", 'M', HULL, 'W', CIRCUIT, 'G', PUMP, 'T',
                PIPE_LARGE);
        registerMachineRecipe(MetaTileEntities.FISHER, "WTW", "PMP", "TGT", 'M', HULL, 'W', CIRCUIT, 'G', PUMP, 'T',
                MOTOR, 'P', PISTON);
        registerMachineRecipe(MetaTileEntities.ITEM_COLLECTOR, "MRM", "RHR", "CWC", 'M', MOTOR, 'R', ROTOR, 'H', HULL,
                'C', CIRCUIT, 'W', CABLE);
        registerMachineRecipe(MetaTileEntities.BLOCK_BREAKER, "MGM", "CHC", "WSW", 'M', MOTOR, 'H', HULL, 'C', CIRCUIT,
                'W', CABLE, 'S', OreDictNames.chestWood, 'G', GRINDER);
        registerMachineRecipe(MetaTileEntities.WORLD_ACCELERATOR, "IGI", "FHF", "IGI", 'H', HULL, 'F', EMITTER, 'G',
                SENSOR, 'I', FIELD_GENERATOR);
        registerMachineRecipe(MetaTileEntities.MINER, "MMM", "WHW", "CSC", 'M', MOTOR, 'W', CABLE, 'H', HULL, 'C',
                CIRCUIT, 'S', SENSOR);

        registerMachineRecipe(MetaTileEntities.MUFFLER_HATCH, "HM", "PR", 'H', HULL, 'M', MOTOR, 'P', PIPE_NORMAL, 'R',
                ROTOR);

        registerMachineRecipe(ArrayUtils.subarray(MetaTileEntities.DIODES, GTValues.ULV, GTValues.HV), "CDC", "DHD",
                "PDP", 'H', HULL, 'D', new UnificationEntry(OrePrefix.component, MarkerMaterials.Component.Diode), 'P',
                PLATE, 'C', CABLE_QUAD);
        registerMachineRecipe(ArrayUtils.subarray(MetaTileEntities.DIODES, GTValues.HV, GTValues.LuV), "CDC", "DHD",
                "PDP", 'H', HULL, 'D', MetaItems.SMD_DIODE, 'P', PLATE, 'C', CABLE_QUAD);
        registerMachineRecipe(
                ArrayUtils.subarray(MetaTileEntities.DIODES, GTValues.LuV, MetaTileEntities.DIODES.length), "CDC",
                "DHD", "PDP", 'H', HULL, 'D', MetaItems.ADVANCED_SMD_DIODE, 'P', PLATE, 'C', CABLE_QUAD);

        registerMachineRecipe(ArrayUtils.subarray(MetaTileEntities.TRANSFORMER, GTValues.ULV, GTValues.MV), " CC",
                "TH ", " CC", 'C', CABLE, 'T', CABLE_TIER_UP, 'H', HULL);
        registerMachineRecipe(ArrayUtils.subarray(MetaTileEntities.TRANSFORMER, GTValues.MV, GTValues.UHV), "WCC",
                "TH ", "WCC", 'W', POWER_COMPONENT, 'C', CABLE, 'T', CABLE_TIER_UP, 'H', HULL);

        registerMachineRecipe(ArrayUtils.subarray(MetaTileEntities.HI_AMP_TRANSFORMER, GTValues.ULV, GTValues.MV),
                " CC", "TH ", " CC", 'C', CABLE_QUAD, 'T', CABLE_QUAD_TIER_UP, 'H', TRANSFORMER);
        registerMachineRecipe(ArrayUtils.subarray(MetaTileEntities.HI_AMP_TRANSFORMER, GTValues.MV, GTValues.UHV),
                "WCC", "TH ", "WCC", 'W', VOLTAGE_COIL, 'C', CABLE_QUAD, 'T', CABLE_QUAD_TIER_UP, 'H', TRANSFORMER);

        registerMachineRecipe(MetaTileEntities.BATTERY_BUFFER[0], "WTW", "WMW", 'M', HULL, 'W', WIRE_QUAD, 'T',
                OreDictNames.chestWood);
        registerMachineRecipe(MetaTileEntities.BATTERY_BUFFER[1], "WTW", "WMW", 'M', HULL, 'W', WIRE_OCT, 'T',
                OreDictNames.chestWood);
        registerMachineRecipe(MetaTileEntities.BATTERY_BUFFER[2], "WTW", "WMW", 'M', HULL, 'W', WIRE_HEX, 'T',
                OreDictNames.chestWood);

        registerMachineRecipe(MetaTileEntities.CHARGER, "WTW", "WMW", "BCB", 'M', HULL, 'W', WIRE_QUAD, 'T',
                OreDictNames.chestWood, 'B', CABLE, 'C', CIRCUIT);

        ModHandler.addShapedRecipe(true, "wooden_crate", MetaTileEntities.WOODEN_CRATE.getStackForm(), "RPR", "PsP",
                "RPR", 'P', "plankWood", 'R', new UnificationEntry(OrePrefix.screw, Materials.Iron));
        ModHandler.addShapedRecipe(true, "bronze_crate", MetaTileEntities.BRONZE_CRATE.getStackForm(), "RPR", "PhP",
                "RPR", 'P', new UnificationEntry(OrePrefix.plate, Materials.Bronze), 'R',
                new UnificationEntry(OrePrefix.stickLong, Materials.Bronze));
        ModHandler.addShapedRecipe(true, "steel_crate", MetaTileEntities.STEEL_CRATE.getStackForm(), "RPR", "PhP",
                "RPR", 'P', new UnificationEntry(OrePrefix.plate, Materials.Steel), 'R',
                new UnificationEntry(OrePrefix.stickLong, Materials.Steel));
        ModHandler.addShapedRecipe(true, "aluminium_crate", MetaTileEntities.ALUMINIUM_CRATE.getStackForm(), "RPR",
                "PhP", "RPR", 'P', new UnificationEntry(OrePrefix.plate, Materials.Aluminium), 'R',
                new UnificationEntry(OrePrefix.stickLong, Materials.Aluminium));
        ModHandler.addShapedRecipe(true, "stainless_steel_crate", MetaTileEntities.STAINLESS_STEEL_CRATE.getStackForm(),
                "RPR", "PhP", "RPR", 'P', new UnificationEntry(OrePrefix.plate, Materials.StainlessSteel), 'R',
                new UnificationEntry(OrePrefix.stickLong, Materials.StainlessSteel));
        ModHandler.addShapedRecipe(true, "titanium_crate", MetaTileEntities.TITANIUM_CRATE.getStackForm(), "RPR", "PhP",
                "RPR", 'P', new UnificationEntry(OrePrefix.plate, Materials.Titanium), 'R',
                new UnificationEntry(OrePrefix.stickLong, Materials.Titanium));
        ModHandler.addShapedRecipe(true, "tungstensteel_crate", MetaTileEntities.TUNGSTENSTEEL_CRATE.getStackForm(),
                "RPR", "PhP", "RPR", 'P', new UnificationEntry(OrePrefix.plate, Materials.TungstenSteel), 'R',
                new UnificationEntry(OrePrefix.stickLong, Materials.TungstenSteel));

        ModHandler.addShapedRecipe(true, "wooden_barrel", MetaTileEntities.WOODEN_DRUM.getStackForm(), "rSs", "PRP",
                "PRP", 'S', MetaItems.STICKY_RESIN.getStackForm(), 'P', "plankWood", 'R',
                new UnificationEntry(OrePrefix.stickLong, Materials.Iron));
        ModHandler.addShapedRecipe(true, "bronze_drum", MetaTileEntities.BRONZE_DRUM.getStackForm(), " h ", "PRP",
                "PRP", 'P', new UnificationEntry(OrePrefix.plate, Materials.Bronze), 'R',
                new UnificationEntry(OrePrefix.stickLong, Materials.Bronze));
        ModHandler.addShapedRecipe(true, "steel_drum", MetaTileEntities.STEEL_DRUM.getStackForm(), " h ", "PRP", "PRP",
                'P', new UnificationEntry(OrePrefix.plate, Materials.Steel), 'R',
                new UnificationEntry(OrePrefix.stickLong, Materials.Steel));
        ModHandler.addShapedRecipe(true, "aluminium_drum", MetaTileEntities.ALUMINIUM_DRUM.getStackForm(), " h ", "PRP",
                "PRP", 'P', new UnificationEntry(OrePrefix.plate, Materials.Aluminium), 'R',
                new UnificationEntry(OrePrefix.stickLong, Materials.Aluminium));
        ModHandler.addShapedRecipe(true, "stainless_steel_drum", MetaTileEntities.STAINLESS_STEEL_DRUM.getStackForm(),
                " h ", "PRP", "PRP", 'P', new UnificationEntry(OrePrefix.plate, Materials.StainlessSteel), 'R',
                new UnificationEntry(OrePrefix.stickLong, Materials.StainlessSteel));
        ModHandler.addShapedRecipe(true, "titanium_drum", MetaTileEntities.TITANIUM_DRUM.getStackForm(), " h ", "PRP",
                "PRP", 'P', new UnificationEntry(OrePrefix.plate, Materials.Titanium), 'R',
                new UnificationEntry(OrePrefix.stickLong, Materials.Titanium));
        ModHandler.addShapedRecipe(true, "tungstensteel_drum", MetaTileEntities.TUNGSTENSTEEL_DRUM.getStackForm(),
                " h ", "PRP", "PRP", 'P', new UnificationEntry(OrePrefix.plate, Materials.TungstenSteel), 'R',
                new UnificationEntry(OrePrefix.stickLong, Materials.TungstenSteel));
        ModHandler.addShapedRecipe(true, "gold_drum", MetaTileEntities.GOLD_DRUM.getStackForm(), " h ", "PRP", "PRP",
                'P', new UnificationEntry(OrePrefix.plate, Materials.Gold), 'R',
                new UnificationEntry(OrePrefix.stickLong, Materials.Gold));

        // Hermetic Casings: the tier's plates around a large pipe; the UHV casing's recipe is named for MAX
        Object[] hermeticPipes = { null, new UnificationEntry(OrePrefix.pipeLargeFluid, Materials.Polyethylene),
                new UnificationEntry(OrePrefix.pipeLargeItem, Materials.PolyvinylChloride),
                new UnificationEntry(OrePrefix.pipeLargeFluid, Materials.Polytetrafluoroethylene),
                new UnificationEntry(OrePrefix.pipeLargeFluid, Materials.StainlessSteel),
                new UnificationEntry(OrePrefix.pipeLargeFluid, Materials.Titanium),
                new UnificationEntry(OrePrefix.pipeLargeFluid, Materials.TungstenSteel),
                new UnificationEntry(OrePrefix.pipeLargeFluid, Materials.NiobiumTitanium),
                new UnificationEntry(OrePrefix.pipeLargeFluid, Materials.Naquadah),
                new UnificationEntry(OrePrefix.pipeLargeFluid, Materials.Duranium) };
        for (int tier = GTValues.LV; tier <= GTValues.UHV; tier++) {
            String name = tier == GTValues.UHV ? "hermetic_casing_max" : "hermetic_casing_" + tierName(tier);
            ModHandler.addShapedRecipe(true, name,
                    MetaBlocks.HERMETIC_CASING.getItemVariant(HermeticCasingsType.values()[tier - GTValues.LV]),
                    "PPP", "PFP", "PPP", 'P', PLATE.getIngredient(tier), 'F', hermeticPipes[tier]);
        }

        // Quantum Storage Controller
        ModHandler.addShapedRecipe(true, "quantum_storage_controller",
                MetaTileEntities.QUANTUM_STORAGE_CONTROLLER.getStackForm(),
                "PCP", "EHS", "CFC",
                'C', new UnificationEntry(OrePrefix.circuit, Tier.MV),
                'P', new UnificationEntry(OrePrefix.plate, Materials.Steel),
                'H', MetaTileEntities.HULL[GTValues.MV].getStackForm(),
                'F', MetaItems.FIELD_GENERATOR_MV.getStackForm(),
                'E', MetaItems.EMITTER_MV.getStackForm(),
                'S', MetaItems.SENSOR_MV.getStackForm());

        // Quantum Storage Proxy
        ModHandler.addShapedRecipe(true, "quantum_storage_proxy",
                MetaTileEntities.QUANTUM_STORAGE_PROXY.getStackForm(),
                "PEP", "ACA", "PHP",
                'P', new UnificationEntry(OrePrefix.plate, Materials.Steel),
                'E', MetaItems.EMITTER_MV.getStackForm(),
                'C', new UnificationEntry(OrePrefix.pipeHugeItem, Materials.SterlingSilver),
                'A', MetaItems.ROBOT_ARM_MV.getStackForm(),
                'H', MetaTileEntities.HULL[GTValues.MV].getStackForm());

        // Quantum Storage Extender
        ModHandler.addShapedRecipe(true, "quantum_storage_extender",
                MetaTileEntities.QUANTUM_STORAGE_EXTENDER.getStackForm(4),
                "PPP", "CHC", "PPP",
                'P', new UnificationEntry(OrePrefix.plate, Materials.Steel),
                'C', new UnificationEntry(OrePrefix.pipeSmallItem, Materials.SterlingSilver),
                'H', MetaTileEntities.HULL[GTValues.MV].getStackForm());

        // Super / Quantum Chests and Tanks: the super ones, LV to IV, from the tier's crate or hermetic casing and
        // plates, with a field generator of two tiers below from HV; the quantum ones, IV to UHV, from the tier's hull
        // or hermetic casing and dense plates, with a field generator of the tier below. The UHV ones take plain
        // plates, and the UHV quantum tank takes the UV pump.
        MetaTileEntity[] crates = { MetaTileEntities.STEEL_CRATE, MetaTileEntities.ALUMINIUM_CRATE,
                MetaTileEntities.STAINLESS_STEEL_CRATE, MetaTileEntities.TITANIUM_CRATE,
                MetaTileEntities.TUNGSTENSTEEL_CRATE };
        for (int tier = GTValues.LV; tier <= GTValues.IV; tier++) {
            ItemStack chest = MetaTileEntities.QUANTUM_CHEST[tier - 1].getStackForm();
            Object circuit = new UnificationEntry(OrePrefix.circuit, circuitTier(tier));
            Object plate = new UnificationEntry(OrePrefix.plate, material(PLATE, tier));
            ItemStack crate = crates[tier - GTValues.LV].getStackForm();
            if (tier < GTValues.HV) {
                ModHandler.addShapedRecipe(true, "super_chest_" + tierName(tier), chest, "CPC", "PFP", "CPC",
                        'C', circuit, 'P', plate, 'F', crate);
            } else {
                ModHandler.addShapedRecipe(true, "super_chest_" + tierName(tier), chest, "CPC", "PFP", "CGC",
                        'C', circuit, 'P', plate, 'F', crate, 'G', fieldGenerator(tier - 2).getStackForm());
            }
        }

        for (int tier = GTValues.IV; tier <= GTValues.UHV; tier++) {
            OrePrefix plate = tier == GTValues.UHV ? OrePrefix.plate : OrePrefix.plateDense;
            ModHandler.addShapedRecipe(true, "quantum_chest_" + tierName(tier),
                    MetaTileEntities.QUANTUM_CHEST[tier].getStackForm(), "CPC", "PHP", "CFC",
                    'C', new UnificationEntry(OrePrefix.circuit, circuitTier(tier)),
                    'P', new UnificationEntry(plate, material(PLATE, tier)),
                    'F', fieldGenerator(tier - 1).getStackForm(), 'H', MetaTileEntities.HULL[tier].getStackForm());
        }

        for (int tier = GTValues.LV; tier <= GTValues.IV; tier++) {
            ItemStack tank = MetaTileEntities.QUANTUM_TANK[tier - 1].getStackForm();
            Object circuit = new UnificationEntry(OrePrefix.circuit, circuitTier(tier));
            Object plate = new UnificationEntry(OrePrefix.plate, material(PLATE, tier));
            ItemStack pump = pump(tier).getStackForm();
            ItemStack casing = MetaBlocks.HERMETIC_CASING.getItemVariant(HermeticCasingsType.values()[tier - 1]);
            if (tier < GTValues.HV) {
                ModHandler.addShapedRecipe(true, "super_tank_" + tierName(tier), tank, "CPC", "PHP", "CFC",
                        'C', circuit, 'P', plate, 'F', pump, 'H', casing);
            } else {
                ModHandler.addShapedRecipe(true, "super_tank_" + tierName(tier), tank, "CGC", "PHP", "CFC",
                        'C', circuit, 'P', plate, 'F', pump, 'H', casing, 'G',
                        fieldGenerator(tier - 2).getStackForm());
            }
        }

        for (int tier = GTValues.IV; tier <= GTValues.UHV; tier++) {
            OrePrefix plate = tier == GTValues.UHV ? OrePrefix.plate : OrePrefix.plateDense;
            ModHandler.addShapedRecipe(true, "quantum_tank_" + tierName(tier),
                    MetaTileEntities.QUANTUM_TANK[tier].getStackForm(), "CGC", "PHP", "CUC",
                    'C', new UnificationEntry(OrePrefix.circuit, circuitTier(tier)),
                    'P', new UnificationEntry(plate, material(PLATE, tier)),
                    'U', pump(Math.min(tier, GTValues.UV)).getStackForm(),
                    'G', fieldGenerator(tier - 1).getStackForm(),
                    'H', MetaBlocks.HERMETIC_CASING.getItemVariant(HermeticCasingsType.values()[tier - 1]));
        }

        // buffers: the tier's hull, pump and conveyor, each with an LV circuit
        for (int tier = GTValues.LV; tier <= GTValues.HV; tier++) {
            ModHandler.addShapedRecipe(true, "buffer_" + tierName(tier),
                    MetaTileEntities.BUFFER[tier - 1].getStackForm(), "HP", "CV",
                    'H', MetaTileEntities.HULL[tier].getStackForm(), 'P', pump(tier).getStackForm(),
                    'V', conveyor(tier).getStackForm(),
                    'C', new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.LV));
        }

        if (ConfigHolder.recipes.harderEBFControllerRecipe) {
            ModHandler.addShapedRecipe(true, "electric_blast_furnace",
                    MetaTileEntities.ELECTRIC_BLAST_FURNACE.getStackForm(),
                    "FFF", "CHC", "WCW",
                    'F', MetaTileEntities.ELECTRIC_FURNACE[1].getStackForm(),
                    'C', new UnificationEntry(OrePrefix.circuit, Tier.LV),
                    'H', MetaBlocks.METAL_CASING.getItemVariant(INVAR_HEATPROOF),
                    'W', new UnificationEntry(OrePrefix.cableGtSingle, Materials.Tin));
        } else {
            ModHandler.addShapedRecipe(true, "electric_blast_furnace",
                    MetaTileEntities.ELECTRIC_BLAST_FURNACE.getStackForm(), "FFF", "CMC", "WCW", 'M',
                    MetaBlocks.METAL_CASING.getItemVariant(INVAR_HEATPROOF), 'F', OreDictNames.craftingFurnace, 'C',
                    new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.LV), 'W',
                    new UnificationEntry(OrePrefix.cableGtSingle, Materials.Tin));
        }

        ModHandler.addShapedRecipe(true, "cleanroom", MetaTileEntities.CLEANROOM.getStackForm(), "FFF", "RHR", "MCM",
                'F', MetaItems.ITEM_FILTER.getStackForm(), 'R',
                new UnificationEntry(OrePrefix.rotor, Materials.StainlessSteel), 'H', HULL.getIngredient(GTValues.HV),
                'M', MetaItems.ELECTRIC_MOTOR_HV.getStackForm(), 'C', new UnificationEntry(OrePrefix.circuit, Tier.HV));

        if (ConfigHolder.compat.energy.enableFEConverters) {
            registerMachineRecipe(MetaTileEntities.ENERGY_CONVERTER[0], " WW", "RMC", " WW", 'C', CIRCUIT, 'M', HULL,
                    'W', CABLE, 'R', new UnificationEntry(OrePrefix.cableGtSingle, Materials.RedAlloy));
            registerMachineRecipe(MetaTileEntities.ENERGY_CONVERTER[1], " WW", "RMC", " WW", 'C', CIRCUIT, 'M', HULL,
                    'W', CABLE_QUAD, 'R', new UnificationEntry(OrePrefix.cableGtQuadruple, Materials.RedAlloy));
            registerMachineRecipe(MetaTileEntities.ENERGY_CONVERTER[2], " WW", "RMC", " WW", 'C', CIRCUIT, 'M', HULL,
                    'W', CABLE_OCT, 'R', new UnificationEntry(OrePrefix.cableGtOctal, Materials.RedAlloy));
            registerMachineRecipe(MetaTileEntities.ENERGY_CONVERTER[3], " WW", "RMC", " WW", 'C', CIRCUIT, 'M', HULL,
                    'W', CABLE_HEX, 'R', new UnificationEntry(OrePrefix.cableGtHex, Materials.RedAlloy));
        }
    }

    // Can only accept a subset of "Item" types:
    // - ItemStack
    // - Item
    // - Block
    // - MetaItem.MetaValueItem
    // - CraftingComponent.Component
    // - UnificationEntry
    // - String (intended as an OreDictionary entry)
    public static <
            T extends MetaTileEntity & ITieredMetaTileEntity> void registerMachineRecipe(boolean withUnificationData,
                                                                                         T[] metaTileEntities,
                                                                                         Object... recipe) {
        for (T metaTileEntity : metaTileEntities) {

            // Needed to skip certain tiers if not enabled.
            // Leaves UHV+ machine recipes to be implemented by addons.
            if (metaTileEntity != null) {
                Object[] prepRecipe = prepareRecipe(metaTileEntity.getTier(), Arrays.copyOf(recipe, recipe.length));
                if (prepRecipe == null) {
                    return;
                }
                ModHandler.addShapedRecipe(withUnificationData, metaTileEntity.getMetaName(),
                        metaTileEntity.getStackForm(), prepRecipe);
            }
        }
    }

    public static <T extends MetaTileEntity & ITieredMetaTileEntity> void registerMachineRecipe(T[] metaTileEntities,
                                                                                                Object... recipe) {
        registerMachineRecipe(true, metaTileEntities, recipe);
    }

    private static Object[] prepareRecipe(int tier, Object... recipe) {
        for (int i = 3; i < recipe.length; i++) {
            if (recipe[i] instanceof Component) {
                Object component = ((Component) recipe[i]).getIngredient(tier);
                if (component == null) {
                    return null;
                }
                recipe[i] = component;
            }
        }
        return recipe;
    }

    /** A metal casing: plates around a frame, made with a hammer and a wrench. */
    private static void metalCasing(String name, MetalCasingType type, Material plate, Material frame) {
        ModHandler.addShapedRecipe(true, name,
                MetaBlocks.METAL_CASING.getItemVariant(type, ConfigHolder.recipes.casingsPerCraft), "PhP", "PFP",
                "PwP", 'P', new UnificationEntry(OrePrefix.plate, plate), 'F',
                new UnificationEntry(OrePrefix.frameGt, frame));
    }

    /** A turbine casing: plates around a frame or a steel turbine casing, made with a hammer and a wrench. */
    private static void turbineCasing(String name, TurbineCasingType type, Material plate, Object frame) {
        ModHandler.addShapedRecipe(true, name,
                MetaBlocks.TURBINE_CASING.getItemVariant(type, ConfigHolder.recipes.casingsPerCraft), "PhP", "PFP",
                "PwP", 'P', new UnificationEntry(OrePrefix.plate, plate), 'F', frame);
    }

    /** A boiler pipe casing: plates and fluid pipes around a frame, all of one material. */
    private static void pipeCasing(String name, BoilerCasingType type, Material material) {
        ModHandler.addShapedRecipe(true, name,
                MetaBlocks.BOILER_CASING.getItemVariant(type, ConfigHolder.recipes.casingsPerCraft), "PIP", "IFI",
                "PIP", 'P', new UnificationEntry(OrePrefix.plate, material), 'F',
                new UnificationEntry(OrePrefix.frameGt, material), 'I',
                new UnificationEntry(OrePrefix.pipeNormalFluid, material));
    }

    /** A firebox casing: plates and rods around a frame, all of one material. */
    private static void fireboxCasing(String name, FireboxCasingType type, Material material) {
        ModHandler.addShapedRecipe(true, name,
                MetaBlocks.BOILER_FIREBOX_CASING.getItemVariant(type, ConfigHolder.recipes.casingsPerCraft), "PSP",
                "SFS", "PSP", 'P', new UnificationEntry(OrePrefix.plate, material), 'F',
                new UnificationEntry(OrePrefix.frameGt, material), 'S',
                new UnificationEntry(OrePrefix.stick, material));
    }

    /** A gearbox casing: plates and gears around a frame, all of one material, made with a hammer and a wrench. */
    private static void gearboxCasing(String name, TurbineCasingType type, Material material) {
        ModHandler.addShapedRecipe(true, name,
                MetaBlocks.TURBINE_CASING.getItemVariant(type, ConfigHolder.recipes.casingsPerCraft), "PhP", "GFG",
                "PwP", 'P', new UnificationEntry(OrePrefix.plate, material), 'F',
                new UnificationEntry(OrePrefix.frameGt, material), 'G',
                new UnificationEntry(OrePrefix.gear, material));
    }

    private static void warningSign(BlockWarningSign.SignType type, String... pattern) {
        warningSign("warning_sign_" + type.getName(), MetaBlocks.WARNING_SIGN.getItemVariant(type), pattern);
    }

    private static void warningSign(BlockWarningSign1.SignType type, String... pattern) {
        warningSign("warning_" + type.getName(), MetaBlocks.WARNING_SIGN_1.getItemVariant(type), pattern);
    }

    /** A warning sign: a solid steel casing with yellow dye at Y and black dye at B in the pattern. */
    private static void warningSign(String name, ItemStack sign, String... pattern) {
        ModHandler.addShapedRecipe(true, name, sign, pattern[0], pattern[1], pattern[2], 'M',
                MetaBlocks.METAL_CASING.getItemVariant(STEEL_SOLID), 'Y', "dyeYellow", 'B', "dyeBlack");
    }
}
