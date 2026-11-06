package gregtech.api.definition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A failed preparation never exposes a partially prepared value. */
public record DefinitionResult<T>(Optional<T> value, List<DefinitionDiagnostic> diagnostics) {

    public DefinitionResult {
        Objects.requireNonNull(value, "value");
        diagnostics = List.copyOf(diagnostics);
        boolean errors = diagnostics.stream().anyMatch(d -> d.severity() == DefinitionDiagnostic.Severity.ERROR);
        if (value.isPresent() == errors) throw new IllegalArgumentException("Value and diagnostics disagree");
    }

    public static <T> DefinitionResult<T> success(T value) {
        return new DefinitionResult<>(Optional.of(value), List.of());
    }

    public static <T> DefinitionResult<T> failure(List<DefinitionDiagnostic> diagnostics) {
        return new DefinitionResult<>(Optional.empty(), diagnostics);
    }
}
