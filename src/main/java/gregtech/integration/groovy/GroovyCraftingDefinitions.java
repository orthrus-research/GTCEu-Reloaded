package gregtech.integration.groovy;

import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.definition.*;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

import com.cleanroommc.groovyscript.GroovyScript;
import com.cleanroommc.groovyscript.api.IIngredient;
import com.cleanroommc.groovyscript.compat.vanilla.ItemStackMixinExpansion;
import com.cleanroommc.groovyscript.compat.vanilla.ItemStackTransformer;
import com.cleanroommc.groovyscript.compat.vanilla.ShapedCraftingRecipe;
import com.cleanroommc.groovyscript.compat.vanilla.ShapelessCraftingRecipe;
import com.cleanroommc.groovyscript.helper.ingredient.IngredientBase;
import com.cleanroommc.groovyscript.helper.ingredient.OreDictIngredient;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Authoring-only conversion. Ordinary inputs compile to native descriptors; custom engine behavior stays explicit. */
public final class GroovyCraftingDefinitions {

    private static final VarHandle CONDITION;
    private static final VarHandle TRANSFORMER;

    static {
        try {
            var lookup = MethodHandles.privateLookupIn(IngredientBase.class, MethodHandles.lookup());
            CONDITION = lookup.findVarHandle(IngredientBase.class, "matchCondition", Predicate.class);
            TRANSFORMER = lookup.findVarHandle(IngredientBase.class, "transformer", ItemStackTransformer.class);
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private GroovyCraftingDefinitions() {}

    public static ResourceLocation name(String name) {
        return name.contains(":") ? new ResourceLocation(name) :
                new ResourceLocation(GroovyScript.getRunConfig().getPackId(), name);
    }

    public static CraftingMutation shaped(SourceOrigin origin, ResourceLocation name, ItemStack output,
                                          List<? extends List<?>> rows) {
        if (output.isEmpty() || rows.isEmpty() || rows.size() > 3)
            throw new IllegalArgumentException("Shaped crafting needs a nonempty output and one to three rows");
        int width = rows.getFirst().size();
        int height = rows.size();
        if (width < 1 || width > 3 || rows.stream().anyMatch(row -> row.size() != width))
            throw new IllegalArgumentException("Shaped crafting needs equal rows of one to three cells");
        List<Object> inputs = new ArrayList<>();
        rows.forEach(inputs::addAll);
        validateCrafting(inputs);
        var result = ItemValue.of(output);
        if (inputs.stream().allMatch(GroovyCraftingDefinitions::plain)) {
            return new ShapedCraftingDefinition(origin, name, "", result, width, height, false,
                    inputs.stream().map(GroovyCraftingDefinitions::nativeInput).toList());
        }
        var captured = copyCustom(inputs);
        return new CustomCraftingDefinition(origin, name, () -> new ShapedCraftingRecipe(result.stack(),
                copyCustom(captured), width, height, false, null, null).setRegistryName(name));
    }

    public static CraftingMutation shapeless(SourceOrigin origin, ResourceLocation name, ItemStack output,
                                             List<?> inputs) {
        List<Object> occupied = new ArrayList<>();
        for (Object input : inputs) {
            validateOre(input);
            if (!empty(input)) occupied.add(input);
        }
        if (output.isEmpty() || occupied.size() > 9)
            throw new IllegalArgumentException("Shapeless crafting needs a nonempty output and at most nine inputs");
        validateCrafting(occupied);
        var result = ItemValue.of(output);
        if (occupied.stream().allMatch(GroovyCraftingDefinitions::plain)) {
            return new ShapelessCraftingDefinition(origin, name, "", result,
                    occupied.stream().map(GroovyCraftingDefinitions::nativeInput).toList());
        }
        var captured = copyCustom(occupied);
        return new CustomCraftingDefinition(origin, name, () -> new ShapelessCraftingRecipe(result.stack(),
                copyCustom(captured), null, null).setRegistryName(name));
    }

    /** First-alternative recovery with explicit zero quantities omitted; unresolved named ores are diagnosed first. */
    public static List<GTRecipeInput> recoveryInputs(List<?> inputs) {
        List<GTRecipeInput> result = new ArrayList<>();
        for (Object input : inputs) {
            if (input instanceof List<?> nested) {
                result.addAll(recoveryInputs(nested));
                continue;
            }
            validateOre(input);
            if (empty(input)) continue;
            int amount = amount(input);
            ItemStack[] matches = input instanceof ItemStack stack ? new ItemStack[] { stack } :
                    ((IIngredient) input).withAmount(1).getMatchingStacks();
            if (amount <= 0 || matches == null || matches.length == 0 || matches[0].isEmpty())
                throw new IllegalArgumentException("Recovery input must resolve to a nonempty first alternative");
            result.add(new GTRecipeItemInput(matches[0], amount));
        }
        return List.copyOf(result);
    }

    private static void validateCrafting(List<?> inputs) {
        boolean occupied = false;
        for (Object input : inputs) {
            validateOre(input);
            if (empty(input)) continue;
            occupied = true;
            if (amount(input) != 1) throw new IllegalArgumentException("Crafting consumes one item per occupied cell");
        }
        if (!occupied) throw new IllegalArgumentException("Crafting needs at least one occupied input");
    }

    private static void validateOre(Object input) {
        if (input instanceof OreDictIngredient ore && OreDictionary.getOres(ore.getOreDict(), false).isEmpty())
            throw new IllegalArgumentException("Unresolved ore ingredient at recipe registration: " + ore.getOreDict());
        if (input != null && !(input instanceof ItemStack) && !(input instanceof IIngredient))
            throw new IllegalArgumentException("Expected an item ingredient, got " + input.getClass().getName());
    }

    private static int amount(Object input) {
        return input instanceof ItemStack stack ? stack.getCount() : ((IIngredient) input).getAmount();
    }

    private static boolean empty(Object input) {
        return input == null || (input instanceof ItemStack stack ? stack.isEmpty() : ((IIngredient) input).isEmpty());
    }

    private static boolean plain(Object input) {
        if (empty(input)) return true;
        if (input instanceof ItemStack stack) {
            return !((Object) stack instanceof ItemStackMixinExpansion expansion) ||
                    (expansion.grs$getMatcher() == null && expansion.grs$getNbtMatcher() == null &&
                            expansion.grs$getTransformer() == null);
        }
        return input.getClass() == OreDictIngredient.class && CONDITION.get(input) == null &&
                TRANSFORMER.get(input) == null;
    }

    private static ItemInput nativeInput(Object input) {
        if (empty(input)) return null;
        if (input instanceof ItemStack stack)
            return new ItemIngredient(List.of(ItemValue.of(stack)), 1, true, NbtPolicy.any(), CapabilityPolicy.IGNORE);
        return new OreIngredient(((OreDictIngredient) input).getOreDict(), 1, true, NbtPolicy.any());
    }

    private static List<IIngredient> copyCustom(List<?> inputs) {
        return inputs.stream().map(input -> empty(input) ? IIngredient.EMPTY : ((IIngredient) input).exactCopy())
                .toList();
    }
}
