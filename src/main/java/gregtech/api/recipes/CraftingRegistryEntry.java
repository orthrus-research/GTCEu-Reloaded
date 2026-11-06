package gregtech.api.recipes;

import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Exact state for one claimed name and its reserved ID; absence is captured at the assigned ID. */
record CraftingRegistryEntry(ResourceLocation name, int id, @Nullable IRecipe recipe, @Nullable Object owner,
                             boolean available, boolean blocked, boolean dummy, @Nullable ResourceLocation alias,
                             Set<ResourceLocation> incomingAliases, List<IRecipe> overrides) {

    static CraftingRegistryEntry capture(ForgeRegistry<IRecipe> registry, ResourceLocation name, int assignedId) {
        var recipe = CraftingRegistryAccess.names(registry).get(name);
        int id = recipe == null ? assignedId : registry.getID(recipe);
        if (id < 0) throw new IllegalStateException("A claimed crafting name must have an assigned ID");
        return new CraftingRegistryEntry(name, id, recipe,
                recipe == null ? null : CraftingRegistryAccess.owners(registry).inverse().get(recipe),
                CraftingRegistryAccess.availability(registry).get(id),
                CraftingRegistryAccess.blocked(registry).contains(id),
                CraftingRegistryAccess.dummies(registry).contains(name),
                CraftingRegistryAccess.aliases(registry).get(name), incomingAliases(registry, name),
                List.copyOf(CraftingRegistryAccess.overrides(registry).get(name)));
    }

    boolean matches(ForgeRegistry<IRecipe> registry) {
        return CraftingRegistryAccess.names(registry).get(name) == recipe &&
                CraftingRegistryAccess.ids(registry).get(id) == recipe &&
                (recipe == null ? owner == null :
                        CraftingRegistryAccess.owners(registry).inverse().get(recipe) == owner) &&
                CraftingRegistryAccess.availability(registry).get(id) == available &&
                CraftingRegistryAccess.blocked(registry).contains(id) == blocked &&
                CraftingRegistryAccess.dummies(registry).contains(name) == dummy &&
                Objects.equals(CraftingRegistryAccess.aliases(registry).get(name), alias) &&
                incomingAliases(registry, name).equals(incomingAliases) &&
                sameRecipes(List.copyOf(CraftingRegistryAccess.overrides(registry).get(name)), overrides);
    }

    boolean sameState(CraftingRegistryEntry other) {
        return name.equals(other.name) && id == other.id && recipe == other.recipe && owner == other.owner &&
                available == other.available && blocked == other.blocked && dummy == other.dummy &&
                Objects.equals(alias, other.alias) && incomingAliases.equals(other.incomingAliases) &&
                sameRecipes(overrides, other.overrides);
    }

    /** First FROZEN sync recreates owner keys and allocation flags through native add, preserving recipe identities. */
    boolean matchesFrozenCopy(CraftingRegistryEntry frozen) {
        boolean copiedOwner = overrides.isEmpty() ? CraftingRegistryAccess.isDefaultOwner(frozen.owner, name) :
                Objects.equals(owner, frozen.owner);
        return recipe != null && name.equals(frozen.name) && id == frozen.id && recipe == frozen.recipe &&
                copiedOwner && frozen.available && !frozen.blocked && dummy == frozen.dummy &&
                Objects.equals(alias, frozen.alias) && incomingAliases.equals(frozen.incomingAliases) &&
                sameRecipes(overrides, frozen.overrides);
    }

    CraftingRegistryEntry originalForFrozen(CraftingRegistryEntry copied) {
        return new CraftingRegistryEntry(name, id, recipe, recipe == null ? null : copied.owner,
                recipe != null, false, dummy, alias, incomingAliases, overrides);
    }

    void restore(ForgeRegistry<IRecipe> registry, CraftingRegistryEntry expected) {
        if (!expected.matches(registry)) throw new IllegalStateException("Owned crafting entry changed: " + name);
        var current = CraftingRegistryAccess.names(registry).get(name);
        var currentOwner = current == null ? null : CraftingRegistryAccess.owners(registry).inverse().get(current);
        if (currentOwner != null && currentOwner != owner) CraftingRegistryAccess.owners(registry).remove(currentOwner);
        if (recipe == null) {
            CraftingRegistryAccess.names(registry).remove(name);
            CraftingRegistryAccess.ids(registry).remove(id);
        } else {
            CraftingRegistryAccess.names(registry).put(name, recipe);
            CraftingRegistryAccess.ids(registry).put(id, recipe);
            if (owner != null) CraftingRegistryAccess.owners(registry).put(owner, recipe);
        }
        CraftingRegistryAccess.availability(registry).set(id, available);
        set(CraftingRegistryAccess.blocked(registry), id, blocked);
        set(CraftingRegistryAccess.dummies(registry), name, dummy);
        if (alias == null) CraftingRegistryAccess.aliases(registry).remove(name);
        else CraftingRegistryAccess.aliases(registry).put(name, alias);
        CraftingRegistryAccess.overrides(registry).removeAll(name);
        CraftingRegistryAccess.overrides(registry).putAll(name, overrides);
    }

    private static Set<ResourceLocation> incomingAliases(ForgeRegistry<IRecipe> registry, ResourceLocation name) {
        return CraftingRegistryAccess.aliases(registry).entrySet().stream()
                .filter(entry -> entry.getValue().equals(name)).map(java.util.Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean sameRecipes(List<IRecipe> left, List<IRecipe> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) if (left.get(i) != right.get(i)) return false;
        return true;
    }

    private static <T> void set(Set<T> values, T key, boolean present) {
        if (present) values.add(key);
        else values.remove(key);
    }
}
