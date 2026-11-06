package gregtech.api.definition;

import java.util.Objects;

/** Stable machine-readable code and field path, with a frontend-independent message and source. */
public record DefinitionDiagnostic(String code, Severity severity, SourceOrigin origin, String field,
                                   String message) {

    public DefinitionDiagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(message, "message");
    }

    public enum Severity {
        WARNING,
        ERROR
    }

    public String format() {
        return origin.location() + " [" + code + "] " + origin.declaration() + "." + field + ": " + message;
    }
}
