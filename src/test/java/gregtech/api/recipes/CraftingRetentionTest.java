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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CraftingRetentionTest {

    private static final SourceOrigin SOURCE = new SourceOrigin("java", "crafting_retention", 1, 1, "recipe",
            "postInit");
    private static final AtomicInteger IDS = new AtomicInteger();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void incrementalEditsRetainOrdinaryRecipesAndInvokeOnlyNewFactories() {
        var registrar = new RecipeDefinitionRegistrar();
        var plain = ordinary(name());
        var calls = new AtomicInteger();
        var custom = custom(name(), calls);
        try {
            assertTrue(registrar.replaceCrafting("content", List.of(plain, custom)).value().isPresent());
            var original = ForgeRegistries.RECIPES.getValue(plain.name());
            var originalCustom = ForgeRegistries.RECIPES.getValue(custom.name());
            assertTrue(registrar.replaceCrafting("content", List.of(plain, custom, ordinary(name())))
                    .value().isPresent());
            assertSame(original, ForgeRegistries.RECIPES.getValue(plain.name()));
            assertSame(originalCustom, ForgeRegistries.RECIPES.getValue(custom.name()));
            assertEquals(1, calls.get());
        } finally {
            assertTrue(registrar.clear().value().isPresent());
        }
    }

    @Test
    void freshEqualDeclarationRequestsNewCompilation() {
        var registrar = new RecipeDefinitionRegistrar();
        var definition = ordinary(name());
        try {
            assertTrue(registrar.replaceCrafting("content", List.of(definition)).value().isPresent());
            var original = ForgeRegistries.RECIPES.getValue(definition.name());
            var fresh = ordinary(definition.name());
            assertEquals(definition, fresh);
            assertNotSame(definition, fresh);
            assertTrue(registrar.replaceCrafting("content", List.of(fresh)).value().isPresent());
            assertNotSame(original, ForgeRegistries.RECIPES.getValue(definition.name()));
        } finally {
            assertTrue(registrar.clear().value().isPresent());
        }
    }

    @Test
    void failedReplacementRetainsTheAcceptedFactoryResult() {
        var registrar = new RecipeDefinitionRegistrar();
        var calls = new AtomicInteger();
        var definition = custom(name(), calls);
        try {
            assertTrue(registrar.replaceCrafting("content", List.of(definition)).value().isPresent());
            var original = ForgeRegistries.RECIPES.getValue(definition.name());
            var invalid = new CustomCraftingDefinition(SOURCE, name(), () -> null);
            assertTrue(registrar.replaceCrafting("content", List.of(definition, invalid)).value().isEmpty());
            assertTrue(registrar.replaceCrafting("content", List.of(definition)).value().isPresent());
            assertSame(original, ForgeRegistries.RECIPES.getValue(definition.name()));
            assertEquals(1, calls.get());
        } finally {
            assertTrue(registrar.clear().value().isPresent());
        }
    }

    @Test
    void repeatedDisableRetainsSentinelButReenableAndClearRegenerate() {
        var registrar = new RecipeDefinitionRegistrar();
        var calls = new AtomicInteger();
        var definition = custom(name(), calls);
        var disabled = new DisabledCraftingDefinition(SOURCE, definition.name());
        try {
            assertTrue(registrar.replaceCrafting("content", List.of(definition)).value().isPresent());
            assertTrue(registrar.replaceCrafting("content", List.of(disabled)).value().isPresent());
            var sentinel = ForgeRegistries.RECIPES.getValue(definition.name());
            assertTrue(registrar.replaceCrafting("content", List.of()).value().isPresent());
            assertSame(sentinel, ForgeRegistries.RECIPES.getValue(definition.name()));
            assertTrue(registrar.replaceCrafting("content", List.of(disabled)).value().isPresent());
            assertSame(sentinel, ForgeRegistries.RECIPES.getValue(definition.name()));
            assertEquals(1, calls.get());
            assertTrue(registrar.replaceCrafting("content", List.of(definition)).value().isPresent());
            assertEquals(2, calls.get());
            assertTrue(registrar.clear().value().isPresent());
            assertTrue(registrar.replaceCrafting("content", List.of(definition)).value().isPresent());
            assertEquals(3, calls.get());
        } finally {
            assertTrue(registrar.clear().value().isPresent());
        }
    }

    @Test
    void retainedRecipeStillRejectsExternalReplacement() {
        var registrar = new RecipeDefinitionRegistrar();
        var definition = ordinary(name());
        try {
            assertTrue(registrar.replaceCrafting("content", List.of(definition)).value().isPresent());
            var outside = CraftingRegistryTransaction.prepare(Map.of(definition.name(), compile(definition.name())));
            outside.publish();
            try {
                assertTrue(registrar.replaceCrafting("content", List.of(definition)).value().isEmpty());
            } finally {
                outside.restore();
            }
            assertTrue(registrar.replaceCrafting("content", List.of(definition)).value().isPresent());
        } finally {
            assertTrue(registrar.clear().value().isPresent());
        }
    }

    @Test
    void noWritePreparationStillRejectsAnInterveningOwnedRegistryChange() {
        var ownership = new RecipeCraftingOwnership();
        var name = name();
        var recipe = compile(name);
        var initial = ownership.prepare("content", Map.of(name, recipe), SOURCE);
        initial.publish();
        initial.commitOwnership();
        try {
            var retained = ownership.prepare("content", Map.of(name, recipe), SOURCE);
            var outside = CraftingRegistryTransaction.prepare(Map.of(name, compile(name)));
            outside.publish();
            try {
                assertFalse(retained.isCurrent());
                assertThrows(IllegalStateException.class, retained::publish);
            } finally {
                outside.restore();
            }
        } finally {
            var clear = ownership.prepareClear();
            clear.publish();
            clear.commitOwnership();
        }
    }

    private static CustomCraftingDefinition custom(ResourceLocation name, AtomicInteger calls) {
        return new CustomCraftingDefinition(SOURCE, name, () -> {
            calls.incrementAndGet();
            return compile(name);
        });
    }

    private static ResourceLocation name() {
        return new ResourceLocation("crafting_retention", "recipe_" + IDS.incrementAndGet());
    }

    private static ShapelessCraftingDefinition ordinary(ResourceLocation name) {
        return new ShapelessCraftingDefinition(SOURCE, name, "", ItemValue.of(new ItemStack(Items.DIAMOND)),
                List.of(ItemIngredient.exact(new ItemStack(Items.PAPER))));
    }

    private static IRecipe compile(ResourceLocation name) {
        return CraftingDefinitionCompiler.compile(ordinary(name)).value().orElseThrow();
    }
}
