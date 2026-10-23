package gregtech.api.capability.impl;

import gregtech.Bootstrap;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class PendingGeneratorOutputTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void actualCombustionLogicDoesNotChargeLubricantBoosterOrEnergyDuringPendingReload() throws Exception {
        PendingCombustionTestController controller = new PendingCombustionTestController();
        MultiblockRecipeLogic logic = controller.getRecipeMapWorkable();
        Field boost = logic.getClass().getDeclaredField("isOxygenBoosted");
        boost.setAccessible(true);
        boost.setBoolean(logic, true);
        logic.progressTime = 1;
        logic.maxProgressTime = 1;
        logic.recipeEUt = 1;
        logic.itemOutputs = Collections.singletonList(new ItemStack(Items.GOLD_INGOT));
        logic.setActive(true);
        controller.getOutputInventory().setStackInSlot(0, new ItemStack(Items.STICK, 64));
        logic.updateWorkable();
        assertTrue(logic.isAwaitingOutputs());
        assertFalse(controller.isActive());
        assertEquals(99, controller.lubricant.getFluidAmount());
        assertEquals(80, controller.oxygen.getFluidAmount());
        assertEquals(1, controller.getEnergyContainer().getEnergyStored());
        NBTTagCompound saved = logic.serializeNBT();
        logic.deserializeNBT(saved);
        controller.lubricant.setFluid(null);
        for (int tick = 20; tick <= 80; tick++) {
            controller.tick = tick;
            logic.updateWorkable();
        }
        assertTrue(logic.isAwaitingOutputs(), "The normal lubricant recheck would invalidate this completed recipe");
        assertFalse(controller.isActive());
        assertEquals(80, controller.oxygen.getFluidAmount());
        assertEquals(1, controller.getEnergyContainer().getEnergyStored());
        assertEquals(saved.getTag("ItemOutputs"), logic.serializeNBT().getTag("ItemOutputs"));
        controller.getOutputInventory().setStackInSlot(0, ItemStack.EMPTY);
        logic.tryCompleteRecipe();
        assertFalse(logic.isAwaitingOutputs());
        assertEquals(Items.GOLD_INGOT, controller.getOutputInventory().getStackInSlot(0).getItem());
        assertEquals(1, controller.getOutputInventory().getStackInSlot(0).getCount());
        assertEquals(1, controller.getEnergyContainer().getEnergyStored());
    }
}
