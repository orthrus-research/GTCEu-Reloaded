package gregtech.integration.jei.multiblock;

import gregtech.Bootstrap;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PreviewPartsAndLifecycleTest {

    @BeforeAll
    static void setup() {
        Bootstrap.perform();
    }

    @Test
    void countUsesItemMetadataAndNbtWithoutChangingInputs() {
        ItemStack first = stack(1, "one");
        ItemStack same = first.copy();
        same.setCount(3);
        ItemStack nbt = stack(1, "two");
        ItemStack metadata = stack(2, "one");
        List<ItemStack> result = PreviewParts.count(Arrays.asList(first, same, nbt, metadata, ItemStack.EMPTY));
        assertEquals(3, result.size());
        assertEquals(4, result.get(0).getCount());
        assertEquals(1, first.getCount());
        assertEquals(3, same.getCount());
        result.get(0).getTagCompound().setString("value", "changed");
        assertEquals("one", first.getTagCompound().getString("value"));
    }

    @Test
    void paginationExposesEveryPartAndReturnsDefensiveStacks() {
        List<ItemStack> parts = new ArrayList<>();
        for (int i = 0; i < 41; i++) parts.add(stack(i, "part"));
        List<ItemStack> gathered = new ArrayList<>();
        for (int page = 0; page < 3; page++) gathered.addAll(PreviewParts.page(parts, page, 18));
        assertEquals(41, gathered.size());
        assertEquals(40, gathered.get(40).getItemDamage());
        gathered.get(0).setCount(9);
        assertEquals(1, parts.get(0).getCount());
        assertTrue(PreviewParts.page(parts, 3, 18).isEmpty());
    }

    @Test
    void lifecycleExpiresHiddenRecipesAndClosesEachSessionOnce() {
        Map<String, Long> sessions = new HashMap<>();
        sessions.put("hidden", 0L);
        sessions.put("visible", 20L);
        List<String> closed = new ArrayList<>();
        PreviewLifecycle.expire(sessions, 1, closed::add);
        assertEquals(Arrays.asList("hidden"), closed);
        assertEquals(1, sessions.size());
        PreviewLifecycle.expire(sessions, Long.MAX_VALUE, closed::add);
        PreviewLifecycle.expire(sessions, Long.MAX_VALUE, closed::add);
        assertEquals(Arrays.asList("hidden", "visible"), closed);
        assertTrue(sessions.isEmpty());
    }

    private static ItemStack stack(int metadata, String value) {
        ItemStack stack = new ItemStack(Items.DYE, 1, metadata);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("value", value);
        stack.setTagCompound(tag);
        return stack;
    }
}
