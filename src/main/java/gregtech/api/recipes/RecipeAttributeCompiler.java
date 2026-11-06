package gregtech.api.recipes;

import gregtech.api.definition.DefinitionDiagnostic;
import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.recipes.builders.BlastRecipeBuilder;
import gregtech.api.recipes.builders.CircuitAssemblerRecipeBuilder;
import gregtech.api.recipes.builders.ComputationRecipeBuilder;
import gregtech.api.recipes.builders.FusionRecipeBuilder;
import gregtech.api.recipes.builders.ImplosionRecipeBuilder;
import gregtech.api.recipes.builders.UniversalDistillationRecipeBuilder;
import gregtech.api.recipes.definition.RecipeAttribute;
import gregtech.api.recipes.definition.RecipeDefinition;
import gregtech.api.recipes.properties.impl.CleanroomProperty;
import gregtech.api.recipes.properties.impl.ComputationProperty;
import gregtech.api.recipes.properties.impl.ConditionProperty;
import gregtech.api.recipes.properties.impl.DimensionProperty;
import gregtech.api.recipes.properties.impl.FusionEUToStartProperty;
import gregtech.api.recipes.properties.impl.ImplosionExplosiveProperty;
import gregtech.api.recipes.properties.impl.ResearchProperty;
import gregtech.api.recipes.properties.impl.TemperatureProperty;
import gregtech.api.recipes.properties.impl.TotalComputationProperty;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Applies only to an isolated builder, never a published recipe or a registry. */
final class RecipeAttributeCompiler {

    private RecipeAttributeCompiler() {}

    static void apply(RecipeDefinition definition, RecipeBuilder<?> builder, List<DefinitionDiagnostic> errors) {
        Set<Object> seen = new HashSet<>();
        for (int i = 0; i < definition.attributes().size(); i++) {
            RecipeAttribute attribute = definition.attributes().get(i);
            String path = "attributes[" + i + "]";
            Object key = duplicateKey(attribute);
            if (!seen.add(key)) {
                error(errors, definition, "recipe.attribute.duplicate", path,
                        "Each property kind may be declared once");
                continue;
            }
            switch (attribute) {
                case RecipeAttribute.Custom value -> applyCustom(definition, builder, errors, path, value);
                case RecipeAttribute.Research value -> ResearchDefinitionCompiler.apply(definition, builder,
                        value.requirements(), path, errors);
                case RecipeAttribute.Recycling ignored -> builder.withRecycling();
                case RecipeAttribute.SolderMultiplier value -> {
                    if (builder instanceof CircuitAssemblerRecipeBuilder circuit) {
                        if (value.multiplier() < 1 || value.multiplier() > 64000 / 144) {
                            error(errors, definition, "recipe.attribute.range", path + ".multiplier",
                                    "Solder multiplier must be between 1 and 444");
                        } else circuit.solderMultiplier(value.multiplier());
                    } else
                        unsupported(errors, definition, path, "Solder multiplier requires a circuit assembler builder");
                }
                case RecipeAttribute.DistilleryRecipes value -> {
                    if (builder instanceof UniversalDistillationRecipeBuilder distillation) {
                        if (!value.enabled()) distillation.disableDistilleryRecipes();
                    } else
                        unsupported(errors, definition, path, "Distillery generation requires a distillation builder");
                }
                case RecipeAttribute.Temperature value -> {
                    if (builder instanceof BlastRecipeBuilder blast) {
                        if (positive(errors, definition, value.kelvin(), path + ".kelvin")) {
                            blast.blastFurnaceTemp(value.kelvin());
                        }
                    } else unsupported(errors, definition, path, "Temperature requires a blast recipe builder");
                }
                case RecipeAttribute.FusionStart value -> {
                    if (builder instanceof FusionRecipeBuilder fusion) {
                        if (positive(errors, definition, value.energy(), path + ".energy")) {
                            fusion.EUToStart(value.energy());
                        }
                    } else
                        unsupported(errors, definition, path, "Fusion start energy requires a fusion recipe builder");
                }
                case RecipeAttribute.ComputationRate value -> {
                    if (builder instanceof ComputationRecipeBuilder computation) {
                        if (value.perTick() < 0) {
                            error(errors, definition, "recipe.attribute.range", path + ".perTick",
                                    "Computation rate must be nonnegative");
                        } else computation.CWUt(value.perTick());
                    } else
                        unsupported(errors, definition, path, "Computation rate requires a computation recipe builder");
                }
                case RecipeAttribute.TotalComputation value -> {
                    if (builder instanceof ComputationRecipeBuilder computation) {
                        if (positive(errors, definition, value.amount(), path + ".amount")) {
                            if (definition.duration() != value.amount()) {
                                error(errors, definition, "recipe.computation.duration", path + ".amount",
                                        "Duration must equal total computation work for this native recipe type");
                            } else computation.totalCWU(value.amount());
                        }
                    } else unsupported(errors, definition, path,
                            "Total computation requires a computation recipe builder");
                }
                case RecipeAttribute.Explosives value -> {
                    if (builder instanceof ImplosionRecipeBuilder implosion) {
                        var stack = value.stack().stack();
                        if (stack.isEmpty() || stack.getCount() > 64) {
                            error(errors, definition, "recipe.explosives.range", path + ".stack",
                                    "Explosives require a nonempty stack of 1 to 64 items");
                        } else implosion.explosives(stack);
                    } else unsupported(errors, definition, path, "Explosives require an implosion recipe builder");
                }
                case RecipeAttribute.Cleanroom value -> {
                    var cleanroom = CleanroomType.getByName(value.name());
                    if (cleanroom == null) {
                        error(errors, definition, "recipe.cleanroom.unknown", path + ".name",
                                "Unknown cleanroom type: " + value.name());
                    } else builder.cleanroom(cleanroom);
                }
                case RecipeAttribute.Dimensions value -> {
                    if (value.allowed().stream().anyMatch(value.blocked()::contains)) {
                        error(errors, definition, "recipe.dimensions.conflict", path,
                                "A dimension cannot be both allowed and blocked");
                    } else {
                        value.allowed().forEach(id -> builder.dimension(id, false));
                        value.blocked().forEach(id -> builder.dimension(id, true));
                    }
                }
                case RecipeAttribute.Conditions value -> builder.applyProperty(ConditionProperty.getInstance(),
                        value.conditions());
            }
        }
    }

    private static Object duplicateKey(RecipeAttribute attribute) {
        return switch (attribute) {
            case RecipeAttribute.Custom custom -> {
                var type = RecipeDefinitionExtensions.attribute(custom.type());
                yield type == null ? custom.type() : type.propertyKey();
            }
            case RecipeAttribute.Temperature ignored -> TemperatureProperty.KEY;
            case RecipeAttribute.FusionStart ignored -> FusionEUToStartProperty.KEY;
            case RecipeAttribute.ComputationRate ignored -> ComputationProperty.KEY;
            case RecipeAttribute.TotalComputation ignored -> TotalComputationProperty.KEY;
            case RecipeAttribute.Explosives ignored -> ImplosionExplosiveProperty.KEY;
            case RecipeAttribute.Cleanroom ignored -> CleanroomProperty.KEY;
            case RecipeAttribute.Dimensions ignored -> DimensionProperty.KEY;
            case RecipeAttribute.Conditions ignored -> ConditionProperty.KEY;
            case RecipeAttribute.Research ignored -> ResearchProperty.KEY;
            default -> attribute.getClass();
        };
    }

    private static void applyCustom(RecipeDefinition definition, RecipeBuilder<?> builder,
                                    List<DefinitionDiagnostic> errors, String path, RecipeAttribute.Custom value) {
        var type = RecipeDefinitionExtensions.attribute(value.type());
        if (type == null) {
            error(errors, definition, "recipe.attribute.unknown", path + ".type",
                    "Unknown addon attribute: " + value.type());
            return;
        }
        if (!type.supports(builder)) {
            unsupported(errors, definition, path, "Addon attribute does not support this builder: " + value.type());
            return;
        }
        if (builder.recipePropertyStorage.entrySet().stream()
                .anyMatch(entry -> entry.getKey().getKey().equals(type.propertyKey()))) {
            error(errors, definition, "recipe.attribute.duplicate", path,
                    "Native property already declared: " + type.propertyKey());
            return;
        }
        try {
            type.apply(builder, value);
            if (builder.recipePropertyStorage.entrySet().stream()
                    .noneMatch(entry -> entry.getKey().getKey().equals(type.propertyKey()))) {
                error(errors, definition, "recipe.attribute.adapter", path,
                        "Addon adapter did not supply its declared native property: " + type.propertyKey());
            }
        } catch (RuntimeException failure) {
            error(errors, definition, "recipe.attribute.invalid", path + ".payload",
                    failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private static boolean positive(List<DefinitionDiagnostic> errors, RecipeDefinition definition, long value,
                                    String path) {
        if (value > 0) return true;
        error(errors, definition, "recipe.attribute.range", path, "Value must be positive");
        return false;
    }

    private static void unsupported(List<DefinitionDiagnostic> errors, RecipeDefinition definition, String path,
                                    String message) {
        error(errors, definition, "recipe.attribute.builder", path, message);
    }

    private static void error(List<DefinitionDiagnostic> errors, RecipeDefinition definition, String code, String path,
                              String message) {
        errors.add(new DefinitionDiagnostic(code, DefinitionDiagnostic.Severity.ERROR, definition.origin(), path,
                message));
    }
}
