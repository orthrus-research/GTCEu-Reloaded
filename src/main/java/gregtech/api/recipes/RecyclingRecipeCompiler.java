package gregtech.api.recipes;

import gregtech.api.GTValues;
import gregtech.api.definition.DefinitionDiagnostic;
import gregtech.api.definition.DefinitionResult;
import gregtech.api.recipes.definition.RecyclingRecipeDefinition;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.loaders.recipe.RecyclingRecipes;

import java.util.ArrayList;
import java.util.List;

/** Uses native recovery generation and the same preparation-safe expansion boundary as ordinary definitions. */
final class RecyclingRecipeCompiler {

    private RecyclingRecipeCompiler() {}

    static DefinitionResult<List<OwnedRecipeDefinition>> prepare(RecyclingRecipeDefinition definition) {
        List<DefinitionDiagnostic> errors = new ArrayList<>();
        if (definition.input().isEmpty()) error(errors, definition, "input", "Recycling input cannot be empty");
        for (int i = 0; i < definition.materials().size(); i++) {
            var value = definition.materials().get(i);
            if (value.amount() <= 0) {
                error(errors, definition, "materials[" + i + "]", "Material amount must be positive");
            }
        }
        if (!errors.isEmpty()) return DefinitionResult.failure(errors);
        List<OwnedRecipeDefinition> prepared = new ArrayList<>();
        try {
            // Native extraction stores fluid amounts and durations as ints. Reject narrowing/overflow up front.
            long mass = 0;
            for (var value : definition.materials()) {
                Math.toIntExact(Math.multiplyExact(value.amount(), GTValues.L) / GTValues.M);
                mass = Math.addExact(mass, Math.multiplyExact(value.amount(), value.material().getMass()));
            }
            Math.toIntExact(mass / GTValues.M);
            var materials = definition.materials().stream()
                    .map(value -> new MaterialStack(value.material(), value.amount())).toList();
            RecyclingRecipes.generateRecyclingRecipes(definition.input().stack(), materials,
                    definition.ignoreArcSmelting(), definition.prefix(), builder -> {
                        var result = RecipePreparation.prepare(builder, definition.origin());
                        errors.addAll(result.diagnostics());
                        result.value().ifPresent(prepared::addAll);
                    });
        } catch (RuntimeException failure) {
            error(errors, definition, "generation", failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
        return errors.isEmpty() ? DefinitionResult.success(List.copyOf(prepared)) : DefinitionResult.failure(errors);
    }

    private static void error(List<DefinitionDiagnostic> errors, RecyclingRecipeDefinition definition,
                              String field, String message) {
        errors.add(new DefinitionDiagnostic("recipe.recycling.generation", DefinitionDiagnostic.Severity.ERROR,
                definition.origin(), field, message));
    }
}
