package gregtech.api.recipes;

import gregtech.api.definition.DefinitionDiagnostic;
import gregtech.api.definition.DefinitionResult;
import gregtech.api.recipes.builders.AssemblyLineRecipeBuilder;
import gregtech.api.recipes.builders.BlastRecipeBuilder;
import gregtech.api.recipes.builders.CircuitAssemblerRecipeBuilder;
import gregtech.api.recipes.builders.ComputationRecipeBuilder;
import gregtech.api.recipes.builders.FuelRecipeBuilder;
import gregtech.api.recipes.builders.FusionRecipeBuilder;
import gregtech.api.recipes.builders.ImplosionRecipeBuilder;
import gregtech.api.recipes.builders.PrimitiveRecipeBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.builders.UniversalDistillationRecipeBuilder;
import gregtech.api.recipes.chance.output.ChancedOutputLogic;
import gregtech.api.recipes.definition.CircuitIngredient;
import gregtech.api.recipes.definition.ItemIngredient;
import gregtech.api.recipes.definition.ItemValue;
import gregtech.api.recipes.definition.MaterialIngredient;
import gregtech.api.recipes.definition.OreIngredient;
import gregtech.api.recipes.definition.RecipeDefinition;
import gregtech.api.recipes.ingredients.IntCircuitIngredient;

import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Prepares native recipes without registering them. Known builders retain their native property behavior.
 * Unsupported builders/actions are diagnosed explicitly until their effects are owned by the registration layer.
 */
public final class RecipeDefinitionCompiler {

    static final Set<Class<?>> SUPPORTED_BUILDERS = Set.of(SimpleRecipeBuilder.class, BlastRecipeBuilder.class,
            FusionRecipeBuilder.class, ComputationRecipeBuilder.class, ImplosionRecipeBuilder.class,
            CircuitAssemblerRecipeBuilder.class, UniversalDistillationRecipeBuilder.class,
            PrimitiveRecipeBuilder.class, FuelRecipeBuilder.class, AssemblyLineRecipeBuilder.class);

    private RecipeDefinitionCompiler() {}

    /**
     * Preflights the declaration and its expansions, returning its primary native recipe for inspection.
     * Use {@link RecipeDefinitionRegistrar} to register the complete expansion as an owned group.
     */
    public static DefinitionResult<Recipe> compile(RecipeDefinition definition) {
        var result = prepare(definition);
        if (result.value().isEmpty()) return DefinitionResult.failure(result.diagnostics());
        return DefinitionResult.success(result.value().orElseThrow().getLast().recipe());
    }

    static DefinitionResult<List<OwnedRecipeDefinition>> prepare(RecipeDefinition definition) {
        List<DefinitionDiagnostic> errors = new ArrayList<>();
        RecipeMap<?> map = RecipeMap.getByName(definition.recipeMap());
        if (map == null) {
            error(errors, definition, "recipe.map.unknown", "recipeMap",
                    "Unknown recipe map: " + definition.recipeMap());
        }
        if (definition.duration() <= 0) {
            error(errors, definition, "recipe.duration", "duration", "Duration must be positive");
        }
        if (definition.eut() == 0) {
            error(errors, definition, "recipe.energy", "eut", "EU/t must be nonzero");
        }
        if (definition.inputs().isEmpty() && definition.fluidInputs().isEmpty()) {
            error(errors, definition, "recipe.inputs.empty", "inputs", "At least one input is required");
        }
        for (int i = 0; i < definition.inputs().size(); i++) {
            String path = "inputs[" + i + "]";
            switch (definition.inputs().get(i)) {
                case ItemIngredient input -> {
                    amount(errors, definition, input.amount(), path + ".amount");
                    if (input.alternatives().isEmpty() || input.alternatives().stream().anyMatch(ItemValue::isEmpty)) {
                        error(errors, definition, "ingredient.alternatives", path + ".alternatives",
                                "At least one nonempty alternative is required; empty alternatives are invalid");
                    }
                }
                case OreIngredient input -> {
                    amount(errors, definition, input.amount(), path + ".amount");
                    if (input.oreName().isBlank() || !OreDictionary.doesOreNameExist(input.oreName()) ||
                            OreDictionary.getOres(input.oreName(), false).isEmpty()) {
                        error(errors, definition, "ingredient.ore.unknown", path + ".oreName",
                                "Ore dictionary entry must exist and have members: " + input.oreName());
                    }
                }
                case MaterialIngredient input -> {
                    amount(errors, definition, input.amount(), path + ".amount");
                    if (!OreDictionary.doesOreNameExist(input.oreName()) ||
                            (input.amount() > 0 && input.compile().getInputStacks().length == 0)) {
                        error(errors, definition, "ingredient.material.unresolved", path + ".material",
                                "No registered ore member resolves to material " + input.material() + " / " +
                                        input.oreName());
                    }
                }
                case CircuitIngredient input -> {
                    if (input.configuration() < IntCircuitIngredient.CIRCUIT_MIN ||
                            input.configuration() > IntCircuitIngredient.CIRCUIT_MAX) {
                        error(errors, definition, "ingredient.circuit.range", path + ".configuration",
                                "Circuit configuration must be between 0 and 32");
                    }
                }
            }
        }
        for (int i = 0; i < definition.fluidInputs().size(); i++) {
            amount(errors, definition, definition.fluidInputs().get(i).value().amount(),
                    "fluidInputs[" + i + "].amount");
        }
        for (int i = 0; i < definition.fluidOutputs().size(); i++) {
            amount(errors, definition, definition.fluidOutputs().get(i).amount(), "fluidOutputs[" + i + "].amount");
        }
        for (int i = 0; i < definition.chancedOutputs().size(); i++) {
            var output = definition.chancedOutputs().get(i);
            String path = "chancedOutputs[" + i + "]";
            if (output.output().isEmpty()) {
                error(errors, definition, "recipe.output.empty", path + ".output", "Output must be nonempty");
            }
            chance(errors, definition, output.chance(), path + ".chance");
        }
        for (int i = 0; i < definition.chancedFluidOutputs().size(); i++) {
            var output = definition.chancedFluidOutputs().get(i);
            String path = "chancedFluidOutputs[" + i + "]";
            amount(errors, definition, output.output().amount(), path + ".output.amount");
            chance(errors, definition, output.chance(), path + ".chance");
        }
        for (int i = 0; i < definition.outputs().size(); i++) {
            if (definition.outputs().get(i).isEmpty()) {
                error(errors, definition, "recipe.output.empty", "outputs[" + i + "]", "Output must be nonempty");
            }
        }
        if (map != null) {
            if (definition.inputs().size() > map.getMaxInputs()) {
                error(errors, definition, "recipe.inputs.limit", "inputs", "Map input limit: " + map.getMaxInputs());
            }
            if (definition.outputs().size() + definition.chancedOutputs().size() > map.getMaxOutputs()) {
                error(errors, definition, "recipe.outputs.limit", "outputs",
                        "Map output limit: " + map.getMaxOutputs());
            }
            if (definition.fluidInputs().size() > map.getMaxFluidInputs()) {
                error(errors, definition, "recipe.fluidInputs.limit", "fluidInputs",
                        "Map fluid input limit: " + map.getMaxFluidInputs());
            }
            if (definition.fluidOutputs().size() + definition.chancedFluidOutputs().size() > map.getMaxFluidOutputs()) {
                error(errors, definition, "recipe.fluidOutputs.limit", "fluidOutputs",
                        "Map fluid output limit: " + map.getMaxFluidOutputs());
            }
            if (definition.eut() > 0 && definition.outputs().isEmpty() && definition.fluidOutputs().isEmpty() &&
                    definition.chancedOutputs().isEmpty() && definition.chancedFluidOutputs().isEmpty() &&
                    !map.allowsEmptyOutputs()) {
                error(errors, definition, "recipe.outputs.empty", "outputs", "This map requires an output");
            }
            if (map.getBuildActions().values().stream()
                    .anyMatch(action -> !(action instanceof RecipeExpansion<?>))) {
                error(errors, definition, "recipe.map.effects", "recipeMap",
                        "Transactional preparation requires declarative RecipeExpansion build actions");
            }
        }
        if (!errors.isEmpty()) return DefinitionResult.failure(errors);

        if (!RecipeDefinitionExtensions.supportsBuilder(map.recipeBuilderType())) {
            error(errors, definition, "recipe.builder.effects", "recipeMap",
                    "This preparation path requires a supported builder");
            return DefinitionResult.failure(errors);
        }
        RecipeBuilder<?> builder;
        try {
            builder = map.recipeBuilder();
            if (builder == null || builder.getClass() != map.recipeBuilderType() ||
                    map.isRecipeBuilderSample(builder)) {
                error(errors, definition, "recipe.builder.copy", "recipeMap",
                        "Builder copy must be a new instance of its registered concrete type");
                return DefinitionResult.failure(errors);
            }
        } catch (RuntimeException failure) {
            error(errors, definition, "recipe.builder.copy", "recipeMap",
                    failure.getClass().getSimpleName() + ": " + failure.getMessage());
            return DefinitionResult.failure(errors);
        }
        if (!builder.getInputs().isEmpty() || !builder.getOutputs().isEmpty() ||
                !builder.getFluidInputs().isEmpty() || !builder.getFluidOutputs().isEmpty() ||
                !builder.getChancedOutputs().isEmpty() || !builder.getChancedFluidOutputs().isEmpty() ||
                builder.recipePropertyStorage.size() != 0) {
            error(errors, definition, "recipe.builder.defaults", "recipeMap",
                    "Implicit default ingredients, outputs and properties need an explicit descriptor adapter");
            return DefinitionResult.failure(errors);
        }
        definition.inputs().forEach(input -> builder.inputs(input.compile()));
        definition.outputs().forEach(output -> builder.outputs(output.stack()));
        definition.fluidInputs().forEach(input -> builder.fluidInputs(input.compile()));
        definition.fluidOutputs().forEach(output -> builder.fluidOutputs(output.stack()));
        definition.chancedOutputs()
                .forEach(output -> builder.chancedOutput(output.output().stack(), output.chance(), output.tierBoost()));
        definition.chancedFluidOutputs().forEach(
                output -> builder.chancedFluidOutput(output.output().stack(), output.chance(), output.tierBoost()));
        builder.chancedOutputLogic(definition.itemChanceMode().nativeLogic());
        builder.chancedFluidOutputLogic(definition.fluidChanceMode().nativeLogic());
        try {
            builder.duration(definition.duration());
        } catch (RuntimeException failure) {
            error(errors, definition, "recipe.builder.duration", "duration",
                    failure.getClass().getSimpleName() + ": " + failure.getMessage());
            return DefinitionResult.failure(errors);
        }
        try {
            builder.EUt(definition.eut());
        } catch (RuntimeException failure) {
            error(errors, definition, "recipe.builder.energy", "eut",
                    failure.getClass().getSimpleName() + ": " + failure.getMessage());
            return DefinitionResult.failure(errors);
        }
        RecipeAttributeCompiler.apply(definition, builder, errors);
        if (builder.getInputs().size() > map.getMaxInputs()) {
            error(errors, definition, "recipe.inputs.expanded_limit", "attributes",
                    "Property-generated ingredients exceed the map input limit: " + map.getMaxInputs());
        }
        if (!errors.isEmpty()) return DefinitionResult.failure(errors);
        builder.hidden = definition.hidden();
        return RecipePreparation.prepare(builder.authored(), definition.origin());
    }

    private static void amount(List<DefinitionDiagnostic> errors, RecipeDefinition definition, int amount,
                               String path) {
        if (amount <= 0) error(errors, definition, "ingredient.amount", path, "Amount must be positive");
    }

    private static void chance(List<DefinitionDiagnostic> errors, RecipeDefinition definition, int chance,
                               String path) {
        if (chance <= 0 || chance > ChancedOutputLogic.getMaxChancedValue()) {
            error(errors, definition, "recipe.chance.range", path, "Chance must be between 1 and 10000");
        }
    }

    private static void error(List<DefinitionDiagnostic> errors, RecipeDefinition definition, String code,
                              String field, String message) {
        errors.add(new DefinitionDiagnostic(code, DefinitionDiagnostic.Severity.ERROR, definition.origin(), field,
                message));
    }
}
