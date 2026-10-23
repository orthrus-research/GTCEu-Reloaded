package gregtech.api.recipes.modifiers;

import gregtech.api.recipes.Recipe;

import org.jetbrains.annotations.Nullable;

/** One private adjusted recipe, or bounded failure provenance. Exception messages are never sent to clients. */
public final class RecipeModifierResult {

    public enum Status {
        SUCCESS,
        CANCELLED,
        INVALID,
        UNSUPPORTED
    }

    private final Status status;
    private final Recipe recipe;
    private final String modifierId;
    private final int index;
    private final String reasonKey;

    private RecipeModifierResult(Status status, Recipe recipe, String modifierId, int index, String reasonKey) {
        this.status = status;
        this.recipe = recipe;
        this.modifierId = modifierId;
        this.index = index;
        this.reasonKey = reasonKey;
    }

    static RecipeModifierResult success(Recipe recipe) {
        return new RecipeModifierResult(Status.SUCCESS, recipe, "gregtech:modifiers", -1, "");
    }

    static RecipeModifierResult failure(Status status, String id, int index, String key) {
        return new RecipeModifierResult(status, null, id, index, key);
    }

    public Status status() {
        return status;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public @Nullable Recipe recipe() {
        return recipe;
    }

    public String modifierId() {
        return modifierId;
    }

    public int index() {
        return index;
    }

    public String reasonKey() {
        return reasonKey;
    }
}
