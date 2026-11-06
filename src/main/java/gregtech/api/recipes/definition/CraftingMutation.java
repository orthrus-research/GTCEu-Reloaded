package gregtech.api.recipes.definition;

import gregtech.api.definition.SourceOrigin;

import net.minecraft.util.ResourceLocation;

/** A named creation/replacement or an explicit disable operation. */
public sealed interface CraftingMutation permits CraftingDefinition, DisabledCraftingDefinition,
                                         CustomCraftingDefinition {

    SourceOrigin origin();

    ResourceLocation name();
}
