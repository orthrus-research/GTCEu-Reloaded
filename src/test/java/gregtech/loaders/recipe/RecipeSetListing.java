package gregtech.loaders.recipe;

import gregtech.Bootstrap;
import gregtech.api.block.machines.MachineItemBlock;
import gregtech.api.items.materialitem.MetaPrefixItem;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.chance.output.BoostableChanceOutput;
import gregtech.api.recipes.chance.output.ChancedOutput;
import gregtech.api.recipes.chance.output.ChancedOutputList;
import gregtech.api.recipes.ingredients.GTRecipeFluidInput;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;
import gregtech.api.recipes.ingredients.GTRecipeOreInput;
import gregtech.api.recipes.ingredients.IntCircuitIngredient;
import gregtech.api.recipes.ingredients.nbtmatch.NBTMatcher;
import gregtech.api.recipes.properties.RecipeProperty;
import gregtech.api.unification.material.Material;
import gregtech.api.util.GTUtility;
import gregtech.common.crafting.GTFluidCraftingIngredient;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.OreIngredient;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * A canonical listing of every recipe registered: each recipe map's recipes, the crafting table recipes and the
 * furnace recipes, one line a recipe, sorted, so the order the loaders register them in does not count.
 * <p>
 * A machine recipe line holds its map, item inputs (count, ore dictionary name with any material, the stacks of an
 * item input, non-consumed and NBT matching flags), fluid inputs, item and chanced outputs, fluid and chanced fluid
 * outputs, EU/t, duration, properties and category. A crafting line holds the recipe's registry name, type, shape,
 * flags, output and ingredients; a furnace line its input, output and experience.
 */
final class RecipeSetListing {

    private static final Field ORE_INGREDIENT_ORES = field(OreIngredient.class, "ores");
    private static final Field NATIVE_INGREDIENT_INPUT = field(nativeIngredientClass(), "input");
    private static final Field FURNACE_EXPERIENCE = field(FurnaceRecipes.class, "experienceList");
    private static final List<String> CRAFTING_FLAGS = List.of("mirrored", "isClearing", "transferMaxCharge");

    private final Map<Object, String> oreNames = new IdentityHashMap<>();
    private final Map<NBTMatcher, String> matcherNames = new IdentityHashMap<>();

    private RecipeSetListing() {
        for (String name : OreDictionary.getOreNames()) {
            oreNames.put(OreDictionary.getOres(name, false), name);
        }
        for (Field field : NBTMatcher.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == NBTMatcher.class) {
                matcherNames.put((NBTMatcher) read(field, null), field.getName());
            }
        }
    }

    /**
     * Runs the game's recipe registration on top of the test bootstrap, then lists what it registered.
     * {@link RecipeSetDigestTest} calls this in a class loader of its own, so that the registration neither sees
     * what other tests left behind nor leaves its recipes to them.
     *
     * @return the sorted listing of the recipes registered
     */
    static List<String> registerAndTake() {
        Bootstrap.registerRecipes();
        return new RecipeSetListing().lines();
    }

    private List<String> lines() {
        List<String> lines = new ArrayList<>();
        for (RecipeMap<?> map : RecipeMap.getRecipeMaps()) {
            for (Recipe recipe : map.getRecipeList()) {
                lines.add(machine(map, recipe));
            }
        }
        for (Map.Entry<ResourceLocation, IRecipe> entry : ForgeRegistries.RECIPES.getEntries()) {
            lines.add(crafting(entry.getKey(), entry.getValue()));
        }
        // the experience is kept by the output stack itself, so that equal outputs can carry their own
        @SuppressWarnings("unchecked")
        Map<ItemStack, Float> experience = (Map<ItemStack, Float>) read(FURNACE_EXPERIENCE, FurnaceRecipes.instance());
        FurnaceRecipes.instance().getSmeltingList().forEach((input, output) -> lines.add("furnace " + item(input) +
                " | " + stack(output) + " | xp " + experience.get(output)));
        lines.sort(null);
        return lines;
    }

    private String machine(RecipeMap<?> map, Recipe recipe) {
        StringBuilder line = new StringBuilder("machine ").append(map.unlocalizedName);
        section(line, "in", recipe.getInputs(), this::input);
        section(line, "fluid in", recipe.getFluidInputs(), this::input);
        section(line, "out", recipe.getOutputs(), this::stack);
        chanced(line, "chanced", recipe.getChancedOutputs(), this::stack);
        section(line, "fluid out", recipe.getFluidOutputs(), this::fluid);
        chanced(line, "fluid chanced", recipe.getChancedFluidOutputs(), this::fluid);
        line.append(" | ").append(recipe.getEUt()).append(" EU/t ").append(recipe.getDuration()).append(" t");

        Map<String, String> properties = new TreeMap<>();
        for (Map.Entry<RecipeProperty<?>, Object> entry : recipe.propertyStorage().entrySet()) {
            properties.put(entry.getKey().getKey(), nbt(entry.getKey().serialize(entry.getValue())));
        }
        if (!properties.isEmpty()) {
            line.append(" | ");
            properties.forEach((key, value) -> line.append(key).append('=').append(value).append(' '));
            line.setLength(line.length() - 1);
        }
        line.append(" | category ").append(recipe.getRecipeCategory().getUniqueID());
        if (recipe.isHidden()) line.append(" | hidden");
        return line.toString();
    }

    private static <T> void section(StringBuilder line, String name, Collection<T> values,
                                    Function<T, String> text) {
        if (values.isEmpty()) return;
        line.append(" | ").append(name).append(' ');
        join(line, values, text);
    }

    private static <I, T extends ChancedOutput<I>> void chanced(StringBuilder line, String name,
                                                                ChancedOutputList<I, T> outputs,
                                                                Function<I, String> text) {
        if (outputs.getChancedEntries().isEmpty()) return;
        line.append(" | ").append(name).append(' ').append(outputs.getChancedOutputLogic().getTranslationKey())
                .append(' ');
        join(line, outputs.getChancedEntries(), output -> {
            String chance = text.apply(output.getIngredient()) + " @" + output.getChance();
            return output instanceof BoostableChanceOutput<?> boostable ?
                    chance + '+' + boostable.getChanceBoost() : chance;
        });
    }

    private static <T> void join(StringBuilder line, Collection<T> values, Function<T, String> text) {
        boolean first = true;
        for (T value : values) {
            if (!first) line.append(", ");
            line.append(text.apply(value));
            first = false;
        }
    }

    private String input(GTRecipeInput input) {
        StringBuilder text = new StringBuilder();
        Class<?> type = input.getClass();
        if (type != GTRecipeItemInput.class && type != GTRecipeOreInput.class && type != GTRecipeFluidInput.class &&
                type != IntCircuitIngredient.class) {
            text.append(type.getSimpleName()).append(' ');
        }
        if (input instanceof IntCircuitIngredient) {
            text.append(input);
        } else if (input.isOreDict()) {
            text.append(input.getAmount()).append("x ore:").append(OreDictionary.getOreName(input.getOreDict()));
            if (input.getMaterialIdentity() != null) text.append(" [").append(input.getMaterialIdentity()).append(']');
        } else if (input.getInputFluidStack() != null) {
            FluidStack fluid = input.getInputFluidStack();
            text.append(input.getAmount()).append("mb ").append(fluid.getFluid().getName()).append(nbt(fluid.tag));
        } else {
            text.append(input.getAmount()).append("x ");
            ItemStack[] stacks = input.getInputStacks();
            if (stacks.length == 1) {
                text.append(item(stacks[0]));
            } else {
                text.append('[');
                join(text, items(stacks), Function.identity());
                text.append(']');
            }
        }
        if (input.isNonConsumable()) text.append(" nc");
        if (input.hasNBTMatchingCondition()) {
            text.append(" nbt ").append(matcherNames.getOrDefault(input.getNBTMatcher(), "custom"))
                    .append(' ').append(input.getNBTMatchingCondition());
        }
        return text.toString();
    }

    private String crafting(ResourceLocation name, IRecipe recipe) {
        String type = recipe.getClass().getSimpleName();
        StringBuilder line = new StringBuilder("crafting ").append(name).append(" | ")
                .append(type.isEmpty() ? recipe.getClass().getName() : type);
        if (recipe instanceof IShapedRecipe shaped) {
            line.append(' ').append(shaped.getRecipeWidth()).append('x').append(shaped.getRecipeHeight());
        }
        for (String flag : CRAFTING_FLAGS) {
            Field field = findField(recipe.getClass(), flag);
            if (field != null && (boolean) read(field, recipe)) line.append(' ').append(flag);
        }
        if (!recipe.getGroup().isEmpty()) line.append(" group ").append(recipe.getGroup());
        line.append(" | ").append(stack(recipe.getRecipeOutput())).append(" | ");
        join(line, recipe.getIngredients(), this::ingredient);
        return line.toString();
    }

    private String ingredient(Ingredient ingredient) {
        if (ingredient == Ingredient.EMPTY) return "-";
        if (ingredient instanceof OreIngredient) {
            return "ore:" + oreNames.getOrDefault(read(ORE_INGREDIENT_ORES, ingredient), "?");
        }
        if (NATIVE_INGREDIENT_INPUT.getDeclaringClass().isInstance(ingredient)) {
            return input((GTRecipeInput) read(NATIVE_INGREDIENT_INPUT, ingredient));
        }
        StringBuilder text = new StringBuilder();
        if (ingredient.getClass() != Ingredient.class) text.append(ingredient.getClass().getSimpleName()).append(' ');
        if (ingredient instanceof GTFluidCraftingIngredient fluid) {
            text.append(fluid(fluid.getFluidStack())).append(' ');
        }
        text.append('[');
        join(text, items(ingredient.getMatchingStacks()), Function.identity());
        return text.append(']').toString();
    }

    /** The stacks an input or ingredient accepts, sorted: their order only decides which one a display shows first. */
    private List<String> items(ItemStack[] stacks) {
        List<String> items = new ArrayList<>(stacks.length);
        for (ItemStack stack : stacks) {
            items.add(item(stack));
        }
        items.sort(null);
        return items;
    }

    private String stack(ItemStack stack) {
        return stack.getCount() + "x " + item(stack);
    }

    /** The item, the meta item value, material or machine it stands for, and its NBT, without the count. */
    private String item(ItemStack stack) {
        if (stack.isEmpty()) return "empty";
        StringBuilder text = new StringBuilder(String.valueOf(stack.getItem().getRegistryName()));
        int damage = stack.getItemDamage();
        Material material = stack.getItem() instanceof MetaPrefixItem prefixItem ? prefixItem.getMaterial(stack) :
                null;
        MetaItem<?>.MetaValueItem value = material == null && stack.getItem() instanceof MetaItem<?> metaItem ?
                metaItem.getItem(stack) : null;
        MetaTileEntity machine = stack.getItem() instanceof MachineItemBlock ? GTUtility.getMetaTileEntity(stack) :
                null;
        if (material != null) {
            text.append('/').append(material.getResourceLocation());
        } else if (value != null) {
            text.append('/').append(value.unlocalizedName);
        } else if (machine != null) {
            text.append('/').append(machine.metaTileEntityId);
        } else {
            text.append('@').append(damage == OreDictionary.WILDCARD_VALUE ? "*" : String.valueOf(damage));
        }
        return text.append(nbt(stack.getTagCompound())).toString();
    }

    private String fluid(FluidStack fluid) {
        return fluid.amount + "mb " + fluid.getFluid().getName() + nbt(fluid.tag);
    }

    /** NBT with compound keys sorted, so a tag reads the same whatever order its keys were set in. */
    private static String nbt(@Nullable NBTBase tag) {
        if (tag == null) return "";
        if (tag instanceof NBTTagCompound compound) {
            StringBuilder text = new StringBuilder("{");
            for (String key : new TreeSet<>(compound.getKeySet())) {
                if (text.length() > 1) text.append(',');
                text.append(key).append(':').append(nbt(compound.getTag(key)));
            }
            return text.append('}').toString();
        }
        if (tag instanceof NBTTagList list) {
            StringBuilder text = new StringBuilder("[");
            for (int i = 0; i < list.tagCount(); i++) {
                if (i > 0) text.append(',');
                text.append(nbt(list.get(i)));
            }
            return text.append(']').toString();
        }
        return tag.toString();
    }

    private static Class<?> nativeIngredientClass() {
        try {
            return Class.forName("gregtech.api.recipes.NativeCraftingIngredient");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("The recipe listing no longer finds GregTech's native ingredient", e);
        }
    }

    private static @Nullable Field findField(Class<?> type, String name) {
        for (Class<?> owner = type; owner != null && owner != Object.class; owner = owner.getSuperclass()) {
            for (Field field : owner.getDeclaredFields()) {
                if (field.getName().equals(name) && field.getType() == boolean.class) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        return null;
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("The recipe listing no longer finds " + owner.getName() + '.' + name, e);
        }
    }

    private static Object read(Field field, @Nullable Object owner) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
