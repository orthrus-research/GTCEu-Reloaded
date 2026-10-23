package gregtech.api.recipes.conditions;

/** Missing evidence is never made valid by inversion. */
public enum ConditionState {

    PASS,
    FAIL,
    UNAVAILABLE;

    public ConditionState inverted(boolean inverted) {
        if (!inverted || this == UNAVAILABLE) return this;
        return this == PASS ? FAIL : PASS;
    }
}
