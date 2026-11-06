package gregtech.api.recipes;

import java.util.List;

/** Registration-time input for deriving secondary indexes without modifying live state. */
public record RecipeRegistrationChange(List<Recipe> removed, List<Recipe> added, boolean clear) {

    public RecipeRegistrationChange {
        removed = List.copyOf(removed);
        added = List.copyOf(added);
    }
}
