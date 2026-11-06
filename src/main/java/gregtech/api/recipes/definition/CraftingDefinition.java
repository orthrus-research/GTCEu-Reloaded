package gregtech.api.recipes.definition;

import gregtech.api.definition.SourceOrigin;

import net.minecraft.util.ResourceLocation;

import java.util.List;

/** Engine-independent crafting declarations; compilation does not register or claim their names. */
public sealed interface CraftingDefinition extends CraftingMutation
                                           permits ShapedCraftingDefinition, ShapelessCraftingDefinition {

    SourceOrigin origin();

    ResourceLocation name();

    String group();

    ItemValue output();

    List<ItemInput> inputs();
}
