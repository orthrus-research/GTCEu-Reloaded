# 3.0 alpha pre-delivery qualification

Candidate: `gtceu-reloaded-3.0.0-reloaded.1-alpha.1.jar`, 14,669,670 bytes.
SHA-256: `d1c67a1a93fafc46f21e231c2d6911a04b7444eaf64185fb5ba1aa5753d6b447`.
Qualification date: November 6, 2026. Branch: `ct-overhaul`.

This receipt covers Reloaded's supported native Java/Groovy authoring and representative gameplay
on the runtime below. It does not qualify an existing third-party pack, old addon binaries or
legacy-world import. Those are optional services. CCL replacement and quest database ownership
are outside this release.

**Pre-delivery qualification is complete for this candidate and documented scope.** Gates 1–7 pass;
gate 8 is deliberately not run. Publication has not occurred. The frozen source checkpoint identifies the
candidate used for these tests. A build from the published source must be compared with the
qualified artifact before reusing this receipt.

## Reproducible packaging

The functional matrix below was run against the preceding artifact, SHA-256
`d6213db753c85e5b9e8194c75a4e369b7cc9cbaf5d5ce2c27cade40d49ebfff8`.
The current build normalizes packaged text to LF so checkout line endings cannot change the
production digest. An archive comparison verifies identical entry names and all 2,224 compiled
classes; the only differences are CRLF-to-LF normalization in 1,605 text resources. Binary
assets and resource content are unchanged.

The current artifact passes the complete build, tests and formatting checks, and a clean source
export reproduces its exact bytes without private build scripts or cached build outputs. Fresh
Cleanroom client and dedicated-server smoke runs both load a world, save and exit successfully.
These checks qualify the packaging change. The earlier functional, multiplayer and performance
matrix is retained as evidence for the unchanged implementation; it was not rerun or relabeled
as execution against the new digest. Hosted CI remains pending.

## Supported runtime

| Component | Qualified identity |
| --- | --- |
| Minecraft / loader | 1.12.2 / Cleanroom 0.7.14-alpha |
| Java | Temurin 26.0.2.1+1; product classfile version 70 |
| CodeChickenLib | 3.3.10, Modrinth release `CBqIvJQ2`; SHA-256 `bd27374119e728569d6b8e7f493db70869cf8f915f10089b421df76b6a349b34` |
| ModularUI | 3.1.6; runtime SHA-256 `4c16829c70b322aab735e73f53608fcabbf1d14956d5365c0c2b911c4d4dca54` |
| Groovy authoring | GroovyScript 1.5.0; SHA-256 `9f7edb9f4b668059b7517afc0cd872a65ea9014772862d9110347331e25b63c4`; absent in Java-only qualification |
| HEI reload | Repaired 4.36.1; SHA-256 `f75d36d8d0050c7a8b275aed85440b8e94bbdd34a8aed9547b08cbbaf63aeaf4` |
| Client support dependencies | Fugue 0.25.5 and Scalar Legacy artifact 1.0.1; complete mod/library hashes retained in each run |

Unpatched HEI 4.36.1 is not qualified for asynchronous Groovy reload. The repair is reproducible
from [retained source, tests and packaging instructions](../tools/dependency-patches/hei-async/README.md).
The Java 26 build reproduced the previously tested repaired JAR byte for byte. Its four concurrency
tests pass. The dependency patch retains upstream classfile compatibility; Reloaded itself targets
Java 26. Choosing a maintained public download for those exact bytes is part of delivery.

## Changes closing the previous gaps

The build previously compiled against CCL 3.2.3.358 while native tests loaded 3.3.10. Compilation,
tests, loader metadata and production qualification now select the same pinned release. The build
checks the production hash and classpath selection; the native launcher checks installed bytes.
Six negative baseline checks reject changed CCL/MUI/candidate hashes and wrong CCL/Java/loader
versions before starting Minecraft.

The combined inventory handler's bounds guard used `&&`, making rejection impossible. Invalid
indexes could dereference a missing backing handler. It now uses `||` and preserves the normal
empty-read/rejected-insertion behavior. Four regression cases fail against the original class and
pass against the fixed class, including empty handlers, negative indexes and oversized indexes.
Valid cross-handler access remains covered.

Only `GregTechMod.class` and `ItemHandlerList.class` differ from the preceding Java 26 candidate
`03444a28e21daa5a5c8b1f908a04d292ad001ec2d4aaaf02e9a4f3cd372a7bd5`.
No recipe hot-path class changed. The functional qualification below uses that artifact; the
packaging follow-up above records the current product's equivalence and fresh checks separately.

## Capability accounting

The [CT responsibility inventory](ct-migration.md) maps every removed adapter to a native API,
shared definition contract or an explicitly removed engine-specific wrapper. Immutable descriptors
are not mandatory wrappers around every builder. Existing native material/machine builders remain
supported Java APIs, and Groovy invokes those same APIs. No per-mod registration allowlist is needed.

| Capability | Current evidence |
| --- | --- |
| Materials, elements, items, fluids, blocks and coils | Builder/registry tests plus native material, custom item/fluid/block identity and coil declarations; generated content survives copied-world restart |
| New providers and secondary indexes | Separate Java-only provider and Groovy fixtures publish to an unknown recipe-map subclass through shared registration state; rejected grouped changes publish neither map |
| Create/delete/overwrite/disable/re-enable | Native and typed registration/removal tests; integrated reload checks mixed ownership, restoration and deliberate failure recovery |
| Ingredients and native processing | Matching/NBT/capability/alternative tests; real macerator and formed EBF consume inputs and energy and produce expected outputs |
| Crafting and furnace behavior | Native slot crafting verifies empty shaped cells, custom matching and consumption; a ticking furnace verifies fuel, input/output, XP and disable/re-enable |
| Research, expansion and recycling | Native research side-index publication/removal, owned cutter expansion and recovery checks; transaction and dependency-graph tests cover replacement/rejection |
| Pack helpers | Native coil, preview, tool repair and declaration contracts replace CT wrappers; cape/wall APIs retain their existing native owners |
| Machines, transport and saves | Persisted machine inventory, NBT, fluids, energy, settings and identities; item/fluid/energy/optical/laser transport; pipe and cover state across dimensions |
| UI, networking and players | Actual rendered screens and native widget packets; separate clients, server restart and player inventory/NBT restoration |

This is representative native qualification plus contract tests, not exhaustive testing of every
machine, optional integration, screen or possible pack script. Scripts may use explicit custom
predicates/remainders; ordinary prevalidated recipe matching executes native descriptors.

## Gate receipt

| Gate | Result | Evidence |
| --- | --- | --- |
| 1. Capability completeness | PASS for the documented release scope | Responsibility mapping above; native and typed APIs retained without CT or quest resolvers |
| 2. Build and artifact | PASS | 1,149 tests in 196 suites, zero failures/errors/skips; formatting, definition boundary, mixin mappings, publication metadata, baseline checks and reobfuscation pass; 2,224 product classes target Java 26 |
| 3. Native registration | PASS | Final runs 301/302: ten core assertion groups and separate engine-absent Java provider |
| 4. Lifecycle | PASS | 305: five integrated reload commands and 14 checks; 306: six HEI reloads, ingredient visibility and rapid rebuild, 46 checks, async enabled |
| 5. Persistence | PASS | 303 to 304: current-version custom content/machine/two-dimension pipe state; 309 to 316: UI state; 310 to 313: both player identities and populated inventories/NBT before fixture mutation |
| 6. Gameplay/networking | PASS | 301: crafting, formed EBF and furnace; 307: native transport families; 308: 33 inventory/MUI checks; 309/316: 19/13 rendered UI checks; 310–315: two simultaneous clients and dedicated restart; 350/351: actual remote UI updates/reconnect, 32 checks and two OS-observed TCP connections |
| 7. Performance and cache | PASS within the declared measurement scope | Native recipe/index cost and allocation meet the predeclared 20% process-median tolerance; timed-warmup investigation resolves the unstable cell; all 11 final cache runs execute the expected code |
| 8. Delivery | NOT_RUN | Local prerelease assembled; release publication and repaired dependency distribution remain pending |

Every accepted native run requires the intended assertion markers, verified input identities,
no unexpected loader/script errors, and actual process exit zero. Negative reload cases allow only
their specified injected diagnostic and must verify recovery. World readers operate on verified
copies; original writer worlds remain unchanged. Diagnostic probes and providers are separate JARs
and are excluded from the product.

The remote UI pair records 17 TCP observations across two connections, matching both owned JVM
identities. Eleven rendered screenshots across writer, copied-world reader and remote reconnect
were reviewed. The complete final matrix contains 41 successful native processes: 35 using the
final product and six baseline performance processes. All exited with code zero.

## Performance evidence

Recipe construction, shared-input hits/misses and removal/re-registration were measured with
1, 1,000 and 10,000 entries. Each comparison uses three independent processes per arm in balanced
ABBAAB order, identical heap/GC/runtime and a separate test provider. Allocation counters pass an
escaping 80-byte calibration. The longest matrix passes 11 of 12 cases directly; the largest
positive median time difference in those cases is 17.1%, within the predeclared 20% tolerance.

The one-entry hit initially exceeded the tolerance while getting markedly faster within each
process. Those unfavorable results are retained. A separately declared investigation uses the
same measurement method for 10 seconds of warmup and 20 half-second samples. All six processes
pass the first-half/second-half stability check. Its candidate/baseline median is +1.7% time and
+9.0% allocation. Baseline process medians range from 172.3–180.6 ns/op; candidate medians range
from 176.7–217.9 ns/op. This meets the chosen median gate; it is not a claim that every process is
faster or that all JIT variability has disappeared.

The cache matrix covers three cold/warm pairs plus same-timestamp edits, older-timestamp edits,
helper changes, dependency changes and corrupted cached classes. All invalidation cases execute
the expected new code. Cold compilation takes 1,075–1,214 ms; all three warm runs report 0 ms.
Median script execution is 471 ms cold versus 563 ms warm. Median driver wall time is 16.61 s
cold versus 16.82 s warm, including launch/collection overhead and a warm OS file cache. No general
startup-speed improvement is claimed. Script allocation and whole-game FPS/TPS were not measured.

The raw initial, longer-operation and timed-warmup samples, process ranges, exact inputs and
analysis are retained in the local performance receipt. Repeated reload separately verifies
stable owned state and category counts; compilation avoidance alone does not establish that.

## Limits and delivery handoff

- Dedicated-server script hot reload is not supported by GroovyScript 1.5.0. Startup authoring and
  integrated reload are qualified. Structural content changes require restart.
- Prepared recipe groups are transactional; a whole script and arbitrary external side effects are
  not. The documented partial-publication behavior after a script exception remains explicit.
- Network evidence uses separate JVMs over loopback, including two simultaneous players. It does
  not certify WAN loss/latency, online authentication, large-server load or full-pack compatibility.
- UI controls are exercised through native focus/widget callbacks and real networking, with rendered
  screenshots reviewed. This is not a physical mouse/keyboard or human playthrough acceptance claim.
- The performance fixture measures native recipe/index operations and allocation. It does not
  establish an FPS/TPS improvement, a retained-heap reduction, or a Java 8 versus Java 26 speedup.
- GroovyScript 1.5.0's two-argument `GroovyFile.setText(String,String)` has the previously captured
  upstream argument behavior. The qualified fixture uses its working single-argument setter.
- Gradle reports that deobfuscation attributes cannot be represented in a Maven POM. Gradle module
  metadata retains them; the supported development lane is CleanroomGradle. Plain Maven and old
  Gradle consumption are not qualified. Hosted CI has not run for this local source state.

Delivery must use the frozen qualified bytes, include the supported-runtime identities, migration
guide and known limits, and make the exact repaired HEI dependency available when advertising that
integration. A rebuilt or altered artifact must be compared and requalified for affected behavior.
Optional addon ports, old saves, Supersymmetry pack migration and the OrthLib replacement are not
remaining requirements for this release.

Raw evidence, commands, source/probe checkpoints, failed attempts and test XML are retained
with the qualification archive. They are not included in the source repository.


## Supersymmetry compatibility trial

A separate trial against the retained Supersymmetry 0.2.0.1-patch2 snapshot does not qualify it
as a supported pack. Original addon binaries fail on removed CraftTweaker APIs. With selected
addon and script ports, the dedicated server creates, ticks and saves a fresh RTG world, but
the client remains blocked by SussyPatches' old multiblock-preview injections. That addon needs
to use Reloaded's native block set and current renderer method signatures. Recipe-removal and
content-registration diagnostics also remain. Pack migration, client gameplay and existing-save
acceptance are separate work; the original pack files were preserved.
