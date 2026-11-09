# 3.0 alpha validation

Artifact: `gtceu-reloaded-3.0.0-reloaded.1-alpha.1.jar`, 14,669,670 bytes.
SHA-256: `d1c67a1a93fafc46f21e231c2d6911a04b7444eaf64185fb5ba1aa5753d6b447`.
Validated November 6, 2026.

Local validation is complete for the scope below. Publication and hosted CI remain pending.
This report covers Reloaded's native Java/Groovy authoring and representative gameplay;
it does not establish compatibility with every GTCEu addon, existing pack or legacy save.

## Runtime requirements

| Component | Tested version or artifact |
| --- | --- |
| Minecraft / loader | 1.12.2 / Cleanroom 0.7.14-alpha |
| Java | Temurin 26.0.2.1+1; Java 26 product bytecode |
| CodeChickenLib | 3.3.10, Modrinth release `CBqIvJQ2`; SHA-256 `bd27374119e728569d6b8e7f493db70869cf8f915f10089b421df76b6a349b34` |
| ModularUI | 3.1.6; SHA-256 `4c16829c70b322aab735e73f53608fcabbf1d14956d5365c0c2b911c4d4dca54` |
| Groovy authoring | GroovyScript 1.5.0; SHA-256 `9f7edb9f4b668059b7517afc0cd872a65ea9014772862d9110347331e25b63c4`; optional for Java-only consumers |
| HEI reload | Repaired 4.36.1; SHA-256 `af38fd9a883f629a4be16a3eac14b95ec5d9b3bb91802b994b8cc7ae19361d6d` |
| Client support | Fugue 0.25.5 and Scalar Legacy artifact 1.0.1 |

Unpatched HEI 4.36.1 can stall during asynchronous Groovy reload. Use the exact repaired
dependency described in the [source and packaging instructions](../tools/dependency-patches/hei-async/README.md).
The repair retains upstream bytecode compatibility and is built separately from Reloaded.
Its distribution must accompany any release advertising asynchronous HEI reload support.

## Validation coverage

| Area | Result | Coverage |
| --- | --- | --- |
| Authoring capabilities | Pass | The [migration guide](ct-migration.md) maps CT responsibilities to native Java/Groovy APIs and identifies removed engine-specific wrappers. |
| Build and artifact | Pass | Full tests, formatting, definition boundaries, mixin mappings, publication metadata, dependency identity and reobfuscation; clean-source artifact reproduction. |
| Registration | Pass | Java-only and Groovy providers, generated content, custom map subclasses, secondary indexes and rejected grouped changes. |
| Recipe lifecycle | Pass | Create, remove, replace, disable and re-enable; mixed imperative/typed ownership; repeated integrated reload and recovery after script errors. |
| Persistence | Pass | Current-version content identities, machine inventory/NBT, fluids, energy, settings, pipe/cover state across dimensions, and player data after save/reopen. |
| Gameplay and networking | Pass | Representative machine processing, crafting and furnace behavior; native transport; rendered UI updates, separate clients, reconnect and dedicated-server restart. |
| Performance and cache | Pass within the measured scope | Recipe/index operations meet the declared median tolerance after stable warmup; cache reuse and invalidation execute the expected scripts. See limitations below. |
| Delivery | Pending | Local prerelease assembled; hosted CI, publication and repaired dependency distribution remain outstanding. |

This is representative coverage, not exhaustive testing of every machine, optional integration,
screen or script. Test providers are separate artifacts and are excluded from the mod JAR.

## Artifact equivalence

The functional, persistence, multiplayer and performance checks used the preceding artifact:
`d6213db753c85e5b9e8194c75a4e369b7cc9cbaf5d5ce2c27cade40d49ebfff8`.
The current build normalizes packaged text to LF for reproducible archives. Entry names,
compiled classes and binary assets are identical; changed entries differ only in line endings.

A clean source build reproduces the current digest. Fresh client and dedicated-server smoke
checks load a world, save and exit successfully. These checks cover the packaging change;
the earlier functional matrix remains evidence for the unchanged implementation and was not
rerun against the new digest. Compare any rebuilt artifact before carrying this validation forward.

## Performance and cache limits

Recipe construction, shared-input lookup and removal/re-registration were compared at 1, 1,000
and 10,000 entries using independent processes with matched runtime, heap and GC settings.
Acceptance used a predeclared 20% process-median tolerance. Eleven of twelve cases passed the
longer measurement directly. The one-entry hit exceeded that tolerance while still warming up;
a separate run with stable, longer warmup measured +1.7% median time and +9.0% allocation.
That result meets the median criterion but does not eliminate process-to-process JIT variability.

Cache checks cover cold/warm launches, same- and older-timestamp edits, helper and dependency
changes, and corrupt compiled files. Warm runs avoided script compilation, but total measured
startup time did not improve. These results do not establish an FPS/TPS gain, lower retained heap,
or a Java 8 versus Java 26 speedup. Script allocation was not measured.

## Operational limits

- GroovyScript 1.5.0 supports startup authoring and integrated-server reload. Dedicated-server
  script hot reload is unsupported; structural content changes require restart.
- Prepared recipe groups are transactional. Whole scripts and arbitrary external side effects
  are not; a later script error can leave earlier successful edits active.
- Network tests use separate JVMs over loopback, including simultaneous players. WAN conditions,
  online authentication and large-server load are outside this coverage.
- UI coverage uses automated native widget interactions and rendered screenshots. Manual
  playtesting remains separate.
- For script-file edits, use `GroovyFile.setText(String)`. The 1.5.0 two-argument overload did not
  provide the expected text/encoding behavior in the tested runtime.
- Use CleanroomGradle for addon development. Maven POMs cannot represent the deobfuscation
  attributes retained by Gradle module metadata; plain Maven and older Gradle are unqualified.

## Supersymmetry compatibility

Supersymmetry 0.2.0.1-patch2 is not currently a supported replacement target. Original addon
binaries reference removed CraftTweaker APIs. With selected addon and script ports, a dedicated
server creates, ticks and saves a fresh RTG world. The client still requires a SussyPatches
preview-renderer port, and recipe-removal/content-registration diagnostics remain unresolved.
Client gameplay and existing-save migration therefore remain unqualified for this pack.
