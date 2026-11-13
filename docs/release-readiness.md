# Reloaded release acceptance

Release acceptance covers Reloaded's documented APIs and the capabilities needed to build and
play a GregTech-style pack. Compatibility with upstream addon binaries, old scripting APIs and
existing packs is supported only where explicitly documented and tested.

Materials, content, machines, recipes, research, recycling, scripting and player persistence must
work through native contracts. Content providers declare their own identities and registration
state; registration must not depend on a list of recognized mods. Quest databases remain the
responsibility of the quest mod.

## Supported release target

The current results are in [release validation](pre-delivery-qualification.md).
It records the tested artifact, supported runtime and known limits. Earlier candidates do not
extend that qualification to different artifacts or integrations.

The current candidate targets Java 25 (Cleanroom's declared runtime), CodeChickenLib 3.3.10, ModularUI 3.1.6, Cleanroom
0.7.14-alpha and the dependencies pinned in the build. GroovyScript 1.5.0 is required for Groovy
authoring and optional for Java-only consumers. Record runtime, source, configuration and artifact
identities for each validation run. Additional JVM versions require separate validation.

CodeChickenLib remains a required dependency. Compilation and runtime validation use the same
pinned release; this release does not replace CCL.

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

The responsibility mapping in [ct-migration.md](ct-migration.md) identifies the replacement APIs.
Builder and effect contracts must describe their registration behavior explicitly. Transactional
publication covers declared state; arbitrary external side effects cannot be rolled back.

## Release gates and evidence

1. **Completeness:** account for the capability inventory above and all replaced CT responsibilities;
   document intentional removals and verify the replacement behavior.
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

The [validation report](pre-delivery-qualification.md) records the current outcomes and limits.

## Optional compatibility

Addon ports, CT script conversion, legacy-world import and compatibility with a named existing pack
each require a defined scope and validation. A source or JAR used as a reference does not establish
support. Advertised integrations must pass their own runtime and persistence checks.

Migration must preserve original saves and require an identified source format. Registered content
is authoritative; a quest reference does not create or validate an item, fluid or material.
