package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class PendingComputationOutputTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void realComputationCompletionWaitsBeforeResetAndStopsCwuEnergyAndMaintenance() {
        RecipeMap<SimpleRecipeBuilder> map = RecipeFailureTest.map();
        PendingComputationTestController controller = new PendingComputationTestController(map);
        int[] energyDraws = { 0 };
        ComputationRecipeLogic logic = new ComputationRecipeLogic(controller,
                ComputationRecipeLogic.ComputationType.SPORADIC) {

            @Override
            protected boolean drawEnergy(long eut, boolean simulate) {
                if (!simulate) energyDraws[0]++;
                return true;
            }

            @Override
            protected boolean shouldSearchForRecipes() {
                return false;
            }
        };
        controller.install(logic);
        logic.progressTime = 1;
        logic.maxProgressTime = 2;
        logic.recipeEUt = 1;
        logic.itemOutputs = Collections.singletonList(new ItemStack(Items.GOLD_INGOT));
        NBTTagCompound running = logic.serializeNBT();
        running.setInteger("RecipeCWUt", 8);
        running.setBoolean("IsDurationTotalCWU", true);
        logic.deserializeNBT(running);
        controller.products.setStackInSlot(0, new ItemStack(Items.STICK, 64));
        controller.runFormed();
        assertTrue(logic.isAwaitingOutputs());
        assertEquals(2, logic.getProgress(), "Overshooting total CWU is bounded at completed progress while waiting");
        assertEquals(8, logic.getRecipeCWUt(), "Virtual completion cleanup has not run yet");
        assertEquals(1, energyDraws[0]);
        assertEquals(1, controller.actualCWU);
        assertFalse(controller.isActive());
        NBTTagCompound saved = logic.serializeNBT();
        logic.deserializeNBT(saved);
        for (int tick = 20; tick <= 80; tick++) {
            controller.tick = tick;
            controller.runFormed();
            controller.update();
        }
        assertEquals(0, controller.maintenanceCalls);
        assertEquals(1, energyDraws[0]);
        assertEquals(1, controller.actualCWU);
        assertEquals(1, controller.simulatedCWU);
        assertEquals(saved.getTag("ItemOutputs"), logic.serializeNBT().getTag("ItemOutputs"));
        controller.products.setStackInSlot(0, ItemStack.EMPTY);
        controller.runFormed();
        assertFalse(logic.isAwaitingOutputs());
        assertEquals(0, logic.getRecipeCWUt());
        assertEquals(0, logic.getCurrentDrawnCWUt());
        assertEquals(Items.GOLD_INGOT, controller.products.getStackInSlot(0).getItem());
        assertEquals(1, controller.products.getStackInSlot(0).getCount());
        assertEquals(1, energyDraws[0]);
    }
}
