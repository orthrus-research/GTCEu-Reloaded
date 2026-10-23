package gregtech.api.capability.impl;

import gregtech.Bootstrap;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PendingOutputPersistenceTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void sampledLargeCountsAndTypedTagsRoundTripWithoutMutatingTheSample() {
        for (int count : new int[] { 128, 1000, Integer.MAX_VALUE }) {
            PendingOutputTestMachine machine = PendingRecipeOutputTest.machine();
            PendingOutputTestLogic logic = new PendingOutputTestLogic(machine, RecipeFailureTest.map());
            ItemStack output = new ItemStack(Items.GOLD_INGOT, count);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setByte("Byte", (byte) 1);
            tag.setInteger("Int", 1);
            tag.setString("Name", "pending");
            output.setTagCompound(tag);
            List<ItemStack> sampled = Arrays.asList(output, ItemStack.EMPTY);
            logic.itemOutputs = sampled;
            logic.progressTime = 1;
            logic.maxProgressTime = 1;
            machine.acceptItems = false;
            logic.tryCompleteRecipe();
            assertTrue(logic.isAwaitingOutputs());
            NBTTagCompound saved = logic.serializeNBT();
            NBTTagCompound entry = saved.getTagList("ItemOutputs", Constants.NBT.TAG_COMPOUND).getCompoundTagAt(0);
            assertEquals(1, entry.getByte("Count"));
            assertTrue(entry.hasKey("RecipeOutputCount", Constants.NBT.TAG_INT));
            assertEquals(count, entry.getInteger("RecipeOutputCount"));
            assertSame(sampled, logic.itemOutputs);
            assertSame(output, logic.itemOutputs.get(0));
            assertEquals(count, output.getCount());
            assertEquals(tag, output.getTagCompound());
            assertTrue(ItemStack.EMPTY.isEmpty());

            PendingOutputTestLogic loaded = new PendingOutputTestLogic(machine, RecipeFailureTest.map());
            loaded.deserializeNBT(saved);
            assertTrue(loaded.isAwaitingOutputs());
            assertFalse(loaded.isActive());
            assertEquals(1, loaded.itemOutputs.size());
            assertEquals(count, loaded.itemOutputs.get(0).getCount());
            assertEquals(Items.GOLD_INGOT, loaded.itemOutputs.get(0).getItem());
            assertEquals(tag, loaded.itemOutputs.get(0).getTagCompound());
            assertEquals(saved.getTag("ItemOutputs"), loaded.serializeNBT().getTag("ItemOutputs"));
        }
    }

    @Test
    void legacyCountDecodingRemainsAndMalformedPresentCountsCannotCreateProducts() {
        PendingOutputTestLogic logic = new PendingOutputTestLogic(PendingRecipeOutputTest.machine(),
                RecipeFailureTest.map());
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger("Progress", 1);
        saved.setInteger("MaxProgress", 1);
        saved.setBoolean("AwaitingOutputs", true);
        NBTTagList outputs = new NBTTagList();
        NBTTagCompound legacy = new ItemStack(Items.GOLD_INGOT, 64).writeToNBT(new NBTTagCompound());
        outputs.appendTag(legacy);
        for (int count : new int[] { 0, -1 }) {
            NBTTagCompound malformed = legacy.copy();
            malformed.setInteger("RecipeOutputCount", count);
            outputs.appendTag(malformed);
        }
        NBTTagCompound wrongNumberType = legacy.copy();
        wrongNumberType.setLong("RecipeOutputCount", Long.MAX_VALUE);
        outputs.appendTag(wrongNumberType);
        NBTTagCompound wrongType = legacy.copy();
        wrongType.setString("RecipeOutputCount", "64");
        outputs.appendTag(wrongType);
        saved.setTag("ItemOutputs", outputs);
        NBTTagCompound before = saved.copy();
        logic.deserializeNBT(saved);
        assertEquals(1, logic.itemOutputs.size());
        assertEquals(64, logic.itemOutputs.get(0).getCount());
        assertEquals(Items.GOLD_INGOT, logic.itemOutputs.get(0).getItem());
        assertEquals(before, saved);
    }
}
