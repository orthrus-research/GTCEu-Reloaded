package gregtech.api.recipes.definition;

import gregtech.api.definition.SourceOrigin;

import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Row-major inputs; null cells intentionally represent empty positions. */
public record ShapedCraftingDefinition(SourceOrigin origin, ResourceLocation name, String group, ItemValue output,
                                       int width, int height, boolean mirrored, List<ItemInput> inputs)
        implements CraftingDefinition {

    public ShapedCraftingDefinition {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(output, "output");
        inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
    }
}
