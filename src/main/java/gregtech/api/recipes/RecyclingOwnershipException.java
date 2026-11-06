package gregtech.api.recipes;

import gregtech.api.definition.SourceOrigin;

/** Identifies the contribution responsible for a rejected recycling registration. */
public final class RecyclingOwnershipException extends IllegalStateException {

    private final SourceOrigin origin;

    RecyclingOwnershipException(SourceOrigin origin, String message) {
        super(message);
        this.origin = origin;
    }

    public SourceOrigin origin() {
        return origin;
    }
}
