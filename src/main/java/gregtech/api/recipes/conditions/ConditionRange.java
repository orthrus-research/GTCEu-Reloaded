package gregtech.api.recipes.conditions;

/** Immutable integer endpoints; the condition adapter defines inclusive or half-open semantics. */
public final class ConditionRange {

    private final int minimum;
    private final int maximum;

    public ConditionRange(int minimum, int maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public int minimum() {
        return minimum;
    }

    public int maximum() {
        return maximum;
    }
}
