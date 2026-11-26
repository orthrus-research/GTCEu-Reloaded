package gregtech.api.capability.impl;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.modifiers.RecipeModifierAttempt;
import gregtech.api.recipes.modifiers.RecipeModifierResult;

import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * The recipe modifier attempt a logic has open: the attempt, the inventories it was opened for, and the metadata of
 * the recipe last appended in parallel under it. At most one attempt is open, for the length of one search or
 * preparation.
 * <p>
 * It holds the scope only. {@link AbstractRecipeLogic} resolves the machine's modifiers, opens the attempt through
 * {@code withRecipeModifiers} and reports what goes wrong.
 */
final class RecipeModifierScope {

    private RecipeModifierAttempt attempt;
    private IItemHandlerModifiable inputs;
    private IMultipleTankHandler fluids;
    private Recipe appendMetadata;

    boolean isOpen() {
        return attempt != null;
    }

    /** Only while open: whether the attempt is adjusting a recipe now, so a nested adjustment would re-enter it. */
    boolean isEvaluating() {
        return attempt.isEvaluating();
    }

    /** Only while open: the result that refuses a nested adjustment. */
    RecipeModifierResult rejectReentry() {
        return attempt.rejectReentry();
    }

    /** @return whether the attempt was opened for exactly these inventories */
    boolean isOpenFor(IItemHandlerModifiable inputs, IMultipleTankHandler fluids) {
        return this.inputs == inputs && this.fluids == fluids;
    }

    /** @return the items the open attempt reads, or null when none is open */
    IItemHandlerModifiable getInputs() {
        return inputs;
    }

    /** @return the fluids the open attempt reads, or null when none is open */
    IMultipleTankHandler getFluids() {
        return fluids;
    }

    /** Only while open: adjusts a recipe through the attempt's modifiers. */
    RecipeModifierResult adjust(Recipe source) {
        return attempt.adjust(source);
    }

    /** Opens the attempt for the inventories, runs the operation once, and closes it again however it ends. */
    boolean run(RecipeModifierAttempt attempt, IItemHandlerModifiable inputs, IMultipleTankHandler fluids,
                BooleanSupplier operation) {
        this.attempt = attempt;
        this.inputs = inputs;
        this.fluids = fluids;
        try {
            return operation.getAsBoolean();
        } finally {
            this.attempt = null;
            this.inputs = null;
            this.fluids = null;
        }
    }

    /** @return the metadata kept from the recipe last appended in parallel, or null */
    @Nullable
    Recipe getAppendMetadata() {
        return appendMetadata;
    }

    void setAppendMetadata(@Nullable Recipe metadata) {
        this.appendMetadata = metadata;
    }
}
