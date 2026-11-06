package gregtech.api.recipes.definition;

import gregtech.api.definition.SourceOrigin;

import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Each input occupies one slot; overlapping alternatives are resolved by native matching. */
public record ShapelessCraftingDefinition(SourceOrigin origin, ResourceLocation name, String group, ItemValue output,
                                          List<ItemInput> inputs)
        implements CraftingDefinition {

    public ShapelessCraftingDefinition {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(output, "output");
        inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
    }
}
