# Typed recipe definitions

The configuration API is shared by Java and Groovy. A declaration owns its lists,
stack copies and NBT policy; preparation produces native recipe inputs. Machine lookup does
not invoke a script closure or retain a Groovy interpreter. Custom crafting callbacks remain
available where needed; their execution rules are described below. See
[release validation](pre-delivery-qualification.md) for the supported runtime and tested scope.

## Supported definitions

`RecipeDefinition` describes item and fluid inputs, deterministic and chanced outputs on ordinary
`RecipeMap` instances. Supported builders are Simple, Blast, Fusion, Computation, Implosion,
CircuitAssembler, UniversalDistillation, Primitive, Fuel and AssemblyLine, without implicit
input/output/property defaults. Native oxygen, solder, chemical-reactor copying, cutter-fluid
and distillery expansions use the same preparation algorithm for Java and Groovy.
Additional builder classes require an explicit preparation contract through
`RecipeDefinitionExtensions.registerBuilder`; see [native addon extensions](#native-addon-extensions).
Unsupported builders produce a diagnostic. Native builders remain available independently of
typed definitions. Native registries must already exist when declarations are prepared.

`SourceOrigin` carries frontend, source, one-based line/column (zero means unknown), declaration
identity and authoring stage. Diagnostics have a stable code, field path and source. Stage is
provenance here; it does not grant permission to write a registry from a machine tick.

`ItemIngredient.exact(stack)` uses exact NBT and capability matching. Its alternative form makes
quantity, consumption, NBT and capabilities explicit:

```java
var input = new ItemIngredient(
    List.of(ItemValue.of(iron), ItemValue.of(gold)),
    2, true, NbtPolicy.contains(requiredTag), CapabilityPolicy.MATCH);
var recipe = new RecipeDefinition(
    new SourceOrigin("java", "MetalRecipes.java", 42, 1, "metal_dust", "recipes"),
    "macerator", List.of(input), List.of(ItemValue.of(output)), 40, 8L, false);
var prepared = RecipeDefinitionCompiler.compile(recipe);
```

These same Java types and calls work in statically checked Groovy; no parallel Groovy recipe
implementation is needed.
When reading native objects from Groovy, use explicit Java accessors where bean names collide.
For example, `material.getElement()` returns its element identity, whereas `material.isElement()`
returns a boolean; Groovy's `material.element` selects the boolean accessor. Keep identity and
predicate calls explicit when converting an authored value into a descriptor.
GroovyScript exposes the persistent adapter at `mods.gregtech.definitions`. Call
`definitions.replace(group, recipes)` in `postInit`; invalid stages are rejected. Its reload
callback removes its owned groups before scripts run again, following GroovyScript's baseline
restoration lifecycle. Clearing all owned groups is one transaction: an ownership conflict leaves
every group untouched. A failed replacement within a run preserves that group's current state.
A failed whole script reload does not retain the previous run's groups. Successfully published edits
from the new run remain active if a later script throws, and GroovyScript stops the remaining scripts
in that load. A subsequent normal reload restores baselines before trying again. This behavior is
verified in an isolated integrated client using the normal reload command, including mixed imperative
and typed crafting/machine recipes. It is not whole-script atomicity or rollback of arbitrary side
effects. Do not instantiate a registrar on every script rerun.

## Recipe mutation remains supported

Immutability applies to each prepared definition, not to the recipe registry. Pack authors
can create recipes, delete them, overwrite them and deliberately disable them:

| Operation | Typed Java / Groovy definitions | Existing Groovy authoring |
| --- | --- | --- |
| Create | Replace an unused group with its definitions | Native recipe builder or crafting add call |
| Overwrite | Replace the same group with revised definitions | Explicit remove-and-register or crafting replace call |
| Delete or disable | Replace the group with an empty list | Remove by input, output or identity as supported by the adapter |
| Re-enable | Replace the empty group with definitions again | Register the recipe again |

A failed typed group replacement preserves that group's current recipes. Separate imperative
remove-and-register calls are separate operations: they do not gain rollback merely by being
written together in a script. Do not mix independent removals with a typed group's owned recipes;
replace the owning group so its derived recipes and metadata change together.

Intentional null crafting cells and omitted input channels in existing Groovy APIs retain their
meaning. A missing named ingredient is an error, not permission to discard that ingredient or
broaden a removal query. Use an empty group to disable recipes explicitly; malformed descriptors
do not mean deletion. An unmatched removal may be an intentional no-op.

Crafting registration has an internal transaction participant for already-compiled native recipes.
Its null replacement values disable existing recipes while retaining their numeric IDs; a name absent
from the active registry remains an intentional removal no-op. This is distinct from null cells in a
shaped recipe, which describe empty grid positions. Explicit replacement re-enables a disabled recipe.

The participant also captures a selective restoration baseline. A coordinator can carry the earliest
baseline through later replacements and restore only its edited names, preserving unrelated recipes
registered afterward and the positions of existing registry entries. Outside edits to a captured name
are rejected before publication. Whole-registry rollback remains available for immediate transaction
abort. Neither operation executes from recipe matching or machine ticks.

Retained groups follow Forge's native freeze and world-load synchronization. Native synchronization
may recreate ACTIVE registry owner keys; adoption requires the same recipe objects, numeric IDs,
aliases and override history, with unchanged reference views. Foreign recipe or ownership edits
still reject restoration. Repeated world loads do not require recompiling otherwise unchanged recipes.

`RecipeDefinitionRegistrar.replaceCrafting(group, declarations)` owns crafting names across Java
and Groovy registrars. Its declarations are `CraftingMutation` values: a shaped/shapeless
`CraftingDefinition` creates or replaces a recipe, while `DisabledCraftingDefinition(source, name)`
explicitly disables it. Duplicate names in one request are diagnosed; an absent disable is a no-op
and claims nothing. A name claimed by another group cannot be adopted implicitly.

When editing an accepted group, retaining the same declaration object retains its native recipe;
only changed names are staged for replacement. To request preparation again, submit a fresh
declaration, even if its values are equal. Disabled or omitted declarations discard their compiled
result, so re-enabling prepares them again. Failed edits retain the last accepted results. Registry
ownership checks still run for retained recipes and transactions requiring no registry writes.
Ordinary ore ingredients continue to match live ore membership through the native implementation.

`CustomCraftingDefinition(source, name, factory)` supplies exceptional native crafting behavior.
Its preparation-safe factory must return a fresh, detached `IRecipe` with the declared name and must
not mutate registries. Null results, wrong names, already registered instances and factory exceptions
reject the replacement before publication. The factory runs during registration only; the returned
recipe may retain custom matching and remainder behavior. Arbitrary factory side effects cannot be
rolled back. Use ordinary immutable descriptors when those express the recipe.

`GroovyCraftingDefinitions.shaped/shapeless` convert ordinary Groovy item/ore inputs to these native
descriptors, preserving Groovy's non-mirrored shaped default and ordinary item matching that ignores
NBT/capabilities. Custom predicates or remainder transforms select an explicit custom definition
backed by GroovyScript's native recipe implementation. This exceptional path retains its callbacks;
it does not promise closure-free execution. Ingredient lists and outputs are captured at conversion.
Callback state itself is not deep-copied.

For ordinary native crafting, `consumable=false` returns the original ingredient without
applying its container-item behavior. Use a consumable tool ingredient when crafting should
apply normal tool wear and breakage; Forge's native remainder hook returns the damaged tool.
Likewise, a consumed water bucket returns its empty bucket through that hook. A retained tool
is intentionally preserved without wear. Crafting quantities are one ingredient per occupied
slot; repeated output taking consumes successive sets of inputs.

## Tool state

Tool crafting sounds use a server-side, session-only cooldown per player and sound. They do
not write timing metadata into the tool remainder. The same sound is limited to once per
second for that player, including when using different copies of a tool. Existing saved
`LastCraftingUse` tags are ignored and preserved. The old stack-based
`IGTTool.setLastCraftingSoundTime`, `IGTTool.canPlaySound` and
`ToolHelper.LAST_CRAFTING_USE_KEY` are removed; custom behavior can override
`IGTTool.playCraftingSound` without introducing prediction-dependent item data.

The default numeric tool-stat readers are also read-only: tool speed, attack damage/speed,
maximum durability, enchantability and harvest level use correctly typed stored values
when present, otherwise derive the existing fallback without caching it into item NBT.
Material fallback and missing-damage reads do not attach or repair tags. Explicit creation,
overrides and damage changes still write through `ToolHelper.getToolTag` and the normal
setters. Stored values from earlier versions remain authoritative; no migration clears them.
Without a stored override, derived values follow current definition/material/stack state
instead of freezing at the first read. Custom definition implementations remain responsible
for their own side effects.

Default AoE readers also leave item NBT unchanged. Current and maximum area values use
`GT.Behaviours`, the same section used by construction, mining and configuration. Missing
current values fall back to the maximum without being written. Obsolete current-area fields
under `GT.Tool` are ignored and preserved. Configuration setup and value reads do not attach
behavior data; explicit adjustments still write through `ToolHelper.getBehaviorsTag` and
clamp to the existing maximum. Stored integers are not sanitized by reads. Custom maximum
overrides, tooltip callbacks and toolbelt capabilities remain responsible for their own behavior.

## Materials and recipe identities

GroovyScript's `stack * n` sets the stack's absolute amount to `n`. Applying `* 3` to an
existing two-item output produces a three-item stack, not six. Declare six explicitly when
describing the result of three crafts producing two items each.

Fully qualified material lookups such as `material('susy:zircaloy_4')` and
`GregTechAPI.materialManager.getMaterial("gregtech:zircaloy_4")` resolve that exact logical identity.
An unknown namespace cannot silently resolve a material from another owner. Logical namespaces may
share a physical numeric material registry; qualified lookup does not rename generated item/block
registry entries or change their metadata. Unqualified names still use the registry's short-name
alias and should not be used to distinguish colliding owners. Duplicate fully qualified material
identities reject ordinary registration, and an occupied numeric ID rejects before publishing either name.
Fluid identity still requires an explicit naming policy.

`MaterialIngredient(prefix, materialIdentity, amount, consumable, nbt)` describes a qualified material
input; `materialIdentity` is a `ResourceLocation`, with a convenience constructor accepting a `Material`.
`RecipeBuilder.input(prefix, material)` and the corresponding `GTRecipeOreInput` constructor preserve
the same identity. `OreIngredient(oreName, ...)`, string-based native inputs and Groovy `ore(...)`
continue to accept the broad named membership. Choose the named form when cross-material substitution
is intended; a shared short name does not make distinct qualified materials interchangeable.

Typed `OreDictUnifier.registerOre(stack, prefix, material)` retains the explicit physical material,
even when a raw ore event has already inferred another owner. Replayed events do not duplicate its
native output alternatives or override that explicit registration. Raw names still use existing
prefix/material inference; ambiguous external registrations should supply the explicit material.
Inputs intersect this ownership with live ore membership, including removals and wildcard metadata.
Native `ModHandler` shaped and shapeless helpers also preserve the qualified owner of a
`UnificationEntry`, including fluid-replacement and energy-transfer recipes using shaped finalization.
Explicit ore strings and entries without a material retain broad matching. Native ingredients follow
later ore membership changes; a display stack returned to a caller cannot mutate their prototypes.
Wildcard ore display alternatives expand to concrete creative variants. Packed recipe-book alternatives
are also computed from current membership, so an ingredient created before its ore is populated does
not retain an empty constructor snapshot. Returned packed lists are owned by the caller. These queries
do not change native matching or make packed item IDs a substitute for material/NBT validation.
Shapeless helpers accept native `Ingredient` values and preserve the caller's argument array. Tool
symbols, container returns, mirroring and NBT-clearing behavior retain their native recipe contracts.
Shaped finalization copies its caller's argument array for ordinary, fluid-replacement and
energy-transfer recipes. Patterns consisting only of implicit tool symbols need no explicit key pair.
Fluid replacement uses native filled-bucket resolution for vanilla and universal buckets. Other
fluid containers retain their item and unrelated NBT; crafting does not mutate the input stack.
`CraftingDefinitionCompiler.ingredientIdentity(ingredient)` returns a read-only ore/material identity
for Reloaded's compiled ingredients. An empty optional means an unrecognized ingredient implementation;
within a recognized identity, a null material means no qualified material constraint. Inspection does
not expose mutable input state or require reflective script access.

Generated material crafting recipes use the material owner's namespace through
`ModHandler.materialRecipeName(material, path)`. Core and pack materials sharing a short name therefore
have separate recipe IDs, for example `gregtech:small_dust_assembling_zircon` and
`susy:small_dust_assembling_zircon`. Pack scripts should remove or replace the intended fully qualified
recipe. Generated prefix components use prefix names, not diagnostic strings containing numeric ordinals.
No short-name alias is registered. Explicitly qualified recipe IDs use the same resolution for duplicate
checking and publication; ordinary registration cannot overwrite an existing recipe implicitly.

Player data before GregTech data version 3 migrates generated recipe-book IDs before Minecraft reads
them. Generation records old-to-current names in `RecipeBookMigrationTable.GENERATED`; the overload
`materialRecipeName(material, path, previousPath)` also records former prefix paths containing ordinals.
The table is migration metadata and does not register crafting recipes. Its snapshots are immutable.

Both learned recipes and pending notifications migrate. If a formerly shared ID could mean multiple
material owners, discovery is preserved for every surviving target: the old save contains no reliable
record of which owner won. This can reveal both recipes in the recipe book, but creates no recipe or
item and does not bypass native matching. Targets must remain registered, non-dynamic and have a
nonempty output after pack edits. Removed/disabled targets are omitted; unrelated names and other
player data are preserved. Forge's version gate prevents re-expansion of current-version saves.

Ore-prefix processing visits current material declarations in qualified-name order. Explicit replacements
supersede their old declaration for recipe generation; the previous physical numeric entries remain
registered and qualified inputs can still match their forms. Marker materials retain processing through
their separate marker registry. Each pass drains its initial work; registrations enqueued by callbacks
remain pending for the next pass. This ordering does not automatically resolve competing recipes authored
by different mods or authorize a pack's intentional replacement.

Native tool construction serializes enchantments in registry-name order after applying the existing
material/default level precedence and eligibility checks. Both ordinary and explicit-stat tool builders
use this order. Generated recipe outputs therefore do not acquire different enchantment NBT list orders
from hash iteration across launches. Existing player tool NBT is not rewritten by construction.

Native recipe indexes distinguish qualified owners without snapshotting alternatives at registration.
Old and replacement physical forms of the same fully qualified identity remain acceptable inputs;
their physical material objects and numeric storage remain distinct. Named ore edits must use the
engine's normal reverse-map rebake when mutating its membership lists directly.

An intentional material override uses `builder.buildReplacing(new MaterialReplacement(origin, expected))`
from Java or Groovy during `MaterialEvent`. The expected object must still be the current qualified
declaration, the new declaration must have the same full name, and its numeric ID must be unoccupied.
The new declaration becomes the qualified and short-name lookup target. Both physical numeric entries
remain registered, and previously captured component references retain their original objects and
properties. This operation does not merge the materials, remap saved item metadata, or promise that
their unification entries are interchangeable. It rejects stale targets and closed registries with
source diagnostics. An ordinary duplicate remains an error even after an explicit override.

`getAllMaterials()` and the manager's closed-registry aggregate enumerate current fully qualified
declarations, including owners sharing a short name. Each registry retains first-declaration order;
an explicit replacement occupies its logical identity's position. Numeric registry iteration still
includes superseded physical entries. Fluid producers must assign distinct fluid names when distinct
materials would otherwise claim the same name; enumeration does not infer an ownership or alias policy.

Resolve inputs at their recipe registration phase, after scripts that create their content have run.
Creating an ore ingredient before its ore exists is supported; converting it while unresolved is
rejected without allocating an ore entry. The adapter checks named ores before Groovy's `isEmpty`,
which otherwise disguises an unresolved ore as a zero-quantity ingredient. Null cells and intentional
zero quantities of resolved inputs remain supported by `recoveryInputs`.

An unresolved-name diagnostic describes the registry at preparation time, not whether another script
will create that ingredient later. A failed machine group replacement preserves its previous recipes;
the same `RecipeDefinition` can be submitted again after the content script registers the ore.
Successful preparation keeps native ore matching live, including members registered afterward.
There is no automatic deferred retry: order dependent scripts explicitly so recipe creation, removal
and replacement retain their authored order.

## Crafting and recovery ownership

`generatedRecovery(group)` returns an immutable list of the group's current native recovery handles.
It excludes displaced originals and other owners' recipes. Helpers can exclude their own generated
recipes when discovering newly encountered native baselines. Query it again after publication:
replacement can regenerate handles, and the returned list does not grant mutation authority.

Recovery policies share the sealed `RecoveryDefinition` boundary. `RecyclingRecoveryDefinition`
derives quantities from a composition owned by the same group. `RecyclingRecipeDefinition` supplies
fixed material quantities and does not claim or change item composition metadata. Both can participate
in `replaceCrafting` or `replaceRecovery`, including native baseline adoption. This distinction lets
a pack edit crafting and its recovery recipes without taking over another addon's material ownership.

Retaining the same recovery declaration object retains its prepared native recipes when its effective
material quantities have not changed. Unrelated registrations therefore do not rebuild those recipes
or their indexes. Composition-derived recovery still regenerates when upstream quantities change.
Supply a fresh declaration to explicitly regenerate against changed native material properties,
unification choices or build actions; those mutable authoring environments are not watched implicitly.
Retained handles are checked for external removal, and map revisions are rechecked before publication.

An empty replacement disables the group's previously edited names. Omitting a previous name from a
nonempty replacement also keeps that name disabled. Claims and the earliest pre-group baseline remain
until `clear()` restores them. Explicitly supplying a recipe re-enables it. Clearing one registrar
preserves unrelated registrations and other owners; an outside edit to its owned state rejects the
whole clear before mutation.

The full overload takes crafting declarations, `RecyclingContribution` values,
`RecyclingRecoveryDefinition` policies and optional native recovery baselines. It prepares crafting,
composition and all affected generated recovery before publication. The caller supplies explicit
composition policy; the registrar does not infer it from arbitrary custom recipes. Changes to upstream
contributions recompute dependent recovery owned by other groups. Empty replacements remove the
group's contributions/generated recovery while keeping adopted recovery recipes displaced; `clear()`
restores their baselines. A group cannot switch between direct, recovery-only and crafting ownership
without clearing its owner first.

`craftingGroup(group)` on either the Java registrar or the Groovy frontend returns an optional,
immutable `CraftingGroupDefinition` containing the last accepted declarations, contributions and
recovery policies. Helpers can copy and revise these lists before resubmitting the full group,
without keeping a second static cache across Groovy reloads. A rejected replacement or clear keeps
the accepted state; a successful clear removes it. An accepted empty group is present with empty
lists, distinct from a group that has never been registered or has been cleared.

This value is authoring data, not a native registry snapshot or an ownership transfer. Omitted names
remain disabled under the registrar's existing claims even though they are absent from these lists.
Native restoration baselines stay private to the owning registrar and must not be resubmitted when
revising an existing group. A crafting or composition/recovery group may explicitly adopt additional
native recovery recipes encountered later: supply only the new exact handles. Validation, displacement,
composition, generated recipes and crafting edits publish together. Earlier originals remain retained
until clear; duplicate handles, displaced originals and already-generated owned recipes are rejected.
Empty replacements leave all adopted baselines displaced. Reads use the same registration-thread requirement as writes; Groovy reads
also require `postInit`. Retaining an old value does not make it current after another replacement.

Groovy uses `mods.gregtech.definitions.replaceCrafting(...)` over the same implementation. The frontend's
`clear()` restores all groups owned by that frontend during `postInit`; the reload callback also uses
the registrar's combined clear. An entry hook restores the registered frontend before Groovy begins
its ordinary registry reload callbacks; a failed clear stops that reload before imperative backups
are touched. The frontend's later ordinary callback is safe to repeat.

Pack-created materials, items and ore entries remain valid inputs. Resolve and validate a replacement
at its authorized registration stage, after its required content has been registered. An empty lookup
at that point establishes an unresolved reference in that phase; it does not prove the pack never
defines the ingredient. Investigate script definitions and ordering before changing a named reference.
Intentionally empty shaped cells and zero-contribution recovery inputs are separate authoring cases.

Ownership retained during startup can adopt Cleanroom's first FROZEN crafting view when its recipe
objects, IDs, allocation state, aliases, override history and native owner-copy rules agree. Original
ACTIVE and VANILLA entries remain separate baselines; the new FROZEN baseline is derived from ACTIVE.
Other stage replacements and mismatched copies still reject restoration. Existing imperative Groovy
crafting operations remain available; do not modify a typed group's claimed names through independent removers.

Recipe index conflicts are checked before inserting any alternative path. A path may end in a
recipe and also continue to longer recipes. Lookup tries eligible descendants before that path's
terminal recipe, independent of their registration order. Native quantity, voltage and eligibility
checks still apply; a rejected descendant allows fallback to the shorter recipe. This defines
precedence within a shared path, not a global specificity ranking across unrelated paths. Exact
duplicate endpoints still require explicit replacement.

Removal clears every alternative path to the removed identity while retaining recipes above or
below it. Typed transactions copy terminal recipes as well as branches. Machines rediscover cached
prefix recipes before starting another operation, and registry revisions invalidate idle recipe
caches even without the optional idle scheduler. An operation already underway retains its sampled
outputs. Prefix rediscovery can cost additional native lookups; no performance gain is claimed.

## Generated recycling recipes

`definitions.replaceRecycling(group, declarations)` and the Java registrar's same method accept
`RecyclingRecipeDefinition` values. Each contains a source, copied input stack, immutable ordered
`RecyclingMaterial` quantities, arc-smelting policy and optional native prefix hint. Quantities
describe recovery per input batch. This uses the native recycling generator, including its NBT
rules, categories, output selection and preparation-safe recipe expansions; it does not implement
a second generator in Groovy.

Native recovery combines contributions of the same material with checked addition, preserving the
first occurrence of each distinct material. Macerator and arc outputs use descending quantity,
then fully qualified material name before the output-slot cutoff, with ash after
other materials. Repeated full stacks remain separate outputs; equal material/quantity pairs are
not deduplicated. Output capacity still applies, so larger split stacks can occupy slots before
smaller material outputs. Compound extraction preserves declared component priority for its fluid
and secondary material; simple prefixes still use the native unifier's material amount. Hash-map
iteration cannot decide output-slot ties. Material definitions, declared priority and unifier
selection still need to be stable.

All generated maps are staged before the group changes. A conflicting map, invalid quantity or
unsupported build action leaves the old group intact. An empty composition or empty declaration
list removes the group's generated recipes; registering definitions again re-enables them.
The normal Groovy adapter reload callback clears these groups too.

`recoveryConflicts(definition)` on either frontend prepares one fixed-material definition and returns
the exact existing native registrations that prevent insertion. The query does not publish, adopt or
remove recipes. It follows registration keys, including ingredient alternatives and NBT, rather than
collecting every recipe craftable from the input: compatible prefixes, descendants and recipes in
maps where nothing is generated remain untouched. Callers can explicitly pass selected handles as
baselines to their combined crafting/recovery replacement. Normal ownership and conflict checks run
again on replacement; this query is not a reservation. Preparation-safe build actions may run twice.
Additive helper operations use this narrower scope; explicit remove/replace operations can still
choose the pack's broad input-based removal policy.

This API owns generated recipes only. It neither changes crafting recipes nor derives the minimum
recovery across alternative crafting routes, and it does not update composition metadata in the
recycling manager. Existing unowned recipes remain conflicts; they are not silently adopted or
deleted. Migrating the pack helper requires a coordinated crafting/composition/generated-recipe
lifecycle, including restoration on reload. Native extractor prefix behavior is retained, including
its use of unified material amounts for simple prefixes.

## Matching and ownership

- `exact(null)` distinguishes absent NBT from an empty compound.
- `contains(tag)` requires the supplied top-level entries. Nested compounds/lists compare
  exactly; they are not recursive partial patterns. An empty requirement matches absent NBT.
- `any()` imposes no NBT condition. It does not implicitly disable capability matching.
- `CapabilityPolicy.MATCH` checks Forge capability compatibility against the alternatives.
  `IGNORE` is an explicit choice. Inventory count does not bypass either NBT or capability checks.
- Stack ownership follows Forge's copy/serialized-capability contract. Addon capability providers
  must implement that contract correctly. Public stack accessors return copies.
- Wildcard metadata uses the native item's enumerated subitems, matching the existing native
  ingredient representation. This does not promise arbitrary unenumerated metadata values.
- `OreIngredient` resolves an existing nonempty dictionary entry and follows native membership.
  Its NBT policy is explicit; `any()` uses the native ore matcher without an extra policy call.
  Ore membership does not imply capability equality against each member stack.
- `CircuitIngredient` uses the native integrated-circuit selector (0–32), quantity one,
  nonconsumable. `FluidIngredient` uses exact fluid/tag matching and explicit consumption.
  Nonconsumable inputs must still satisfy their required quantity.
- `FluidValue` owns its quantity and NBT; absent and empty fluid tags remain distinct.
  Chance definitions use the native 1–10,000 scale and tier boost, with OR/AND/XOR/NONE modes.
  Deterministic and chanced outputs both count toward a map's slot limit.

## Typed properties

`recipe.withAttributes(...)` returns a new immutable declaration. Built-in `RecipeAttribute`
variants cover temperature, fusion start energy, computation rate/total work, explosives,
cleanroom type, dimensions and the existing immutable condition set. Each property kind may
appear once; duplicates and incompatible builder types produce source diagnostics.

Specialized properties call the native typed builder methods. Explosives add a real input,
and validation includes that expanded input count. Total computation must agree with duration,
since the native computation recipe uses that field for total work. Cleanroom properties follow
the native `enableCleanroom` configuration. Compiling dimensions creates a fresh native list;
mutating a resulting recipe does not mutate the immutable declaration.

```java
var blastRecipe = recipe.withAttributes(new RecipeAttribute.Temperature(1800));
```

## Registration groups

### Native addon extensions

`RecipeDefinitionExtensions.registerBuilder(MyBuilder.class)` opts in one exact concrete builder
class. The compiler checks the map's template type before calling `copy()`. Copies must be new
instances of that same class and detach mutable authoring state. Subclasses need their own explicit
registration. Map subclasses use the same final native registration operations as the base map;
the compiler does not maintain a map-class allowlist. Use preparation-safe `RecipeExpansion` callbacks
for derived recipes. Arbitrary build callbacks are not transactional.

A map declares extra immutable registration state with `registrationState(initial, prepare)` before
registering recipes. The preparation function receives the current value and a `RecipeRegistrationChange`
containing removed recipes, added recipes and a clear flag. It must return a detached immutable value,
without modifying the current value or any registry. Both native registration and typed groups use
this function. All affected values prepare before publication; publication assigns values without
invoking addon code. A rejected preparation or recipe collision leaves the previous values intact.
`RecipeRegistrationState.update` supports explicit secondary-index edits and invalidates prepared
groups through the owning map's revision. Calls require quiescent registration; this is not a runtime
callback facility or rollback for arbitrary external side effects. The assembly-line research index
uses this same contract, without an assembly-line branch in the compiler or transaction code.

Implement registration reducers in Java or as explicit `@CompileStatic` classes implementing
`BiFunction<T, RecipeRegistrationChange, T>`. GroovyScript 1.5.0 intercepts exceptions from script
closures, including closures coerced to Java functional interfaces. A thrown rejection can therefore
reach the registrar as a null prepared value and a separate engine error. An explicit typed
implementation preserves the rejection message in the `recipe.state.prepare` diagnostic.
`@CompileStatic` on the enclosing class alone does not bypass closure interception.
This distinction concerns registration infrastructure; ordinary Groovy recipe declarations remain
supported. Neither reducer form belongs on the recipe matching or per-tick execution path.

An addon can register a `RecipeAttributeType<B, V>` with a namespaced ID, the actual native property
storage key, a `RecipeAttributeCodec<V>`, and a typed native builder setter:

```java
RecipeDefinitionExtensions.registerBuilder(MyBuilder.class);
var temperature = RecipeDefinitionExtensions.registerAttribute(
        new ResourceLocation("myaddon", "temperature"), MyTemperatureProperty.KEY,
        MyBuilder.class, temperatureCodec, MyBuilder::temperature);
var authored = recipe.withAttributes(temperature.descriptor(1800));
```

The same handle works from statically compiled Groovy. `descriptor(V)` encodes and snapshots its
payload; `RecipeAttribute.Custom` also accepts an already encoded payload from another frontend.
NBT is copied on construction and access. Codecs should include a schema version and reject invalid
types, unknown versions, unexpected fields and invalid values. Decoding receives a private copy, then
the typed setter performs builder-specific validation and required ingredient expansion. Do not bypass
those domain rules by blindly inserting a decoded property into storage.

Install contracts once during addon initialization before authoring; script reload does not replace
or unregister them. Duplicate IDs and native property-key aliases are rejected. Unknown attributes,
incompatible builders, codec/setter failures, and missing declared properties produce source diagnostics.
Built-in and custom attributes cannot claim the same native property in a single declaration, regardless
of order. Independent addon properties can appear together.

An addon may impose a narrower physical energy or duration domain than the native recipe's numeric
storage. Exceptions from those typed setters become `recipe.builder.energy` or `recipe.builder.duration`
diagnostics on the corresponding source field. A failed declaration leaves an existing registration
group intact; callers do not need to catch an arithmetic overflow to preserve the previous recipes.

This is a trusted native integration contract, not a sandbox or a proof that addon code is pure.
Copy/build methods, codecs and adapters must not publish recipes, mutate registries, retain builders,
or perform external side effects. Adapters may populate their declared native property and necessary
ingredients; they must not overwrite other properties. Runtime values must be immutable or privately
owned by the built recipe. The registrar can atomically publish the prepared recipes and expansions;
it cannot undo arbitrary external effects. Decoders and attribute adapters are not invoked by native
recipe lookup, and descriptor payloads are not consulted on the matching path.

Retain one `RecipeDefinitionRegistrar` for an owning component. Invoke it on its creating
registration thread while machine ticks and other registry writers are quiescent:

```java
var result = registrar.replace("pack:metals", List.of(recipe));
if (result.value().isEmpty()) {
    result.diagnostics().forEach(d -> logger.error(d.format()));
}
// Replace that group's definitions, or remove every recipe owned by it:
registrar.replace("pack:metals", List.of());
// Or remove all groups owned by this registrar atomically:
registrar.clear();
```

A Java lifecycle adapter can supply an explicit registration-thread predicate when startup and runtime reload
use different threads. It must authorize only one current writer. The Groovy adapter authorizes its startup
thread before a server exists, then the live server thread used by the integrated-server reload command.
This does not enable dedicated-server scripting reload, which GroovyScript does not support.

Declaration identities must be unique within the group. Preparation validates all declarations
and stages detached lookup/category indexes for every affected map. A conflict discards the
staged state. Publication checks map revisions and then performs native state assignments without
callbacks. Replacing or removing a group removes every path to its recipe identities, including
alternative ingredients. Hidden and unowned recipes remain present. Do not mutate owned recipes
through an independent remover; detected ownership changes produce an error.

`RecipeExpansion` is the explicit Java extension contract for preparation-safe build actions:
mutate only the unpublished builder and emit fresh derived builders. It cannot perform external
registry writes. All derived recipes are validated and staged with the primary recipe; a conflict,
exception or expansion cycle rejects the group. Ordinary `RecipeBuildAction` callbacks remain
available through the native API but are rejected by transactional preparation. `compile()`
returns the primary recipe for inspection; use the registrar to publish all expansions.
`SolderMultiplier` and `DistilleryRecipes` attributes control their native builders.

`Research` holds immutable `ResearchRequirement.Existing`, `Scanner` or `Station` declarations with explicit
research IDs and data items. Generated scanner/station recipes and the assembly-line research index are staged
and owned with the assembly recipe. Conflicts publish none of them; replacement removes the complete old group.
The native research enable/disable setting is honored. Existing research is one requirement without generation.
Dynamic maps retain their native lookup behavior. New map subclasses accept registered descriptors through
the shared registration contract without a compiler adapter. Fuel builders support negative EU/t for generation.

`Recycling` requests native material-composition inference for the first deterministic item output.
Composition is staged with the recipes; replacement/removal restores the previous exact entry,
including an absent entry above wildcard data. Chained declarations resolve against the staged
composition regardless of declaration order. Conflicting output composition, dependency cycles
and detected external changes reject the transaction. This owns recycling metadata; it does not
rerun the global generation of recycling machine recipes after that startup phase has completed.

Composition snapshots describe the ingredients available at registration time. An unrelated helper
edit can reuse a resolved snapshot only while recycling metadata and observed ore-unification inputs
remain unchanged. Late ore registrations and direct prefix secondary-material edits invalidate that
snapshot, including when an ingredient previously had no known composition. Publication rejects a
prepared transaction if those inputs change before it is applied. Resolve script-created ingredients
after their content-registration phase; immutability does not prohibit later replacement, removal,
disabling or re-enabling recipes.

The native recycling manager shares immutable `RecyclingContribution` snapshots across owners and
their groups, including contributions whose input composition is not known yet. Replacing or removing an upstream group
recomputes retained dependents before publication. Conflicts in those dependents report their own
source. Exact entries take precedence over wildcard data during dependency resolution. Independent
registrars share this graph; equal group names under separate owners remain distinct. Clearing one
owner preserves the other owners and recomputes their dependents. Native integrations retain a
`RecyclingOwner` and use `RecyclingManager.ownership().prepare(...)` on the quiescent registration
thread. Prepared changes reject stale ownership or registry revisions before publication. Arbitrary
direct registry writes do not automatically create graph contributions. Pack helpers must explicitly
adopt these ownership contracts. Startup capture and generated-recycling ownership are available
through the explicit contracts below.

`RecyclingManager.beginNativeCapture()` opens an opt-in collector before native recipe registration.
It records accepted recycling-enabled machine and shaped recipes, including unknown compositions.
`snapshot()` returns immutable contributions in registration order, filtering removed or replaced
recipe identities against the current native registries. Equal-looking replacement recipes remain
distinct. Capture is confined to its creating registration thread; nested capture is rejected.
Close the collector after taking the required snapshots. It is not a reload subscription and does
not automatically publish an owned graph or capture recipes registered before it opened.

Shaped declarations count consumed cells, exclude tool symbols and preserve the caller's input array.
Material arithmetic completes before crafting registration, so overflow cannot leave a newly registered
recipe behind. Native `GTRecipeInput.isRecyclingInput()` controls participation consistently for direct
inference and captured descriptors. Nonconsumable inputs and specialized `FluidCellInput` containers
do not contribute material. Fluid-cell copies retain their matching and recycling behavior. Registration
input interning now distinguishes concrete input classes and recycling policy in addition to the
input's equality contract, preventing ordinary ingredients from replacing specialized input behavior.

Native `RecyclingContribution` declarations may include direct material quantities alongside item
counts. Both maps are copied, quantities are positive `long` values per batch, and material identity
includes its actual registered material object rather than an unqualified name. The default
`CONSISTENT` policy still rejects conflicting inferred compositions. Opt-in `MINIMUM_INPUTS` routes
intersect item and material identities separately and take the minimum exact quantity per output.
Two different items do not become a common ingredient merely because they contain the same metal.
`REPLACE` discards preceding routes for that output; subsequent minimum-input routes can constrain it.
Route order is declaration-list order within the owning group. These policies do not bypass ownership
of another group's output. Mixing strict and minimum policies requires an explicit replacement.

Route reduction occurs before cycle detection, so discarded dependencies do not create false cycles.
Registration arithmetic uses exact rational intermediates and rounds only each final material total
to a checked `long`. This also avoids narrowing direct material amounts to 32 bits. An empty minimum
intersection publishes `RecyclingData.empty()`, explicitly suppressing previous exact/wildcard and
unification fallback. A temporarily unknown minimum route conservatively has no recoverable material;
its contribution remains retained and recomputes when upstream data arrives. Removing its owner restores
the prior native entry. Native recycling recipe generation skips empty compositions.

`RecipeBuilder.buildAndRegisterWithReceipt()` runs native build actions and returns the exact map
and primary recipe accepted by registration. Validation and lookup rejection return an empty result.
Recycling is computed from the post-validated recipe before admission, then published only after
admission succeeds. Missing deterministic outputs reject recycling-enabled recipes; final composition
overflow throws before recipe admission. This native path is not an owned group transaction and
cannot undo arbitrary callback effects or already registered derived recipes.

Native builders invoke `postValidateRecipe` once, then the map's final admission methods.
`addRecipe` and `compileRecipe` cannot be overridden. Addon validation belongs in
`postValidateRecipe`; secondary indexes belong in declared `RecipeRegistrationState` values.
Addons tracking registration should use the receipt rather than private builder callbacks. Derived
registrations have their own results; the primary receipt does not represent an entire expansion group.

Contribution keys deliberately follow the native item/metadata composition registry; NBT and
capabilities are not part of that registry identity. The existing native callback overload still
receives the original first-alternative input stack, including its NBT. Registration arithmetic
uses exact intermediate totals and truncates once per output to a checked `long`; it does not wrap
on multiplication overflow. Unspecified negative prefix quantities contribute no inferred primary
material; explicitly negative composition data is rejected. No graph work enters machine ticks.

An expansion may also return immutable `FurnaceRecipeDefinition` values from `furnaceRecipes(builder)`.
The registrar stages these with the machine recipes and owns both vanilla smelting and output-XP entries.
Inputs use vanilla item/metadata matching: `of` explicitly normalizes their count to one and rejects NBT
or capability constraints. Outputs retain count, NBT and capabilities. XP must be finite and nonnegative.
Overlapping exact/wildcard inputs, conflicting output XP or item XP overrides reject the group. Compatible
existing XP entries remain unowned; groups sharing the same output and XP retain that entry until its last
owner is removed. External changes to owned entries, or outside producers that would lose XP on removal,
reject replacement/clear with source diagnostics. Native furnace tables keep their object identities.
Direct `buildAndRegister()` still uses the ordinary native helper's duplicate handling; transactional
publication requires the registrar. Furnace matching and ticking remain Minecraft's native implementation.

This transaction covers owned recipe/index state, recycling metadata, furnace effects and preparation-safe expansions. It does not cover
GroovyScript backup bookkeeping, arbitrary direct mutations, concurrent writers, or process-crash
durability. Interned ingredient caches may receive reusable entries during preparation; these are
not recipe registrations. Current staging copies affected indexes, so registration cost must be
measured on realistic large groups before making performance claims.

## Fluid temperature inference during authoring

`FluidBuilder.previewTemperature(material, key)` queries the same temperature inference used by
native fluid construction without registering a fluid or changing the builder. An explicit builder
temperature wins; otherwise its explicit state takes precedence over the storage key's default.
Plasma inference uses an already registered base fluid when available and does not speculate about
future registrations. A preview is therefore a query against the current material state, not a
promise about a later registration after other content changes.

Startup helpers can obtain queued builders through `FluidProperty.getQueuedBuilder(key)` and
call this API instead of reading private builder fields. Queued builders are only available before
fluid registration; use registered native fluids afterward. Keep these queries in authoring code,
outside recipe matching and machine ticks.

## Script-defined fluid containment

Named `FluidAttribute` decisions configured through Java or Groovy survive pipe-size conversion
and graph serialization, including explicit denials. Saved graph keys retain matching identity;
tooltip callbacks stay with the registered material and are not serialized.

When a fluid-pipe tile loads, its material supplies the graph's derived named attribute decisions.
This also repairs older graph records that saved only acid resistance. Recovery requires the same
loaded tile and matching saved throughput, channel count, temperature and built-in proof flags.
It replaces only that node's property value, retaining connections, paint mark, activity and tank
contents; it does not modify shared property objects or infer properties for unloaded tiles.
Ordinary fluid handling remains native and does not invoke the authoring script.

## Content registration and runtime support

Content becomes available through its native registration, including declarations authored in Groovy.
Consumers resolve registered identities after the relevant content phase. Reloaded does not parse,
rewrite, or repair external quest databases. Native saved-state migration remains separate from
content registration: it translates historical identities rather than making current content valid.

Asynchronous HEI reload requires the repaired dependency listed in
[release validation](pre-delivery-qualification.md). That report defines the tested runtime,
reload, persistence and multiplayer scope. Pack and addon compatibility requires separate testing.
CT adapter sources and dependencies are removed; migrate callers using
[the migration guide](ct-migration.md).
