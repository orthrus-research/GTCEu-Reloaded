package gregtech.api.definition;

import gregtech.api.GregTechAPI;
import gregtech.api.block.IHeatingCoilBlockStats;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Native ownership of startup content previously created by the scripting adapter. */
public final class ContentDefinitions {

    // This saved identity is intentionally stable; its historical spelling does not imply a CT dependency.
    public static final OreItemRegistry ORE_ITEMS = new OreItemRegistry(
            new ResourceLocation("gregtech", "meta_oredict_item_ct"));
    private static boolean coilsFrozen;

    private ContentDefinitions() {}

    public static DefinitionResult<HeatingCoilDefinition> coil(IBlockState state, HeatingCoilDefinition definition) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(definition, "definition");
        List<DefinitionDiagnostic> errors = new ArrayList<>();
        if (coilsFrozen) error(errors, definition, "content.frozen", "Coil registration has already frozen");
        if (definition.name().isBlank()) error(errors, definition, "coil.name", "A coil needs a name");
        if (definition.temperature() <= 0 || definition.level() <= 0 || definition.energyDiscount() <= 0 ||
                definition.tier() < 0) {
            error(errors, definition, "coil.statistics",
                    "Temperature, level and energy discount must be positive; tier must be nonnegative");
        }
        if (!errors.isEmpty()) return DefinitionResult.failure(errors);
        GregTechAPI.HEATING_COILS.put(state, definition);
        return DefinitionResult.success(definition);
    }

    public static IHeatingCoilBlockStats removeCoil(IBlockState state) {
        if (coilsFrozen) throw new IllegalStateException("Coil registration has already frozen");
        return GregTechAPI.HEATING_COILS.remove(Objects.requireNonNull(state, "state"));
    }

    public static void freezeCoils() {
        coilsFrozen = true;
    }

    private static void error(List<DefinitionDiagnostic> errors, HeatingCoilDefinition definition, String code,
                              String message) {
        errors.add(new DefinitionDiagnostic(code, DefinitionDiagnostic.Severity.ERROR, definition.origin(), "coil",
                message));
    }
}
