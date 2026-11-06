package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.definition.ItemValue;
import gregtech.api.recipes.definition.RecyclingRecoveryDefinition;
import gregtech.api.recipes.fixtures.RecyclingUnificationFixture;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.RecyclingManager;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.ItemAndMetadata;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.unification.stack.RecyclingData;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static gregtech.api.GTValues.M;
import static org.junit.jupiter.api.Assertions.*;

class RecyclingCompositionRetentionTest {

    private static final SourceOrigin ORIGIN = new SourceOrigin("java", "composition-retention", 0, 0,
            "late-input", "postInit");
    private RecyclingUnificationFixture unification;
    private final RecyclingManager manager = new RecyclingManager();
    private final RecyclingOwner owner = new RecyclingOwner();
    private final RecyclingOwner helper = new RecyclingOwner();
    private ItemStack output;
    private List<MaterialStack> secondary;

    @BeforeEach
    void bootstrap() throws Exception {
        Bootstrap.perform();
        output = new ItemStack(Items.PAPER, 1, 29000);
        unification = new RecyclingUnificationFixture();
        secondary = List.copyOf(OrePrefix.ingot.secondaryMaterials);
    }

    @AfterEach
    void restore() throws Exception {
        OrePrefix.ingot.secondaryMaterials.clear();
        OrePrefix.ingot.secondaryMaterials.addAll(secondary);
        manager.ownership().prepare(owner, "all", List.of(), true).publish();
        manager.ownership().prepare(helper, "all", List.of(), true).publish();
        unification.close();
    }

    @Test
    void unrelatedHelperEditRetainsCompositionAndGeneratedRecipeIdentities() {
        var first = declare(OreDictUnifier.get(OrePrefix.ingot, Materials.Iron));
        var generated = manager.ownership().generatedRecovery(owner, "content");
        assertFalse(generated.isEmpty());
        long revision = manager.revision();
        var unchanged = helperEdit();
        assertSame(first.resolved(), unchanged.resolved());
        unchanged.publish();
        assertEquals(revision, manager.revision());
        var retained = manager.ownership().generatedRecovery(owner, "content");
        assertEquals(generated.size(), retained.size());
        for (int i = 0; i < generated.size(); i++) {
            assertSame(generated.get(i).recipe(), retained.get(i).recipe());
        }
    }

    @Test
    void lateOreRegistrationResolvesPreviouslyUnknownIngredient() {
        var raw = new ItemStack(new Item().setRegistryName("retention_test", "late_ingredient"));
        declare(raw);
        assertNull(manager.getRecyclingData(output));
        assertTrue(manager.ownership().generatedRecovery(owner, "content").isEmpty());
        long revision = manager.revision();
        registerIron(raw);
        assertEquals(revision, manager.revision());
        helperEdit().publish();
        assertEquals(M, quantity(Materials.Iron));
        assertFalse(manager.ownership().generatedRecovery(owner, "content").isEmpty());
    }

    @Test
    void directSecondaryMaterialEditInvalidatesCompositionWithoutManagerRevision() {
        declare(OreDictUnifier.get(OrePrefix.ingot, Materials.Iron));
        long revision = manager.revision();
        OrePrefix.ingot.secondaryMaterials.add(new MaterialStack(Materials.Copper, M));
        assertEquals(revision, manager.revision());
        helperEdit().publish();
        assertEquals(M, quantity(Materials.Iron));
        assertEquals(M, quantity(Materials.Copper));
    }

    @Test
    void externalMetadataChangeRecalculatesDependentRecovery() {
        var raw = OreDictUnifier.get(OrePrefix.ingot, Materials.Iron);
        declare(raw);
        manager.registerRecyclingData(raw, new RecyclingData(new MaterialStack(Materials.Copper, 2 * M)));
        helperEdit().publish();
        assertEquals(0, quantity(Materials.Iron));
        assertEquals(2 * M, quantity(Materials.Copper));
        assertTrue(manager.ownership().generatedRecovery(owner, "content").stream()
                .anyMatch(entry -> entry.recipe().getOutputs().stream()
                        .anyMatch(stack -> stack.isItemEqual(OreDictUnifier.get(OrePrefix.dust, Materials.Copper)) &&
                                stack.getCount() == 2)));
    }

    @Test
    void lateRegistrationBetweenPreparationAndPublicationRejectsStaleChange() {
        var raw = new ItemStack(new Item().setRegistryName("retention_test", "late_publication"));
        declare(raw);
        var prepared = helperEdit();
        registerIron(raw);
        assertFalse(prepared.isCurrent());
        assertThrows(IllegalStateException.class, prepared::publish);
        assertNull(manager.getRecyclingData(output));
        helperEdit().publish();
        assertEquals(M, quantity(Materials.Iron));
    }

    @Test
    void ownedMetadataTamperingIsStillRejected() {
        declare(OreDictUnifier.get(OrePrefix.ingot, Materials.Iron));
        var accepted = manager.getRecyclingData(output);
        try {
            manager.registerRecyclingData(output, RecyclingData.empty());
            assertThrows(IllegalStateException.class, this::helperEdit);
        } finally {
            manager.registerRecyclingData(output, accepted);
        }
    }

    @Test
    void deletingAndRecreatingContributionDoesNotReuseRemovedComposition() {
        var raw = OreDictUnifier.get(OrePrefix.ingot, Materials.Iron);
        declare(raw);
        manager.ownership().prepareRecovery(owner, "content", List.of(), List.of(), List.of(), false).publish();
        helperEdit().publish();
        assertNull(manager.getRecyclingData(output));
        assertTrue(manager.ownership().generatedRecovery(owner, "content").isEmpty());
        declare(raw);
        assertEquals(M, quantity(Materials.Iron));
        assertFalse(manager.ownership().generatedRecovery(owner, "content").isEmpty());
    }

    private RecyclingOwnershipRegistry.Change declare(ItemStack input) {
        var contribution = new RecyclingContribution(ORIGIN, new ItemAndMetadata(output), 1,
                Map.of(new ItemAndMetadata(input), 1L));
        var change = manager.ownership().prepareRecovery(owner, "content", List.of(contribution),
                List.of(new RecyclingRecoveryDefinition(ORIGIN, ItemValue.of(output), true, null)),
                List.of(), false);
        change.publish();
        return change;
    }

    private RecyclingOwnershipRegistry.Change helperEdit() {
        return manager.ownership().prepareRecovery(helper, "helper", List.of(), List.of(), List.of(), false);
    }

    private long quantity(Material material) {
        return manager.getRecyclingData(output).getMaterials().stream()
                .filter(stack -> stack.material == material).mapToLong(stack -> stack.amount).sum();
    }

    private static void registerIron(ItemStack input) {
        OreDictUnifier.onItemRegistration(new OreDictionary.OreRegisterEvent("ingotIron", input));
    }
}
