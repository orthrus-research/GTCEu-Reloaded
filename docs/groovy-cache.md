# Groovy cache content validation

Reloaded's GroovyScript 1.5.0 integration checks content identity before reusing compiled scripts.
The guard covers the script tree (including helpers and run configuration), ordered available
classpath/mod JAR contents, the Java/Groovy runtime, physical side and relevant compiler settings.
Absolute installation paths and modification times are not cache identities. Both manual and generated
side-only configuration participate because they change transformed method visibility.

File sources and local JAR-entry URLs are resolved to their actual dependency bytes; loose class
directories also participate. Absent file placeholders are recorded as missing; creating the file
invalidates their identity. If a loader reports an invalid or unsupported source, the guard
logs the cause and uses a fresh session identity. Scripts still compile and execute, but persisted
bytecode cannot be reused across launches until that dependency source is fixed. Unknown sources
are never silently omitted from the cache identity.
Forge's known `asmgen:/` source is handled by hashing the generated transformer-wrapper bytes and
the active transformer order, alongside the generator and parent dependencies.

A cache manifest also hashes the engine's index and compiled files. Missing, old, corrupt or
mismatched manifests cause the engine to discard its compiled cache. The guard does not delete
authoring sources. The manifest is written atomically where the filesystem supports it, after
the engine writes its index; a source change during compilation prevents sealing that result.

Before each authoring stage, source content is checked again. Any changed source invalidates the
engine's compiled classes together, which also invalidates helper dependents. This favors correctness
over incremental recompilation of individual scripts. Unchanged verified content remains eligible
for the engine's normal cache reuse. No hashing or scripting work runs in recipe matching or ticks.

Dependency identity is captured once per engine lifetime: changing installed mod JARs requires a
restart. Structural preInit declarations also require a restart, even when their changed source can
be recompiled. Runtime values retained by arbitrary script closures are outside cache validity.
Custom compilation transforms that read undeclared external files must arrange their own input
invalidation; the guard cannot infer arbitrary I/O dependencies. This is not a security signature.

Tests cover same/older timestamps, helper edits/additions/deletion, dependency byte/order changes,
compiler settings, rollback, relocation, corrupt compiled files and incomplete manifests.
[Release validation](pre-delivery-qualification.md) records native cache reuse and measured
compilation cost. Pack-specific startup performance requires separate measurement.

Cached helper loading normalizes binary class names and resolves secondary classes to their actual source
file. A warm cache must execute helper-dependent scripts successfully; an index hit alone is insufficient.
