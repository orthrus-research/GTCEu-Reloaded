package gregtech.api.capability.impl;

import gregtech.Bootstrap;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeOutputCodecTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void itemsAreSavedAsOneItemWithTheirFullCount() {
        NBTTagList list = RecipeOutputCodec.writeItems(Arrays.asList(new ItemStack(Items.APPLE, 100), ItemStack.EMPTY,
                new ItemStack(Items.STICK, 3)));
        assertEquals(2, list.tagCount(), "Empty outputs are not saved");
        NBTTagCompound apple = list.getCompoundTagAt(0);
        assertEquals("minecraft:apple", apple.getString("id"));
        assertEquals(1, apple.getByte("Count"));
        assertEquals(100, apple.getInteger("RecipeOutputCount"));

        List<ItemStack> read = RecipeOutputCodec.readItems(list);
        assertEquals(2, read.size());
        assertSame(Items.APPLE, read.get(0).getItem());
        assertEquals(100, read.get(0).getCount(), "Counts above the stack limit survive");
        assertEquals(3, read.get(1).getCount());
        assertDoesNotThrow(() -> read.add(ItemStack.EMPTY), "The logic gets a modifiable list");
    }

    @Test
    void anOlderSaveWithoutTheCountKeepsTheItemsOwnCount() {
        NBTTagList list = new NBTTagList();
        list.appendTag(new ItemStack(Items.APPLE, 5).writeToNBT(new NBTTagCompound()));
        List<ItemStack> read = RecipeOutputCodec.readItems(list);
        assertEquals(1, read.size());
        assertEquals(5, read.get(0).getCount());
    }

    @Test
    void itemsWithACountThatIsNotAPositiveIntegerAreSkipped() {
        NBTTagList list = new NBTTagList();
        for (int count : new int[] { 0, -4 }) {
            NBTTagCompound tag = new ItemStack(Items.APPLE).writeToNBT(new NBTTagCompound());
            tag.setInteger("RecipeOutputCount", count);
            list.appendTag(tag);
        }
        NBTTagCompound wrongType = new ItemStack(Items.APPLE).writeToNBT(new NBTTagCompound());
        wrongType.setString("RecipeOutputCount", "7");
        list.appendTag(wrongType);
        assertTrue(RecipeOutputCodec.readItems(list).isEmpty());
    }

    @Test
    void fluidsRoundTripAndUnregisteredOnesAreDropped() {
        NBTTagList list = RecipeOutputCodec.writeFluids(Collections.singletonList(
                new FluidStack(FluidRegistry.WATER, 2500)));
        NBTTagCompound missing = new NBTTagCompound();
        missing.setString("FluidName", "recipe_output_codec_missing");
        missing.setInteger("Amount", 10);
        list.appendTag(missing);

        List<FluidStack> read = RecipeOutputCodec.readFluids(list);
        assertEquals(1, read.size());
        assertSame(FluidRegistry.WATER, read.get(0).getFluid());
        assertEquals(2500, read.get(0).amount);
        assertDoesNotThrow(() -> read.add(new FluidStack(FluidRegistry.LAVA, 1)), "The logic gets a modifiable list");
    }
}
