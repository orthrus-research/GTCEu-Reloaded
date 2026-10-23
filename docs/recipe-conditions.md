# Recipe conditions

Recipe conditions let a recipe require an environment at startup and while it runs. Built-in conditions cover biome registry IDs, an inclusive Y range, day or night, a time window, global rain/thunder strength and nearby blocks. Existing cleanroom and dimension properties remain available.

## Add conditions

Java and Groovy recipe builders expose the same convenience methods. Java can also pass descriptors from `gregtech.api.recipes.conditions.RecipeConditions` to `RecipeBuilder.condition`:

```java
builder.condition(RecipeConditions.height(40, 100, false))
       .conditionBiome("minecraft:plains")
       .conditionBiome("minecraft:forest")
       .conditionRain(0.7f)
       .conditionAdjacentBlocks(new String[][] {
           { "minecraft:stone", "minecraft:cobblestone" },
           { "minecraft:planks@2" }
       });
```

The two biome entries are alternatives. The height, weather and adjacent-block requirements must also pass. Each adjacent row needs its own matching neighbor; a single block cannot satisfy two rows. The search covers the 18 face and edge neighbors, excluding corners. Entries use registry names with optional `@metadata`, not OreDictionary names or tags.

```groovy
builder.conditionTime(23000, 1000)
       .conditionThunder(0.5f, true)
```

Time windows are half-open and wrap at 24000 ticks; equal endpoints are invalid. Weather strengths are finite values from 0 to 1 and use the world's global strength. Minecraft's thunder value can depend on rain strength. The optional final boolean inverts a condition.

CraftTweaker exposes explicit methods on `mods.gregtech.recipe.RecipeBuilder`:

```zenscript
builder.conditionBiome("minecraft:plains")
       .conditionHeight(40, 100)
       .conditionDaytime(true) // inverted daytime: require night
       .conditionAdjacentBlocks([["minecraft:stone", "minecraft:cobblestone"]]);
```

Invalid parameters reject the builder. Conditions are immutable; later builder edits do not change an already-built recipe. When recipes are appended, each source's requirements remain an AND clause: `(A OR B) AND (B OR C)` does not become one broad OR group.

## Processing and missing evidence

Conditions are checked during candidate selection and again on the prepared recipe before inputs are consumed. A running recipe whose condition stops passing becomes inactive and retains its progress, sampled outputs and resources. It resumes when conditions and ordinary machine constraints permit. The prepared conditions persist across saves.

Completed products waiting for output space retry independently of conditions and do not draw additional processing energy. Changing the environment cannot reroll those products or restart their processing.

Evaluation distinguishes `PASS`, `FAIL` and `UNAVAILABLE`. Missing adapters, unknown registry IDs, malformed saved values and unavailable world evidence block processing. Inversion never turns unavailable evidence into a pass. A complete known adjacent-block assignment can pass even if an unused alternative ID is missing. World reads only inspect loaded positions and do not load chunks.

JEI shows the registered requirements. Machine diagnostics report the last observed failure without reevaluating a recipe during rendering. Long summaries use explicit ellipses; complete requirements remain stored. GUI and HWYLA details localize on the client; The One Probe's condition details use the server locale.

## Addon adapters and bounds

Register a `RecipeConditionType<T>` through `RecipeConditions.register` before recipes or saved work are read. Give it a stable namespaced ID, validating decoder, canonical encoder, pure evaluator and bounded description. Duplicate IDs reject. Values retained by adapters must be immutable; descriptors must not retain a world, machine or mutable caller-owned object. Adapter evaluation exceptions produce unavailable evidence.

Sets permit 32 distinct leaves, 16 resolved AND clauses and 8 same-type alternatives per clause. Adjacent requirements permit 8 groups with 8 alternatives each. A leaf payload is limited to 16 KiB, depth 8 and 512 nodes; a complete set to 64 KiB, depth 12 and 4096 nodes. NBT arrays are unsupported. Invalid or oversized data remains blocking rather than silently dropping requirements.
