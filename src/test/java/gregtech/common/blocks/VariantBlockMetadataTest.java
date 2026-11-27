package gregtech.common.blocks;

import gregtech.Bootstrap;
import gregtech.common.blocks.wood.BlockGregPlanks;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A placed variant block is saved as its variant's position in the enum, so the positions below are what worlds
 * hold. Add a new variant at the end of its enum and of its list here. Moving or removing one turns saved blocks
 * into other blocks.
 */
class VariantBlockMetadataTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    static Stream<Arguments> savedPositions() {
        return Stream.of(
                saved(BlockAsphalt.BlockType.class, "ASPHALT"),
                saved(BlockBatteryPart.BatteryPartType.class, "EMPTY_TIER_I", "LAPOTRONIC_EV", "LAPOTRONIC_IV",
                        "EMPTY_TIER_II", "LAPOTRONIC_LuV", "LAPOTRONIC_ZPM", "EMPTY_TIER_III", "LAPOTRONIC_UV",
                        "ULTIMATE_UHV"),
                saved(BlockBoilerCasing.BoilerCasingType.class, "BRONZE_PIPE", "STEEL_PIPE", "TITANIUM_PIPE",
                        "TUNGSTENSTEEL_PIPE", "POLYTETRAFLUOROETHYLENE_PIPE"),
                saved(BlockCleanroomCasing.CasingType.class, "PLASCRETE", "FILTER_CASING", "FILTER_CASING_STERILE"),
                saved(BlockComputerCasing.CasingType.class, "COMPUTER_CASING", "COMPUTER_HEAT_VENT",
                        "HIGH_POWER_CASING", "ADVANCED_COMPUTER_CASING"),
                saved(BlockFireboxCasing.FireboxCasingType.class, "BRONZE_FIREBOX", "STEEL_FIREBOX",
                        "TITANIUM_FIREBOX", "TUNGSTENSTEEL_FIREBOX"),
                saved(BlockFusionCasing.CasingType.class, "SUPERCONDUCTOR_COIL", "FUSION_COIL", "FUSION_CASING",
                        "FUSION_CASING_MK2", "FUSION_CASING_MK3"),
                saved(BlockGlassCasing.CasingType.class, "TEMPERED_GLASS", "FUSION_GLASS", "LAMINATED_GLASS",
                        "CLEANROOM_GLASS"),
                saved(BlockHermeticCasing.HermeticCasingsType.class, "HERMETIC_LV", "HERMETIC_MV", "HERMETIC_HV",
                        "HERMETIC_EV", "HERMETIC_IV", "HERMETIC_LUV", "HERMETIC_ZPM", "HERMETIC_UV", "HERMETIC_UHV"),
                saved(BlockMachineCasing.MachineCasingType.class, "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM",
                        "UV", "UHV", "UEV", "UIV", "UXV", "OpV", "MAX"),
                saved(BlockMetalCasing.MetalCasingType.class, "BRONZE_BRICKS", "PRIMITIVE_BRICKS", "INVAR_HEATPROOF",
                        "ALUMINIUM_FROSTPROOF", "STEEL_SOLID", "STAINLESS_CLEAN", "TITANIUM_STABLE",
                        "TUNGSTENSTEEL_ROBUST", "COKE_BRICKS", "PTFE_INERT_CASING", "HSSE_STURDY",
                        "PALLADIUM_SUBSTATION"),
                saved(BlockMultiblockCasing.MultiblockCasingType.class, "ENGINE_INTAKE_CASING",
                        "EXTREME_ENGINE_INTAKE_CASING", "GRATE_CASING", "ASSEMBLY_CONTROL", "ASSEMBLY_LINE_CASING"),
                saved(BlockSteamCasing.SteamCasingType.class, "BRONZE_HULL", "BRONZE_BRICKS_HULL", "STEEL_HULL",
                        "STEEL_BRICKS_HULL", "PUMP_DECK", "WOOD_WALL"),
                saved(BlockTurbineCasing.TurbineCasingType.class, "BRONZE_GEARBOX", "STEEL_GEARBOX",
                        "STAINLESS_STEEL_GEARBOX", "TITANIUM_GEARBOX", "TUNGSTENSTEEL_GEARBOX",
                        "STEEL_TURBINE_CASING", "TITANIUM_TURBINE_CASING", "STAINLESS_TURBINE_CASING",
                        "TUNGSTENSTEEL_TURBINE_CASING"),
                saved(BlockWarningSign.SignType.class, "YELLOW_STRIPES", "SMALL_YELLOW_STRIPES",
                        "RADIOACTIVE_HAZARD", "BIO_HAZARD", "EXPLOSION_HAZARD", "FIRE_HAZARD", "ACID_HAZARD",
                        "MAGIC_HAZARD", "FROST_HAZARD", "NOISE_HAZARD", "GENERIC_HAZARD", "HIGH_VOLTAGE_HAZARD",
                        "MAGNETIC_HAZARD", "ANTIMATTER_HAZARD", "HIGH_TEMPERATURE_HAZARD", "VOID_HAZARD"),
                saved(BlockWarningSign1.SignType.class, "MOB_SPAWNER_HAZARD", "SPATIAL_STORAGE_HAZARD",
                        "LASER_HAZARD", "MOB_HAZARD", "BOSS_HAZARD", "GREGIFICATION_HAZARD", "CAUSALITY_HAZARD",
                        "AUTOMATED_DEFENSES_HAZARD", "HIGH_PRESSURE_HAZARD"),
                saved(StoneVariantBlock.StoneType.class, "BLACK_GRANITE", "RED_GRANITE", "MARBLE", "BASALT",
                        "CONCRETE_LIGHT", "CONCRETE_DARK"),
                saved(BlockGregPlanks.BlockType.class, "RUBBER_PLANK", "TREATED_PLANK"));
    }

    @ParameterizedTest
    @MethodSource("savedPositions")
    void variantsKeepTheirSavedPositions(Class<? extends Enum<?>> variants, List<String> saved) {
        List<String> declared = Arrays.stream(variants.getEnumConstants()).map(Enum::name).toList();
        assertEquals(saved, declared,
                variants.getName() + ": a saved block's metadata is its variant's position");
    }

    private static Arguments saved(Class<? extends Enum<?>> variants, String... names) {
        return Arguments.of(variants, List.of(names));
    }
}
