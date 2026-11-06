package gregtech.api.recipes;

import gregtech.api.definition.DefinitionDiagnostic;
import gregtech.api.definition.DefinitionResult;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.builders.UniversalDistillationRecipeBuilder;
import gregtech.api.recipes.definition.FurnaceRecipeDefinition;
import gregtech.api.util.EnumValidationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Collects native expansion results without writing recipe maps or Groovy backup state. */
final class RecipePreparation {

    private final SourceOrigin origin;
    private final List<OwnedRecipeDefinition> recipes = new ArrayList<>();
    private final List<DefinitionDiagnostic> errors = new ArrayList<>();
    private final Set<RecipeBuilder<?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());

    private RecipePreparation(SourceOrigin origin) {
        this.origin = origin;
    }

    static DefinitionResult<List<OwnedRecipeDefinition>> prepare(RecipeBuilder<?> builder,
                                                                 SourceOrigin origin) {
        RecipePreparation preparation = new RecipePreparation(origin);
        try {
            preparation.expand(builder, 0);
        } catch (RuntimeException failure) {
            preparation.error("recipe.expansion.failed",
                    failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
        return preparation.errors.isEmpty() ? DefinitionResult.success(List.copyOf(preparation.recipes)) :
                DefinitionResult.failure(preparation.errors);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void expand(RecipeBuilder<?> builder, int depth) {
        if (!errors.isEmpty()) return;
        if (depth > 32 || seen.size() >= 4096 || !seen.add(builder)) {
            error("recipe.expansion.cycle", "Expansion repeated a builder or exceeded the preparation limit");
            return;
        }
        RecipeMap<?> map = builder.recipeMap;
        if (map == null ||
                !RecipeDefinitionExtensions.supportsBuilder(builder.getClass())) {
            error("recipe.expansion.effects", "Derived recipes need a supported builder/map");
            return;
        }
        // Reject unknown callbacks before invoking any expansion, including on derived maps.
        var actions = map.getBuildActions().entrySet().stream()
                .filter(entry -> !builder.ignoresAllBuildActions() &&
                        !builder.getIgnoredBuildActions().containsKey(entry.getKey()))
                .toList();
        if (actions.stream().anyMatch(entry -> !(entry.getValue() instanceof RecipeExpansion<?>))) {
            error("recipe.expansion.effects",
                    "Arbitrary build actions are not preparation-safe: " + map.unlocalizedName);
            return;
        }
        if (builder instanceof UniversalDistillationRecipeBuilder distillation) {
            if (builder.getFluidInputs().size() != 1) {
                error("recipe.distillation.inputs", "Distillation requires exactly one fluid input");
                return;
            }
            distillation.expandDistilleryRecipes(derived -> expand(derived, depth + 1));
        }
        List<FurnaceRecipeDefinition> furnace = new ArrayList<>();
        for (var action : actions) {
            ((RecipeExpansion) action.getValue()).expand(builder,
                    derived -> expand((RecipeBuilder<?>) derived, depth + 1));
            if (!errors.isEmpty()) return;
            furnace.addAll(List.copyOf(((RecipeExpansion) action.getValue()).furnaceRecipes(builder)));
        }
        if (!errors.isEmpty()) return;
        if (builder.getInputs().size() > map.getMaxInputs() ||
                builder.getOutputs().size() + builder.getChancedOutputs().size() > map.getMaxOutputs() ||
                builder.getFluidInputs().size() > map.getMaxFluidInputs() ||
                builder.getFluidOutputs().size() + builder.getChancedFluidOutputs().size() > map.getMaxFluidOutputs()) {
            error("recipe.expansion.slots",
                    "Expanded ingredients/outputs exceed the slot limits of " + map.unlocalizedName);
            return;
        }
        var result = map.postValidateRecipe(builder.authored().build());
        if (result.getType() != EnumValidationResult.VALID) {
            error("recipe.native.invalid", "Native recipe validation failed in " + map.unlocalizedName);
            return;
        }
        if (builder.hasItemRecycling() && builder.getOutputs().isEmpty()) {
            error("recipe.recycling.output", "Recycling requires a deterministic item output");
            return;
        }
        recipes.add(
                new OwnedRecipeDefinition(map, result.getResult(), origin, builder.hasItemRecycling(), furnace));
    }

    private void error(String code, String message) {
        errors.add(new DefinitionDiagnostic(code, DefinitionDiagnostic.Severity.ERROR, origin,
                "expansion", message));
    }
}
