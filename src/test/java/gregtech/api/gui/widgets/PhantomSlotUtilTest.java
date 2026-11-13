package gregtech.api.gui.widgets;

import gregtech.Bootstrap;

import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhantomSlotUtilTest {

    @BeforeAll
    public static void prepare() {
        Bootstrap.perform();
    }

    private static Slot slotWith(ItemStack stack) {
        Slot slot = new Slot(new InventoryBasic("phantom", false, 1), 0, 0, 0);
        slot.putStack(stack);
        return slot;
    }

    @Test
    public void equalStacksCompareEqual() {
        assertTrue(PhantomSlotUtil.areItemsEqual(new ItemStack(Blocks.STONE, 4), new ItemStack(Blocks.STONE, 9)));
        assertFalse(PhantomSlotUtil.areItemsEqual(new ItemStack(Blocks.STONE), new ItemStack(Blocks.DIRT)));
    }

    @Test
    public void clickWithSameItemAdjustsTheCount() {
        Slot slot = slotWith(new ItemStack(Blocks.STONE, 4));
        PhantomSlotUtil.slotClickPhantom(slot, 0, ClickType.PICKUP, new ItemStack(Blocks.STONE, 10));
        assertEquals(3, slot.getStack().getCount());

        PhantomSlotUtil.slotClickPhantom(slot, 1, ClickType.PICKUP, new ItemStack(Blocks.STONE, 10));
        assertEquals(4, slot.getStack().getCount());
    }

    @Test
    public void clickWithOtherItemReplacesTheStack() {
        Slot slot = slotWith(new ItemStack(Blocks.STONE, 4));
        PhantomSlotUtil.slotClickPhantom(slot, 0, ClickType.PICKUP, new ItemStack(Blocks.DIRT, 10));
        assertTrue(slot.getStack().isItemEqual(new ItemStack(Blocks.DIRT)));
        assertEquals(10, slot.getStack().getCount());
    }
}
