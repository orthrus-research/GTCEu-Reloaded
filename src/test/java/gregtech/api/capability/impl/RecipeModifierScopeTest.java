package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.modifiers.RecipeModification;
import gregtech.api.recipes.modifiers.RecipeModifier;
import gregtech.api.recipes.modifiers.RecipeModifierAttempt;
import gregtech.api.recipes.modifiers.RecipeModifierContext;
import gregtech.api.recipes.modifiers.RecipeModifierList;
import gregtech.api.recipes.modifiers.RecipeModifierResult;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipeModifierScopeTest {

    private static final RecipeModifierContext CONTEXT = new RecipeModifierContext(
            new ResourceLocation("test:scope"), 0, BlockPos.ORIGIN, 32, 1);

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static RecipeModifierAttempt doubling() {
        return new RecipeModifierAttempt(RecipeModifierList.of(RecipeModifier.named("test:double",
                (context, view) -> RecipeModification.builder().duration(2, 0).build())), CONTEXT, -1, -1);
    }

    private static Recipe recipe() {
        RecipeMap<SimpleRecipeBuilder> map = RecipeFailureTest.map();
        return RecipeFailureTest.add(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)));
    }

    @Test
    void aClosedScopeHasNoAttemptOrInventories() {
        RecipeModifierScope scope = new RecipeModifierScope();
        assertFalse(scope.isOpen());
        assertNull(scope.getInputs());
        assertNull(scope.getFluids());
        assertFalse(scope.isOpenFor(new ItemStackHandler(), new FluidTankList(false)));
    }

    @Test
    void anAttemptIsOpenForItsInventoriesOnlyWhileItsOperationRuns() {
        RecipeModifierScope scope = new RecipeModifierScope();
        ItemStackHandler items = new ItemStackHandler();
        FluidTankList fluids = new FluidTankList(false);
        Recipe source = recipe();
        RecipeModifierAttempt attempt = doubling();
        boolean result = scope.run(attempt, items, fluids, () -> {
            assertTrue(scope.isOpen());
            assertSame(items, scope.getInputs());
            assertSame(fluids, scope.getFluids());
            assertTrue(scope.isOpenFor(items, fluids));
            assertFalse(scope.isOpenFor(new ItemStackHandler(), fluids), "Inventories are compared by identity");
            assertFalse(scope.isOpenFor(items, new FluidTankList(false)));
            assertFalse(scope.isEvaluating());
            RecipeModifierResult adjusted = scope.adjust(source);
            assertTrue(adjusted.isSuccess());
            assertEquals(source.getDuration() * 2, adjusted.recipe().getDuration());
            assertSame(adjusted, attempt.adjust(source), "The scope adjusts through the open attempt");
            return false;
        });
        assertFalse(result, "The operation's answer is returned");
        assertFalse(scope.isOpen());
        assertNull(scope.getInputs());
        assertNull(scope.getFluids());
    }

    @Test
    void anOperationThatThrowsStillClosesTheAttempt() {
        RecipeModifierScope scope = new RecipeModifierScope();
        assertThrows(IllegalStateException.class, () -> scope.run(doubling(), new ItemStackHandler(),
                new FluidTankList(false), () -> {
                    throw new IllegalStateException();
                }));
        assertFalse(scope.isOpen());
        assertNull(scope.getInputs());
    }

    @Test
    void appendMetadataIsKeptApartFromTheAttempt() {
        RecipeModifierScope scope = new RecipeModifierScope();
        Recipe metadata = recipe();
        assertNull(scope.getAppendMetadata());
        scope.setAppendMetadata(metadata);
        assertTrue(scope.run(doubling(), new ItemStackHandler(), new FluidTankList(false), () -> true));
        assertSame(metadata, scope.getAppendMetadata());
        scope.setAppendMetadata(null);
        assertNull(scope.getAppendMetadata());
    }
}
