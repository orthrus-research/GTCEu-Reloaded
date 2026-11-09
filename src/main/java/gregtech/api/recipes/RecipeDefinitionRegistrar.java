package gregtech.api.recipes;

import gregtech.api.definition.DefinitionDiagnostic;
import gregtech.api.definition.DefinitionResult;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.definition.CraftingDefinition;
import gregtech.api.recipes.definition.CraftingGroupDefinition;
import gregtech.api.recipes.definition.CraftingMutation;
import gregtech.api.recipes.definition.CustomCraftingDefinition;
import gregtech.api.recipes.definition.DisabledCraftingDefinition;
import gregtech.api.recipes.definition.RecipeDefinition;
import gregtech.api.recipes.definition.RecoveryDefinition;
import gregtech.api.recipes.definition.RecyclingRecipeDefinition;
import gregtech.api.recipes.definition.RegistrationReceipt;
import gregtech.api.util.GTLog;

import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Owns replaceable groups of typed recipes. Call on the creating registration thread while machine ticks and other
 * registry writers are quiescent. All declarations, preparation-safe expansions and indexes are staged before
 * publication, with recycling composition restored alongside the group. Arbitrary build callbacks, direct script
 * mutations and process-crash durability are outside
 * this initial contract.
 */
public final class RecipeDefinitionRegistrar {

    private final BooleanSupplier registrationThread;
    private final Map<String, List<OwnedRecipeDefinition>> groups = new LinkedHashMap<>();
    private final Map<String, List<OwnedRecipeDefinition>> recyclingBaselines = new LinkedHashMap<>();
    private final Set<String> recoveryGroups = new LinkedHashSet<>();
    private final Map<String, CraftingGroupDefinition> craftingGroups = new LinkedHashMap<>();
    private final Map<String, Map<ResourceLocation, PreparedCraftingDefinition>> preparedCrafting = new LinkedHashMap<>();
    private final RecipeRecyclingOwnership recycling = new RecipeRecyclingOwnership();
    private final RecipeFurnaceOwnership furnace = new RecipeFurnaceOwnership();
    private final RecipeCraftingOwnership crafting = new RecipeCraftingOwnership();
    /** Test seam: sees each publication stage and may throw to simulate a failing participant. */
    private Consumer<String> publicationProbe = stage -> {};
    private long generation;

    /** Default native authoring is confined to the creating thread. */
    public RecipeDefinitionRegistrar() {
        Thread owner = Thread.currentThread();
        this.registrationThread = () -> Thread.currentThread() == owner;
    }

    /**
     * Lifecycle adapters may select the currently authorized registration thread. The predicate must allow only
     * one thread at a time; its owner must keep machine ticks and other registry writers quiescent while registering.
     */
    public RecipeDefinitionRegistrar(BooleanSupplier registrationThread) {
        this.registrationThread = Objects.requireNonNull(registrationThread, "registrationThread");
    }

    public DefinitionResult<RegistrationReceipt> replace(String group, List<RecipeDefinition> declarations) {
        requireOwner();
        Objects.requireNonNull(group, "group");
        if (group.isBlank()) throw new IllegalArgumentException("A group needs a stable name");
        declarations = List.copyOf(declarations);
        List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
        List<OwnedRecipeDefinition> prepared = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (RecipeDefinition definition : declarations) {
            if (definition.origin().declaration().isBlank() || !names.add(definition.origin().declaration())) {
                diagnostics.add(error("recipe.declaration.identity", definition.origin(),
                        "Declaration identities must be nonempty and unique within a group"));
            }
            var result = RecipeDefinitionCompiler.prepare(definition);
            diagnostics.addAll(result.diagnostics());
            result.value().ifPresent(prepared::addAll);
        }
        if (!diagnostics.isEmpty()) return DefinitionResult.failure(diagnostics);
        return replacePrepared(group, prepared);
    }

    /**
     * Prepares native recovery and reports the exact existing registrations that prevent its insertion. This does
     * not adopt or remove them. Callers may explicitly pass selected handles to a subsequent replacement, whose
     * normal ownership and index checks still apply. Preparation-safe actions can run again on replacement.
     */
    public DefinitionResult<List<NativeRecipeRegistration>> recoveryConflicts(RecyclingRecipeDefinition definition) {
        requireOwner();
        var prepared = RecyclingRecipeCompiler.prepare(Objects.requireNonNull(definition, "definition"));
        if (prepared.value().isEmpty()) return DefinitionResult.failure(prepared.diagnostics());
        List<NativeRecipeRegistration> conflicts = new ArrayList<>();
        for (var recipe : prepared.value().orElseThrow()) {
            for (var existing : recipe.map().registrationConflicts(recipe.recipe())) {
                if (conflicts.stream().noneMatch(value -> value.map() == recipe.map() && value.recipe() == existing))
                    conflicts.add(new NativeRecipeRegistration(recipe.map(), existing));
            }
        }
        return DefinitionResult.success(List.copyOf(conflicts));
    }

    public DefinitionResult<RegistrationReceipt> replaceRecycling(String group,
                                                                  List<RecyclingRecipeDefinition> declarations) {
        return replaceRecycling(group, declarations, List.of());
    }

    /**
     * Explicitly displaces exact native recovery registrations when first creating a group. Preparation failure
     * leaves them registered. Empty replacements keep the baseline disabled; clear restores its original identities.
     * This owns generated recipes only, not their crafting producers or composition metadata.
     */
    public DefinitionResult<RegistrationReceipt> replaceRecycling(String group,
                                                                  List<RecyclingRecipeDefinition> declarations,
                                                                  List<NativeRecipeRegistration> baselines) {
        requireOwner();
        Objects.requireNonNull(group, "group");
        if (group.isBlank()) throw new IllegalArgumentException("A group needs a stable name");
        List<DefinitionDiagnostic> diagnostics = new ArrayList<>();
        List<OwnedRecipeDefinition> prepared = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (var definition : List.copyOf(declarations)) {
            if (definition.origin().declaration().isBlank() || !names.add(definition.origin().declaration())) {
                diagnostics.add(error("recipe.declaration.identity", definition.origin(),
                        "Declaration identities must be nonempty and unique within a group"));
            }
            var result = RecyclingRecipeCompiler.prepare(definition);
            diagnostics.addAll(result.diagnostics());
            result.value().ifPresent(prepared::addAll);
        }
        if (!diagnostics.isEmpty()) return DefinitionResult.failure(diagnostics);
        List<OwnedRecipeDefinition> displaced = new ArrayList<>();
        SourceOrigin source = origin(group, prepared);
        baselines = List.copyOf(baselines);
        if (!baselines.isEmpty() && groups.containsKey(group)) {
            return DefinitionResult.failure(List.of(error("recipe.baseline.identity", source,
                    "Baselines can only be adopted when first creating a group")));
        }
        for (var baseline : baselines) {
            if (baseline.map() != RecipeMaps.MACERATOR_RECIPES && baseline.map() != RecipeMaps.EXTRACTOR_RECIPES &&
                    baseline.map() != RecipeMaps.ARC_FURNACE_RECIPES) {
                return DefinitionResult.failure(List.of(error("recipe.baseline.identity", source,
                        "Recovery baseline must belong to a native recovery map")));
            }
            if (displaced.stream().anyMatch(value -> value.map() == baseline.map() &&
                    value.recipe() == baseline.recipe()) || groups.values().stream().flatMap(List::stream)
                            .anyMatch(value -> value.map() == baseline.map() && value.recipe() == baseline.recipe())) {
                return DefinitionResult.failure(List.of(error("recipe.baseline.identity", source,
                        "Recovery baseline is duplicated or already owned by this registrar")));
            }
            displaced.add(new OwnedRecipeDefinition(baseline.map(), baseline.recipe(), source, false, List.of()));
        }
        return replacePrepared(group, prepared, List.copyOf(displaced));
    }

    private DefinitionResult<RegistrationReceipt> replacePrepared(String group, List<OwnedRecipeDefinition> prepared) {
        return replacePrepared(group, prepared, List.of());
    }

    private DefinitionResult<RegistrationReceipt> replacePrepared(String group, List<OwnedRecipeDefinition> prepared,
                                                                  List<OwnedRecipeDefinition> displaced) {
        if (recoveryGroups.contains(group) || craftingGroups.containsKey(group))
            return DefinitionResult.failure(List.of(error("recipe.group.owner",
                    origin(group, prepared), "This group is managed by composition-derived recovery")));
        List<OwnedRecipeDefinition> previous = groups.getOrDefault(group, List.of());
        var removed = new ArrayList<>(previous);
        removed.addAll(displaced);
        var baselineErrors = verifyBaselines();
        if (!baselineErrors.isEmpty()) return DefinitionResult.failure(baselineErrors);
        var recyclingChange = prepareRecycling(group, prepared, false, removed, prepared);
        if (recyclingChange.value().isEmpty()) return DefinitionResult.failure(recyclingChange.diagnostics());
        var furnaceChange = prepareFurnace(group, prepared, false);
        if (furnaceChange.value().isEmpty()) return DefinitionResult.failure(furnaceChange.diagnostics());
        var publicationErrors = publishFurnace(group, prepared,
                recyclingChange.value().orElseThrow(), furnaceChange.value().orElseThrow());
        if (!publicationErrors.isEmpty()) return DefinitionResult.failure(publicationErrors);
        // Recycling publication only swaps prevalidated state, but a failure must still not strand furnace tables.
        var publication = new RecipePublication();
        publication.published(furnaceChange.value().orElseThrow()::restore);
        try {
            publicationProbe.accept("furnace.published");
            recyclingChange.value().orElseThrow().publish();
        } catch (RuntimeException failure) {
            return DefinitionResult.failure(List.of(rolledBack(origin(group, prepared), publication, failure)));
        }
        furnaceChange.value().orElseThrow().commitOwnership();
        if (!displaced.isEmpty()) recyclingBaselines.put(group, displaced);
        if (prepared.isEmpty() && !recyclingBaselines.containsKey(group)) groups.remove(group);
        else groups.put(group, List.copyOf(prepared));
        return DefinitionResult.success(new RegistrationReceipt(group, ++generation, prepared.size()));
    }

    /** Replaces material contributions and their generated recipes in the same shared transaction. */
    public DefinitionResult<RegistrationReceipt> replaceRecovery(String group,
                                                                 List<RecyclingContribution> contributions,
                                                                 List<? extends RecoveryDefinition> definitions,
                                                                 List<NativeRecipeRegistration> baselines) {
        requireOwner();
        Objects.requireNonNull(group, "group");
        if (group.isBlank()) throw new IllegalArgumentException("A group needs a stable name");
        contributions = List.copyOf(contributions);
        definitions = List.copyOf(definitions);
        baselines = List.copyOf(baselines);
        var source = !definitions.isEmpty() ? definitions.getFirst().origin() :
                !contributions.isEmpty() ? contributions.getFirst().origin() : origin(group, List.of());
        if (groups.containsKey(group) || craftingGroups.containsKey(group))
            return DefinitionResult.failure(List.of(error("recipe.group.owner", source,
                    "This group is managed by another definition kind")));
        for (var baseline : baselines) {
            if (groups.values().stream().flatMap(List::stream)
                    .anyMatch(value -> value.map() == baseline.map() && value.recipe() == baseline.recipe()))
                return DefinitionResult.failure(List.of(error("recipe.baseline.identity", source,
                        "Recovery baseline is already owned by this registrar")));
        }
        var baselineErrors = verifyBaselines();
        if (!baselineErrors.isEmpty()) return DefinitionResult.failure(baselineErrors);
        try {
            var change = recycling.prepareRecovery(group, contributions, definitions, baselines);
            if (!change.isCurrent()) return DefinitionResult.failure(List.of(error("recipe.registry.changed", source,
                    "A recipe or composition registry changed during preparation")));
            change.publish();
            recoveryGroups.add(group);
            return DefinitionResult
                    .success(new RegistrationReceipt(group, ++generation, change.recoveryRecipes(group)));
        } catch (RecipeRegistrationException conflict) {
            return DefinitionResult.failure(List.of(conflict.diagnostic()));
        } catch (RecyclingOwnershipException conflict) {
            return DefinitionResult.failure(List.of(error("recipe.recycling.conflict", conflict.origin(),
                    conflict.getMessage())));
        } catch (IllegalStateException failure) {
            return DefinitionResult.failure(List.of(error("recipe.recycling.conflict", source, failure.getMessage())));
        }
    }

    public DefinitionResult<RegistrationReceipt> replaceCrafting(String group,
                                                                 List<? extends CraftingMutation> declarations) {
        return replaceCrafting(group, declarations, List.of(), List.of(), List.of());
    }

    /**
     * Replaces crafting edits, composition and generated recovery together. Omitted previously owned names stay
     * disabled until re-enabled explicitly or restored by clear. Shared claims prevent ownership stealing.
     */
    public DefinitionResult<RegistrationReceipt> replaceCrafting(String group,
                                                                 List<? extends CraftingMutation> declarations,
                                                                 List<RecyclingContribution> contributions,
                                                                 List<? extends RecoveryDefinition> recovery,
                                                                 List<NativeRecipeRegistration> baselines) {
        requireOwner();
        Objects.requireNonNull(group, "group");
        if (group.isBlank()) throw new IllegalArgumentException("A group needs a stable name");
        declarations = List.copyOf(declarations);
        contributions = List.copyOf(contributions);
        recovery = List.copyOf(recovery);
        baselines = List.copyOf(baselines);
        var acceptedDefinition = new CraftingGroupDefinition(List.copyOf(declarations), contributions,
                List.copyOf(recovery));
        SourceOrigin source = !declarations.isEmpty() ? declarations.getFirst().origin() :
                !contributions.isEmpty() ? contributions.getFirst().origin() : origin(group, List.of());
        if (groups.containsKey(group) || recoveryGroups.contains(group))
            return DefinitionResult.failure(List.of(error("recipe.group.owner", source,
                    "This group is managed by another definition kind")));
        for (var baseline : baselines) {
            if (groups.values().stream().flatMap(List::stream)
                    .anyMatch(value -> value.map() == baseline.map() && value.recipe() == baseline.recipe()))
                return DefinitionResult.failure(List.of(error("recipe.baseline.identity", source,
                        "Recovery baseline is already owned by this registrar")));
        }
        var diagnostics = new ArrayList<>(verifyBaselines());
        Map<ResourceLocation, IRecipe> replacements = new LinkedHashMap<>();
        var retained = preparedCrafting.getOrDefault(group, Map.of());
        Map<ResourceLocation, PreparedCraftingDefinition> prepared = new LinkedHashMap<>();
        Set<ResourceLocation> names = new HashSet<>();
        int recipes = 0;
        for (var declaration : declarations) {
            if (!names.add(declaration.name())) {
                diagnostics.add(error("crafting.declaration.identity", declaration.origin(),
                        "Crafting names must be unique within a replacement: " + declaration.name()));
                continue;
            }
            var previous = retained.get(declaration.name());
            if (previous != null && previous.declaration() == declaration) {
                replacements.put(declaration.name(), previous.recipe());
                prepared.put(declaration.name(), previous);
                recipes++;
                continue;
            }
            switch (declaration) {
                case CraftingDefinition definition -> {
                    var compiled = CraftingDefinitionCompiler.compile(definition);
                    diagnostics.addAll(compiled.diagnostics());
                    compiled.value().ifPresent(recipe -> replacements.put(definition.name(), recipe));
                    recipes++;
                }
                case DisabledCraftingDefinition disabled -> replacements.put(disabled.name(), null);
                case CustomCraftingDefinition custom -> {
                    try {
                        var recipe = custom.factory().get();
                        if (recipe == null || !custom.name().equals(recipe.getRegistryName()) ||
                                CraftingRegistryTransaction.currentRegistries().stream()
                                        .anyMatch(registry -> registry.getValue(custom.name()) == recipe)) {
                            diagnostics.add(error("crafting.custom.invalid", custom.origin(),
                                    "Custom factories must return a detached recipe with the declared name"));
                        } else {
                            replacements.put(custom.name(), recipe);
                            recipes++;
                        }
                    } catch (RuntimeException failure) {
                        diagnostics.add(error("crafting.custom.invalid", custom.origin(), failure.toString()));
                    }
                }
            }
            var recipe = replacements.get(declaration.name());
            if (recipe != null) prepared.put(declaration.name(), new PreparedCraftingDefinition(declaration, recipe));
        }
        if (!diagnostics.isEmpty()) return DefinitionResult.failure(diagnostics);
        try {
            var recyclingChange = recycling.prepareRecovery(group, contributions, recovery, baselines);
            var craftingChange = crafting.prepare(group, replacements, source);
            var errors = publishCrafting(source, recyclingChange, craftingChange, null);
            if (!errors.isEmpty()) return DefinitionResult.failure(errors);
            craftingGroups.put(group, acceptedDefinition);
            preparedCrafting.put(group, Map.copyOf(prepared));
            return DefinitionResult.success(new RegistrationReceipt(group, ++generation,
                    recipes + recyclingChange.recoveryRecipes(group)));
        } catch (RecipeRegistrationException conflict) {
            return DefinitionResult.failure(List.of(conflict.diagnostic()));
        } catch (RecyclingOwnershipException conflict) {
            return DefinitionResult.failure(List.of(error("recipe.recycling.conflict", conflict.origin(),
                    conflict.getMessage())));
        } catch (IllegalStateException conflict) {
            return DefinitionResult
                    .failure(List.of(error("crafting.ownership.changed", source, conflict.getMessage())));
        }
    }

    /** Clears all owned groups as one registration transaction, returning the number of removed recipes. */
    public DefinitionResult<Integer> clear() {
        requireOwner();
        List<OwnedRecipeDefinition> previous = groups.values().stream().flatMap(List::stream).toList();
        // A displaced baseline that something outside already re-registered is released, not restored twice; refusing
        // would fail this clear, and every reload after it, until restart.
        var restored = recyclingBaselines.values().stream().flatMap(List::stream)
                .filter(baseline -> baseline.map().getRecipesByCategory().values().stream().flatMap(List::stream)
                        .noneMatch(recipe -> recipe == baseline.recipe()))
                .toList();
        var recyclingChange = prepareRecycling("<all groups>", List.of(), true, previous, restored);
        if (recyclingChange.value().isEmpty()) return DefinitionResult.failure(recyclingChange.diagnostics());
        int removedRecovery = recyclingChange.value().orElseThrow().previousRecoveryRecipes();
        var furnaceChange = prepareFurnace("<all groups>", List.of(), true);
        if (furnaceChange.value().isEmpty()) return DefinitionResult.failure(furnaceChange.diagnostics());
        RecipeCraftingOwnership.Change craftingChange;
        try {
            craftingChange = crafting.prepareClear();
        } catch (RecipeRegistrationException conflict) {
            return DefinitionResult.failure(List.of(conflict.diagnostic()));
        } catch (IllegalStateException conflict) {
            return DefinitionResult
                    .failure(List.of(error("crafting.ownership.changed", origin("<all groups>", previous),
                            conflict.getMessage())));
        }
        int removedCrafting = crafting.recipes();
        var publicationErrors = publishCrafting(origin("<all groups>", previous),
                recyclingChange.value().orElseThrow(), craftingChange, furnaceChange.value().orElseThrow());
        if (!publicationErrors.isEmpty()) return DefinitionResult.failure(publicationErrors);
        groups.clear();
        recyclingBaselines.clear();
        recoveryGroups.clear();
        craftingGroups.clear();
        preparedCrafting.clear();
        generation++;
        return DefinitionResult.success(previous.size() + removedRecovery + removedCrafting);
    }

    private List<DefinitionDiagnostic> verifyBaselines() {
        for (var baseline : recyclingBaselines.values().stream().flatMap(List::stream).toList()) {
            if (baseline.map().getRecipesByCategory().values().stream().flatMap(List::stream)
                    .anyMatch(recipe -> recipe == baseline.recipe())) {
                return List.of(error("recipe.baseline.changed", baseline.origin(),
                        "A displaced recovery baseline was registered outside its owning group"));
            }
        }
        return List.of();
    }

    private DefinitionResult<RecipeRecyclingOwnership.Change> prepareRecycling(String group,
                                                                               List<OwnedRecipeDefinition> prepared,
                                                                               boolean clear,
                                                                               List<OwnedRecipeDefinition> removed,
                                                                               List<OwnedRecipeDefinition> added) {
        try {
            return DefinitionResult.success(recycling.prepare(group, prepared, clear, removed, added));
        } catch (RecipeRegistrationException conflict) {
            return DefinitionResult.failure(List.of(conflict.diagnostic()));
        } catch (RecyclingOwnershipException conflict) {
            return DefinitionResult.failure(List.of(error("recipe.recycling.conflict", conflict.origin(),
                    conflict.getMessage())));
        } catch (IllegalStateException failure) {
            SourceOrigin origin = prepared.isEmpty() ?
                    new SourceOrigin("java", "registration", 0, 0, group, "recipes") :
                    prepared.getFirst().origin();
            return DefinitionResult.failure(List.of(error("recipe.recycling.conflict", origin, failure.getMessage())));
        }
    }

    /**
     * Returns the last successfully submitted crafting declarations, not the live native registry or a restorable
     * ownership token. Failed replacement or clear preserves this value; successful clear removes it.
     */
    public Optional<CraftingGroupDefinition> craftingGroup(String group) {
        requireOwner();
        return Optional.ofNullable(craftingGroups.get(Objects.requireNonNull(group, "group")));
    }

    /** Exact current generated identities, excluding displaced originals and other owners. Not ownership tokens. */
    public List<NativeRecipeRegistration> generatedRecovery(String group) {
        requireOwner();
        return recycling.generatedRecovery(Objects.requireNonNull(group, "group"));
    }

    public List<String> groups() {
        requireOwner();
        var names = new ArrayList<>(groups.keySet());
        names.addAll(recoveryGroups);
        names.addAll(craftingGroups.keySet());
        return List.copyOf(names);
    }

    private DefinitionResult<RecipeFurnaceOwnership.Change> prepareFurnace(String group,
                                                                           List<OwnedRecipeDefinition> prepared,
                                                                           boolean clear) {
        try {
            return DefinitionResult.success(furnace.prepare(group, prepared, clear));
        } catch (FurnaceOwnershipException conflict) {
            return DefinitionResult.failure(List.of(error("recipe.furnace.conflict", conflict.origin(),
                    conflict.getMessage())));
        } catch (RuntimeException failure) {
            return DefinitionResult.failure(List.of(error("recipe.furnace.conflict", origin(group, prepared),
                    failure.getClass().getSimpleName() + ": " + failure.getMessage())));
        }
    }

    private List<DefinitionDiagnostic> publishFurnace(String group, List<OwnedRecipeDefinition> prepared,
                                                      RecipeRecyclingOwnership.Change recyclingChange,
                                                      RecipeFurnaceOwnership.Change furnaceChange) {
        // Furnace preparation invokes item XP hooks. Recheck all staged registries after those hooks return.
        if (!recyclingChange.isCurrent()) {
            return List.of(error("recipe.registry.changed", origin(group, prepared),
                    "A recipe or recycling registry changed during preparation"));
        }
        try {
            furnaceChange.publish();
            if (!recyclingChange.isCurrent()) {
                furnaceChange.restore();
                return List.of(error("recipe.registry.changed", origin(group, prepared),
                        "A registry changed while publishing furnace tables; furnace changes restored"));
            }
            return List.of();
        } catch (IllegalStateException failure) {
            return List.of(error("recipe.furnace.publish", origin(group, prepared), failure.getMessage()));
        }
    }

    private List<DefinitionDiagnostic> publishCrafting(SourceOrigin source,
                                                       RecipeRecyclingOwnership.Change recyclingChange,
                                                       RecipeCraftingOwnership.Change craftingChange,
                                                       RecipeFurnaceOwnership.Change furnaceChange) {
        if (!recyclingChange.isCurrent() || !craftingChange.isCurrent())
            return List.of(error("recipe.registry.changed", source,
                    "A recipe, crafting or composition registry changed during preparation"));
        var publication = new RecipePublication();
        try {
            if (furnaceChange != null) {
                publication.publish(furnaceChange::publish, furnaceChange::restore);
                publicationProbe.accept("furnace.published");
            }
            if (!recyclingChange.isCurrent() || !craftingChange.isCurrent())
                throw new IllegalStateException("A registry changed while publishing furnace tables");
            publication.publish(craftingChange::publish, () -> {
                publicationProbe.accept("crafting.restore");
                craftingChange.restore();
            });
            publicationProbe.accept("crafting.published");
            if (!recyclingChange.isCurrent())
                throw new IllegalStateException("A registry changed while publishing crafting tables");
            // Remaining publications only swap prevalidated native state; they do not invoke author callbacks.
            recyclingChange.publish();
            craftingChange.commitOwnership();
            if (furnaceChange != null) furnaceChange.commitOwnership();
            return List.of();
        } catch (RuntimeException failure) {
            return List.of(rolledBack(source, publication, failure));
        }
    }

    private static DefinitionDiagnostic rolledBack(SourceOrigin source, RecipePublication publication,
                                                   RuntimeException failure) {
        publication.rollback(failure);
        if (failure.getSuppressed().length > 0) {
            GTLog.logger.error("Recipe publication failed and could not be fully restored", failure);
            return error("recipe.registry.restore", source, failure.getMessage() +
                    "; restoring published registries also failed, see the log");
        }
        return error("recipe.registry.changed", source, failure.getMessage());
    }

    /** Test seam only; production code never installs a probe. */
    void publicationProbe(Consumer<String> probe) {
        this.publicationProbe = Objects.requireNonNull(probe, "probe");
    }

    private SourceOrigin origin(String group, List<OwnedRecipeDefinition> prepared) {
        if (!prepared.isEmpty()) return prepared.getFirst().origin();
        var previous = group.equals("<all groups>") ? groups.values().stream().flatMap(List::stream).toList() :
                groups.getOrDefault(group, List.of());
        return previous.isEmpty() ? new SourceOrigin("java", "registration", 0, 0, group, "recipes") :
                previous.getFirst().origin();
    }

    private void requireOwner() {
        if (!registrationThread.getAsBoolean())
            throw new IllegalStateException("Recipe registration is thread confined");
    }

    private static DefinitionDiagnostic error(String code, SourceOrigin origin, String message) {
        return new DefinitionDiagnostic(code, DefinitionDiagnostic.Severity.ERROR, origin, "registration", message);
    }
}
