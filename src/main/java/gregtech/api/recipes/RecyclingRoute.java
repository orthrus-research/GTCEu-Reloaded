package gregtech.api.recipes;

import gregtech.api.definition.SourceOrigin;
import gregtech.api.unification.material.MarkerMaterial;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.stack.ItemAndMetadata;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.unification.stack.RecyclingData;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Prepared routes after explicit override/intersection, before dependency resolution or native publication. */
record RecyclingRoute(SourceOrigin origin, Map<ItemAndMetadata, RecyclingQuantity> items,
                      Map<Material, RecyclingQuantity> materials, boolean authoritativeEmpty) {

    RecyclingRoute {
        items = Map.copyOf(items);
        materials = Map.copyOf(materials);
    }

    static List<RecyclingRoute> prepare(List<RecyclingContribution> declarations) {
        List<RecyclingRoute> routes = new ArrayList<>();
        boolean minimum = false;
        for (var declaration : declarations) {
            var route = from(declaration);
            switch (declaration.policy()) {
                case REPLACE -> {
                    routes.clear();
                    routes.add(route);
                    minimum = true;
                }
                case MINIMUM_INPUTS -> {
                    if (!routes.isEmpty() && !minimum) throw mixedPolicy(declaration);
                    if (routes.isEmpty()) routes.add(route);
                    else routes.set(0, intersect(routes.getFirst(), route));
                    minimum = true;
                }
                case CONSISTENT -> {
                    if (minimum) throw mixedPolicy(declaration);
                    routes.add(route);
                }
            }
        }
        return List.copyOf(routes);
    }

    private static RecyclingOwnershipException mixedPolicy(RecyclingContribution declaration) {
        return new RecyclingOwnershipException(declaration.origin(),
                "Cannot mix strict and minimum-input recycling routes without an explicit replacement");
    }

    private static RecyclingRoute from(RecyclingContribution declaration) {
        Map<ItemAndMetadata, RecyclingQuantity> items = new LinkedHashMap<>();
        declaration.inputs()
                .forEach((item, count) -> items.put(item, RecyclingQuantity.of(count, declaration.outputCount())));
        Map<Material, RecyclingQuantity> materials = new LinkedHashMap<>();
        declaration.materials().forEach(
                (material, count) -> materials.put(material, RecyclingQuantity.of(count, declaration.outputCount())));
        return new RecyclingRoute(declaration.origin(), items, materials,
                declaration.policy() != RecyclingRoutePolicy.CONSISTENT);
    }

    private static RecyclingRoute intersect(RecyclingRoute earlier, RecyclingRoute later) {
        return new RecyclingRoute(later.origin(), minimum(earlier.items(), later.items()),
                minimum(earlier.materials(), later.materials()), true);
    }

    private static <K> Map<K, RecyclingQuantity> minimum(Map<K, RecyclingQuantity> earlier,
                                                         Map<K, RecyclingQuantity> later) {
        Map<K, RecyclingQuantity> common = new LinkedHashMap<>();
        earlier.forEach((key, amount) -> {
            RecyclingQuantity other = later.get(key);
            if (other != null) common.put(key, amount.compareTo(other) <= 0 ? amount : other);
        });
        return common;
    }

    RecyclingData resolve(Function<ItemStack, RecyclingData> composition) {
        Map<Material, RecyclingQuantity> totals = new HashMap<>();
        materials.forEach((material, amount) -> {
            if (!(material instanceof MarkerMaterial)) totals.merge(material, amount, RecyclingQuantity::add);
        });
        boolean knownEmpty = authoritativeEmpty;
        for (var entry : items.entrySet()) {
            ItemStack stack = entry.getKey().toItemStack();
            RecyclingData explicit = composition.apply(stack);
            if (explicit != null && explicit.getMaterials().isEmpty()) knownEmpty = true;
            RecyclingHandler.materialTotals(stack, ignored -> explicit).forEach((material, amount) -> totals
                    .merge(material, entry.getValue().multiply(amount), RecyclingQuantity::add));
        }
        if (totals.isEmpty()) return knownEmpty ? RecyclingData.empty() : null;
        List<MaterialStack> result = totals.entrySet().stream()
                .map(entry -> new MaterialStack(entry.getKey(), entry.getValue().floorLongExact()))
                .sorted(Comparator.<MaterialStack>comparingLong(stack -> stack.amount).reversed()
                        .thenComparing(stack -> stack.material.getRegistryName().toString()))
                .toList();
        return new RecyclingData(result);
    }
}
