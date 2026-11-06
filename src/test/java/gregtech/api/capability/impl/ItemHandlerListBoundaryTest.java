package gregtech.api.capability.impl;

import gregtech.Bootstrap;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ItemHandlerListBoundaryTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 2, Integer.MAX_VALUE })
    void rejectedSlotsPreserveTheOfferedStackAndBackingInventories(int slot) {
        var first = new ItemStackHandler(1);
        var second = new ItemStackHandler(1);
        first.setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
        var combined = new ItemHandlerList(List.of(first, second));
        var offered = new ItemStack(Items.COAL, 7);
        assertDoesNotThrow(() -> combined.setStackInSlot(slot, offered));
        assertSame(offered, combined.insertItem(slot, offered, false));
        assertSame(offered, combined.insertItem(slot, offered, true));
        assertTrue(combined.getStackInSlot(slot).isEmpty());
        assertTrue(combined.extractItem(slot, 1, false).isEmpty());
        assertEquals(0, combined.getSlotLimit(slot));
        assertFalse(combined.isItemValid(slot, offered));
        assertEquals(7, offered.getCount());
        assertEquals(3, first.getStackInSlot(0).getCount());
        assertTrue(second.getStackInSlot(0).isEmpty());
        combined.setStackInSlot(1, offered);
        assertSame(offered, second.getStackInSlot(0));
        assertEquals(3, first.getStackInSlot(0).getCount());
    }

    @Test
    void emptyAggregateHasNoWritableSlotZero() {
        var combined = new ItemHandlerList(List.of());
        var offered = new ItemStack(Items.COAL);
        assertDoesNotThrow(() -> combined.setStackInSlot(0, offered));
        assertSame(offered, combined.insertItem(0, offered, false));
        assertTrue(combined.getStackInSlot(0).isEmpty());
        assertTrue(combined.extractItem(0, 1, false).isEmpty());
        assertEquals(0, combined.getSlotLimit(0));
    }
}
