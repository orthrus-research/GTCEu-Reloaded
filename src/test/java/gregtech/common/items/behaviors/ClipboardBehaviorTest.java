package gregtech.common.items.behaviors;

import gregtech.BootstrapExtension;
import gregtech.common.items.MetaItems;
import gregtech.common.metatileentities.MetaTileEntityClipboard;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The clipboard's setters take what a client sent, so they keep it within the clipboard's limits. */
@ExtendWith(BootstrapExtension.class)
public class ClipboardBehaviorTest {

    private static ItemStack clipboard() {
        ItemStack stack = MetaItems.CLIPBOARD.getStackForm();
        ClipboardBehavior.initNBT(stack);
        return stack;
    }

    @Test
    public void Test_Title_And_Tasks_Are_Cut_To_Their_Lengths() {
        ItemStack stack = clipboard();
        assertTrue(ClipboardBehavior.setTitle(stack, "t".repeat(40)));
        assertTrue(ClipboardBehavior.setString(stack, 3, "s".repeat(40)));

        assertEquals(ClipboardBehavior.MAX_TITLE_LENGTH, ClipboardBehavior.getTitle(stack).length());
        assertEquals(ClipboardBehavior.MAX_TASK_LENGTH, ClipboardBehavior.getString(stack, 3).length());
    }

    @Test
    public void Test_Lines_And_States_Out_Of_Range_Are_Ignored() {
        ItemStack stack = clipboard();
        assertFalse(ClipboardBehavior.setString(stack, -1, "x"));
        assertFalse(ClipboardBehavior.setString(stack, ClipboardBehavior.TASKS, "x"));
        assertFalse(ClipboardBehavior.setString(stack, 0, null));
        assertFalse(ClipboardBehavior.setTitle(stack, null));
        assertFalse(ClipboardBehavior.setButton(stack, ClipboardBehavior.TASKS, 1));
        assertFalse(ClipboardBehavior.setButton(stack, 0, ClipboardBehavior.BUTTON_STATES));
        assertFalse(ClipboardBehavior.setButton(stack, 0, -1));

        for (int line = 0; line < ClipboardBehavior.TASKS; line++) {
            assertEquals("", ClipboardBehavior.getString(stack, line));
            assertEquals(0, ClipboardBehavior.getButtonState(stack, line));
        }
    }

    @Test
    public void Test_A_Check_Box_Keeps_The_Others() {
        ItemStack stack = clipboard();
        assertTrue(ClipboardBehavior.setButton(stack, 2, 3));
        assertTrue(ClipboardBehavior.setButton(stack, 7, 1));
        assertTrue(ClipboardBehavior.setButton(stack, 2, 2));

        assertEquals(2, ClipboardBehavior.getButtonState(stack, 2));
        assertEquals(1, ClipboardBehavior.getButtonState(stack, 7));
        assertEquals(0, ClipboardBehavior.getButtonState(stack, 6));
    }

    @Test
    public void Test_Pages_Stay_Between_The_First_And_The_Last() {
        ItemStack stack = clipboard();
        ClipboardBehavior.setPage(stack, 100);
        assertEquals(ClipboardBehavior.MAX_PAGES - 1, ClipboardBehavior.getPageNum(stack));
        ClipboardBehavior.setPage(stack, -5);
        assertEquals(0, ClipboardBehavior.getPageNum(stack));
    }

    @Test
    public void Test_Each_Page_Keeps_Its_Own_Text() {
        ItemStack stack = clipboard();
        ClipboardBehavior.setTitle(stack, "first");
        ClipboardBehavior.setPage(stack, 4);
        ClipboardBehavior.setTitle(stack, "fifth");

        assertEquals("fifth", ClipboardBehavior.getTitle(stack));
        ClipboardBehavior.setPage(stack, 0);
        assertEquals("first", ClipboardBehavior.getTitle(stack));
    }

    @Test
    public void Test_Other_Items_Are_Left_Alone() {
        ItemStack stack = new ItemStack(Items.PAPER);
        assertFalse(ClipboardBehavior.setTitle(stack, "title"));
        assertFalse(ClipboardBehavior.setButton(stack, 0, 1));
        ClipboardBehavior.setPage(stack, 3);
        assertNull(stack.getTagCompound());
    }

    @Test
    public void Test_A_Click_On_A_Check_Box_Moves_It_On() {
        ItemStack stack = clipboard();
        int y = MetaTileEntityClipboard.PAGE_TASK_Y + MetaTileEntityClipboard.PAGE_TASK_SPACING * 2 + 5;
        int x = MetaTileEntityClipboard.PAGE_BUTTON_X + 5;

        assertTrue(MetaTileEntityClipboard.clickPage(stack, x, y, false));
        assertEquals(1, ClipboardBehavior.getButtonState(stack, 2));
        for (int i = 0; i < 3; i++) MetaTileEntityClipboard.clickPage(stack, x, y, false);
        assertEquals(0, ClipboardBehavior.getButtonState(stack, 2));
    }

    @Test
    public void Test_A_Click_On_The_Arrows_Turns_Pages() {
        ItemStack stack = clipboard();
        int y = MetaTileEntityClipboard.PAGE_ARROW_Y + 8;

        assertTrue(MetaTileEntityClipboard.clickPage(stack, MetaTileEntityClipboard.PAGE_RIGHT_X + 8, y, false));
        assertEquals(1, ClipboardBehavior.getPageNum(stack));
        assertTrue(MetaTileEntityClipboard.clickPage(stack, MetaTileEntityClipboard.PAGE_RIGHT_X + 8, y, true));
        assertEquals(11, ClipboardBehavior.getPageNum(stack));
        assertTrue(MetaTileEntityClipboard.clickPage(stack, MetaTileEntityClipboard.PAGE_LEFT_X + 8, y, true));
        assertEquals(1, ClipboardBehavior.getPageNum(stack));
        ClipboardBehavior.setPage(stack, 0);
        assertFalse(MetaTileEntityClipboard.clickPage(stack, MetaTileEntityClipboard.PAGE_LEFT_X + 8, y, false));
    }

    @Test
    public void Test_A_Click_Elsewhere_Changes_Nothing() {
        ItemStack stack = clipboard();
        assertFalse(MetaTileEntityClipboard.clickPage(stack, 100, 100, false));
        assertFalse(MetaTileEntityClipboard.clickPage(stack, -1, -1, false));
        assertFalse(MetaTileEntityClipboard.clickPage(stack, MetaTileEntityClipboard.PAGE_WIDTH + 1,
                MetaTileEntityClipboard.PAGE_HEIGHT + 1, false));
    }
}
