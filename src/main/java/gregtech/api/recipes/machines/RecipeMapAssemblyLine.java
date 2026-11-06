package gregtech.api.recipes.machines;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeRegistrationChange;
import gregtech.api.recipes.RecipeRegistrationState;
import gregtech.api.recipes.properties.impl.ResearchProperty;
import gregtech.api.recipes.properties.impl.ResearchPropertyData;
import gregtech.api.recipes.ui.RecipeMapUIFunction;
import gregtech.core.sound.GTSoundEvents;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApiStatus.Internal
public class RecipeMapAssemblyLine<R extends RecipeBuilder<R>> extends RecipeMap<R> implements IResearchRecipeMap {

    private final RecipeRegistrationState<Map<String, List<Recipe>>> researchEntries = registrationState(Map.of(),
            RecipeMapAssemblyLine::prepareResearch);

    public RecipeMapAssemblyLine(@NotNull String unlocalizedName, @NotNull R defaultRecipeBuilder,
                                 @NotNull RecipeMapUIFunction recipeMapUI) {
        super(unlocalizedName, defaultRecipeBuilder, recipeMapUI, 16, 1, 4, 0);
        setSound(GTSoundEvents.ASSEMBLER);
    }

    private static Map<String, List<Recipe>> prepareResearch(Map<String, List<Recipe>> current,
                                                             RecipeRegistrationChange change) {
        if (change.clear()) return Map.of();
        Set<Recipe> removed = Collections.newSetFromMap(new IdentityHashMap<>());
        removed.addAll(change.removed());
        for (Recipe recipe : change.removed()) {
            ResearchPropertyData data = recipe.getProperty(ResearchProperty.getInstance(), null);
            if (data == null) continue;
            for (var entry : data) {
                var indexed = current.get(entry.researchId());
                if (indexed == null || indexed.stream().noneMatch(value -> value == recipe))
                    throw new IllegalStateException("Owned research index was changed: " + entry.researchId());
            }
        }
        Map<String, List<Recipe>> staged = new LinkedHashMap<>();
        current.forEach((id, recipes) -> {
            var retained = recipes.stream().filter(recipe -> !removed.contains(recipe)).toList();
            if (!retained.isEmpty()) staged.put(id, retained);
        });
        for (Recipe recipe : change.added()) {
            ResearchPropertyData data = recipe.getProperty(ResearchProperty.getInstance(), null);
            if (data != null) for (var entry : data) addEntry(staged, entry.researchId(), recipe);
        }
        return Map.copyOf(staged);
    }

    @Override
    public void addDataStickEntry(@NotNull String researchId, @NotNull Recipe recipe) {
        researchEntries.update(current -> {
            var staged = new LinkedHashMap<>(current);
            addEntry(staged, researchId, recipe);
            return Map.copyOf(staged);
        });
    }

    private static void addEntry(Map<String, List<Recipe>> entries, String researchId, Recipe recipe) {
        for (String key : researchKeys(researchId)) {
            var previous = entries.getOrDefault(key, List.of());
            if (previous.stream().anyMatch(value -> value == recipe)) continue;
            var added = new ArrayList<>(previous);
            added.add(recipe);
            entries.put(key, List.copyOf(added));
        }
    }

    private static List<String> researchKeys(String id) {
        // Preserve the native saved research identity migration from 2.8.6.
        return id.contains("xmetaitem.") ? List.of(id, id.replace("xmetaitem.", "xitem.meta_item.")) : List.of(id);
    }

    @Nullable
    @Override
    public Collection<Recipe> getDataStickEntry(@NotNull String researchId) {
        return researchEntries.value().get(researchId);
    }

    @Override
    public boolean removeDataStickEntry(@NotNull String researchId, @NotNull Recipe recipe) {
        var keys = researchKeys(researchId);
        if (keys.stream().noneMatch(key -> researchEntries.value().getOrDefault(key, List.of()).stream()
                .anyMatch(value -> value == recipe)))
            return false;
        researchEntries.update(current -> {
            var staged = new LinkedHashMap<>(current);
            for (String key : keys) {
                var retained = current.getOrDefault(key, List.of()).stream().filter(value -> value != recipe).toList();
                if (retained.isEmpty()) staged.remove(key);
                else staged.put(key, retained);
            }
            return Map.copyOf(staged);
        });
        return true;
    }
}
