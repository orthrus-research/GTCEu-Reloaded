package gregtech.api.definition;

/** A rejected material replacement has not published a numeric entry or changed a name lookup. */
public final class MaterialReplacementException extends IllegalStateException {

    private final DefinitionDiagnostic diagnostic;

    public MaterialReplacementException(String code, SourceOrigin origin, String field, String message) {
        this(new DefinitionDiagnostic(code, DefinitionDiagnostic.Severity.ERROR, origin, field, message));
    }

    private MaterialReplacementException(DefinitionDiagnostic diagnostic) {
        super(diagnostic.format());
        this.diagnostic = diagnostic;
    }

    public DefinitionDiagnostic diagnostic() {
        return diagnostic;
    }
}
