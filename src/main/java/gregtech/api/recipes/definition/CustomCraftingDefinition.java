package gregtech.api.recipes.definition;

import gregtech.api.definition.SourceOrigin;

import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Explicit native extension for behavior not expressible by built-in descriptors. The preparation-safe factory
 * must return a detached recipe with this exact name and must not mutate registries. Its recipe may retain custom
 * matching/remainder behavior; prefer shaped/shapeless descriptors for ordinary inputs. The factory runs only
 * during registration, never during recipe matching. Retaining this exact declaration object in an owned group
 * retains its accepted native recipe. Submit a fresh declaration to run the factory again; removal or disabling
 * discards the retained result, so re-enabling also runs the factory again.
 */
public record CustomCraftingDefinition(SourceOrigin origin, ResourceLocation name, Supplier<? extends IRecipe> factory)
        implements CraftingMutation {

    public CustomCraftingDefinition {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(factory, "factory");
    }
}
