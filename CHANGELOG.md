# Changelog

Changes for players, pack makers and addon developers. GTCEu's history up to the fork is in the
[upstream changelog](https://github.com/GregTechCEu/GregTech/blob/master/CHANGELOG.md) and
[upstream releases](https://github.com/GregTechCEu/GregTech/releases).

## Unreleased

### 3.0 authoring overhaul (alpha)

| Area | Change |
| --- | --- |
| Breaking | The recipe, material, meta item, machine and tool types no longer name optional mods, so an addon using them compiles against the published artifact without GroovyScript, AE2, BuildCraft, CoFH Core, Ender IO, EnderCore, Forestry, Project Red or Railcraft. `RecipeBuilder`'s `inputs` and `notConsumable` overloads taking GroovyScript ingredients are GroovyScript expansions (scripts are unchanged; Java passes a `GTRecipeInput`), `validateGroovy(GroovyLog.Msg)` is `validateScripted(List<String>)`, and `RecipeScripting.isRunning()` tells whether a script is running. `IGTTool` no longer extends the mods' tool interfaces nor `MetaItem` EnderCore's overlay interface: while a mod is loaded, mixins add them to `ItemGTTool`, `ItemGTAxe`, `ItemGTSword`, `ItemGTHoe` and `MetaItem`, whose subclasses keep them; an addon implementing `IGTTool` on another item class declares them itself. The build compiles a sample addon against the published classpath ([docs/ct-migration.md](docs/ct-migration.md#optional-mods-and-the-published-api)) |
| Breaking | Remove the API whose announced removal (2.9 or 2.10) is overdue and deprecated API nothing uses. Recipe maps: the UI forwarders `isHidden`, `setProgressBar`, `setSlotOverlay`, `createJeiUITemplate`, `createUITemplate`, `createUITemplateNoOutputs`, `addInventorySlotGroup`, `addSlot`, `getOverlaysForSlot`, `getPropertyHeightShift`, `getPropertyListHeight`, `setSpecialTexture` and `addSpecialTexture` (use `getRecipeMapUI()`), `Recipe.getMaxChancedValue` (use `ChancedOutputLogic.getMaxChancedValue`) and `RecipeBuilder.invalidateOnBuildAction` (drop the call). Covers: `CoverUIFactory` and `CoverWithUI`'s `createUI` and `usesMui2`; a cover's screen is its ModularUI 2 panel. Fluids: `GTUtility.getFluidTranslation` and `GTMaterialFluid.toTextComponentTranslation` (use `KeyUtil.fluid` or `GTMaterialFluid.getLocalizedKey`; old-GUI tanks keep `getFluidTextComponent`). Recipe inputs: the `getOrCreate` factories of `GTRecipeInput`, `GTRecipeItemInput`, `GTRecipeOreInput`, `GTRecipeFluidInput` and `IntCircuitIngredient` (use the constructors or the ingredient). Bloom, kept deprecated for addons until now: `BloomEffectUtil`, `BloomEffect`, `BloomType`, `IBloomEffect` and `GTBloomParticle` (use `EmissiveLayer`, `GTBloomKind.contribute` and `GTParticle`; [docs/emissive-rendering.md](docs/emissive-rendering.md#removed-api)). Also `IProgressBarMultiblock` (use `ProgressBarMultiblock`), `ModCompatibility` (use `ItemRenderCompat.getRepresentedStack`), `MetaTileEntity.getModularUI`, `FuelMultiblockController.addFuelText`, `Material.Builder.addDefaultEnchant` (use `MaterialToolProperty.Builder.enchantment`), the varargs `MultiblockShapeInfo.Builder` constructor (use `MultiblockShapeInfo.builder`) and `GuiTextures.SLOT_DARKENED` with its texture |
| API | Features that were on only for one exact class are hooks that subclasses inherit and override to opt out: configuration cards and tool hints of the conveyor and pump covers, simple machines and item and fluid pipes (`getConfigurationAdapter()`, `getToolActionHint()`), JEI preview editing (`supportsPreviewEditing()`), output bus and hatch filters (`supportsOutputFilter()`), paint groups (`canUseRecipeInputViews()`), simple machine idle and auto-output scheduling (`supportsIdleRecipeScheduling()`, `supportsAutoOutputScheduling()`), tank item fluid badges (`showsItemFluidBadge()`) and the assembly line and blast furnace candidate pre-checks (`isRecipeEligible()`). Recipe logic declares a `checkRecipe` that replaces or relaxes the inherited requirements by returning false from `supportsRecipeCandidateEligibility()`; otherwise its candidates are pre-checked and the search falls back past ineligible ones. Recipe maps get revision tracking and candidate fallback per map: `RecipeMapBuilder` admits the maps it builds, and other maps opt in with `allowRecipeRevisionTracking()` and `allowRecipeCandidateFallback()` |
| Breaking | Remove `AbstractRecipeLogic.usesLegacyRecipeCheck` and `PaintGroupSupport`. Addon subclasses now inherit the features above: a recipe logic whose `checkRecipe` replaces or relaxes the inherited requirements returns false from `supportsRecipeCandidateEligibility()`, a controller whose `checkRecipe` accepts recipes its parent's `isRecipeEligible` rejects overrides that too, a subclass whose settings, tool clicks, push methods or idle work differ overrides the matching hook, and a plain `RecipeMap` constructed without `RecipeMapBuilder` calls the two admission methods to keep those optimisations |
| Rendering | Draw no bloom of GTCEu's own: emissive textures are meshed in an emissive layer declared through OrthLib's render interface, and the fusion reactor ring and cable heat glow are emissive contributions with their own bloom kinds; a renderer that provides the interface meshes and draws them and computes the glow. Without one, emissive textures are drawn full-bright in `CUTOUT`, OrthLib draws the fusion reactor ring and cable heat glow plainly, and nothing glows. The bloom pass, its framebuffers and shader programs, the depth texture and the `BLOOM` game layer are removed with their Mixins ([docs/emissive-rendering.md](docs/emissive-rendering.md)) |
| Dependencies | Require OrthLib 0.5.0-dev or later; the build compiles against the OrthLib, ModularUI and CodeChickenLib files the Set names, by SHA-256 |
| Breaking | Retire the bloom style, mipmap count and `hookDepthTexture` settings and the fusion reactor and heat effect `useShader` switches. GTCEu's custom textures still read a `BLOOM` texture layer as the emissive layer, which CTM cannot read; GTCEu's own textures name `CUTOUT`. The Vintagium and Nothirium Mixins, which added their `BLOOM` passes, are removed |
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
| Build | Publish releases from the bytes the Set names for GTCEuR's line instead of rebuilding on a tag; a published release is checked against the Set, and fails when an asset is not the name and SHA-256 a Set revision names at its tagged commit |
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
| Breaking | Remove classes that nothing in GTCEu used: `BlockableSlotWidget`, `DrawableWidget`, `ImageTextFieldWidget`, `IncrementButtonWidget`, `IndicatorImageWidget`, `PhantomFluidWidget`, `PhantomSlotWidget`, `ServerWidgetGroup`, `SortingButtonWidget`, `SuppliedImageWidget`, `SyncableColorRectWidget` and `tab.IGuiTextureTabInfo` in `gregtech.api.gui.widgets`, `gregtech.api.mui.InputAccessor`, and outside the API `CycleItemStackHandler`, `ModeSwitchBehavior` and `OreGlobCompileStatusWidget`. An addon that used one must keep its own copy |
| API | Laser and optical pipes extend shared beam pipe bases in `gregtech.common.pipelike.beam` (block, item, tile, net, world net, handler, walker and route), which an addon can extend for a pipe family of its own; their methods keep their names and types, and addons built against an earlier version must be rebuilt |
| API | A cover declares each configurable setting once as a `CoverSetting` (name, saved key, card key, type with bounds or constants, default, getter and setter) and lists them in `CoverSettings`, which saves, loads and card-copies them and builds their sync values. `CoverConveyor.SETTINGS` and `CoverPump.SETTINGS` and their settings are public; saved keys and card keys are unchanged |
| Breaking | Remove the load-time conversions of old saves, which data version `V6_LEGACY_MACHINE_AND_COVER_DATA` now applies once: `BaseFilterContainer.handleLegacyNBT`, `handleLegacyNBT` and the legacy key constants of the filter readers, and `MetaTileEntityQuantumTank.legacyTankItemHandlerNBTReading`. A subclass of the changed covers and machines is converted by its class; an addon reader that overrode `handleLegacyNBT` drops the override |
| API | `GTConfig.get()` (`gregtech.api.config`) answers the configuration values the API reads, read-only and as the config file has them now, and the API reads them there instead of from `ConfigHolder` (the old-GUI and ModularUI 2 screen code follows when its move to ModularUI 2 lands). `ConfigHolder` keeps its fields and the config file its entries, names and layout |
| API | The API has its own helpers for what it borrowed from the implementation: `ModifierKeys.isShiftDown()` and `isCtrlDown()` (`gregtech.api.util.input`) and `BlockUtility.statePropertiesToString`, and `GTFluidRegistration` keeps the fluid blocks it registers. `TooltipHelper`'s key checks, `MetaBlocks.statePropertiesToString` and `MetaBlocks.FLUID_BLOCKS` stay and answer the same |
| Breaking | `MonitorPluginBaseBehavior.customUI` is replaced by `getConfigWidth`, `getConfigHeight` and `buildConfigUI`, which build a plugin's settings in ModularUI 2; the legacy `WidgetPluginConfig`, `WidgetCoverList`, `WidgetMonitorScreen`, `WidgetScreenGrid`, `WidgetScrollBar` and `WidgetARGB` are removed |
| Breaking | Remove the legacy item screen path now that every item screen is a ModularUI 2 screen: `PlayerInventoryHolder`, `PlayerInventoryUIFactory` and its UI factory registration, `ItemUIFactory.createUI`, `MetaItem.createUI` and `IntCircuitIngredient.adjustConfiguration(PlayerInventoryHolder, int)`. An item screen implements `ItemUIFactory.buildUI` and opens with `MetaItemGuiFactory.open` |
| Breaking | Remove the old-GUI code that only the screens moved to ModularUI 2 used: the `gregtech.common.gui.widget.appeng` widgets with their `slot` package, `FixWiringTaskWidget` (the April Fools' wiring task is now `gregtech.common.mui.widget.FixWiringTaskWidget`), the terminal's `SelectorWidget`, `PatternBufferTextWidget` and `MetaTileEntityCreativeEnergy.getTextFieldValidator`. An addon that used it must keep its own copy. The ME input and output parts' screen hooks `createConfigWidget`, `addSideWidgets`, `createUITemplate` and `createBufferWidget` are replaced by ModularUI 2 ones; an addon part builds on `buildUI` |
| Breaking | Recipe map screens are laid out as data for ModularUI 2. `RecipeMapUI` and `RecipeMapBuilder` take ModularUI 2 textures (`GTGuiTextures`, `UITexture`) and fill directions (`ProgressWidget.Direction`) for their progress bar, slot overlays and special texture, in place of `GuiTextures`' `TextureArea` and `ProgressWidget.MoveType`; `setProgressBarMoveType` and `progressBarMoveType` are `setProgressBarDirection` and `progressBarDirection`. `RecipeMapUI` lays out a `RecipeMapLayout` of slots, tanks, progress bars and images with `createLayout` and `createJeiLayout`, which replace `createUITemplate`, `createUITemplateNoOutputs` and `createJeiUITemplate`, and its `addInventorySlotGroup`, `addSlot` and `addSpecialTexture` add to a layout. JEI and HEI categories draw that layout, with the same slots, tanks, progress bars and positions |
| Breaking | Electric single-block machines (`SimpleMachineMetaTileEntity`) open a ModularUI 2 screen with the slots, tanks, buttons and tooltips the old one had; `RecipeMapLayout.addWidgets` places a recipe map layout on a ModularUI 2 panel. Removed with the old screen: `createGuiTemplate`, `createUI`, `getXmasLogo` (`getLogo` picks the Christmas logo) and `getCircuitSlotTooltip` (the circuit slot shows its own); `getLogo` and `getCircuitSlotOverlay` return a `UITexture`. A subclass that added to the old screen overrides `buildUI` instead. The recipe failure indicator and the recipe progress bar that opens JEI are ModularUI 2 widgets, `RecipeFailureWidget` and `RecipeProgressWidget` in `gregtech.api.mui.widget` |
| Breaking | Single-block generators (`SimpleGeneratorMetaTileEntity`) open a ModularUI 2 screen with the input slots, tanks and progress bar the old one had; `createGuiTemplate` and `createUI` are removed, and a subclass that added to the old screen overrides `buildUI` instead. Machines and generators lay out their recipe map screen with `createRecipeLayout`, which a generator that does not handle its recipe outputs builds without them |
| Breaking | Steam single-blocks and boilers open ModularUI 2 screens in the bronze or steel theme, with the slots, tanks, progress bars and images the old ones had. `SteamMetaTileEntity` builds its screen from the `RecipeMapLayout` its subclasses return from the new `createScreenLayout`, which replaces `createUITemplate`; `SteamMachineDefinition.Builder` takes `gregtech.api.mui.SteamTexture` and `ProgressWidget.Direction`, and the definition lays out its machine's screen with `createLayout`. `SteamBoiler` builds its screen and has its subclasses add their fuel with the new `addFuelWidgets`, which replaces `createUITemplate`; its unused `bronzeSlotBackgroundTexture`, `slotFurnaceBackground` and `getGuiTexture` are removed. `GTGuiTextures` has the steam variants of the old `GuiTextures` steam textures under the same names. `gregtech.api.gui.widgets.RecipeFailureWidget`, which only these screens used, is removed |
| Recipes | The tiered recipe loaders build each tier from one table row instead of a copied block. The machine casings, crafted and assembled, the hulls in the assembler, the hermetic casings, rotor holders, super and quantum chests and tanks, 4A, 16A and 64A hatches and laser hatches now read the tier's plate and cable from `CraftingComponent`, so an addon that replaces `CraftingComponent.PLATE` or `CABLE` changes them as it changes the machine recipes; with GregTech's own components every recipe is as before |
| Breaking | The renderers and recipe viewer pages that drew the old GUI's textures draw ModularUI 2 ones. `GTGuiTextures` has the recipe viewer icons `MULTIBLOCK_CATEGORY`, `ARC_FURNACE_RECYCLING_CATEGORY`, `MACERATOR_RECYCLING_CATEGORY` and `EXTRACTOR_RECYCLING_CATEGORY` and the lamp item overlays `LAMP_NO_BLOOM` and `LAMP_NO_LIGHT`, at the old textures' paths. `GTRecipeCategory.jeiIcon` takes a `UITexture` where it took a `TextureArea`, `RenderUtil.renderTextureArea` draws a `UITexture`, and `FluidTooltipUtil.addIngotMolFluidTooltip` also adds its line to a plain tooltip list, as `TankWidget.addIngotMolFluidTooltip` did |
| Breaking | Move what the old GUI packages held for code outside screens: `ResourceHelper` is `gregtech.api.util.ResourceHelper`, and the deprecated `gregtech.api.gui.resources.ResourceHelper` forwards to it; the prospector's `ProspectorMode` and `ProspectingTexture` are in `gregtech.common.mui.widget.prospector`; the central monitor's picture textures (`PictureTexture` and its subclasses, no longer an `IGuiTexture`) are in `gregtech.client.renderer.texture.picture`, and its image download, decoding and cache (`DownloadThread`, `GifDecoder`, `ImageUtils`, `ProcessedImageData`, `TextureCache`) in `gregtech.client.utils.image`. The old GUI's `FileTexture`, `URLTexture` and `ModifyGuiTexture`, which no screen drew, are removed |
| Breaking | Remove the old GUI system, now that every screen is a ModularUI 2 screen: the `gregtech.api.gui` and `gregtech.common.gui` packages (`ModularUI`, `Widget` and the widgets, `GuiTextures`, `UIFactory`, `IUIHolder`, `BlankUIHolder`, the textures in `resources`, the JEI hooks in `ingredient` and the terminal widgets and search), `MetaTileEntity.createUI`, `MetaTileEntityUIFactory`, `GregTechAPI.UI_FACTORY_REGISTRY` with its `RegisterEvent<UIFactory>`, `MetaTileEntityHolder.getUIColorOverride`, `MCGuiUtil`, the JEI handler `ModularUIGuiHandler` and the packets `PacketUIOpen`, `PacketUIWidgetUpdate` and `PacketUIClientAction`, whose ids 1 to 3 stay held so that the other packets keep theirs. A machine builds its screen in `buildUI` and opens it by returning true from `usesMui2`, which stays and still defaults to false for machines without a screen; covers and items build theirs in `buildUI` as before. `IGregTechTileEntity` declares `isValid` and `isRemote`, and `CoverWithUI` keeps them and is an `IDirtyNotifiable`, as they were through `IUIHolder`. The `useSprayCanColorInUI` client option, which only the old screens read, has no effect. The deprecated `gregtech.api.gui.resources.ResourceHelper` stays and forwards to `gregtech.api.util.ResourceHelper`. An addon that used the old GUI moves its screens to ModularUI 2 (`gregtech.api.mui`) |

See [the migration guide](docs/ct-migration.md). Alpha qualification does not imply arbitrary pack compatibility.

### Fixes

| Area | Change |
| --- | --- |
| Compatibility | The One Probe and HWYLA show machines from one shared set of lines, so HWYLA now matches The One Probe: lines in its colours; maintenance only where it applies; machine modes only for machines with several; recipe failure amounts in the short form; steam boilers cooling down, heating up by percent and missing water; transformer and diode sides as the machine has them; spaces in quantum storage lines; laser container energy under a new Laser Containers option; and no "Energy: 0 EU / 0 EU", "Idle" or second duration line for computation. Its active transformer and ME network options take effect. HWYLA now reads machine modes and quantum controllers on the server, as it already read the rest |
| Tools | Swords, knives, butchery knives and scythes (`ItemGTSword`) pass `onItemUseFirst` to their tool behaviours as the other tools do; before, they skipped it, so a behaviour that acts there, such as an addon's, did nothing on them |
| Machines | Steam multiblocks melt the snow in front of their controller when they vent, a layer at a time, as single-block steam machines do; before, they cleared only a single layer and vented no steam through deeper snow or a snow block |
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
| Tools | Gameplay change: Fortune on a hard hammer adds crushed ore when it hammers ore blocks, as its code intended; before, Fortune never applied |
| API | `GTValues.VAOC` holds 15/16 of each `VOC` entry; before, eight hand-typed entries were wrong |
| Multiblocks | Large turbine screens show the fuel need reduced by the rotor's efficiency bonus; before, a bonus below 200 % was ignored and higher ones were rounded down to whole multiples |
| Localisation | The low rotor durability warning, the inertia-cancel key bind, recipe modifier failure details and the `/gregtech recipecheck` usage show text instead of their raw translation keys |
| Config | The tools section no longer shows a second Armor HUD Location block that had no effect; the HUD position is set under client options as before |
| Energy | An energy container list no longer accepts more amperes from a network than were offered; each container now sees only the amperage still unclaimed. |
| Fluids | `MultiFluidFilter` now tests the fluid it is given and honours its blacklist flag; it used to accept every fluid. |
| Fluids | `IFilteredFluidContainer.COMPARATOR` no longer throws on a container without a filter; such containers sort last. |
| Networking | `NetworkUtil.writeFluidStack` no longer writes a tag after a null stack, which misaligned every value read after it. |
| GUI | `ModifyGuiTexture.loadConfig` loads each texture type as saved; a saved text texture used to come back as a plain colour rectangle. |
| GUI | Phantom slots adjust their count when clicked with the same item and take the held stack otherwise; `PhantomSlotUtil.areItemsEqual` returned true for different stacks. |
| Materials | Gameplay change: materials built with `colorAverage()` average each colour channel separately. Ruridit turns from green-grey `0x6BBF7F` to the blue `0x6BBED4` of its ruthenium and iridium components |
| Items | Gameplay change: food potion effects apply with their stated chance. The chocolate coin gives Speed 10% of the time and the purple drink gives Haste 90% of the time; before, the odds were the other way round |
| Networking | A GregTech packet with an unknown id is logged and ignored instead of throwing in the network handler. |
| Modules | A module skipped for a missing dependency now also skips the modules that depend on it, each with a log line; modules left out by a dependency cycle are logged. |
| Armor | GregTech armour no longer leaves blending enabled after it renders, which could tint entities drawn after it. |
| Tools | Repairing GregTech tools through automated crafting no longer posts a tool-destroyed event without a player. |
| World generation | Gameplay change, custom configs only: bedrock fluid veins with a negative biome modifier no longer change the odds of other veins or of no vein. Bundled veins roll as before |
| Utilities | `GTUtility.nearestLesserOrEqual` and `nearestLesser` terminate for every input, and `formatLongToCompactString` rejects a precision below 3 instead of dividing by zero. |
| Items | Code that checked for an empty item stack now also treats zero-count stacks as empty, not only the shared empty stack. |
| Multiblocks | The Power Substation's passive drain after a battery change no longer includes a remainder left from the previous batteries, which could add 1 EU/t. |
| Saved data | Machines and covers whose saved mode is out of range load with their default mode instead of failing to load. |
| Multiblocks | The multi smelter accepts coils with no energy discount, the HPCA's computation bar shows empty instead of NaN when it has no capacity, and the central monitor follows its EU-cost config without a restart. |
| AE2 | Breaking an ME output hatch that is not on a working network now unforms its multiblock like any other hatch. |
| Multiblocks | Gameplay change: touching a spinning turbine rotor below 1000 rpm does damage in proportion to its speed; before, it did none |
| AE2 | An ME input bus slot reconfigured to an item that differs only in NBT no longer keeps counting stock of the previous item. |
| Cables | A cable overloaded within its own voltage tier, possible only with custom cable limits, now heats up instead of cooling down. |
| GUI | Scrolling a list that fits in its window no longer pushes the content down. |
| GUI | `TextFieldWidget` accepts text without a validator and keeps its configured maximum length on dedicated servers. |
| Machines | The World Accelerator stops ticking a neighbouring tile entity as soon as that tile removes itself. |
| Machines | The pump returns to a fluid block it skipped because its tank was full, instead of ignoring the block until its next full rescan. |
| Machines | The fisher's bait use is saved even when nothing else changes in the machine. |
| Clipboard | Left-clicking a clipboard outside its page no longer throws, and a clipboard whose GUI cannot be built logs once instead of every tick. |
| Machines | Gameplay change: a finished charcoal pile converts only the logs still inside it; before, blocks at positions recorded by earlier or larger pile checks also turned into brittle charcoal |
| Crafting | Gameplay change: the crafting station damages, empties or uses only as many tools, buckets and cells as the recipe needs; before, one craft damaged every matching tool and emptied every matching bucket in the station, and one needing two items from a stack used only one |
| Storage | The quantum storage controller no longer throws when its network changes before every saved storage has been loaded. |
| Covers | Turning a conveyor, pump, shutter, voiding or ender link cover on or off is saved even when nothing else changes in that chunk. |
| Covers | The fluid regulator and advanced fluid voiding covers no longer overwrite the amount in tanks that expose their live contents, and the voider leaves tanks below its keep amount alone. |
| Localisation | Machine ids, texture paths and translation keys no longer change under a Turkish system locale. |
| Multiblocks | Gameplay change: a large boiler that runs short of water uses its distilled water or configured boiler fluids for the rest instead of exploding |
| Multiblocks | Fuel a large boiler burns from an input bus is saved with that bus, so it is not returned after a reload. |
| Config | A miner replacement block configured as a bare name or with a non-numeric meta no longer stops every miner from being created. |
| Saved data | A machine saved mid-recipe with a fluid output from a removed mod finishes the recipe without that fluid instead of failing. |
| API | `MTEManager.getRegistry(int)` returns null for an unknown network id instead of the GregTech registry. |
| Recipes | Machines with more than 64 different inputs find recipes whose ingredients sit more than 64 slots apart. |
| Recipes | A recipe with a rejected input, such as a null fluid or a negative count, is now refused instead of registering without that input. |
| Recipes | Two mods can create recipe categories with the same name without one taking over the other. |
| Tools | Tree felling stops when the player changes dimension and no longer breaks blocks placed where a felled log used to be. |
| Armor | Equipping electric armour and flying a jetpack play their sounds for nearby players. `ArmorUtils.JET_ENGINE` moved to `GTSoundEvents.JET_ENGINE`. |
| Armor | Gameplay change: swapping the quantum chestplate for another GregTech chest piece ends its fire immunity; before, it lasted until the slot was emptied. Night vision from potions is no longer removed by GregTech armour |
| Armor | Other players and armour stands wearing NanoMuscle or QuarkTech armour show their own advanced or normal texture, not yours. |
| API | The `StoneTypes` constants are `final`. |
| API | `Material.setProperty` and `MaterialProperties.setProperty` take a value of the key's property type, so a mismatched property no longer compiles. |
| World generation | Gameplay change, custom configs only: a one-layer layered vein places its sporadic block at the configured density instead of everywhere, and a `state_match` filler may leave out "default". Bundled veins generate as before |
| Blocks | Gameplay change: GregTech explosives broken by a machine, another mod or a mob explode; before, the server threw an exception |
| Machines | Placing a machine from a creative stack with stored data no longer strips that data from the stack. |
| GUI | Multiblock screens show 0/s for a zero-amount output instead of infinity, and highlighted text fields without an unfocus callback no longer throw when they lose focus. |
| Ender links | A damaged or client-sent ender link colour no longer fails the virtual registry load, and an overfull virtual tank no longer loses fluid when filled. |
| Fluids | A fluid that another mod registered first keeps that mod's temperature, colour, density, luminosity and viscosity instead of GregTech's defaults. |
| World generation | Stone types registered with a gap in their ids get ore blocks; before, every type after the gap had none. |
| Rendering | Blocks with custom texture models keep geometry that has no cull face, and meshing on one thread no longer turns off the render layer hook for the others. |
| Rendering | Entities drawn after a GregTech portal keep their transparent cut-outs. |
| Rendering | Fluid fog under night vision no longer turns black for fluids with a black fog colour. |
| Recipes | Blocks of a material with neither a gem nor an ingot get arc furnace recycling recipes instead of stopping recipe loading. |
| Tools | Using the multiblock builder on a controller that has not built its structure pattern yet no longer throws. |
| Networking | The server rejects key binding packets with more entries than there are key binds, or with unknown keys, instead of allocating or throwing. |
| Networking | Machine recovery, clipboard and monitor plugin packets only act on positions in the sender's dimension and in chunks the sender can see, and clipboard edits only within reach. They no longer load chunks. |
| Multiblocks | The large boiler throttle stays within 0 to 100% whatever a client sends, and an empty throttle field no longer throws on the server. |
| AE2 | ME bus and hatch config slots ignore a zero or negative amount sent by a client. |
| Monitors | Monitor picture plugins load only http and https images, give up after 10 seconds, and refuse images over 16 MiB. Cached pictures are reused when the server reports them unchanged. |
| Ender links | Only players allowed to use an ender link cover can delete its entries, and only from that cover's own list. |
| Monitors | The advanced monitor plugin only opens machines that belong to the structure it displays. |
| GUI | Machine fluid slots ignore phantom-slot actions from a client, so a modified client cannot set or empty a real tank through them. |
| OpenComputers | Setting a machine's output side from OpenComputers rejects invalid sides and the machine's front, as the wrench does. |
| Machines | The alarm's radius stays within 0 to 128 blocks whatever a client sends, and non-numeric input to the alarm or the creative energy source no longer throws on the server. |
| World generation | A new single-player world no longer inherits the bedrock fluid veins of the world played before it. |
| Pipes | Worlds with long-distance pipes are released when they unload instead of being kept in memory until the game exits. |
| Cables | Gameplay change: a hot cable cools at the normal rate after its chunk loads instead of twice as fast, and a broken hot cable no longer sets fire to what replaces it |
| Pipes | A fluid pipe that saved an unknown fluid keeps new fluid in that channel across saves. |
| Pipes | Gameplay change: fluid in quadruple and nonuple pipes no longer flows back toward the pipe it just came from; before, every channel after the first could |
| Monitors | Monitor pictures with long URLs can be cached, and concurrent downloads no longer corrupt cache times. |
| Ender links | Ender link covers in single-player no longer add entries to the shared registry from the client thread. |
| Covers | The digital interface cover's energy chart shows the recent history as soon as it loads instead of starting flat. |
| Machines | Gameplay change: machines on a chunk border stop sending items and energy into an unloaded neighbour, where they were lost, and resume when that chunk loads |
| API | `DimensionPropertyList.EMPTY_LIST` is final and immutable; adding to the lists `RecipeBuilder.getDimensionIDs` returns for a recipe without dimensions now throws instead of changing every recipe's default. |
| Pipes | A pipe destroyed without a player, by an explosion or another mod, drops its own item, not the item of a pipe broken before it. |
| Machines | Gameplay change: several machines destroyed by one explosion each drop their own item and contents; before, the first machine's item and contents were duplicated for every other machine in the blast and the others were lost |
| Pipes | Laser pipes no longer switch off early when several players load them, and a laser pipe saved while active turns off after loading. |
| Damage | Gameplay change: armour keeps protecting against heat and frost damage after a player has carried a hot or cryogenic item; before, all such damage ignored armour for the rest of the session |
| Armor | Gameplay change: advanced NanoMuscle and QuarkTech chestplates charge only their wearer's items, at the same rate however many players wear them, and fuel jetpacks and QuarkTech boots no longer share state between wearers |
| Armor | Equipping a fuel jetpack by right-click plays its sound for nearby players. |
| World generation | Gameplay change, new chunks only: surface block vein indicators are placed with the same eight-block population offset as surface rocks |
| World generation | Gameplay change, new chunks only: the blocks within an ore vein are chosen the same way for a given seed whatever order chunks generate in |
| Rendering | Casings and coils at the same coordinates in another world no longer glow as active because a multiblock was running there in the previous one. |
| API | `GTValues.RNG` is safe to use from several threads; each thread draws from its own generator. `GTValues.FOOLS` and `XMAS` are `final`. |
| JEI | Bedrock fluid vein pages in JEI no longer collect another copy of the bucket each time they are opened. |
| JEI | The integrated circuit page in JEI always labels each slot with its own number. |
| Rendering | Players' own capes no longer vanish when their profile loads after they are first drawn. |
| Rendering | Active and idle laser pipes meshed at the same time no longer swap their glow. |
| JEI | JEI pages that show several ore vein, fluid vein, material tree or by-product recipes at once draw each recipe's own details. |
| JEI | Looking up a block hovered in a JEI multiblock preview works when the game renders below 10 frames per second. |
| Fluids | Moving fluid out of tanks that expose their live contents, such as some other mods' tanks, no longer changes the amount stored in them. |
| Covers | Gameplay change: solar panel covers check for sunlight once a second instead of every tick, so they react to a change up to a second later |
| API | `MultiblockControllerBase.getStructureRevision()` changes whenever a structure forms or breaks, for caching what is built from its parts. |
| Recipes | Gameplay change: recipes that use no energy and output only fluids run at most their requested number of parallels; before, they could run far more when the output tanks had room |
| GUI | Multiblock screens update when a line gets shorter but otherwise matches what was shown before. |
| API | `TraceabilityPredicate.ANY`, `AIR` and `HEATING_COILS` are `final`, and limiting a predicate no longer changes others that share its entries. |
| OpenComputers | OpenComputers cover values kept by a script no longer load their machine's dimension or chunk when called. |
| Saved data | An asymmetric cleanroom keeps its front and back depth across a reload. |
| Multiblocks | Multiblocks whose pattern names an ability with no registered part, such as one from an absent add-on, no longer fail to build their structure. |
| Recipes | Gameplay change: generated material recipes register in a fixed order that add-ons cannot shuffle; where two generated recipes conflict, which one is kept can differ from before |
| GUI | Multiblock screens display correctly on a client whose maintenance setting differs from the server's. |
| Networking | Machine traits keep their synced data apart however the client and server happened to load them. |
| Modules | Disabling a GregTech module in its config keeps that module's code from loading at all. |
| World generation | Gameplay change: in new worlds a bedrock fluid vein's fluid and yield no longer depend on which of its chunks is prospected first; existing worlds keep their veins |
| Multiblocks | Gameplay change: a fusion reactor that re-forms with fewer energy hatches keeps only as much energy as its new buffer holds |
| AE2 | Gameplay change: ME buses and hatches broken while their network is offline or full keep their contents: items drop, and fluids and output buffers stay in the dropped part |
| Recipes | Chanced fluid outputs that differ only in NBT are no longer merged into one stack |
| Saved data | Legacy ender tank data is migrated once instead of on every start, so it no longer overwrites newer ender entries |
| Storage | Gameplay change: a quantum chest refuses other items in its input slot, and with voiding on no longer deletes matching items while its output slot is empty |
| Machines | Gameplay change: the alarm uses its stated 4 EU/t; before, it drained up to three times that, and probes reading it drained energy too |
| Machines | Miners scan an ore-free area over several ticks instead of walking all of it at once |
| Machines | Energy sent to FE machines at the highest voltage tiers is capped at the largest FE amount instead of wrapping to nothing |
| Performance | The EU to FE adapter is skipped when native conversion is off and no longer allocated on every cable transfer |
| Recipes | Checking whether an item is an integrated circuit no longer adds a tag to it, so circuits keep stacking after JEI or a filter looks at them |
| Pipes | Long distance pipe networks are rebuilt on the server thread instead of a separate thread |
| World generation | Gameplay change: a vein's guaranteed surface indicator is placed only from the chunk holding its center, ending cascading chunk generation; a vein with no ore in that chunk has no guaranteed indicator |
| Tools | Gameplay change: industrial TNT and similar GT explosives respect claim and protection mods, which can now cancel each block they break |
| API | Custom loot entries no longer depend on reflection that current JDKs refuse |
| API | Two vanilla hooks now combine with other mods that hook the same code instead of failing to apply |
| GUI | In ModularUI text, /n becomes a line break only in GregTech's own translations; other mods' text keeps it as written |
| API | GregTech's ModularUI tweaks apply only to the tested ModularUI release; other releases log a warning and run unmodified |
| Compatibility | GregTech's HEI and The One Probe tweaks apply only to the tested releases, HEI 4.36.1 and The One Probe 1.4.28; other releases log a warning and run unmodified. HEI or JEI older than 4.36.1 is refused at startup. A GregTech hook into Minecraft, Forge, CodeChickenLib, GroovyScript, HEI or The One Probe that no longer finds its target now fails with an error naming it instead of silently doing nothing |
| Monitors | Animated GIFs on monitors no longer carry one frame's delay and transparency into the next, and malformed GIFs fail cleanly |
| Rendering | The invar and platinum lighters show their own textures again |
| Pipes | A fluid pipe whose destination throws during a fill records the uncertain amount, logs it and pauses its automatic distribution instead of returning fluid it cannot account for; `/gt fluid-reconcile` lets a server operator inspect the record and acknowledge it ([docs/pipenet-compatibility.md](docs/pipenet-compatibility.md)) |
| Saves | World accelerators carried over from a world older than the machine ID reallocation keep their tier; before, the reallocation moved them to IDs nothing registers and they became unknown items |
| Multiblocks | Shift-right-clicking a coke oven with a fluid container opens its screen again |
| Multiblocks | After a world load, a multiblock that resumes a recipe lists what it is producing, the research station shows what it is researching and the fusion reactor ring takes its product colour; before, all three stayed blank until the next recipe started |
| Covers | Conveyor, pump, filter, voiding, machine controller and digital interface covers, and the prospector, fall back to their default mode when saved data names a mode that does not exist; before, loading threw and the cover or item lost its data |
| Saves | Transformers, pumps and the creative energy source carried over from a world older than the machine ID reallocation keep their identity; before, the reallocation moved them to IDs nothing registers and they became unknown items |
| Configuration | The `loginMessage` (Miscellaneous Options), `terminalRootPath` and `coilsActiveEmissiveTextures` (Client Options) settings are removed because nothing read them; Forge ignores their old lines in existing config files |
| API | `GregTechAPI` has checked accessors for its managers (`materialManager()`, `mteManager()`, `commandManager()` and the others) that throw an `IllegalStateException` naming the manager and the stage that sets it; the fields remain. `GregTechAPI.isHighTier()` read before GTCEu's pre-initialization decides it still answers false, and now logs a warning with the caller's stack once. `BedrockFluidVeinHandler.veinCache` is private: read it through `getVeinCache()` and clear it with `clearVeinCache()` |
| Saves | Covers, filters, quantum tanks and chests, fluid, steam and pump hatches, power substations, maintenance hatches and the creative chest saved in older formats are rewritten once when their chunk first loads, instead of being converted on every load. Filters saved before the filter rework keep their case-insensitive or any-entry ore dictionary matching, a simple item filter's respect for damage and NBT, and a robotic arm's transfer stack size, and a maintenance hatch from before 2.5.4 keeps its tape; before, the conversion reset these. Fluid regulators no longer save an empty `filterv2` tag |
| Saves | The datafix passes that move machine blocks keep a quantum chest's, quantum tank's or clipboard's block see-through; before, each pass made every machine block in the chunk opaque |
| Covers | A conveyor or pump whose saved transfer rate is missing or outside its range loads at its full rate; before, it loaded as saved, so a missing rate moved nothing |
| Saves | Machine items held in chests, machines, entities and player inventories are carried through the machine ID migrations, as placed machines are; before, only stacks saved with a large count were, so in a world older than a migration an ordinary stack could keep an ID that no longer names its machine. A world already saved at a migration's version keeps the items it has |
| API | `SimpleItemFilterReader.deserializeNBT` reads back the damage and NBT settings `serializeNBT` writes; before, a filter that respected damage or NBT read its own saved tag as ignoring them |
| Multiblocks | A large boiler forms with a water hatch and a fuel bus wherever they sit in its firebox layer; before, an import fluid hatch also counted as its one allowed item input, because the hatch offers its circuit slot as one, so the boiler refused to form when the pattern reached the bus after the hatch |
| GUI | The prospector's screen is a ModularUI 2 screen, with the same map, resource list, tooltips and waypoints; it no longer leaks its map texture each time it closes. Screens opened from an item in hand close when that item leaves the hand |
| Clipboard | The clipboard's screen, in hand or placed, is a ModularUI 2 screen with the same pages, check boxes and text fields, and the server keeps titles to 25 characters and tasks to 23. A placed clipboard changes only through that screen and through left clicks on its page, which the server reads itself; before, a player near one could send the server any clipboard tag, and each player watching repeated a click on the page |
| Monitors | The monitor screen's settings, the screen grid behind its Back button and the plugin settings, on the screen or in the text plugin's own screen, are ModularUI 2 screens with the same controls. The server checks what a client sends: plugin settings stay in their ranges and text lines within 100 characters, and a selected cover or grid cell must be one of that central monitor's own; before, a longer line could disconnect the players who then saw the monitor, a slot change could carry any scale with it, and selecting a cover that had just gone threw on the server. A shift-click on a GUI proxy plugin acts once instead of once for each player watching |
| Monitors | Online Picture plugins show their pictures only to players who turn on the new `monitorOnlinePictures` client option, which is off by default: a picture is downloaded by each player's own game, so an address another player chose learns that player's IP address. The server accepts only http and https addresses, and the download refuses addresses inside the player's own network, including after a redirect; with the option off the plugin says so on the screen |
| GUI | A fluid dropped from a recipe viewer on a fluid tank that locks, such as an output fluid hatch's, locks the tank; before, only the screen showed the lock, which the server never had |
| GUI | The alarm, charger, item collector and pump open ModularUI 2 screens with the controls they had. The alarm's sounds are a drop-down list, and a sound or radius chosen on its screen is saved even when nothing else about the alarm changes; the item collector's screen drops the empty space its unused filter took; the pump's tank can be drained into a held container, as an output fluid hatch's can |
| Machines | An alarm loaded with no saved sound, or with a sound no longer registered, uses the default alarm sound, and one saved without a radius gets the default 64; before, sending its chunk to a player crashed the server and the alarm could not be saved |
| Machines | An item collector or configurable maintenance hatch loads its saved range or duration multiplier within the bounds its screen keeps, and takes the default when none is saved; before, a missing range collected nothing and a missing or out-of-range multiplier, such as 0, which made recipes take no time, was kept |
| GUI | The creative energy source opens a ModularUI 2 screen with the controls it had; its energy rate line can be translated |
| GUI | The maintenance hatches and the data access hatch open ModularUI 2 screens with the slots, buttons, multipliers and required tools they had, and the April Fools' wiring task |
| GUI | The ME input, stocking and output buses and hatches open ModularUI 2 screens with the slots and settings they had. The amount of a config slot selected on an input bus or hatch is edited in a row below the slots instead of a box above the screen, and fluids keep their tags in the config, stock and waiting slots. The server checks every config edit, so no edit reaches a slot auto-pull manages and a stocking part takes no amounts |
| GUI | The ME pattern buffer and its proxy open ModularUI 2 screens: the patterns, shared ingredients and private batches are tabs on the screen's left, and the same access checks guard each slot, tank and button |
| Recipes | The ULV 16A dynamo hatch recipe makes the ULV 16A dynamo hatch; before, it made the LuV one from ULV parts |
| Recipes | The 4A and 16A energy and dynamo hatches follow one recipe at every tier: the ZPM 4A dynamo hatch no longer takes a ZPM transformer, the ULV 4A energy hatch is assembled at 4 EU/t like the ULV 4A dynamo hatch instead of 2, and the UHV 16A energy hatch takes one UHV 4A energy hatch instead of two, as the dynamo hatch does |
| Recipes | Researching the ZPM emitter takes 4 CWU/t and 16,000 CWU, as researching the ZPM sensor and field generator does; before, it took 8 CWU/t and 32,000 CWU |
| Recipes | The MV and HV buffers are crafted with an MV and an HV circuit; before, every buffer took an LV circuit |
| Monitors | The GUI proxy plugin shows a notice on the monitor that it no longer shows machine screens. It mirrored a machine's old-GUI screen, and since every machine opens a ModularUI 2 screen it showed nothing; its mirroring is removed. The plugin item stays, loads as saved and keeps its part setting, which the advanced monitor plugin draws its connections to |
| Items | The prospector's ore map no longer crashes the game when a scanned chunk holds ore; the map's cells could hold only one kind of map, and the server's scan sends another |
| Multiblocks | Steam multiblocks start a recipe once they hold the steam for its whole run; before, the check compared the run's steam with the stored steam converted to EU, so at the default steam rate of 0.5 EU per millibucket they waited for twice the steam the run uses |
| Pipes | An optical cable in use stays lit, as a laser line does, and shows its glow to a player who loads its chunk; before, it went dark every five seconds while carrying computation and lit again a moment later, and a newly loaded chunk showed it dark |

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
