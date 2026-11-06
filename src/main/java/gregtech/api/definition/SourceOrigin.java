package gregtech.api.definition;

import java.util.Objects;

/** Authoring provenance. Zero line/column means unknown; positive coordinates are one-based. */
public record SourceOrigin(String frontend, String source, int line, int column, String declaration,
                           String stage) {

    public SourceOrigin {
        Objects.requireNonNull(frontend, "frontend");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(declaration, "declaration");
        Objects.requireNonNull(stage, "stage");
        if (line < 0 || column < 0) throw new IllegalArgumentException("Negative source coordinate");
    }

    public String location() {
        return source + (line == 0 ? "" : ":" + line + (column == 0 ? "" : ":" + column));
    }
}
