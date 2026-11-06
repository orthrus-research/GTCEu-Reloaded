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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CraftingRegistryTransactionTest {

    private static final AtomicInteger IDS = new AtomicInteger();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void preparationIsDetachedAndPublicationPreservesMirrorsIdsOwnersLocksAndViews() {
        var registries = mirrors();
        var name = name();
        var original = recipe(name, Items.COAL);
        registries.forEach(registry -> registry.register(original));
        registries.get(1).freeze();
        var before = registries.stream().map(CraftingRegistryImage::new).toList();
        int id = registries.getFirst().getID(original);
        var owners = registries.stream()
                .map(registry -> CraftingRegistryAccess.owners(registry).inverse().get(original))
                .toList();
        var views = registries.stream().map(ForgeRegistry::getValuesCollection).toList();
        var replacement = recipe(name, Items.DIAMOND);
        var transaction = CraftingRegistryTransaction.prepare(registries, Map.of(name, replacement));
        for (int i = 0; i < registries.size(); i++) assertTrue(before.get(i).matches(registries.get(i)));
        transaction.publish();
        for (int i = 0; i < registries.size(); i++) {
            assertSame(replacement, registries.get(i).getValue(name));
            assertEquals(id, registries.get(i).getID(replacement));
            assertSame(owners.get(i), CraftingRegistryAccess.owners(registries.get(i)).inverse().get(replacement));
            assertTrue(views.get(i).contains(replacement));
            assertFalse(views.get(i).contains(original));
        }
        assertTrue(registries.get(1).isLocked());
        assertFalse(transaction.isCurrent());
        assertThrows(IllegalStateException.class, transaction::publish);
        transaction.restore();
        for (int i = 0; i < registries.size(); i++) assertTrue(before.get(i).matches(registries.get(i)));
        assertThrows(IllegalStateException.class, transaction::restore);
    }

    @Test
    void newNamesUseAnIdFreeInEveryMirrorAndDisableKeepsThatIdentityReserved() {
        var registries = mirrors();
        var unrelated = recipe(name(), Items.COAL);
        registries.get(2).register(unrelated);
        int reserved = registries.get(2).getID(unrelated);
        var name = name();
        var value = recipe(name, Items.DIAMOND);
        var added = CraftingRegistryTransaction.prepare(registries, Map.of(name, value));
        added.publish();
        int id = registries.getFirst().getID(value);
        assertNotEquals(reserved, id);
        registries.forEach(registry -> assertEquals(id, registry.getID(value)));
        var removal = new LinkedHashMap<ResourceLocation, IRecipe>();
        removal.put(name, null);
        var disabled = CraftingRegistryTransaction.prepare(registries, removal);
        disabled.publish();
        for (var registry : registries) {
            var dummy = registry.getValue(name);
            assertInstanceOf(DisabledCraftingRecipe.class, dummy);
            assertEquals(id, registry.getID(dummy));
            assertTrue(dummy.isDynamic());
            assertFalse(dummy.canFit(3, 3));
        }
        disabled.restore();
        added.restore();
        assertSame(unrelated, registries.get(2).getValue(reserved));
        registries.forEach(registry -> assertFalse(registry.containsKey(name)));
    }

    @Test
    void removingAnAbsentNameIsANoopAndDoesNotCreateADummy() {
        var registries = mirrors();
        var before = registries.stream().map(CraftingRegistryImage::new).toList();
        var removals = new LinkedHashMap<ResourceLocation, IRecipe>();
        removals.put(name(), null);
        var transaction = CraftingRegistryTransaction.prepare(registries, removals);
        transaction.publish();
        for (int i = 0; i < registries.size(); i++) assertTrue(before.get(i).matches(registries.get(i)));
        transaction.restore();
    }

    @Test
    void noopAndRestorationPreservePublicCollectionOrder() {
        var registries = mirrors();
        for (int i = 0; i < 32; i++) {
            var value = recipe(name(), Items.COAL);
            registries.forEach(registry -> registry.register(value));
        }
        var values = registries.stream().map(registry -> List.copyOf(registry.getValuesCollection())).toList();
        var keys = registries.stream().map(registry -> List.copyOf(registry.getKeys())).toList();
        var noop = CraftingRegistryTransaction.prepare(registries, Map.of());
        noop.publish();
        for (int i = 0; i < registries.size(); i++) {
            assertEquals(values.get(i), List.copyOf(registries.get(i).getValuesCollection()));
            assertEquals(keys.get(i), List.copyOf(registries.get(i).getKeys()));
        }
        noop.restore();
        var name = keys.getFirst().getFirst();
        var replacement = CraftingRegistryTransaction.prepare(registries, Map.of(name, recipe(name, Items.DIAMOND)));
        replacement.publish();
        replacement.restore();
        for (int i = 0; i < registries.size(); i++) {
            assertEquals(values.get(i), List.copyOf(registries.get(i).getValuesCollection()));
            assertEquals(keys.get(i), List.copyOf(registries.get(i).getKeys()));
        }
    }

    @Test
    void invalidLaterRecipeAndConflictingMirrorIdsLeaveEveryRegistryUntouched() {
        var registries = mirrors();
        var name = name();
        var original = recipe(name, Items.COAL);
        registries.forEach(registry -> registry.register(original));
        var before = registries.stream().map(CraftingRegistryImage::new).toList();
        var invalid = new LinkedHashMap<ResourceLocation, IRecipe>();
        invalid.put(name, recipe(name, Items.DIAMOND));
        invalid.put(name(), recipe(name(), Items.EMERALD));
        assertThrows(IllegalArgumentException.class, () -> CraftingRegistryTransaction.prepare(registries, invalid));
        for (int i = 0; i < registries.size(); i++) assertTrue(before.get(i).matches(registries.get(i)));
        int id = registries.getFirst().getID(original);
        var mirror = registries.get(2);
        mirror.remove(name);
        var occupied = recipe(name(), Items.EMERALD);
        CraftingRegistryAccess.put(mirror, occupied.getRegistryName(), occupied, id);
        var conflicted = registries.stream().map(CraftingRegistryImage::new).toList();
        assertThrows(IllegalStateException.class,
                () -> CraftingRegistryTransaction.prepare(registries, Map.of(name, recipe(name, Items.DIAMOND))));
        for (int i = 0; i < registries.size(); i++) assertTrue(conflicted.get(i).matches(registries.get(i)));
    }

    @Test
    void stalePublicationAndRestorationDoNotEraseOutsideChanges() {
        var registries = mirrors();
        var name = name();
        var replacement = recipe(name, Items.DIAMOND);
        var transaction = CraftingRegistryTransaction.prepare(registries, Map.of(name, replacement));
        var outside = recipe(name(), Items.COAL);
        registries.get(1).register(outside);
        assertFalse(transaction.isCurrent());
        assertThrows(IllegalStateException.class, transaction::publish);
        registries.forEach(registry -> assertFalse(registry.containsKey(name)));
        assertSame(outside, registries.get(1).getValue(outside.getRegistryName()));
        var fresh = CraftingRegistryTransaction.prepare(registries, Map.of(name, replacement));
        fresh.publish();
        var later = recipe(name(), Items.EMERALD);
        registries.get(2).register(later);
        assertThrows(IllegalStateException.class, fresh::restore);
        registries.forEach(registry -> assertSame(replacement, registry.getValue(name)));
        assertSame(later, registries.get(2).getValue(later.getRegistryName()));
    }

    @Test
    void preparedTransactionsRequireTheirRegistrationThread() {
        var registries = mirrors();
        var transaction = CraftingRegistryTransaction.prepare(registries, Map.of());
        CompletableFuture.runAsync(() -> {
            assertThrows(IllegalStateException.class, transaction::isCurrent);
            assertThrows(IllegalStateException.class, transaction::publish);
            assertThrows(IllegalStateException.class, transaction::restore);
        }).join();
        transaction.publish();
        transaction.restore();
    }

    private static List<ForgeRegistry<IRecipe>> mirrors() {
        var source = (ForgeRegistry<IRecipe>) ForgeRegistries.RECIPES;
        var copies = new ArrayList<ForgeRegistry<IRecipe>>();
        for (int i = 0; i < 3; i++) copies.add(CraftingRegistryAccess.copy(source));
        return copies;
    }

    private static IRecipe recipe(ResourceLocation name, net.minecraft.item.Item output) {
        return new ShapelessRecipes("", new ItemStack(output),
                NonNullList.from(Ingredient.EMPTY, Ingredient.fromItem(Items.PAPER))).setRegistryName(name);
    }

    private static ResourceLocation name() {
        return new ResourceLocation("crafting_transaction_test", "recipe_" + IDS.incrementAndGet());
    }
}
