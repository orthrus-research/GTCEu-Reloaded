package gregtech.api.recipes;

import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.stack.ItemAndMetadata;

import net.minecraft.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable item composition inputs, resolved only during registration. Quantities are per recipe batch. */
public record RecyclingContribution(SourceOrigin origin, ItemAndMetadata output, int outputCount,
                                    Map<ItemAndMetadata, Long> inputs, Map<Material, Long> materials,
                                    RecyclingRoutePolicy policy) {

    public RecyclingContribution(SourceOrigin origin, ItemAndMetadata output, int outputCount,
                                 Map<ItemAndMetadata, Long> inputs) {
        this(origin, output, outputCount, inputs, Map.of(), RecyclingRoutePolicy.CONSISTENT);
    }

    public RecyclingContribution {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(policy, "policy");
        if (outputCount <= 0) throw new IllegalArgumentException("Recycling output count must be positive");
        inputs = Map.copyOf(inputs);
        materials = Map.copyOf(materials);
        if (inputs.values().stream().anyMatch(amount -> amount <= 0) ||
                materials.values().stream().anyMatch(amount -> amount <= 0)) {
            throw new IllegalArgumentException("Recycling input counts must be positive");
        }
    }

    /** Preserves the native first-alternative policy, excluding nonconsumable and non-item inputs. */
    public static RecyclingContribution of(SourceOrigin origin, ItemStack output, List<GTRecipeInput> inputs) {
        if (output.isEmpty()) throw new IllegalArgumentException("Recycling output cannot be empty");
        return new RecyclingContribution(origin, new ItemAndMetadata(output), output.getCount(), itemInputs(inputs));
    }

    public static RecyclingContribution shaped(SourceOrigin origin, ItemStack output, Object... recipe) {
        if (output.isEmpty()) throw new IllegalArgumentException("Recycling output cannot be empty");
        return new RecyclingContribution(origin, new ItemAndMetadata(output), output.getCount(),
                RecyclingHandler.shapedInputs(recipe));
    }

    static Map<ItemAndMetadata, Long> itemInputs(List<GTRecipeInput> inputs) {
        Map<ItemAndMetadata, Long> values = new LinkedHashMap<>();
        for (GTRecipeInput input : inputs) {
            if (input == null || !input.isRecyclingInput()) continue;
            ItemStack[] alternatives = input.getInputStacks();
            if (alternatives == null || alternatives.length == 0 || alternatives[0].isEmpty()) continue;
            values.merge(new ItemAndMetadata(alternatives[0]), (long) input.getAmount(), Math::addExact);
        }
        return Map.copyOf(values);
    }
}
