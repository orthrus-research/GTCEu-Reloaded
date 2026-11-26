package gregtech.integration.jei.recipe;

import gregtech.api.GTValues;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.category.GTRecipeCategory;
import gregtech.api.recipes.properties.impl.ResearchProperty;
import gregtech.api.recipes.properties.impl.ResearchPropertyData;
import gregtech.api.recipes.ui.RecipeLayoutSlot;
import gregtech.api.recipes.ui.RecipeMapLayout;
import gregtech.api.util.AssemblyLineManager;
import gregtech.api.util.GTUtility;
import gregtech.api.util.LocalizationUtils;
import gregtech.common.ConfigHolder;
import gregtech.integration.jei.JustEnoughItemsModule;
import gregtech.integration.jei.utils.render.FluidStackTextRenderer;
import gregtech.integration.jei.utils.render.ItemStackTextRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RecipeMapCategory implements IRecipeCategory<GTRecipeWrapper> {

    /**
     * The item input slots the category lays out for a map. The assembly line has one more than its maximum inputs,
     * for the data item; output slot indexes start after these.
     */
    static int importSlotCount(@NotNull RecipeMap<?> recipeMap) {
        return recipeMap.getMaxInputs() + (recipeMap == RecipeMaps.ASSEMBLY_LINE_RECIPES ? 1 : 0);
    }

    /** The layout a category draws for a map: its machines' layout with every input and output the map allows. */
    static @NotNull RecipeMapLayout createLayout(@NotNull RecipeMap<?> recipeMap) {
        return recipeMap.getRecipeMapUI().createJeiLayout(importSlotCount(recipeMap), recipeMap.getMaxOutputs(),
                recipeMap.getMaxFluidInputs(), recipeMap.getMaxFluidOutputs());
    }

    private final RecipeMap<?> recipeMap;
    private final GTRecipeCategory category;
    private final RecipeMapLayout layout;
    private final IDrawable backgroundDrawable;
    private Object iconIngredient;
    private IDrawable icon;

    private static final Map<GTRecipeCategory, RecipeMapCategory> gtCategories = new Object2ObjectOpenHashMap<>();
    private static final Map<RecipeMap<?>, List<RecipeMapCategory>> recipeMapCategories = new Object2ObjectOpenHashMap<>();

    public RecipeMapCategory(@NotNull RecipeMap<?> recipeMap, @NotNull GTRecipeCategory category,
                             IGuiHelper guiHelper) {
        this.recipeMap = recipeMap;
        this.category = category;
        this.layout = createLayout(recipeMap);
        this.backgroundDrawable = guiHelper.createBlankDrawable(layout.getWidth(),
                layout.getHeight() * 2 / 3 + recipeMap.getRecipeMapUI().getPropertyHeightShift() +
                        GTRecipeWrapper.OVERCLOCK_HEIGHT);
        gtCategories.put(category, this);
        recipeMapCategories.compute(recipeMap, (k, v) -> {
            if (v == null) v = new ArrayList<>();
            v.add(this);
            return v;
        });
    }

    @Override
    @NotNull
    public String getUid() {
        return category.getUniqueID();
    }

    @Override
    @NotNull
    public String getTitle() {
        return LocalizationUtils.format(category.getTranslationKey());
    }

    @Nullable
    @Override
    public IDrawable getIcon() {
        if (icon != null) {
            return icon;
        } else if (iconIngredient instanceof IDrawable drawable) {
            return icon = drawable;
        } else if (iconIngredient != null) {
            // cache the icon drawable for less gc pressure
            return icon = JustEnoughItemsModule.guiHelper.createDrawableIngredient(iconIngredient);
        }
        // JEI will automatically populate the icon as the first registered catalyst if null
        return null;
    }

    public void setIcon(Object icon) {
        if (iconIngredient == null) {
            iconIngredient = icon;
        }
    }

    @Override
    @NotNull
    public String getModName() {
        return GTValues.MODID;
    }

    @Override
    @NotNull
    public IDrawable getBackground() {
        return backgroundDrawable;
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, @NotNull GTRecipeWrapper recipeWrapper,
                          @NotNull IIngredients ingredients) {
        recipeWrapper.prepareOverclockPreview(backgroundDrawable.getWidth(), backgroundDrawable.getHeight());
        IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
        IGuiFluidStackGroup fluidStackGroup = recipeLayout.getFluidStacks();
        int importSlots = importSlotCount(recipeMap);
        int importTanks = recipeMap.getMaxFluidInputs();
        for (RecipeLayoutSlot slot : layout.getElements(RecipeLayoutSlot.class)) {
            int x = slot.x() + 1;
            int y = slot.y() + 1;
            int size = RecipeLayoutSlot.SIZE - 2;
            int index = slot.index();
            if (!slot.isFluid() && !slot.isOutput()) {
                itemStackGroup.init(index, true, new ItemStackTextRenderer(recipeWrapper.isNotConsumedItem(index)),
                        x, y, size, size, 0, 0);
            } else if (!slot.isFluid()) {
                itemStackGroup.init(importSlots + index, false, new ItemStackTextRenderer(
                        recipeWrapper.getOutputChance(index - recipeWrapper.getRecipe().getOutputs().size()),
                        recipeWrapper.getChancedOutputLogic()), x, y, size, size, 0, 0);
            } else if (!slot.isOutput()) {
                fluidStackGroup.init(index, true, new FluidStackTextRenderer(
                        fluidAmount(ingredients.getInputs(VanillaTypes.FLUID), index), false, size, size, null)
                        .setNotConsumed(recipeWrapper.isNotConsumedFluid(index)), x, y, size, size, 0, 0);
            } else {
                fluidStackGroup.init(importTanks + index, false, new FluidStackTextRenderer(
                        fluidAmount(ingredients.getOutputs(VanillaTypes.FLUID), index), false, size, size, null,
                        recipeWrapper.getFluidOutputChance(index - recipeWrapper.getRecipe().getFluidOutputs().size()),
                        recipeWrapper.getChancedFluidOutputLogic()), x, y, size, size, 0, 0);
            }
        }

        if (ConfigHolder.machines.enableResearch && this.recipeMap == RecipeMaps.ASSEMBLY_LINE_RECIPES) {
            ResearchPropertyData data = recipeWrapper.getRecipe().getProperty(ResearchProperty.getInstance(), null);
            if (data != null) {
                List<ItemStack> dataItems = new ArrayList<>();
                for (ResearchPropertyData.ResearchEntry entry : data) {
                    ItemStack dataStick = entry.dataItem().copy();
                    AssemblyLineManager.writeResearchToNBT(GTUtility.getOrCreateNbtCompound(dataStick),
                            entry.researchId());
                    dataItems.add(dataStick);
                }
                itemStackGroup.set(16, dataItems);
            }
        }

        itemStackGroup.addTooltipCallback(recipeWrapper::addItemTooltip);
        fluidStackGroup.addTooltipCallback(recipeWrapper::addFluidTooltip);
        itemStackGroup.set(ingredients);
        fluidStackGroup.set(ingredients);
    }

    /** The amount of the first fluid a recipe lists at an index, or 0 when it lists none there. */
    private static int fluidAmount(@NotNull List<List<FluidStack>> fluids, int index) {
        if (fluids.size() <= index || fluids.get(index).isEmpty()) return 0;
        return fluids.get(index).get(0).amount;
    }

    @Override
    public void drawExtras(@NotNull Minecraft minecraft) {
        RecipeLayoutRenderer.draw(layout, ConfigHolder.client.guiConfig.smoothProgressBars);
    }

    @Nullable
    public static RecipeMapCategory getCategoryFor(@NotNull GTRecipeCategory category) {
        return gtCategories.get(category);
    }

    /** Starts a new viewer registration cycle without retaining categories from the previous runtime. */
    public static void clearCategories() {
        gtCategories.clear();
        recipeMapCategories.clear();
    }

    @Nullable
    public static Collection<RecipeMapCategory> getCategoriesFor(@NotNull RecipeMap<?> recipeMap) {
        return recipeMapCategories.get(recipeMap);
    }
}
