package gregtech.api.recipes.modifiers;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.chance.output.impl.ChancedFluidOutput;
import gregtech.api.recipes.chance.output.impl.ChancedItemOutput;
import gregtech.api.recipes.conditions.RecipeConditionSet;
import gregtech.api.recipes.properties.impl.ConditionProperty;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A callback's view of the running private recipe. Every mutable value returned is a fresh copy. */
public final class RecipeModifierView {

    private final Recipe recipe;

    RecipeModifierView(Recipe recipe) {
        this.recipe = recipe;
    }

    public long eut() {
        return recipe.getEUt();
    }

    public int duration() {
        return recipe.getDuration();
    }

    public String category() {
        return recipe.getRecipeCategory().getUniqueID();
    }

    public boolean isHidden() {
        return recipe.isHidden();
    }

    public boolean isCraftTweakerRecipe() {
        return recipe.getIsCTRecipe();
    }

    public boolean isGroovyRecipe() {
        return recipe.isGroovyRecipe();
    }

    public RecipeConditionSet conditions() {
        return recipe.getProperty(ConditionProperty.getInstance(), RecipeConditionSet.EMPTY);
    }

    public List<RecipeIngredientView> itemInputs() {
        List<RecipeIngredientView> result = new ArrayList<>();
        recipe.getInputs().forEach(input -> result.add(new RecipeIngredientView(input)));
        return Collections.unmodifiableList(result);
    }

    public List<RecipeIngredientView> fluidInputs() {
        List<RecipeIngredientView> result = new ArrayList<>();
        recipe.getFluidInputs().forEach(input -> result.add(new RecipeIngredientView(input)));
        return Collections.unmodifiableList(result);
    }

    public List<ItemStack> itemOutputs() {
        List<ItemStack> result = new ArrayList<>();
        recipe.getOutputs().forEach(stack -> result.add(stack.copy()));
        return Collections.unmodifiableList(result);
    }

    public List<FluidStack> fluidOutputs() {
        List<FluidStack> result = new ArrayList<>();
        recipe.getFluidOutputs().forEach(stack -> result.add(stack.copy()));
        return Collections.unmodifiableList(result);
    }

    public List<ChancedItemOutput> chancedItemOutputs() {
        List<ChancedItemOutput> result = new ArrayList<>();
        recipe.getChancedOutputs().getChancedEntries().forEach(entry -> result.add(entry.copy()));
        return Collections.unmodifiableList(result);
    }

    public List<ChancedFluidOutput> chancedFluidOutputs() {
        List<ChancedFluidOutput> result = new ArrayList<>();
        recipe.getChancedFluidOutputs().getChancedEntries().forEach(entry -> result.add(entry.copy()));
        return Collections.unmodifiableList(result);
    }

    public String itemChanceLogic() {
        return recipe.getChancedOutputs().getChancedOutputLogic().getTranslationKey();
    }

    public String fluidChanceLogic() {
        return recipe.getChancedFluidOutputs().getChancedOutputLogic().getTranslationKey();
    }

    /** Encoded property values are descriptive copies, never mutable evaluator values. */
    public Map<String, NBTBase> properties() {
        Map<String, NBTBase> result = new LinkedHashMap<>();
        recipe.propertyStorage().entrySet().forEach(
                entry -> result.put(entry.getKey().getKey(), entry.getKey().serialize(entry.getValue()).copy()));
        return Collections.unmodifiableMap(result);
    }
}
