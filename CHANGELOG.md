# Changelog

Changes for players, pack makers and addon developers. GTCEu's history up to the fork is in the
[upstream changelog](https://github.com/GregTechCEu/GregTech/blob/master/CHANGELOG.md) and
[upstream releases](https://github.com/GregTechCEu/GregTech/releases).

## Unreleased

### 3.0 authoring overhaul (alpha)

| Area | Change |
| --- | --- |
| Rendering | Render terrain under Celeritas and Demonica: the bloom pass is no longer applied with them (they draw the bloom layer in their own terrain passes), and emissive textures use their fallback layers there as with OptiFine shaders; before, wrapping their terrain layer call left the world unrendered |
| Build | Normalize packaged text line endings so Windows checkouts and clean source exports produce identical mod JARs |
| Recipes | Clear old recipe-viewer category lookups when HEI rebuilds, preventing duplicate machine recipe links and retained obsolete category objects after reload |
| Recipes | Shaped crafting recycling resolves material ore inputs to the unified GregTech stack, so another mod's item registered first under the same ore name no longer sets the recycling composition |
| Authoring | Let native and typed recipe operations maintain addon-declared registration state and secondary indexes through one shared API |
| Authoring | Keep quantum controller, proxy and extender rendering methods and the clipboard GUI cache client-only so dedicated-server Groovy inspection does not resolve client types |
| Authoring | Remove the remaining CT-specific material-component array accessor; use the native material-component list |
| Pipes | Preserve named fluid-containment attributes through pipe sizing and graph saves; restore loaded pipes' derived decisions without replacing tanks or changing saved geometry and connections |
| Storage | Expose native crate layouts and retention behavior; support retained machine replacement across namespaces without stale harvest state |
| Runtime | Require Java 26 and Cleanroom 0.7.14-alpha; remove the Java 8 and standalone Forge lanes |
| Breaking | Remove the BetterQuesting quest-database migration (`BQuDataFixer`, run at server start) and the `/gregtech datafix` command. Before moving to 3.0, on a 2.x release with BetterQuestingUnofficial: pack makers run `/gregtech datafix` once to migrate the default quests in `config/betterquesting/`, and each existing world is loaded once so server start migrates `<world>/betterquesting/QuestDatabase.json`; native world and item saved-state migration is unchanged |
| Authoring | Apply the GroovyScript mixins only on the tested GroovyScript 1.5.0; other versions keep stock script caching and report typed Groovy definitions as unavailable instead of failing at startup |
| Authoring | Degrade script-cache persistence failures (locked or unreadable `cache/groovy`, link loops) to a session-only cache with a warning; unverified bytecode is still never reused |
| Recipes | Let `clear()` and `/gs reload` release furnace XP, recycling metadata and recovery baselines that outside code now relies on, instead of failing every later reload |
| Recipes | Restore every published participant when a later registration step fails, and keep new crafting names out of the vanilla registry snapshot |
| Build | Publish pre-release tags such as `v3.0.0-reloaded.1-alpha.1` |
| Dependencies | Align CodeChickenLib compilation, tests and runtime on the pinned 3.3.10 release; verify its production artifact hash during the build |
| Inventory | Reject out-of-range slots in combined item handlers before dispatch, preserving input stacks and avoiding missing-handler exceptions |
| Authoring | Replace CT/Zen adapters with shared typed Java definitions, source diagnostics and GroovyScript 1.5.0 authoring |
| Authoring | Handle Groovy hand inspection on both client and server players, preserving copyable item expressions without an unsafe server-player cast |
| Authoring | Generate pipe and cable copy expressions using the owning material registry so cross-namespace materials resolve to the existing item |
| Authoring | Resolve qualified material names exactly across shared storage registries; reject duplicate material identities and occupied IDs before publishing names |
| Authoring | Support explicit Java/Groovy material overrides with source diagnostics while retaining existing numeric identities and captured component references |
| Authoring | Enumerate every current qualified material in declaration order so shared short names cannot hide another owner's content |
| Recipes | Preserve qualified material ownership through typed ore registration, recipe matching and indexing; keep named ore ingredients broad and live |
| Recipes | Preserve material namespaces and live membership in native shaped/shapeless crafting; share ingredient conversion and remove per-recipe reflection |
| Recipes | Expand wildcard ore display variants and refresh packed recipe-book alternatives after late ore registration or removal |
| Recipes | Give generated material recipes owner-qualified names, process current declarations deterministically, and check explicit recipe IDs consistently before registration |
| Saves | Migrate learned and pending recipe-book entries to generated material recipe IDs before loading; retain surviving discoveries for ambiguous former names and respect pack removals |
| Authoring | Preserve owned crafting groups across native registry synchronization during world loading while continuing to reject foreign edits |
| Tools | Serialize built-in tool enchantments in stable registry-name order while retaining level overrides and applicability rules |
| Recipes | Accept tool-only shaped recipes and preserve caller arguments in fluid-replacement and energy-transfer helpers |
| Recipes | Resolve vanilla fluid-replacement buckets through the native bucket API without requiring a universal-bucket registration |
| Machines | Remove unused muffler recovery state from multiblocks and describe exhaust-only behavior accurately in muffler tooltips |
| Recipes | Stage owned registration groups, derived recipes, research indexes, recycling metadata and furnace recipes/XP before publication |
| Recipes | Prepare and replace generated recycling recipes through shared Java/Groovy definitions, validating every affected map before publication |
| Recipes | Own crafting names across typed Java/Groovy groups and stage their explicit composition and generated recovery together; preserve unrelated registrations when restoring baselines |
| Recipes | Retain unchanged crafting declarations and native recipe instances across group edits; fresh declarations, disabling and re-enabling explicitly control preparation |
| Recipes | Let crafting and recovery groups adopt additional native recovery recipes during later edits while retaining every original for cleanup |
| Authoring | Expose immutable accepted crafting groups to Java and Groovy helpers so repeated edits can share registrar state and clear it on reload |
| Recipes | Allow recipes at shared input prefixes; prefer eligible descendants, preserve both on removal/replacement, and refresh affected machine caches |
| Content | Register custom ore items and coils through native APIs; retain saved carrier and explicit material identities |
| Reload | Support typed ownership alongside native Groovy recipe builders during integrated-server reload |
| Reload | Carry crafting baselines into the initial frozen registry and restore the typed frontend before Groovy's ordinary reload callbacks |
| Compilation | Validate script cache reuse against source/dependency/compiler contents and compiled artifact integrity |
| Loader | Use Cleanroom MixinConnector and remapped mixin members; fix server-side inspection of particle and coil interfaces |
| Dependencies | Publish required mod APIs without development loader bundles or optional scripting/integration dependencies |
| API | Rename CT-specific provenance and property hooks; affected Java addons must migrate and rebuild |
| API | Let native addons register exact preparation-safe builder classes and typed descriptor codecs/setters; preserve source diagnostics and owned recipe transactions |

See [the migration guide](docs/ct-migration.md). Alpha qualification does not imply arbitrary pack compatibility.

### Fixes

| Area | Change |
| --- | --- |
| Machines | The Power Substation reports itself active while it holds energy; before, it never did |
| Machines | The Power Substation energy bar shows the actual fill level; before, anything above half charge drew as full |
| Armor | On servers, armor and tool key binds (hover, mode switch, inertia cancel, charging, tool area) reach the server as the key that was pressed; before, a single changed key was recorded as jump |
| Machines | The World Accelerator obeys machine controller covers, the soft mallet and other controllers; before, enabling it paused it and the soft mallet did nothing, and the change was not saved |
| Recipes | Recipe NBT equality conditions on byte, int and long array tags match equal arrays; before, they compared array identity and never matched |
| Recipes | Numeric recipe NBT equality conditions accept any number type; before, a value that was not a `long` failed the recipe check with an exception |
| Machines | The Power Substation prints its stored energy and capacity with the right decimals (12,345,678 EU reads 12.3456M); before, the digits after the point were taken from the wrong position |
| API | `SmallDigits.toSmallUpNumbers` produces superscript digits; before, it produced subscripts |
| Integration | With FTB Utilities, the block breaker respects chunk claims: it breaks blocks in its owner's claims and refuses in other teams'; before, it did the opposite |
| Rendering | Texture metadata that gives separate block and sky light (`"light": {"block": …, "sky": …}`) applies both; before, the sky value overwrote the block value |
| GUI | Editing the green value in an ARGB colour field (monitor plugins) keeps the alpha; before, it made the colour fully transparent |
| Machines | A newly placed diode passes energy straight away; before, it had zero capacity and passed nothing until the world was reloaded or its amperage was changed |
| Machines | Viewing the Large Miner's screen no longer consumes drilling fluid; before, the status check drained it while the screen was open |
| Tools | The tricorder reports the bedrock fluid of the chunk you stand in at negative coordinates; before, it read the neighbouring chunk |
| World generation | A worldgen definition file that fails to parse is skipped with its error; before, it also silently dropped every vein file that sorted after it |
| World generation | Chunk generation heights saved with a world are read back correctly; before, the saved bottom height was ignored after a reload, so later ore generation used the wrong height in those chunks |
| AE2 | ME hatches and buses cap stack sizes they cannot hold at the largest amount instead of wrapping to a negative one when an ME network reports more than 2,147,483,647 mB or items of one kind |
| Tools | A tool belt reports the harvest level of its selected tool for that tool's own material; before, the belt stack was passed in its place |
| API | `ModHandler.removeFurnaceSmelting(UnificationEntry)` removes the furnace recipe of every ore-dictionary variant; before, it stopped after the first one it removed |
| Scripting | GroovyScript `addBlastProperty` on a material without a blast property applies the duration and EU/t overrides as given; before, the two were swapped |
| Storage | Quantum chests and tanks saved by releases before voiding modes were merged load with voiding on if they had partial voiding on; before, that legacy key was misspelled and never read |
| Tools | The foam sprayer updates the neighbours of every block it foams; before, it updated only the neighbours of the block it was aimed at |
| Armor | A jetpack that breaks a fall takes the damage as chest armour; before, the hit was reported for the boots slot |
| Monitors | The text monitor plugin accepts all 16 lines and loads saved colours of any length; before, line 16 threw and a short saved colour list made rendering throw |
| GUI | Crafting station: taking a craft into an empty slot respects that slot's limit, and shift-crafting fills a whole stack; before, a limited slot was overfilled and shift-crafting stopped one craft short |
| GUI | Scrollable panels (terminal apps, monitor and clipboard editors) hide and stop accepting clicks for children scrolled out sideways; before, only the vertical position was checked, and the horizontal scroll bar's drag test read the vertical offset |
| Tools | The prospector map places the player marker correctly on the north-south axis at positive Z, and clicking just left of or above the map no longer adds a waypoint in its first row or column |
| Covers | Energy, fluid, item and activity detectors give the right signal for stored amounts above about 6×10¹⁷; before, the scaled amount overflowed and the signal went wrong or negative |
| API | `PositionedRect.intersects(PositionedRect)` detects every overlap; before, it missed a rectangle inside the other one and cross-shaped overlaps |
| API | `GTUtility.safeCastLongToInt` clamps values below `Integer.MIN_VALUE` too, and the generator tank-size functions give tier 0 the tier 1 size instead of zero |
| Recipes | The sifter recipe for purified gem ores without the high sifter output adds the chipped gem only when one exists; before, it checked the exquisite gem and could add an empty output when chipped gems were disabled |
| Tools | Tools with torch placing remember a torch in the first hotbar slot, and an edited cached slot number no longer throws |
| Multiblocks | Fluid passthrough hatches no longer allocate two unused 16,000-slot item inventories each, which every save and inventory clear walked |
| World generation | Rubber trees check the whole canopy footprint for free space; before, the row on the positive Z side was skipped, so a canopy could replace a block there |
| Machines | The pump moves past positions in unloaded chunks while scanning; before, one unloaded position at the head of its queue stalled the scan for that tick |
| Commands | `/gregtech benchmark lookup` reads its second argument as the rate and reports the third quartile; before, the rate repeated the trial count and Q3 repeated Q1 |
| World generation | Ellipsoid ore veins with radii of about 46 and up generate their full shape; before, the size arithmetic overflowed and the vein came out empty or scrambled |
| World generation | Invalid filler weights and layered veins with the wrong ore count report their value in the error message; before, the message showed a literal `%d` |
| Machines | Fluid drilling rigs on veins with a configured yield above 21,474 produce their scaled yield; before, the arithmetic overflowed |
| Scripting | GroovyScript object names with a one-letter namespace (`x:name`) look up that namespace; before, they fell back to `gregtech` |
| Covers | The ore filter's consecutive-negation warning highlights the whole negated run; before, the highlight covered only the token after it |
| Rendering | Connected-texture blocks rebuild their models after a resource-pack reload; before, models baked against the previous texture atlas stayed in the cache |
| GUI | Nested clipped areas that start left of or above their parent stay inside it, and clipping no longer crashes when another mod replaces the in-game HUD |
| Recipes | JEI output tooltips on assembly-line recipes look up the right chanced output; before, every index was one too high |
| Covers | The digital interface cover's slot arrows cycle through the slots its screen shows and wrap to the last one; before, they used the machine's input inventory, wrapped one past the end, and on non-machine blocks jumped to slot 2,147,483,647 |
| Covers | The advanced energy detector keeps EU thresholds above 2,147,483,647 when switched from percent to EU; before, the maximum was cut to that value |
| Covers | Smart item filters with an out-of-range saved mode load in electrolyzer mode; before, loading them threw |
| Covers | Ore filter expressions that negate a minimum length after alternatives, such as `(a|ab)!(??*)`, reject inputs that one of the alternatives leaves too long; before, they accepted them |
| World generation | Gameplay change: the `single` vein shape places `blocks_count` blocks in a random walk; before, every block went to the same position, so it placed one |
| Multiblocks | Gameplay change: random maintenance failures can pick all six problems; before, the sixth never occurred at random |
| Armor | Gameplay change: QuarkTech leggings move the wearer up and down only underwater and with charge; before, holding jump on land or with an empty battery lifted the player every tick |
| Multiblocks | Gameplay change: large turbines with a high parallel count need fuel for every parallel; before, the fuel requirement could overflow to a negative number and the check passed with no fuel |
| World generation | Gameplay change: the `single` vein shape's walk can continue straight and never steps back onto the block it just placed; before, it was the other way round, so veins often had fewer blocks than configured |
| Armor | Gameplay change: night-vision goggles configured above LV get four times the capacity per tier, like the other electric armour; before, their capacity stayed 80,000 EU at every tier |
| Recipes | Gameplay change: chanced outputs roll at exactly their stated chance; before, each was 0.01 percentage points more likely |
| Machines | Gameplay change: bedrock fluid veins deplete at their configured chance; before, each operation was one percentage point more likely to deplete them |
| Recipes | Gameplay change: fluid-solidifier pipe recipes use the material's working tier for their voltage like every other generated pipe recipe; before, they ignored it |
| Recipes | Parallel recipe checks reject at once when a non-consumable input with a stack size is short; before, some shortages passed this check and were caught only later, after the parallel amount had been computed |

### Earlier fork changes

Changes since the fork from GTCEu at `52c04cd7b`, after 2.8.10.

| Area | Change |
| --- | --- |
| Identity | The mod list, crash reports and the jar name show GTCEu: Reloaded |
| Machines | Item and fluid output sides have independent permissions |
| Machines | Maintenance hatch screens show live maintenance faults |
| Machines | Common simple machines show contextual tool actions |
| Machines | Machine screens explain observed recipe failures without repeating recipe checks |
| Machines | The configuration memory card copies settings between machines, covers and pipes |
| Machines | Drum and quantum tank items can show fluid badges (off by default) |
| Recipes | JEI shows recipe-local overclock estimates |
| Recipes | Eligible recipe alternatives are tried without consuming rejected inputs |
| Recipes | Immutable recipe conditions and processing holds |
| Recipes | Composable startup recipe modifiers |
| Multiblocks | JEI multiblock previews support validated editing |
| Multiblocks | Output insertion is filtered and blocked completed products are retained |
| Multiblocks | Combined item and fluid hatches |
| Multiblocks | Opt-in paint groups and private input views |
| Covers | Item and fluid filters combine with guarded OR matching |
| Covers | Conveyors keep recovery items when block drops are disabled |
| Applied Energistics | ME stocking parts have minimums and refresh controls, and prefer abundant resources |
| Applied Energistics | ME processing pattern buffers and linked proxies |
| Pipes | PipeNet transport accounting and persistence are repaired |
| Pipes | Item pipe state stays independent across copies and dimensions |
| Pipes | Withdrawn fluid returns to the pipe when a destination fill fails |
| Pipes | Ticking pipe networks are tracked by identity, and route callbacks wait for their world |
| Performance | Unchanged energy values send no notifications |
| Performance | Idle recipe callbacks and auto-output capability probes are skipped |
| Performance | Lighter pipe chunk accounting and storage caches |
