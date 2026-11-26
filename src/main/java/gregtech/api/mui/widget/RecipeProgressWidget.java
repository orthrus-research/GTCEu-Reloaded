package gregtech.api.mui.widget;

import gregtech.api.GregTechAPI;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.integration.IntegrationModule;
import gregtech.integration.jei.JustEnoughItemsModule;
import gregtech.integration.jei.recipe.RecipeMapCategory;
import gregtech.modules.GregTechModules;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A machine's progress bar, which shows its recipe map's recipes in the recipe viewer when clicked.
 */
public class RecipeProgressWidget extends ProgressWidget implements Interactable {

    private final RecipeMap<?> recipeMap;

    /**
     * @param recipeMap the recipe map whose recipes a click shows
     */
    public RecipeProgressWidget(@NotNull RecipeMap<?> recipeMap) {
        this.recipeMap = recipeMap;
        addTooltipLine(IKey.lang("gui.widget.recipeProgressWidget.default_tooltip"));
    }

    public @NotNull RecipeMap<?> getRecipeMap() {
        return recipeMap;
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        return showRecipes(recipeMap) ? Result.SUCCESS : Result.IGNORE;
    }

    /**
     * Show a recipe map's categories in the recipe viewer; for the furnace, the viewer's smelting category.
     *
     * @param recipeMap the recipe map whose recipes to show
     * @return whether the viewer showed them; false when no viewer is loaded or the map has no categories
     */
    public static boolean showRecipes(@NotNull RecipeMap<?> recipeMap) {
        if (!GregTechAPI.moduleManager.isModuleEnabled(GregTechModules.MODULE_JEI)) {
            return false;
        }
        Collection<RecipeMapCategory> categories = RecipeMapCategory.getCategoriesFor(recipeMap);
        if (categories == null || categories.isEmpty()) {
            return false;
        }
        // Since categories were even registered at all, we know JEI is active.
        List<String> categoryID = new ArrayList<>();
        if (recipeMap == RecipeMaps.FURNACE_RECIPES) {
            categoryID.add("minecraft.smelting");
        } else {
            for (RecipeMapCategory category : categories) {
                categoryID.add(category.getUid());
            }
        }

        if (JustEnoughItemsModule.jeiRuntime == null) {
            IntegrationModule.logger.error("GTCEu JEI integration has crashed, this is not a good thing");
            return false;
        }
        JustEnoughItemsModule.jeiRuntime.getRecipesGui().showCategories(categoryID);
        return true;
    }
}
