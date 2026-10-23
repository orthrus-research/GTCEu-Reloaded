package gregtech.api.recipes.logic;

/** Stable diagnostic identifiers. These describe observed checks, not additional recipe requirements. */
public enum RecipeFailureReason {

    NONE(0, "none"),
    DISABLED(1, "disabled"),
    UNFORMED(2, "unformed"),
    MUFFLER_OBSTRUCTED(3, "muffler"),
    MAINTENANCE(4, "maintenance"),
    NO_RECIPE(5, "no_recipe"),
    REQUIREMENTS(6, "requirements"),
    CLEANROOM_MISSING(7, "cleanroom_missing"),
    CLEANROOM_DIRTY(8, "cleanroom_dirty"),
    CLEANROOM_TYPE(9, "cleanroom_type"),
    DIMENSION(10, "dimension"),
    TEMPERATURE(11, "temperature"),
    VENTING(12, "venting"),
    FUSION_CAPACITY(13, "fusion_capacity"),
    FUSION_START(14, "fusion_start"),
    INPUT_CHANGED(15, "input_changed"),
    ITEM_OUTPUT_FULL(16, "item_output_full"),
    FLUID_OUTPUT_FULL(17, "fluid_output_full"),
    OUTPUT_FULL(18, "output_full"),
    NOT_ENOUGH_ENERGY(19, "energy"),
    NOT_ENOUGH_STEAM(20, "steam"),
    ENERGY_OUTPUT_FULL(21, "energy_output_full"),
    COMPUTATION(22, "computation"),
    SETUP(23, "setup"),
    PROGRESS_REQUIREMENTS(24, "progress_requirements"),
    CONDITION(25, "condition"),
    CONDITION_UNAVAILABLE(26, "condition_unavailable");

    private final int id;
    private final String translationKey;

    RecipeFailureReason(int id, String key) {
        this.id = id;
        this.translationKey = "gregtech.recipe.failure." + key;
    }

    public int getId() {
        return id;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public boolean hasAmounts() {
        return this == TEMPERATURE || this == FUSION_CAPACITY || this == FUSION_START;
    }

    public boolean hasConditions() {
        return this == CONDITION || this == CONDITION_UNAVAILABLE;
    }

    public static RecipeFailureReason fromId(int id) {
        for (RecipeFailureReason reason : values()) {
            if (reason.id == id) return reason;
        }
        return REQUIREMENTS;
    }
}
