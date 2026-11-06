package gregtech.api.recipes.definition;

import gregtech.api.definition.SourceOrigin;

import java.util.List;
import java.util.Objects;

/** Immutable recipe declaration. Structural validity is reported by the compiler with source diagnostics. */
public record RecipeDefinition(SourceOrigin origin, String recipeMap, List<ItemInput> inputs,
                               List<ItemValue> outputs, List<FluidIngredient> fluidInputs,
                               List<FluidValue> fluidOutputs,
                               List<ChancedItemDefinition> chancedOutputs,
                               List<ChancedFluidDefinition> chancedFluidOutputs,
                               ChanceMode itemChanceMode, ChanceMode fluidChanceMode,
                               List<RecipeAttribute> attributes, int duration, long eut, boolean hidden) {

    public RecipeDefinition {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(recipeMap, "recipeMap");
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        fluidInputs = List.copyOf(fluidInputs);
        fluidOutputs = List.copyOf(fluidOutputs);
        chancedOutputs = List.copyOf(chancedOutputs);
        chancedFluidOutputs = List.copyOf(chancedFluidOutputs);
        attributes = List.copyOf(attributes);
        Objects.requireNonNull(itemChanceMode, "itemChanceMode");
        Objects.requireNonNull(fluidChanceMode, "fluidChanceMode");
    }

    public RecipeDefinition(SourceOrigin origin, String recipeMap, List<ItemInput> inputs,
                            List<ItemValue> outputs, List<FluidIngredient> fluidInputs, List<FluidValue> fluidOutputs,
                            List<ChancedItemDefinition> chancedOutputs,
                            List<ChancedFluidDefinition> chancedFluidOutputs,
                            ChanceMode itemChanceMode, ChanceMode fluidChanceMode,
                            int duration, long eut, boolean hidden) {
        this(origin, recipeMap, inputs, outputs, fluidInputs, fluidOutputs, chancedOutputs, chancedFluidOutputs,
                itemChanceMode, fluidChanceMode, List.of(), duration, eut, hidden);
    }

    public RecipeDefinition withAttributes(RecipeAttribute... attributes) {
        return new RecipeDefinition(origin, recipeMap, inputs, outputs, fluidInputs, fluidOutputs, chancedOutputs,
                chancedFluidOutputs, itemChanceMode, fluidChanceMode, List.of(attributes), duration, eut, hidden);
    }

    /** Convenience for item-only declarations. */
    public RecipeDefinition(SourceOrigin origin, String recipeMap, List<? extends ItemInput> inputs,
                            List<ItemValue> outputs, int duration, long eut, boolean hidden) {
        this(origin, recipeMap, List.copyOf(inputs), outputs, List.of(), List.of(), List.of(), List.of(),
                ChanceMode.OR, ChanceMode.OR, duration, eut, hidden);
    }
}
