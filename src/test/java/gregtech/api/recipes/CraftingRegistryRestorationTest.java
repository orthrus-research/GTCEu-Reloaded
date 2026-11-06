package gregtech.api.recipes;

import gregtech.Bootstrap;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CraftingRegistryRestorationTest {

    private static final AtomicInteger IDS = new AtomicInteger();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void selectiveRestorationKeepsUnrelatedRegistrationsAndOriginalPositions() {
        var registries = mirrors();
        var original = recipe(name());
        var neighbor = recipe(name());
        registries.forEach(registry -> {
            registry.register(original);
            registry.register(neighbor);
        });
        var order = List.copyOf(registries.getFirst().getKeys());
        var replacement = recipe(original.getRegistryName());
        var added = recipe(name());
        var transaction = publish(registries,
                Map.of(original.getRegistryName(), replacement, added.getRegistryName(), added));
        assertEquals(order, new ArrayList<>(registries.getFirst().getKeys()).subList(0, order.size()));
        var outside = recipe(name());
        registries.forEach(registry -> registry.register(outside));
        registries.get(1).freeze();
        assertThrows(IllegalStateException.class, transaction::restore);
        var restore = transaction.restoration().prepare();
        assertSame(replacement, registries.getFirst().getValue(original.getRegistryName()));
        restore.publish();
        for (var registry : registries) {
            assertSame(original, registry.getValue(original.getRegistryName()));
            assertFalse(registry.containsKey(added.getRegistryName()));
            assertSame(outside, registry.getValue(outside.getRegistryName()));
            var expected = new ArrayList<>(order);
            expected.add(outside.getRegistryName());
            assertEquals(expected, List.copyOf(registry.getKeys()));
        }
        assertTrue(registries.get(1).isLocked());
        restore.restore();
        assertSame(replacement, registries.getFirst().getValue(original.getRegistryName()));
        assertSame(added, registries.getFirst().getValue(added.getRegistryName()));
        assertSame(outside, registries.getFirst().getValue(outside.getRegistryName()));
    }

    @Test
    void eachMirrorRecoversItsOwnRecipeAndOriginalAbsence() {
        var registries = mirrors();
        var name = name();
        var active = recipe(name);
        var frozen = recipe(name);
        registries.get(0).register(active);
        registries.get(1).register(frozen);
        var before = registries.stream().map(CraftingRegistryImage::new).toList();
        var transaction = publish(registries, Map.of(name, recipe(name)));
        transaction.restoration().prepare().publish();
        for (int i = 0; i < registries.size(); i++) assertTrue(before.get(i).matches(registries.get(i)));
        assertSame(active, registries.get(0).getValue(name));
        assertSame(frozen, registries.get(1).getValue(name));
        assertFalse(registries.get(2).containsKey(name));
    }

    @Test
    void replacementsComposeThroughDisablingAndNewNamesRetainingTheFirstBaseline() {
        var registries = mirrors();
        var original = recipe(name());
        registries.forEach(registry -> registry.register(original));
        var before = registries.stream().map(CraftingRegistryImage::new).toList();
        var first = publish(registries, Map.of(original.getRegistryName(), recipe(original.getRegistryName())));
        var disable = new LinkedHashMap<ResourceLocation, IRecipe>();
        disable.put(original.getRegistryName(), null);
        var second = publish(registries, disable);
        var added = recipe(name());
        var third = publish(registries, Map.of(original.getRegistryName(), recipe(original.getRegistryName()),
                added.getRegistryName(), added));
        var baseline = first.restoration().then(second.restoration()).then(third.restoration());
        baseline.prepare().publish();
        for (int i = 0; i < registries.size(); i++) assertTrue(before.get(i).matches(registries.get(i)));
        assertThrows(IllegalStateException.class, baseline::prepare);
    }

    @Test
    void outsideEditsAndAliasesPreventRestorationBeforeAnyRegistryChanges() {
        var registries = mirrors();
        var added = recipe(name());
        var transaction = publish(registries, Map.of(added.getRegistryName(), added));
        var alias = name();
        CraftingRegistryAccess.aliases(registries.get(1)).put(alias, added.getRegistryName());
        var before = registries.stream().map(CraftingRegistryImage::new).toList();
        assertThrows(IllegalStateException.class, () -> transaction.restoration().prepare());
        for (int i = 0; i < registries.size(); i++) assertTrue(before.get(i).matches(registries.get(i)));
        CraftingRegistryAccess.aliases(registries.get(1)).remove(alias);
        var replacement = publish(registries, Map.of(added.getRegistryName(), recipe(added.getRegistryName())));
        assertThrows(IllegalStateException.class, () -> transaction.restoration().prepare());
        replacement.restore();
        transaction.restoration().prepare().publish();
        registries.forEach(registry -> assertFalse(registry.containsKey(added.getRegistryName())));
    }

    @Test
    void preparedRestorationRejectsStaleWholeRegistryChangesAndReleasesOwnedIdsOnly() {
        var registries = mirrors();
        var added = recipe(name());
        var transaction = publish(registries, Map.of(added.getRegistryName(), added));
        int id = registries.getFirst().getID(added);
        var restore = transaction.restoration().prepare();
        var outside = recipe(name());
        registries.forEach(registry -> registry.register(outside));
        int outsideId = registries.getFirst().getID(outside);
        assertThrows(IllegalStateException.class, restore::publish);
        transaction.restoration().prepare().publish();
        for (var registry : registries) {
            assertFalse(CraftingRegistryAccess.availability(registry).get(id));
            assertTrue(CraftingRegistryAccess.availability(registry).get(outsideId));
            assertSame(outside, registry.getValue(outsideId));
        }
        var reused = recipe(name());
        publish(registries, Map.of(reused.getRegistryName(), reused));
        registries.forEach(registry -> assertEquals(id, registry.getID(reused)));
    }

    @Test
    void unavailableBaselinesAndBrokenReplacementChainsAreRejected() {
        var registries = mirrors();
        var name = name();
        var first = CraftingRegistryTransaction.prepare(registries, Map.of(name, recipe(name)));
        assertThrows(IllegalStateException.class, first::restoration);
        first.publish();
        var baseline = first.restoration();
        first.restore();
        var later = publish(registries, Map.of(name, recipe(name)));
        assertThrows(IllegalStateException.class, () -> baseline.then(later.restoration()));
        var other = publish(mirrors(), Map.of(name, recipe(name)));
        assertThrows(IllegalStateException.class, () -> baseline.then(other.restoration()));
        var restore = later.restoration().prepare();
        restore.publish();
        assertThrows(IllegalStateException.class, restore::restoration);
    }

    private static CraftingRegistryTransaction publish(List<ForgeRegistry<IRecipe>> registries,
                                                       Map<ResourceLocation, IRecipe> values) {
        var transaction = CraftingRegistryTransaction.prepare(registries, values);
        transaction.publish();
        return transaction;
    }

    private static List<ForgeRegistry<IRecipe>> mirrors() {
        var source = (ForgeRegistry<IRecipe>) ForgeRegistries.RECIPES;
        var result = new ArrayList<ForgeRegistry<IRecipe>>();
        for (int i = 0; i < 3; i++) result.add(CraftingRegistryAccess.copy(source));
        return result;
    }

    private static IRecipe recipe(ResourceLocation name) {
        return new ShapelessRecipes("", new ItemStack(Items.DIAMOND),
                NonNullList.from(Ingredient.EMPTY, Ingredient.fromItem(Items.PAPER))).setRegistryName(name);
    }

    private static ResourceLocation name() {
        return new ResourceLocation("crafting_restoration_test", "recipe_" + IDS.incrementAndGet());
    }
}
