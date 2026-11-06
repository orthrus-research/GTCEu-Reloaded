# Reloaded release acceptance

Reloaded is an independent Cleanroom/Java 26 mod. Release readiness is measured against its own
documented APIs and the capabilities required to build and play a GregTech-style pack. Servicing
upstream GTCEu addon binaries, old scripting APIs or existing third-party packs is optional work.
An incompatibility in an unclaimed legacy integration is not a core release blocker.

Optional compatibility does not make missing functionality acceptable. Materials, content, machines,
recipes, research, recycling, scripting and player persistence must work through Reloaded's native
contracts. New content owners must be able to declare their own identities and state without editing
a list of supported mods in Reloaded. Quest database ownership belongs to a separate quest mod.

## Supported release target

The consolidated current evidence is in [pre-delivery qualification](pre-delivery-qualification.md).
It records the tested artifact, supported runtime and known limits. Earlier candidates do not
extend that qualification to different artifacts or integrations.

The current candidate targets Java 26, CodeChickenLib 3.3.10, ModularUI 3.1.6, Cleanroom
0.7.14-alpha and the dependencies pinned in the build. GroovyScript 1.5.0 is required for Groovy authoring, optional for Java-only consumers.
Additional JVM versions require their own evidence. Freeze exact runtime, source, configuration and
artifact hashes for each qualification run. A build or static addon scan is not a gameplay pass.

This release retains CodeChickenLib. The OrthLib replacement handoff is future work and is
not a release gate. The final candidate aligns and verifies its CCL build/runtime baseline;
removing CCL or reducing the JAR count is outside this release's scope.

## Required pack capabilities

| Capability | Native authoring surface | Acceptance condition |
| --- | --- | --- |
| Materials and elements | Material/element builders, properties, flags, qualified registries | Distinct owners register without alias collisions; lifecycle and replacement errors identify the source; declared forms exist after generation. |
| Items, blocks and fluids | OreItemRegistry, OreBlockRegistryPlan, native item/fluid registration | Java/Groovy declarations produce real registered content; names and metadata survive a current-version save/restart. |
| Machines and multiblocks | MTE registries, abilities, patterns, native machine APIs | A new provider registers usable machines without core class-name exceptions; state, inventories, energy and fluids persist. |
| Machine recipes | RecipeDefinitionRegistrar, native builders and RecipeRegistrationState | Java and both Groovy modes publish matching native behavior, including extra indexes; accepted recipes are observable by the intended machine lookup. |
| Recipe mutation | Owned groups and imperative registration/removal | Create/delete/overwrite/disable/re-enable work; failed grouped preparation leaves prior state intact; foreign edits are diagnosed. |
| Ingredient semantics | Item/fluid/ore/material inputs, NBT and capability policies | Alternatives, quantities, retained inputs and late ore membership work; missing names are not silently dropped; empty shaped cells and unmatched removals retain their intended meanings. |
| Crafting and furnaces | Shaped/shapeless/custom definitions, furnace ownership and policies | Predicates, remainders, tools, fluid containers, experience and disabling behave correctly in actual inventories. |
| Research and specialized recipes | Native properties, RecipeExpansion, research descriptors | Derived recipes and secondary indexes follow registration, removal and reload together; rejected changes publish none of the group. |
| Recycling | Native capture, composition ownership and recovery generation | Amounts and material identities are correct; explicit zero recovery and replacement/removal work without duplicate generation. |
| Pack helpers | Coils, previews, tool/element properties, charcoal walls and cape declarations | Their documented functionality is available through Java/Groovy without CT classes. |
| Script lifecycle | Material/preInit/postInit stages and shared Groovy facade | Stage errors have diagnostics; repeated reload does not duplicate state or leak ownership. State clearly what survives a failed whole-script reload. |
| Runtime compilation | Groovy cache/environment identity | Cold/warm loads, modified scripts/helpers, changed dependencies and damaged caches execute the correct code; measure compilation and allocation cost. |
| Player experience | Native UI, HEI integration, networking and saved state | Representative processing and crafting work on client, dedicated server and separate multiplayer clients, including reconnect/restart. |

The responsibility mapping in [ct-migration.md](ct-migration.md) is the starting inventory.
Every missing capability is implementation work, not a request to restore an upstream wrapper.
The typed compiler's explicit builder/effect contracts are declarations of behavior; registration
must not depend on recognizing a mod's name. Arbitrary external side effects cannot be rolled back.

## Release gates and evidence

1. **Completeness:** account for the capability inventory above and all replaced CT responsibilities;
   document intentional removals explicitly. Source/API churn alone is not functional completion.
2. **Build:** full tests, formatting, definition/mixin/publication checks and reobfuscation pass for the
   frozen source. Exclude test probes, old CT dependencies and quest resolvers from the product.
3. **Native registration:** exercise Java and Groovy providers in a fresh, purpose-built core fixture,
   including an unknown map class, native lookup, side indexes and failed grouped publication.
4. **Lifecycle:** exercise actual registration stages and repeated integrated reload with mixed
   imperative/typed edits, explicit disable/re-enable and failure diagnostics.
5. **Persistence:** create, save and reopen populated Reloaded worlds, retaining content identities,
   machine/pipe state, inventories, fluids and player data. Test any upgrade path explicitly claimed
   by this release. Upstream GTCEu save import is an optional, separately specified service.
6. **Gameplay and networking:** complete representative machine/crafting/UI/HEI scenarios on client
   and dedicated server, and synchronization/reconnect with real separate clients.
7. **Performance:** compare pinned baselines and candidate workloads, including unfavorable cases,
   cold/warm startup, repeated reload and large recipe/index sets. Resolve meaningful regressions;
   make no speedup claim without measurements. Set acceptance tolerances before collecting results.
8. **Delivery:** distribute the tested bytes with supported-version metadata, API migration notes,
   known limits and a gate-by-gate evidence receipt. A newer build requires refreshed evidence.

The consolidated receipt records current gate outcomes and their limits. No percentage-complete
estimate substitutes for
the actual registration, lifecycle, persistence, client, multiplayer and performance checks.

## Separate optional services

Upstream addon ports, old-binary bridges, CT script conversion, legacy-world import and compatibility
with a named existing pack each have separate scope and receipts. They do not enter the release gate
merely because their source or JAR was used as a research reference. If advertised as supported in a
release, test that claim; otherwise do not describe its untested behavior as supported.
Existing native migration code can remain useful without promising universal legacy import.
Never silently rewrite an unidentified legacy world or infer a missing content declaration from a
quest reference. Preserve originals in any optional migration experiment.
