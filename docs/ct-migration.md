# Native Java and Groovy authoring migration

Reloaded 3.0 targets Java 26 and Cleanroom. CraftTweaker/ZenScript scripts and addon APIs must
migrate to native Java or Groovy authoring. This guide maps the replacements and their lifecycle
requirements. See [release validation](pre-delivery-qualification.md) for supported versions and
compatibility limits.

## Replacement map

| Previous responsibility | Native/shared replacement | Authoring/lifecycle rule |
| --- | --- | --- |
| Recipe map bracket handlers, builders and wrappers | `RecipeMap.getByName`, `recipeBuilder`, `findRecipe`, `getRecipeList`; `RecipeDefinitionCompiler` and `RecipeDefinitionRegistrar` | Groovy's existing recipe-map properties call native builders. `mods.gregtech.definitions` owns typed groups in `postInit`. |
| Enumerable ingredients, alternatives and CT NBT conversion | `ItemIngredient`, `ItemValue`, `NbtPolicy`, `CapabilityPolicy` | Explicit exact/contains/any behavior; no count-based bypass. `input.compile()` can also feed an ordinary specialized native builder. |
| Recipe properties and conditions | Native property types, condition descriptors and `applyNamedProperty(String,Object)` | Rename subclass overrides and callers from `applyPropertyCT`; specialized dispatch remains native. |
| Output-based removal and map clearing | `GTRecipeHandler.removeRecipesByOutputs`, `removeAllRecipes` | Item/fluid outputs and optional exact amounts; one removal per matching visible recipe. |
| Materials, properties, flags, elements, tools and fluids | `Material.builder`, `Material.getProperty(PropertyKey...)`, native property builders, `Elements`, `FluidBuilder`; existing Groovy material-event helpers | Declare/create in the material event; modify only during the permitted material lifecycle. Native builder functionality remains available while immutable descriptor coverage expands. |
| Custom ore-dictionary item creation | `ContentDefinitions.ORE_ITEMS` with immutable `OreItemDefinition`; `mods.gregtech.content.items` | Queue in `preInit`, freeze before Forge item registration. Validate whole batches before accepting metadata/name assignments. |
| Coil statistics and removal | `HeatingCoilDefinition`, `ContentDefinitions.coil/removeCoil`; Groovy content facade | Startup-only; freeze before the coil temperature display table is built. Changing structural content requires restart. |
| Furnace exceptions during blast-metal cleanup | `FurnaceRecipePolicy.protect(input, output, source)`; `mods.gregtech.content.protectSmelting` | Explicitly protect the registered pair before cleanup. No reflective inspection of another engine's action queue. |
| Advancement/free capes | Native `CapesRegistry.registerCape(String,String)` / `registerFreeCape(String)` | String registrations remain queued until world/advancement data exists; no optional-method stripping. |
| Charcoal-pile wall blocks | `MetaTileEntityCharcoalPileIgniter.addWallBlock(Block)` | Pass a native block. The CT-typed conversion overload is removed. |
| Multiblock preview suppression | `MultiblockPreviews.hide(ResourceLocation)` / `show` | Server-safe declarations, consumed during client recipe-viewer registration. |
| Generated recipe-removal text | Existing Groovy helper/command integration | Generated text targets Groovy. |

Immutable definitions now cover fluids, ore/circuit inputs, chance outputs, and common specialized
properties through `RecipeAttribute`, including owned cutter/solder/distillery expansion and recycling
metadata. Assembly-line research owns generated scanner/station recipes and its secondary research index.
Custom map subclasses accept descriptors through the same final native registration operations.
Secondary indexes are declared using `RecipeMap.registrationState(initial, prepare)`; both native
builders and typed groups maintain those values. There is no map-class allowlist or per-addon map adapter.
Builder preparation and arbitrary side effects still require the documented explicit contracts.
Exact-artifact reload and release gate results are recorded in [release-readiness.md](release-readiness.md).

## Saved identities

Generated ore blocks retain their existing default `gregtech:ore_<material>_<stone-group>`
identities. Before block registration, `OreBlockRegistryPlan` validates the entire generated-ore
name set and reports both material owners and source diagnostics for collisions. Two materials
with the same path in different namespaces cannot silently claim the same generated ore block.
Preparation enumerates the numeric material entries used by block creation. It does not rely on
the name-only material collection: an addon using the default-registry fallback can replace a
short-name alias while leaving both numeric entries present. This ore plan does not repair those
material aliases or move their existing numeric assignments into another registry.

A pack can assign an explicit immutable `OreBlockIdentity` through
`material.getProperty(PropertyKey.ORE).setBlockIdentity(identity)` during material configuration.
Java and statically checked Groovy use the same record: source origin, fully qualified material
identity and block registry base. The stone-group suffix is appended by native registration.
Assign new content a distinct name rather than taking an existing saved block's identity.
The prepared naming plan snapshots these values. This does not rename existing worlds or prove
that a changed name is safe; packs must preserve historical IDs or provide a verified save migration.

The ore-item carrier remains **`gregtech:meta_oredict_item_ct`**. Keep every existing metadata
assignment. Its historical registry spelling is persisted data, not a CT runtime dependency.
`OreItemRegistry.stack(metadata, amount)` returns the actual carrier stack once registered.
Ore names, chemical-formula formatting and material-icon model selection remain native behavior.
Early item declarations validate structure immediately. Prefix/icon names resolve after material registries
exist, before any carrier item is created; unresolved names stop registration with source diagnostics.

For migrated materials, pass both the original numeric ID and the original namespace/name.
The old CT material builder used the `gregtech` namespace; Groovy's convenience material builder
defaults to the pack namespace. Use an explicit `ResourceLocation("gregtech", oldName)` where
that is the existing identity. Convert automatically allocated CT IDs into their actual assigned
numbers; do not restart allocation in a new script order.

## Intentional source/API changes

- Breaking: the BetterQuesting quest-database migration (`BQuDataFixer`, which ran at server start) and
  the `/gregtech datafix` command are removed. Reloaded no longer rewrites external quest databases.
  Migrate quests on a 2.x release with BetterQuestingUnofficial before moving to 3.0. Pack makers run
  `/gregtech datafix` (permission level 3) once to rewrite the default quests under `config/betterquesting/`;
  server start never touched those files. Each existing world must also be loaded once on 2.x, which
  rewrites `<world>/betterquesting/QuestDatabase.json` at server start. Native world and item saved-state
  migration is unchanged.

- Recipe-map `addRecipe`, `compileRecipe`, `removeRecipe` and `removeAllRecipes` are final so all
  registration paths maintain the same indexes. Move subclass registration side effects to declared
  immutable `RecipeRegistrationState` values; return a detached value from preparation. Existing
  binaries overriding these methods must be migrated and rebuilt. Lookup/UI subclasses remain supported.
  `postValidateRecipe` remains a validation hook and must not publish recipes or mutate registries.

- `.zs` source is not executed. Convert declarations and lifecycle placement to Java/Groovy.
- CT wrapper classes and `ctFindRecipe`, `ctGetRecipeList`, `ctRecipeBuilder`, `addWallBlockCT`
  are removed. Java addons importing those wrappers must migrate and rebuild.
- The shared property extension hook is now `applyNamedProperty`. Native subclass overrides
  and Groovy property expansion dispatch through that method.
- `Elements.getAllElementsCT()` is removed. Use the native `Elements.getAllElements()` collection;
  Java callers that need an array can use `.toArray(Element[]::new)`.
- `Material.getMaterialComponentsCt()` is removed. Use `getMaterialComponents()`;
  callers that need an array can use `.toArray(MaterialStack[]::new)`.
- Generic pack provenance uses `RecipeBuilder.authored()` and `Recipe.isAuthoredRecipe()`;
  modifier views expose `isAuthoredRecipe()`. Builder copies retain that flag. Groovy provenance
  remains separately available where the frontend matters to tooling.
- The terminal search API uses cooperative interruption and owner-thread `drainResults()`;
  the obsolete `isManualInterrupt()` switch is removed.
- Loader integration uses a manifest `MixinConnector` and Cleanroom conditional registration.
  The early/late MixinBooter adapters are removed; Cleanroom 0.7.14-alpha and Java 26 are required.

Recipe mutability remains an authoring feature. Groovy can create recipes, delete individual
recipes, clear recipe maps, and explicitly remove and register replacements. Immutable descriptors
do not make the recipe registry read-only: `definitions.replace(group, declarations)` replaces an
owned group, and `definitions.replace(group, [])` deliberately disables it. Supplying declarations
again re-enables it. The imperative APIs retain their existing registration and reload semantics;
their arbitrary sequence of operations is not automatically a transaction.

For `VirtualizedRecipeMap.find` and `removeByInput`, a null item list means no item channel and a
null fluid list means no fluid channel. A lookup returning null can intentionally mean no matching
recipe. These differ from a requested item name resolving to null inside the ingredient list:
silently discarding that missing ingredient could broaden a recipe-removal query. Resolve the
intended identity or report the missing declaration before changing the registry.

Null cells in a shaped crafting pattern still represent empty slots. An intentional removal that
matches no recipe may remain a no-op; it is not equivalent to an unresolved named ingredient.
Disabling a machine recipe removes its active registration, including through an empty owned
group. The crafting registry can instead retain a nonmatching placeholder to preserve its numeric
ID. Neither operation inserts a null recipe into a native matching index. A null value in the
internal crafting replacement map is an explicit disable instruction, not an unresolved ingredient.

The Groovy typed-group adapter restores its owned recipes to the unscripted baseline on
reload, then accepts new groups. Clearing all owned groups is atomic. Replacement transactions cover each supported group; they do
not promise rollback of arbitrary script code or retention of an earlier run after a failed whole
reload. Do not independently remove recipes belonging to a typed group through another frontend.

For existing generated recovery recipes, `replaceRecycling(group, declarations, baselines)` can
adopt an explicit list of `NativeRecipeRegistration(map, recipe)` handles when first creating the
group. Handles identify the exact currently registered recipes in the macerator, extractor or arc
furnace; no input/output search silently chooses which recipes to overwrite. Validation stages
all affected maps before removing any baseline. Subsequent replacements omit the baseline list.
An empty replacement disables the group while retaining its baseline ownership. Registrar
`clear()` or the Groovy reload callback restores the original recipe identities; a restoration
conflict leaves the current groups intact. Adoption does not transfer crafting recipes or recycling
composition metadata, and callers must not adopt recipes managed by another registrar.

`replaceRecovery(group, contributions, recoveryDefinitions, baselines)` coordinates composition and
generated recovery recipes. `RecyclingContribution` describes the material routes; an immutable
`RecyclingRecoveryDefinition` selects the native generation policy for one owned output item.
The shared graph resolves contributions into a detached view, generates recovery recipes from
that view, and prepares all affected native indexes before publishing either metadata or recipes.
An upstream change also regenerates participating downstream outputs owned by other groups.
Direct recipe changes and generated changes share one index update, even in the same recipe map.

Use a distinct group name for this combined ownership; do not switch that name to direct recipe
definitions. Empty contributions and recovery definitions remove the group's active registrations;
explicit empty composition can instead retain an authoritative no-recovery declaration. Replacing
the group again re-enables it. Clear/reload restores owned metadata and explicit recipe baselines
in one transaction across both group kinds. A failed restore preserves both kinds of active group.
The lower-level `RecyclingOwnershipRegistry.prepareRecovery` supports native owners and exposes a
detached `resolved()` metadata view; prepared changes are revision-checked and single-use.

`replaceRecovery` does not mutate crafting registrations. Use `replaceCrafting` when a helper needs
to coordinate crafting with composition and generated recovery. Generation runs during registration;
no script closure is retained in recipe matching.

The internal `CraftingRegistryTransaction` prepares already-compiled native crafting recipes in
detached Cleanroom registry copies. It mirrors available ACTIVE/FROZEN/VANILLA views, preserves
numeric IDs and registry collection identities, and can restore the captured tables if a combined
operation aborts. Disabling an existing name retains a nonmatching, dynamic placeholder at its ID;
removing an absent name is a no-op. It does not itself compile typed crafting descriptors or join
`replaceRecovery`; use the registrar's `replaceCrafting` method for coordinated publication.
Its cached Java method/field handles are used only at registration, never during crafting matches.

`CraftingDefinitionCompiler.compile(definition)` now compiles immutable `ShapedCraftingDefinition`
and `ShapelessCraftingDefinition` values into detached native `IRecipe` instances. Both Java and
dynamic or statically checked Groovy use this API. The result includes source/field diagnostics;
compilation neither registers the recipe nor claims its name. Publish through
`RecipeDefinitionRegistrar.replaceCrafting` to coordinate owned crafting, composition and recovery;
see the ownership rules below. Compilation alone does not provide those publication guarantees.

Shaped inputs are row-major; null cells are empty positions. Dimensions must match the input list,
and mirroring is explicit. Shapeless inputs must all be occupied. Both use the existing `ItemInput`
descriptors and their NBT/capability policies. Each occupied slot has quantity one, matching native
crafting consumption; quantities greater than one must occupy separate slots. Missing ore names,
empty alternatives, malformed grids and empty outputs produce diagnostics before registration.
The compiler accepts larger grids for native consumers that provide them.

Execution uses native ingredient checks and Cleanroom's shapeless matching routine, including
overlapping alternatives. Nonconsumable inputs return one item to their matched slot; consumable
inputs use native container-item/tool behavior on a copy. Output and display-stack accessors return
copies. No script callback or reflection is retained in these recipes. Arbitrary dynamic output and
ingredient-transform closures are not part of this descriptor surface; existing native/Groovy custom
recipe facilities remain available for those cases.

Under `@CompileStatic`, direct Minecraft member references such as `Items.COAL` can retain
development names and fail with `NoSuchFieldError` in production. Keep statically checked calls
on Reloaded's Java API; use GroovyScript's remapped helpers for Minecraft access, or implement
that access in build-remapped Java. Test compiled scripts against the production mod JAR.

Built-in `GTRecipeOreInput` now follows the native ore membership list directly, including additions,
removals and same-size replacements during authoring. `getInputStacks()` returns fresh, quantity-adjusted
copies for callers; modifying those copies cannot change matching. Matching does not allocate those
snapshots. The old `refreshStackCache()` method and Groovy/HEI/load-complete refresh hooks are removed;
callers should remove explicit invalidations. Ore names must still exist when typed crafting
definitions are compiled: an unknown name produces a diagnostic without allocating an ore entry.

## Crafting ownership

Typed crafting ownership is available through `RecipeDefinitionRegistrar.replaceCrafting` and
`mods.gregtech.definitions.replaceCrafting`. Pass shaped/shapeless definitions to create or overwrite;
pass `DisabledCraftingDefinition` to disable a name explicitly. An empty group disables all its
previously edited names. Replacing those definitions re-enables them; clearing the owner restores
the original baseline while preserving unrelated registrations. Names are claimed across typed
registrars, so competing groups receive a diagnostic rather than silently replacing one another.

The full overload stages explicit composition contributions and generated recovery with crafting.
Caller-provided native recovery baselines remain displaced during disable and return on clear.
Crafting and composition/recovery groups can adopt further native recovery handles in later calls.
Pass only newly encountered, currently registered recipes; the registrar retains earlier originals.
Duplicate or already-owned handles reject the whole edit before any crafting or recovery publication.
This incremental adoption does not apply to the separate `replaceRecycling` generated-recipe-only API.
Existing imperative helper calls do not automatically acquire these guarantees. Migrate the whole
operation, including its composition policy, to the shared API. Ownership can adopt the initial
FROZEN crafting copy after verifying native copy semantics. The registered Groovy frontend now clears
before Groovy's ordinary reload callbacks, so its baselines are restored before imperative crafting
backups. Other registry-stage replacements still reject restoration. Migrate and test each pack
helper's ownership and reload behavior as a complete operation.

## Machine and storage APIs

Mufflers provide exhaust obstruction checks and particles; they have no recovery inventory or
item-generation mechanic. The unused `MultiblockWithDisplayBase.recoveryItems` field and
`setRecoveryItems` method are removed. Remove addon writes to that dead state. An addon that
intentionally produces byproducts should declare ordinary or chanced recipe outputs through the
native recipe API. Mufflers do not open an inventory screen; their tooltip describes their role.
This does not provide a migration for inventories saved by older recovery-enabled mufflers.

Determine logical side from the attached world's `isRemote` flag.
A missing `MinecraftServer` reference does not make a world client-side;
guard server-only operations separately. Reloaded's native update loop already makes that distinction.

Crate subclasses use `getMaterial()`, `getInventorySize()` and `getRowSize()` for their
native configuration. Layouts must have positive dimensions and complete rows. Override
`keepsInventory()` for permanent retention; crate item serialization, placement and inventory
clearing honor that policy without a private-field tape accessor. Ordinary taped crates still
remove their tape on placement. A permanently retained crate remains retained after placement.

`BlockMachine.replaceRetainedMachine(source, target, machineData)` replaces a live, retained
machine with a registered native prototype. Call it on the server thread with complete target-
compatible NBT and no installed covers. It preserves the holder name, selects the target's
namespace block, initializes state before native synchronization and restores the previous
harvest cache. A stale source, client call or refused block change returns false. This is not
a rollback transaction over arbitrary world callbacks or a general inventory-size converter.
Addons remain responsible for compatible target state and consuming an unlocking tool only
after successful replacement.

## Dependencies and runtime

The 3.0 alpha line requires Java 26 and Cleanroom 0.7.14-alpha. GroovyScript 1.5.0 is optional for
native Java consumers and required for Groovy authoring. Publication metadata includes CodeChickenLib 3.3.10,
ModularUI and compile annotations; it does not install the development loader bundle, Groovy engine,
HEI or optional integrations. Addons using an integration's types must declare that dependency explicitly.
The recipe-viewer development API is HEI 4.36.1 (`mezz:jei:4.36.1:dev`), including native subtype
registration and collapsible groups. The `mezz.jei` package and `jei` mod ID are retained by HEI.

Each full viewer registration now resets Reloaded's category lookups. Categories from an earlier
runtime must not be retained by consumers across a rebuild; resolve them again from the current
runtime. Asynchronous reload with GroovyScript requires the repaired HEI 4.36.1 dependency;
unpatched 4.36.1 has a completion lock conflict. See
[the repair instructions](../tools/dependency-patches/hei-async/README.md) and
[release validation](pre-delivery-qualification.md) for its exact identity and tested behavior.

Pack and addon compatibility must be tested against the selected production dependencies,
including reload and copied-world migration where those features are used.
