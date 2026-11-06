package gregtech.api.recipes.fixtures;

/** Deliberately invalid extension used to verify the declaration boundary. */
public final class InvalidAuthoringCopyBuilder extends AuthoringTestBuilder {

    private final boolean returnsSelf;

    public InvalidAuthoringCopyBuilder(boolean returnsSelf) {
        this.returnsSelf = returnsSelf;
    }

    @Override
    public AuthoringTestBuilder copy() {
        return returnsSelf ? this : super.copy();
    }
}
