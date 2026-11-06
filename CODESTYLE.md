# Code style

Follow `.editorconfig` and the Spotless configuration. IntelliJ IDEA's inspections can help
identify unused declarations and formatting issues.

## Naming

- Use `UpperCamelCase` for Java classes and `lowerCamelCase` for fields and methods.
- Use `lower_camel_case` for registry entries, string identifiers and keys.

## Class structure

- Remove unused code and redundant modifiers, such as `public` on interface methods.
- Declare fields before methods.
- Use `this.someField` when assigning a field.
- Split complex logic into focused methods and separate logical steps with blank lines.
- Keep at most one nested class per class; place additional classes in their own files.

## Minecraft conventions

- Use tickable objects and tile entities only when their lifecycle is required.
- Prefer capability interfaces for APIs that expose optional behavior, with implementations
  in separate classes.
- Use `GTLog.logger` for logging.
- Reuse mutable block positions in repeated lookups where their ownership is clear.
- Prefer the widget system for interfaces over direct drawing.

Run `./gradlew spotlessApply` before submitting a change, then run the checks in
[CONTRIBUTING.md](CONTRIBUTING.md).
