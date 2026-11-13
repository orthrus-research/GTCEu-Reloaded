package gregtech.common.metatileentities.storage;

import gregtech.Bootstrap;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CraftingRecipeLogicConsumeTest {

    @BeforeAll
    public static void prepare() {
        Bootstrap.perform();
    }

    @SuppressWarnings("unchecked")
    private static boolean consume(ItemStackHandler inventory, ItemStack required, int amount) throws Exception {
        CraftingRecipeLogic logic = new CraftingRecipeLogic(null, inventory, new ItemStackHandler(9));
        logic.refreshStackMap();
        Field field = CraftingRecipeLogic.class.getDeclaredField("requiredItems");
        field.setAccessible(true);
        ((Object2IntMap<ItemStack>) field.get(logic)).put(required, amount);
        return logic.consumeRecipeItems();
    }

    private static int count(ItemStackHandler inventory, Item item) {
        int total = 0;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.getItem() == item) total += stack.getCount();
        }
        return total;
    }

    @Test
    public void oneContainerItemIsUsedWhenOneIsNeeded() throws Exception {
        ItemStackHandler inventory = new ItemStackHandler(3);
        for (int i = 0; i < 3; i++) inventory.setStackInSlot(i, new ItemStack(Items.WATER_BUCKET));

        assertTrue(consume(inventory, new ItemStack(Items.WATER_BUCKET), 1));
        assertEquals(2, count(inventory, Items.WATER_BUCKET));
        assertEquals(1, count(inventory, Items.BUCKET));
    }

    @Test
    public void twoContainerItemsFromOneStackAreBothUsed() throws Exception {
        // dragon's breath stacks and leaves a glass bottle behind
        ItemStackHandler inventory = new ItemStackHandler(4);
        inventory.setStackInSlot(0, new ItemStack(Items.DRAGON_BREATH, 3));

        assertTrue(consume(inventory, new ItemStack(Items.DRAGON_BREATH), 2));
        assertEquals(1, count(inventory, Items.DRAGON_BREATH));
        assertEquals(2, count(inventory, Items.GLASS_BOTTLE));
    }

    @Test
    public void plainItemsAreConsumedOnce() throws Exception {
        ItemStackHandler inventory = new ItemStackHandler(3);
        for (int i = 0; i < 3; i++) inventory.setStackInSlot(i, new ItemStack(Items.IRON_INGOT));

        assertTrue(consume(inventory, new ItemStack(Items.IRON_INGOT), 1));
        assertEquals(2, count(inventory, Items.IRON_INGOT));
    }
}
