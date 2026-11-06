package gregtech.api.recipes;

import gregtech.api.definition.SourceOrigin;

/** Retains the declaration responsible for a conflict inside a multi-declaration registration group. */
final class FurnaceOwnershipException extends IllegalStateException {

    private final SourceOrigin origin;

    FurnaceOwnershipException(SourceOrigin origin, RuntimeException cause) {
        super(cause.getMessage(), cause);
        this.origin = origin;
    }

    SourceOrigin origin() {
        return origin;
    }
}
