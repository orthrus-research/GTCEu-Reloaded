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
import net.minecraftforge.registries.RegistryManager;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CraftingFrozenStageTest {

    private static final AtomicInteger IDS = new AtomicInteger();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void nativeFrozenCopyRestoresActiveBaselineAndOriginalAbsenceWithNewOwnerKeys() throws Exception {
        var active = copy();
        var vanilla = copy();
        var name = name();
        var added = name();
        var original = recipe(name);
        var vanillaOriginal = recipe(name);
        active.register(original);
        vanilla.register(vanillaOriginal);
        var beforeActive = new CraftingRegistryImage(active);
        var beforeVanilla = new CraftingRegistryImage(vanilla);
        var beforeFrozen = frozenCopy(active);
        var transaction = publish(List.of(active, vanilla), Map.of(name, recipe(name), added, recipe(added)));
        var frozen = frozenCopy(active);
        Object activeOwner = CraftingRegistryAccess.owners(active).inverse().get(active.getValue(name));
        Object frozenOwner = CraftingRegistryAccess.owners(frozen).inverse().get(frozen.getValue(name));
        assertNotSame(activeOwner, frozenOwner);
        assertTrue(CraftingRegistryAccess.isDefaultOwner(frozenOwner, name));
        var activeEntry = CraftingRegistryEntry.capture(active, name, active.getID(active.getValue(name)));
        var frozenEntry = CraftingRegistryEntry.capture(frozen, name, frozen.getID(frozen.getValue(name)));
        assertTrue(activeEntry.matchesFrozenCopy(frozenEntry), () -> activeEntry + "\n" + frozenEntry);
        var baseline = transaction.restoration().adoptFrozen(List.of(active, frozen, vanilla), frozen);
        baseline.prepare().publish();
        assertTrue(beforeActive.matches(active));
        assertTrue(beforeVanilla.matches(vanilla));
        assertTrue(new CraftingRegistryImage(beforeFrozen).matches(frozen));
        assertSame(original, frozen.getValue(name));
        assertFalse(frozen.containsKey(added));
        assertSame(vanillaOriginal, vanilla.getValue(name));
    }

    @Test
    void nativeActiveResyncPreservesOwnershipAcrossWorldLoad() throws Exception {
        var active = copy();
        var vanilla = copy();
        var name = name();
        var added = name();
        var original = recipe(name);
        active.register(original);
        vanilla.register(original);
        var before = new CraftingRegistryImage(frozenCopy(active));
        var transaction = publish(List.of(active, vanilla), Map.of(name, recipe(name), added, recipe(added)));
        var retained = CraftingRegistryEntry.capture(active, name, active.getID(active.getValue(name)));
        var frozen = frozenCopy(active);
        sync(active, frozen);
        var resumed = CraftingRegistryEntry.capture(active, name, active.getID(active.getValue(name)));
        assertNotSame(retained.owner(), resumed.owner());
        assertTrue(CraftingRegistryAccess.isDefaultOwner(resumed.owner(), name));
        var baseline = transaction.restoration().adoptFrozen(List.of(active, frozen, vanilla), frozen);
        sync(active, frozen);
        baseline = baseline.resyncActive();
        sync(active, frozen);
        baseline = baseline.resyncActive();
        baseline.prepare().publish();
        assertSame(original, active.getValue(name));
        assertSame(original, frozen.getValue(name));
        assertFalse(active.containsKey(added));
        assertTrue(before.matches(active));
    }

    @Test
    void differentOwnerNamespaceStillRejectsAfterNativeResync() throws Exception {
        var active = copy();
        var vanilla = copy();
        var name = name();
        var transaction = publish(List.of(active, vanilla), Map.of(name, recipe(name)));
        var frozen = frozenCopy(active);
        sync(active, frozen);
        var value = active.getValue(name);
        var previousOwner = CraftingRegistryAccess.owners(active).inverse().remove(value);
        var constructor = previousOwner.getClass().getDeclaredConstructor(String.class, ResourceLocation.class);
        constructor.setAccessible(true);
        CraftingRegistryAccess.owners(active).put(constructor.newInstance("outside", name), value);
        var before = new CraftingRegistryImage(active);
        assertThrows(IllegalStateException.class,
                () -> transaction.restoration().adoptFrozen(List.of(active, frozen, vanilla), frozen));
        assertTrue(before.matches(active));
    }

    @Test
    void nativeResyncStillRejectsRecipeAliasAndAllocationEdits() throws Exception {
        var active = copy();
        var vanilla = copy();
        var name = name();
        var transaction = publish(List.of(active, vanilla), Map.of(name, recipe(name)));
        var frozen = frozenCopy(active);
        var baseline = transaction.restoration().adoptFrozen(List.of(active, frozen, vanilla), frozen);
        sync(active, frozen);
        int id = active.getID(active.getValue(name));
        CraftingRegistryAccess.blocked(active).add(id);
        assertThrows(IllegalStateException.class, baseline::resyncActive);
        CraftingRegistryAccess.blocked(active).remove(id);
        var alias = name();
        CraftingRegistryAccess.aliases(active).put(alias, name);
        assertThrows(IllegalStateException.class, baseline::resyncActive);
        CraftingRegistryAccess.aliases(active).remove(alias);
        var outside = publish(List.of(active), Map.of(name, recipe(name)));
        assertThrows(IllegalStateException.class, baseline::resyncActive);
        outside.restore();
        CraftingRegistryAccess.owners(frozen).inverse().remove(frozen.getValue(name));
        assertThrows(IllegalStateException.class, baseline::resyncActive);
    }

    @Test
    void mismatchedFrozenEntriesAndOtherStageChangesAreRejectedWithoutMutation() throws Exception {
        var active = copy();
        var vanilla = copy();
        var name = name();
        var transaction = publish(List.of(active, vanilla), Map.of(name, recipe(name)));
        var frozen = frozenCopy(active);
        var originals = List.of(active, frozen, vanilla);
        var outside = publish(originals, Map.of(name, recipe(name)));
        var images = originals.stream().map(CraftingRegistryImage::new).toList();
        assertThrows(IllegalStateException.class, () -> transaction.restoration().adoptFrozen(originals, frozen));
        for (int i = 0; i < originals.size(); i++) assertTrue(images.get(i).matches(originals.get(i)));
        outside.restore();
        CraftingRegistryAccess.aliases(frozen).put(name(), name);
        assertThrows(IllegalStateException.class, () -> transaction.restoration().adoptFrozen(originals, frozen));
        assertThrows(IllegalStateException.class,
                () -> transaction.restoration().adoptFrozen(List.of(copy(), frozen, vanilla), frozen));
        assertThrows(IllegalStateException.class,
                () -> transaction.restoration().adoptFrozen(List.of(active, vanilla), vanilla));
    }

    @Test
    void adoptedBaselinesComposeAcrossDisableReenableAndIndependentGroups() throws Exception {
        var active = copy();
        var vanilla = copy();
        var name = name();
        var other = name();
        var original = recipe(name);
        active.register(original);
        vanilla.register(original);
        var before = new CraftingRegistryImage(active);
        var first = publish(List.of(active, vanilla), Map.of(name, recipe(name)));
        var second = publish(List.of(active, vanilla), Map.of(other, recipe(other)));
        var frozen = frozenCopy(active);
        var current = List.of(active, frozen, vanilla);
        var firstBaseline = first.restoration().adoptFrozen(current, frozen);
        var secondBaseline = second.restoration().adoptFrozen(current, frozen);
        var disabled = new java.util.LinkedHashMap<ResourceLocation, IRecipe>();
        disabled.put(name, null);
        var disable = publish(current, disabled);
        firstBaseline = firstBaseline.then(disable.restoration());
        var reenable = publish(current, Map.of(name, recipe(name)));
        firstBaseline = firstBaseline.then(reenable.restoration());
        firstBaseline.then(secondBaseline).prepare().publish();
        assertTrue(before.matches(active));
        assertTrue(before.matches(vanilla));
        assertTrue(new CraftingRegistryImage(frozenCopy(active)).matches(frozen));
    }

    @Test
    void frozenOwnerAndAllocationRulesAreValidated() throws Exception {
        var active = copy();
        var vanilla = copy();
        var name = name();
        var transaction = publish(List.of(active, vanilla), Map.of(name, recipe(name)));
        var frozen = frozenCopy(active);
        int id = frozen.getID(frozen.getValue(name));
        CraftingRegistryAccess.blocked(frozen).add(id);
        assertThrows(IllegalStateException.class,
                () -> transaction.restoration().adoptFrozen(List.of(active, frozen, vanilla), frozen));
        CraftingRegistryAccess.blocked(frozen).remove(id);
        CraftingRegistryAccess.owners(frozen).inverse().remove(frozen.getValue(name));
        assertThrows(IllegalStateException.class,
                () -> transaction.restoration().adoptFrozen(List.of(active, frozen, vanilla), frozen));
    }

    private static CraftingRegistryTransaction publish(List<ForgeRegistry<IRecipe>> registries,
                                                       Map<ResourceLocation, IRecipe> entries) {
        var transaction = CraftingRegistryTransaction.prepare(registries, entries);
        transaction.publish();
        return transaction;
    }

    @SuppressWarnings("unchecked")
    private static ForgeRegistry<IRecipe> frozenCopy(ForgeRegistry<IRecipe> source) throws Exception {
        var copy = ForgeRegistry.class.getDeclaredMethod("copy", RegistryManager.class);
        copy.setAccessible(true);
        var result = (ForgeRegistry<IRecipe>) copy.invoke(source, RegistryManager.FROZEN);
        sync(result, source);
        result.freeze();
        return result;
    }

    private static void sync(ForgeRegistry<IRecipe> target, ForgeRegistry<IRecipe> source) throws Exception {
        var sync = ForgeRegistry.class.getDeclaredMethod("sync", ResourceLocation.class, ForgeRegistry.class);
        sync.setAccessible(true);
        // Loader.freezeData runs outside mod dispatch; Bootstrap deliberately retains a test mod container.
        var loader = net.minecraftforge.fml.common.Loader.instance();
        var owner = loader.activeModContainer();
        loader.setActiveModContainer(null);
        try {
            sync.invoke(target, new ResourceLocation("minecraft", "recipes"), source);
        } finally {
            loader.setActiveModContainer(owner);
        }
    }

    private static ForgeRegistry<IRecipe> copy() {
        return CraftingRegistryAccess.copy((ForgeRegistry<IRecipe>) ForgeRegistries.RECIPES);
    }

    private static IRecipe recipe(ResourceLocation name) {
        return new ShapelessRecipes("", new ItemStack(Items.DIAMOND),
                NonNullList.from(Ingredient.EMPTY, Ingredient.fromItem(Items.PAPER))).setRegistryName(name);
    }

    private static ResourceLocation name() {
        return new ResourceLocation("crafting_frozen_test", "recipe_" + IDS.incrementAndGet());
    }
}
