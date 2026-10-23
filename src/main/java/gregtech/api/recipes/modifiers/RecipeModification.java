package gregtech.api.recipes.modifiers;

import gregtech.api.recipes.conditions.RecipeConditionSet;

import java.util.Objects;

/** Immutable numeric changes and additional AND requirements; no mutable recipe is given to a callback. */
public final class RecipeModification {

    public static final RecipeModification IDENTITY = new Builder().build();
    private final RecipeAmountModifier energy;
    private final RecipeAmountModifier duration;
    private final RecipeAmountModifier itemInputs;
    private final RecipeAmountModifier fluidInputs;
    private final RecipeAmountModifier itemOutputs;
    private final RecipeAmountModifier fluidOutputs;
    private final RecipeConditionSet conditions;
    private final String cancellationKey;

    private RecipeModification(Builder builder, String cancellationKey) {
        this.energy = builder.energy;
        this.duration = builder.duration;
        this.itemInputs = builder.itemInputs;
        this.fluidInputs = builder.fluidInputs;
        this.itemOutputs = builder.itemOutputs;
        this.fluidOutputs = builder.fluidOutputs;
        this.conditions = builder.conditions;
        this.cancellationKey = cancellationKey;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The declared localization key is display-only and never selects executable behavior. */
    public static RecipeModification cancel(String reasonKey) {
        if (reasonKey == null || reasonKey.isEmpty() || reasonKey.length() > 128 ||
                !reasonKey.matches("[a-zA-Z0-9_.-]+"))
            throw new IllegalArgumentException("Invalid modifier cancellation key");
        return new RecipeModification(new Builder(), reasonKey);
    }

    public boolean isIdentity() {
        return cancellationKey == null && energy.isIdentity() && duration.isIdentity() && itemInputs.isIdentity() &&
                fluidInputs.isIdentity() && itemOutputs.isIdentity() && fluidOutputs.isIdentity() &&
                conditions.isEmpty();
    }

    public String cancellationKey() {
        return cancellationKey;
    }

    RecipeAmountModifier energy() {
        return energy;
    }

    RecipeAmountModifier duration() {
        return duration;
    }

    RecipeAmountModifier itemInputs() {
        return itemInputs;
    }

    RecipeAmountModifier fluidInputs() {
        return fluidInputs;
    }

    RecipeAmountModifier itemOutputs() {
        return itemOutputs;
    }

    RecipeAmountModifier fluidOutputs() {
        return fluidOutputs;
    }

    RecipeConditionSet conditions() {
        return conditions;
    }

    public static final class Builder {

        private RecipeAmountModifier energy = RecipeAmountModifier.IDENTITY;
        private RecipeAmountModifier duration = RecipeAmountModifier.IDENTITY;
        private RecipeAmountModifier itemInputs = RecipeAmountModifier.IDENTITY;
        private RecipeAmountModifier fluidInputs = RecipeAmountModifier.IDENTITY;
        private RecipeAmountModifier itemOutputs = RecipeAmountModifier.IDENTITY;
        private RecipeAmountModifier fluidOutputs = RecipeAmountModifier.IDENTITY;
        private RecipeConditionSet conditions = RecipeConditionSet.EMPTY;

        public Builder energy(double multiplier, double addition) {
            this.energy = RecipeAmountModifier.of(multiplier, addition);
            return this;
        }

        public Builder duration(double multiplier, double addition) {
            this.duration = RecipeAmountModifier.of(multiplier, addition);
            return this;
        }

        public Builder itemInputs(double multiplier, double addition) {
            this.itemInputs = RecipeAmountModifier.of(multiplier, addition);
            return this;
        }

        public Builder fluidInputs(double multiplier, double addition) {
            this.fluidInputs = RecipeAmountModifier.of(multiplier, addition);
            return this;
        }

        public Builder itemOutputs(double multiplier, double addition) {
            this.itemOutputs = RecipeAmountModifier.of(multiplier, addition);
            return this;
        }

        public Builder fluidOutputs(double multiplier, double addition) {
            this.fluidOutputs = RecipeAmountModifier.of(multiplier, addition);
            return this;
        }

        public Builder conditions(RecipeConditionSet requirements) {
            this.conditions = Objects.requireNonNull(requirements);
            return this;
        }

        public RecipeModification build() {
            return new RecipeModification(this, null);
        }
    }
}
