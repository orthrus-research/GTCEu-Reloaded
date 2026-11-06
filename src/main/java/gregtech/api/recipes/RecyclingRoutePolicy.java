package gregtech.api.recipes;

/** How multiple declarations for one owned output contribute to its composition. */
public enum RecyclingRoutePolicy {

    /** Every known route must resolve to the same composition. */
    CONSISTENT,
    /** Retain common ingredient identities at their minimum exact quantity per output. */
    MINIMUM_INPUTS,
    /** Discard preceding routes; subsequent minimum-input routes may further constrain this route. */
    REPLACE
}
