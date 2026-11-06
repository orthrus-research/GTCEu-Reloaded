package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.definition.*;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class CustomCraftingDefinitionTest {

    private static final SourceOrigin SOURCE = new SourceOrigin("java", "custom_crafting_test", 1, 1, "recipe",
            "postInit");

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void invalidFactoriesLeavePriorStateAndOtherDeclarationsIntact() {
        var registrar = new RecipeDefinitionRegistrar();
        var name = new ResourceLocation("custom_crafting_test", "existing");
        var added = new ResourceLocation("custom_crafting_test", "new");
        try {
            assertTrue(registrar.replaceCrafting("custom", List.of(ordinary(name))).value().isPresent());
            var original = ForgeRegistries.RECIPES.getValue(name);
            var accepted = registrar.craftingGroup("custom").orElseThrow();
            List<Supplier<? extends IRecipe>> invalid = List.of(() -> null, () -> recipe(added), () -> original,
                    () -> {
                        throw new IllegalArgumentException("bad authoring");
                    });
            for (var factory : invalid) {
                var result = registrar.replaceCrafting("custom", List.of(ordinary(added),
                        new CustomCraftingDefinition(SOURCE, name, factory)));
                assertTrue(result.value().isEmpty());
                assertEquals("crafting.custom.invalid", result.diagnostics().getFirst().code());
                assertEquals(SOURCE, result.diagnostics().getFirst().origin());
                assertSame(original, ForgeRegistries.RECIPES.getValue(name));
                assertFalse(ForgeRegistries.RECIPES.containsKey(added));
                assertSame(accepted, registrar.craftingGroup("custom").orElseThrow());
            }
        } finally {
            assertTrue(registrar.clear().value().isPresent());
        }
    }

    @Test
    void factoriesRunOnlyDuringRegistrationAndSupportDisableAndReenable() {
        var registrar = new RecipeDefinitionRegistrar();
        var name = new ResourceLocation("custom_crafting_test", "lifecycle");
        var calls = new AtomicInteger();
        var definition = new CustomCraftingDefinition(SOURCE, name, () -> {
            calls.incrementAndGet();
            return recipe(name);
        });
        try {
            assertTrue(registrar.replaceCrafting("custom", List.of(definition)).value().isPresent());
            var first = ForgeRegistries.RECIPES.getValue(name);
            for (int i = 0; i < 20; i++) {
                assertTrue(first.canFit(3, 3));
                assertEquals(Items.DIAMOND, first.getCraftingResult(null).getItem());
            }
            assertEquals(1, calls.get());
            assertTrue(registrar.replaceCrafting("custom", List.of(new DisabledCraftingDefinition(SOURCE, name)))
                    .value().isPresent());
            assertFalse(ForgeRegistries.RECIPES.getValue(name).canFit(3, 3));
            assertEquals(1, calls.get());
            assertTrue(registrar.replaceCrafting("custom", List.of(definition)).value().isPresent());
            assertEquals(2, calls.get());
            assertNotSame(first, ForgeRegistries.RECIPES.getValue(name));
        } finally {
            assertTrue(registrar.clear().value().isPresent());
        }
        assertFalse(ForgeRegistries.RECIPES.containsKey(name));
    }

    private static ShapelessCraftingDefinition ordinary(ResourceLocation name) {
        return new ShapelessCraftingDefinition(SOURCE, name, "", ItemValue.of(new ItemStack(Items.DIAMOND)),
                List.of(ItemIngredient.exact(new ItemStack(Items.PAPER))));
    }

    private static IRecipe recipe(ResourceLocation name) {
        return CraftingDefinitionCompiler.compile(ordinary(name)).value().orElseThrow();
    }
}
