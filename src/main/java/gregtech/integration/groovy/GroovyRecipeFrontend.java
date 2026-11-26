package gregtech.integration.groovy;

import gregtech.api.GregTechAPI;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeScripting;
import gregtech.modules.GregTechModules;

import com.cleanroommc.groovyscript.api.GroovyLog;
import org.jetbrains.annotations.NotNull;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tells GroovyScript about recipe registration: each recipe map gets a virtualized registry, which removes script
 * recipes and restores removed ones on reload, and script errors go to the script log.
 */
final class GroovyRecipeFrontend implements RecipeScripting.Frontend {

    private final Map<RecipeMap<?>, VirtualizedRecipeMap> maps = new IdentityHashMap<>();

    @Override
    public boolean isRunning() {
        return GroovyScriptModule.isCurrentlyRunning();
    }

    @Override
    public boolean reportInvalid(@NotNull String title, @NotNull List<String> problems) {
        GroovyLog.Msg msg = GroovyLog.msg(title).error();
        for (String problem : problems) {
            msg.add(true, () -> problem);
        }
        return msg.postIfNotEmpty();
    }

    @Override
    public void reportConflict(@NotNull RecipeMap<?> map, @NotNull Recipe recipe, @NotNull String details) {
        GroovyLog log = GroovyLog.get();
        log.warn("Recipe duplicate or conflict found in RecipeMap {} and was not added. See next lines for details",
                map.unlocalizedName);
        log.warn("Attempted to add Recipe: {}", recipe);
        log.warn("{}", details);
    }

    @Override
    public void mapCreated(@NotNull RecipeMap<?> map, @NotNull Class<?> builderType) {
        GroovyExpansions.mixinIngredientInputs(builderType);
        if (GregTechAPI.moduleManager().isModuleEnabled(GregTechModules.MODULE_GRS)) {
            maps.computeIfAbsent(map, VirtualizedRecipeMap::new);
        }
    }

    @Override
    public void scriptRecipeAdded(@NotNull RecipeMap<?> map, @NotNull Recipe recipe) {
        VirtualizedRecipeMap registry = maps.get(map);
        if (registry != null) registry.addScripted(recipe);
    }

    @Override
    public void recipeRemoved(@NotNull RecipeMap<?> map, @NotNull Recipe recipe) {
        VirtualizedRecipeMap registry = maps.get(map);
        if (registry != null) registry.addBackup(recipe);
    }
}
