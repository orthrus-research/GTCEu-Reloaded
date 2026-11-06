package gregtech.api.recipes;

import gregtech.api.definition.DefinitionDiagnostic;
import gregtech.api.definition.DefinitionResult;
import gregtech.api.recipes.definition.CraftingDefinition;
import gregtech.api.recipes.definition.OreIngredient;
import gregtech.api.recipes.definition.ShapedCraftingDefinition;
import gregtech.api.recipes.definition.ShapelessCraftingDefinition;

import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Compiles immutable declarations into detached native recipes. No registry is changed. */
public final class CraftingDefinitionCompiler {

    private CraftingDefinitionCompiler() {}

    /** Inspects our compiled ingredients without exposing mutable input state; foreign ingredients return empty. */
    public static Optional<CraftingIngredientIdentity> ingredientIdentity(Ingredient ingredient) {
        return ingredient instanceof NativeCraftingIngredient nativeIngredient ?
                Optional.of(nativeIngredient.identity()) : Optional.empty();
    }

    public static DefinitionResult<IRecipe> compile(CraftingDefinition definition) {
        List<DefinitionDiagnostic> errors = new ArrayList<>();
        if (definition.output().isEmpty())
            errors.add(error(definition, "crafting.output.empty", "output", "Crafting output must not be empty"));
        if (definition.inputs().isEmpty() || definition.inputs().stream().allMatch(input -> input == null))
            errors.add(error(definition, "crafting.inputs.empty", "inputs", "At least one occupied slot is required"));
        if (definition instanceof ShapedCraftingDefinition shaped &&
                (shaped.width() <= 0 || shaped.height() <= 0 ||
                        (long) shaped.width() * shaped.height() != shaped.inputs().size()))
            errors.add(
                    error(definition, "crafting.shape", "inputs", "Dimensions must describe the complete input grid"));
        List<NativeCraftingIngredient> inputs = new ArrayList<>();
        for (int i = 0; i < definition.inputs().size(); i++) {
            var input = definition.inputs().get(i);
            String field = "inputs[" + i + "]";
            if (input == null) {
                if (definition instanceof ShapelessCraftingDefinition)
                    errors.add(error(definition, "crafting.input.empty", field, "Shapeless inputs cannot be empty"));
                inputs.add(null);
                continue;
            }
            try {
                if (input instanceof OreIngredient ore && !OreDictionary.doesOreNameExist(ore.oreName()))
                    throw new IllegalArgumentException("Unknown ore ingredient: " + ore.oreName());
                var compiled = input.compile();
                if (compiled.getAmount() != 1)
                    throw new IllegalArgumentException(
                            "Crafting consumes one item per occupied slot; use separate slots for quantities");
                var matches = compiled.getInputStacks();
                if (matches == null || matches.length == 0 || Arrays.stream(matches).anyMatch(stack -> stack.isEmpty()))
                    throw new IllegalArgumentException("A declared ingredient must resolve to nonempty items");
                inputs.add(new NativeCraftingIngredient(compiled));
            } catch (IllegalArgumentException failure) {
                errors.add(error(definition, "crafting.input.invalid", field, failure.getMessage()));
            }
        }
        if (!errors.isEmpty()) return DefinitionResult.failure(errors);
        IRecipe recipe = switch (definition) {
            case ShapedCraftingDefinition shaped -> new NativeShapedCraftingRecipe(shaped, inputs);
            case ShapelessCraftingDefinition shapeless -> new NativeShapelessCraftingRecipe(shapeless, inputs);
        };
        return DefinitionResult.success(recipe);
    }

    private static DefinitionDiagnostic error(CraftingDefinition definition, String code, String field,
                                              String message) {
        return new DefinitionDiagnostic(code, DefinitionDiagnostic.Severity.ERROR, definition.origin(), field, message);
    }
}
