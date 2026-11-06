package gregtech.api.recipes;

import gregtech.api.definition.DefinitionDiagnostic;
import gregtech.api.definition.SourceOrigin;

/** A failed detached index preparation; no registry has been published. */
public final class RecipeRegistrationException extends IllegalStateException {

    private final DefinitionDiagnostic diagnostic;

    RecipeRegistrationException(String code, SourceOrigin origin, String message) {
        this(new DefinitionDiagnostic(code, DefinitionDiagnostic.Severity.ERROR, origin, "registration", message));
    }

    RecipeRegistrationException(DefinitionDiagnostic diagnostic) {
        super(diagnostic.format());
        this.diagnostic = diagnostic;
    }

    public DefinitionDiagnostic diagnostic() {
        return diagnostic;
    }
}
