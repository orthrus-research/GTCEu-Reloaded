package gregtech.api.recipes.map;

import gregtech.api.recipes.Recipe;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class Branch {

    // Keys on this have *(should)* unique hashcodes.
    private Map<AbstractMapIngredient, Either<Recipe, Branch>> nodes;
    // Keys on this have collisions, and must be differentiated by equality.
    private Map<AbstractMapIngredient, Either<Recipe, Branch>> specialNodes;
    private Recipe terminalRecipe;

    public Recipe getTerminalRecipe() {
        return terminalRecipe;
    }

    public void setTerminalRecipe(Recipe recipe) {
        this.terminalRecipe = recipe;
    }

    /** Copies the index, preserving shared branches and omitting every path to the selected recipe identities. */
    public Branch copyWithoutRecipes(Collection<Recipe> removed) {
        Set<Recipe> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        identities.addAll(removed);
        return copyWithoutRecipes(identities, new IdentityHashMap<>());
    }

    private Branch copyWithoutRecipes(Set<Recipe> removed, Map<Branch, Branch> copies) {
        Branch existing = copies.get(this);
        if (existing != null) return existing;
        Branch copy = new Branch();
        copies.put(this, copy);
        if (!removed.contains(terminalRecipe)) copy.terminalRecipe = terminalRecipe;
        if (nodes != null) copy.nodes = copyNodes(nodes, removed, copies);
        if (specialNodes != null) copy.specialNodes = copyNodes(specialNodes, removed, copies);
        return copy;
    }

    private static Map<AbstractMapIngredient, Either<Recipe, Branch>> copyNodes(
                                                                                Map<AbstractMapIngredient, Either<Recipe, Branch>> source,
                                                                                Set<Recipe> removed,
                                                                                Map<Branch, Branch> copies) {
        Map<AbstractMapIngredient, Either<Recipe, Branch>> result = new Object2ObjectOpenHashMap<>(source.size());
        source.forEach((key, value) -> {
            if (value.left().isPresent()) {
                if (!removed.contains(value.left().orElseThrow())) result.put(key, value);
            } else {
                Branch child = value.right().orElseThrow().copyWithoutRecipes(removed, copies);
                if (!child.isEmptyBranch()) result.put(key, Either.right(child));
            }
        });
        return result;
    }

    public Stream<Recipe> getRecipes(boolean filterHidden) {
        Stream<Recipe> stream = terminalRecipe == null ? Stream.empty() : Stream.of(terminalRecipe);
        if (nodes != null) {
            stream = Stream.concat(stream, nodes.values().stream()
                    .flatMap(either -> either.map(Stream::of, right -> right.getRecipes(filterHidden))));
        }
        if (specialNodes != null) {
            stream = Stream.concat(stream, specialNodes.values().stream()
                    .flatMap(either -> either.map(Stream::of, right -> right.getRecipes(filterHidden))));
        }
        if (filterHidden) {
            stream = stream.filter(t -> !t.isHidden());
        }
        return stream;
    }

    public boolean isEmptyBranch() {
        return terminalRecipe == null && (nodes == null || nodes.isEmpty()) &&
                (specialNodes == null || specialNodes.isEmpty());
    }

    @NotNull
    public Map<AbstractMapIngredient, Either<Recipe, Branch>> getNodes() {
        if (nodes == null) {
            nodes = new Object2ObjectOpenHashMap<>(2);
        }
        return nodes;
    }

    @NotNull
    public Map<AbstractMapIngredient, Either<Recipe, Branch>> getSpecialNodes() {
        if (specialNodes == null) {
            specialNodes = new Object2ObjectOpenHashMap<>(2);
        }
        return specialNodes;
    }
}
