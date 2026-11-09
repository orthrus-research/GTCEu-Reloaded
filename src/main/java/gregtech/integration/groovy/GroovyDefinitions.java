package gregtech.integration.groovy;

import gregtech.api.definition.DefinitionDiagnostic;
import gregtech.api.definition.DefinitionResult;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.NativeRecipeRegistration;
import gregtech.api.recipes.RecipeDefinitionRegistrar;
import gregtech.api.recipes.RecyclingContribution;
import gregtech.api.recipes.definition.CraftingGroupDefinition;
import gregtech.api.recipes.definition.CraftingMutation;
import gregtech.api.recipes.definition.RecipeDefinition;
import gregtech.api.recipes.definition.RecoveryDefinition;
import gregtech.api.recipes.definition.RecyclingRecipeDefinition;
import gregtech.api.recipes.definition.RegistrationReceipt;
import gregtech.api.util.GTLog;

import net.minecraftforge.fml.common.FMLCommonHandler;

import com.cleanroommc.groovyscript.GroovyScript;
import com.cleanroommc.groovyscript.api.GroovyBlacklist;
import com.cleanroommc.groovyscript.api.GroovyLog;
import com.cleanroommc.groovyscript.api.IScriptReloadable;
import com.cleanroommc.groovyscript.registry.NamedRegistry;
import com.cleanroommc.groovyscript.sandbox.LoadStage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Thin lifecycle adapter. Recipe semantics, validation and transaction ownership reside in the Java registrar. */
public final class GroovyDefinitions extends NamedRegistry implements IScriptReloadable {

    private final Supplier<LoadStage> stage;
    private final Consumer<DefinitionDiagnostic> diagnostics;
    private final BooleanSupplier supported;
    private final Thread startupThread = Thread.currentThread();
    private RecipeDefinitionRegistrar registrar;

    public GroovyDefinitions() {
        this(() -> GroovyScript.getSandbox().getCurrentLoader(), d -> GroovyLog.get().error(d.format()),
                GroovyScriptCompatibility::typedDefinitionsAvailable);
        if (!supported.getAsBoolean()) {
            GTLog.logger.warn("GroovyScript {} is outside the tested range {}; GTCEu script caching is off and " +
                    "typed Groovy definitions are unavailable", GroovyScriptCompatibility.describeLoaded(),
                    GroovyScriptCompatibility.TESTED_RANGE);
        }
    }

    GroovyDefinitions(Supplier<LoadStage> stage, Consumer<DefinitionDiagnostic> diagnostics) {
        this(stage, diagnostics, () -> true);
    }

    GroovyDefinitions(Supplier<LoadStage> stage, Consumer<DefinitionDiagnostic> diagnostics,
                      BooleanSupplier supported) {
        super(List.of("definitions"));
        this.stage = stage;
        this.diagnostics = diagnostics;
        this.supported = supported;
    }

    public DefinitionResult<RegistrationReceipt> replace(String group, List<RecipeDefinition> recipes) {
        requireRecipeStage();
        if (registrar == null) this.registrar = new RecipeDefinitionRegistrar(this::isRegistrationThread);
        var result = registrar.replace(group, recipes);
        result.diagnostics().forEach(diagnostics);
        return result;
    }

    public SourceOrigin source(String declaration) {
        requireRecipeStage();
        String source = GroovyScript.getSandbox().getCurrentScript();
        return new SourceOrigin("groovy", source == null ? "<groovy>" : source, 0, 0, declaration, "postInit");
    }

    public DefinitionResult<RegistrationReceipt> replaceCrafting(String group,
                                                                 List<? extends CraftingMutation> recipes) {
        return replaceCrafting(group, recipes, List.of(), List.of(), List.of());
    }

    public DefinitionResult<RegistrationReceipt> replaceCrafting(String group,
                                                                 List<? extends CraftingMutation> recipes,
                                                                 List<RecyclingContribution> contributions,
                                                                 List<? extends RecoveryDefinition> recovery,
                                                                 List<NativeRecipeRegistration> baselines) {
        requireRecipeStage();
        if (registrar == null) this.registrar = new RecipeDefinitionRegistrar(this::isRegistrationThread);
        var result = registrar.replaceCrafting(group, recipes, contributions, recovery, baselines);
        result.diagnostics().forEach(diagnostics);
        return result;
    }

    /** Accepted authoring state belongs to the registrar and is discarded by a successful reload clear. */
    public Optional<CraftingGroupDefinition> craftingGroup(String group) {
        requireRecipeStage();
        Objects.requireNonNull(group, "group");
        return registrar == null ? Optional.empty() : registrar.craftingGroup(group);
    }

    public List<NativeRecipeRegistration> generatedRecovery(String group) {
        requireRecipeStage();
        Objects.requireNonNull(group, "group");
        return registrar == null ? List.of() : registrar.generatedRecovery(group);
    }

    public DefinitionResult<List<NativeRecipeRegistration>> recoveryConflicts(RecyclingRecipeDefinition definition) {
        requireRecipeStage();
        if (registrar == null) this.registrar = new RecipeDefinitionRegistrar(this::isRegistrationThread);
        var result = registrar.recoveryConflicts(definition);
        result.diagnostics().forEach(diagnostics);
        return result;
    }

    public DefinitionResult<RegistrationReceipt> replaceRecycling(String group,
                                                                  List<RecyclingRecipeDefinition> recipes) {
        return replaceRecycling(group, recipes, List.of());
    }

    public DefinitionResult<RegistrationReceipt> replaceRecycling(String group,
                                                                  List<RecyclingRecipeDefinition> recipes,
                                                                  List<NativeRecipeRegistration> baselines) {
        requireRecipeStage();
        if (registrar == null) this.registrar = new RecipeDefinitionRegistrar(this::isRegistrationThread);
        var result = registrar.replaceRecycling(group, recipes, baselines);
        result.diagnostics().forEach(diagnostics);
        return result;
    }

    @Override
    @GroovyBlacklist
    public void onReload() {
        var removed = clearOwned();
        if (removed.value().isEmpty())
            throw new IllegalStateException("Could not restore owned recipe groups; no owned group was cleared");
    }

    /** Explicitly restores this frontend's owned baselines during the recipe stage. */
    public DefinitionResult<Integer> clear() {
        requireRecipeStage();
        return clearOwned();
    }

    private DefinitionResult<Integer> clearOwned() {
        if (registrar == null) return DefinitionResult.success(0);
        var removed = registrar.clear();
        removed.diagnostics().forEach(diagnostics);
        return removed;
    }

    public DefinitionResult<RegistrationReceipt> replaceRecovery(String group,
                                                                 List<RecyclingContribution> contributions,
                                                                 List<? extends RecoveryDefinition> definitions,
                                                                 List<NativeRecipeRegistration> baselines) {
        requireRecipeStage();
        if (registrar == null) this.registrar = new RecipeDefinitionRegistrar(this::isRegistrationThread);
        var result = registrar.replaceRecovery(group, contributions, definitions, baselines);
        result.diagnostics().forEach(diagnostics);
        return result;
    }

    @Override
    @GroovyBlacklist
    public void afterScriptLoad() {}

    private void requireRecipeStage() {
        // Without the reload-order mixin, typed groups could restore after GroovyScript's own backups.
        if (!supported.getAsBoolean()) {
            throw new IllegalStateException("Typed Groovy definitions need GroovyScript " +
                    GroovyScriptCompatibility.TESTED_RANGE + "; imperative GroovyScript recipes still work");
        }
        if (stage.get() != LoadStage.POST_INIT) {
            throw new IllegalStateException("Typed recipe definitions must be registered in postInit");
        }
    }

    private boolean isRegistrationThread() {
        var server = FMLCommonHandler.instance().getMinecraftServerInstance();
        // Initial client loading has no server. /gs reload executes on the integrated server between world ticks.
        return server == null ? Thread.currentThread() == startupThread : server.isCallingFromMinecraftThread();
    }
}
