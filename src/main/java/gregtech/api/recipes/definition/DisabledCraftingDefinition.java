package gregtech.api.recipes.definition;

import gregtech.api.definition.SourceOrigin;

import net.minecraft.util.ResourceLocation;

import java.util.Objects;

/** Disables an existing canonical name; an absent name is an intentional no-op. */
public record DisabledCraftingDefinition(SourceOrigin origin, ResourceLocation name) implements CraftingMutation {

    public DisabledCraftingDefinition {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(name, "name");
    }
}
