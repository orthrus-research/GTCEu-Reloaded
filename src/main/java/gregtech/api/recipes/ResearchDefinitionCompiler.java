package gregtech.api.recipes;

import gregtech.api.definition.DefinitionDiagnostic;
import gregtech.api.recipes.builders.AssemblyLineRecipeBuilder;
import gregtech.api.recipes.definition.RecipeDefinition;
import gregtech.api.recipes.definition.ResearchRequirement;
import gregtech.api.util.AssemblyLineManager;
import gregtech.common.ConfigHolder;

import java.util.HashSet;
import java.util.List;

/** Validates research before invoking native builder methods, which only mutate unpublished state. */
final class ResearchDefinitionCompiler {

    private ResearchDefinitionCompiler() {}

    static void apply(RecipeDefinition definition, RecipeBuilder<?> builder, List<ResearchRequirement> requirements,
                      String path, List<DefinitionDiagnostic> errors) {
        if (!(builder instanceof AssemblyLineRecipeBuilder assembly)) {
            error(definition, errors, path, "Research requires an assembly line builder");
            return;
        }
        if (requirements.isEmpty()) {
            error(definition, errors, path, "Research needs at least one requirement");
            return;
        }
        var ids = new HashSet<String>();
        boolean existing = requirements.stream().anyMatch(value -> value instanceof ResearchRequirement.Existing);
        if (existing && requirements.size() != 1) {
            error(definition, errors, path,
                    "Existing research must be a single requirement without generated research");
            return;
        }
        for (int i = 0; i < requirements.size(); i++) {
            ResearchRequirement requirement = requirements.get(i);
            String entryPath = path + ".requirements[" + i + "]";
            if (requirement.id().isBlank() || !ids.add(requirement.id())) {
                error(definition, errors, entryPath + ".id", "Research IDs must be nonempty and unique");
            }
            var data = requirement.dataItem().stack();
            if (data.getCount() != 1 || !AssemblyLineManager.isStackDataItem(data, true)) {
                error(definition, errors, entryPath + ".dataItem", "Data item must be one native IDataItem");
            }
            switch (requirement) {
                case ResearchRequirement.Existing ignored -> {}
                case ResearchRequirement.Scanner scanner -> {
                    if (scanner.researchItem().isEmpty() || scanner.duration() <= 0 || scanner.eut() <= 0)
                        error(definition, errors, entryPath, "Scanner needs an item, positive duration and EU/t");
                }
                case ResearchRequirement.Station station -> {
                    if (station.researchItem().isEmpty() || station.computationPerTick() <= 0 ||
                            station.totalComputation() < station.computationPerTick() || station.eut() <= 0)
                        error(definition, errors, entryPath,
                                "Station needs an item, positive EU/t and computation, with total at least rate");
                }
            }
        }
        if (!errors.isEmpty() || !ConfigHolder.machines.enableResearch) return;
        for (ResearchRequirement requirement : requirements) {
            switch (requirement) {
                case ResearchRequirement.Existing value -> assembly.researchWithoutRecipe(value.id(),
                        value.dataItem().stack());
                case ResearchRequirement.Scanner value -> assembly.scannerResearch(research -> research
                        .researchId(value.id()).dataStack(value.dataItem().stack())
                        .researchStack(value.researchItem().stack(), value.ignoreNbt())
                        .duration(value.duration()).EUt(value.eut()));
                case ResearchRequirement.Station value -> assembly.stationResearch(research -> research
                        .researchId(value.id()).dataStack(value.dataItem().stack())
                        .researchStack(value.researchItem().stack(), value.ignoreNbt())
                        .CWUt(value.computationPerTick(), value.totalComputation()).EUt(value.eut()));
            }
        }
    }

    private static void error(RecipeDefinition definition, List<DefinitionDiagnostic> errors, String path,
                              String message) {
        errors.add(new DefinitionDiagnostic("recipe.research.invalid", DefinitionDiagnostic.Severity.ERROR,
                definition.origin(), path, message));
    }
}
