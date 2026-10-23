# Startup recipe modifier API

These examples describe the public API. Registering a custom machine remains the addon's responsibility.

## Java custom-machine provider

Implement `gregtech.api.recipes.modifiers.IRecipeModifierProvider` on a custom machine/controller whose recipe logic
delegates to the standard preparation paths. Return a stable immutable `RecipeModifierList`. For example, the following
method and field can be added to that class:

```java
private static final RecipeModifierList MODIFIERS = RecipeModifierList.of(
        RecipeModifier.named("example:efficiency", (context, recipe) ->
                RecipeModification.builder().energy(0.75, 0).duration(0.5, 0).build()),
        RecipeModifier.named("example:height", (context, recipe) ->
                RecipeModification.builder().conditions(RecipeConditionSet.EMPTY.add(
                        RecipeConditions.height(50, 90, false))).build()));

@Override
public RecipeModifierList getRecipeModifiers() {
    return MODIFIERS;
}
```

Import `gregtech.api.recipes.modifiers.*`, `RecipeConditionSet` and `RecipeConditions` from
`gregtech.api.recipes.conditions`. A second callback observes the first callback's already-adjusted summary. Return
`RecipeModification.IDENTITY` for no change or `RecipeModification.cancel("example.recipe.reason")` to reject startup;
the reason is a bounded translation key. Modifier IDs are unique namespaced IDs, with at most 32 entries per list.

Context exposes the actual machine ID, dimension, immutable position, maximum voltage and parallel limit. The recipe
summary exposes scalar values, provenance, immutable conditions and defensive material/property summaries. Mutating a
returned stack or NBT summary does not alter either recipe. Neither World nor inventory handlers are supplied. Captured
addon state and callbacks must remain pure; the API does not sandbox arbitrary Java code or addon predicates.

## Groovy custom-machine provider

A Groovy custom class implements the same Java interface and returns the same immutable factories; there is no global
registry that retrofits modifiers onto every machine. Inside an existing custom-machine class:

```groovy
import gregtech.api.recipes.modifiers.*
import java.util.function.BiFunction

private static final RecipeModifierList MODIFIERS = RecipeModifierList.of(
    RecipeModifier.named('example:batch', { context, recipe ->
        RecipeModification.builder().itemInputs(2.0d, 0.0d)
            .itemOutputs(2.0d, 0.0d).duration(1.5d, 0.0d).build()
    } as BiFunction),
    RecipeModifier.named('example:setup', { context, recipe ->
        RecipeModification.builder().duration(1.0d, 2.0d).build()
    } as BiFunction)
)

@Override
RecipeModifierList getRecipeModifiers() { MODIFIERS }
```

The class declaration must include `implements IRecipeModifierProvider` and its normal cloned machine must preserve
that class/provider. The example covers provider construction; loading and registering the machine through GroovyScript requires the addon's normal registration. CraftTweaker-created source
recipes work and retain provenance; a CraftTweaker custom-machine registration facade is not supplied.

## Startup and numeric rules

Original registered input quantities and voltage remain discovery minimums. A discount reduces actual consumption only
after that original recipe is discoverable. The existing turbine rotor/fuel discovery rules also remain prerequisites.
Increased adjusted inputs and EU are checked before the selected legacy hook and again before actual consumption.

Numeric channels are EU magnitude, duration, consumable item/fluid inputs and item/fluid outputs. Each applies
`original * multiplier + addition` using finite decimal values and checked conversion. Inputs, duration and EU magnitude
round upward; output counts round downward and zero outputs disappear. Required quantities must stay positive. Catalysts
are unchanged. Producer signs, chance probabilities/boosts and item/fluid chance logics are preserved. Chance quantities
scale with their output channel; legacy parallel chance entries remain flat, without per-operation AND/XOR groups.

One nonempty attempt snapshots the provider and caches transformations by registered source identity across lookup,
fallback, selected checks, parallel preparation and consumption. A distinct bus or paired input view receives its own
attempt with exactly the selected paired handlers. Nested preparation using different item **or fluid** handlers is
unsupported. Reentrant callbacks reject. Every attempt clears in `finally`, including fatal errors. Runtime exceptions
produce bounded diagnostics; JVM `Error`s propagate.

The phase order is output trimming, modifiers, existing EU/speed settings, parallel sizing, machine OC, final prepared
conditions/output/input checks, then consumption/setup. Original `previousRecipe` identity is retained. Public
`evaluateRecipeEligibility` is an explicit selection operation and runs a fresh ephemeral modifier attempt; it is not a
UI getter. Passive lookup and fuel/diagnostic getters do not invoke providers/modifiers. Direct `prepareRecipe` starts a
fresh attempt but retains its existing distinction from selected machine checks and discovery.

Active processing, condition holds, completed-output waits and reload use the already-prepared saved values. They never invoke the
pipeline again. Changing the provider affects the next job. JEI continues to show registered source recipes because
machine-specific state has no universal adjusted value.

## Compatibility and preparation bounds

- Existing machines and empty provider lists use the legacy path without new recipe copies or changed arithmetic.
- Base/multiply, distinct buses, selected input views and large-turbine preparation are integrated. Custom preparation
  overrides must delegate through the documented standard methods/attempt helper; replacing preparation entirely is
  outside automatic integration. An interface marker cannot make a non-delegating override honor transformed values.
- A Processing Array controller can explicitly supply its own modifiers. Installed delegates implementing the provider
  are rejected as unsupported, even if their list would be empty, without invoking their getter. Installed ordinary
  machines retain the existing behavior. Delegate-only numeric modifiers are not silently ignored.
- APPEND_ITEMS admits one consumable item input per source, with no fluid inputs, fluid outputs or chanced fluid outputs.
  The first admitted source controls category/provenance/hidden state. Later sources need equivalent serialized non-condition
  properties and compatible chance logic. Condition requirements compose with AND. Later rejection preserves earlier work.
- Expanded preparation is limited to **4096 combined item/fluid chance entries**. Oversized sources reject before
  copying/callbacks. Multiplication/subtick and append admit a smaller count where their existing partial-parallel
  semantics allow it; turbines reject a required fuel batch that exceeds the bound. Aggregate same-identity potential
  item outputs also stay within positive `int` counts, checked with `long` arithmetic before simulation/building. These
  are explicit allocation/count bounds, not a general limit on arbitrary addon source/property memory.
- Nonempty paths own builtin ingredient/stack/NBT data and copy typed properties through their original codec objects.
  Unknown mutable or identity-array NBT requirements reject as unsupported. Arbitrary custom ingredient predicates and
  property codecs remain responsible for their advertised contracts. The legacy general copy/cache/property semantics
  are unchanged outside these owned copies.

Append capacity probes simulate deterministic outputs followed by all potential chance outputs, in final builder order,
without rolling. They use the existing matching-stack-first insertion simulation, reset their private overlay for every
bounded binary-search candidate, and never mutate admitted builders, source recipes or actual inventories.
