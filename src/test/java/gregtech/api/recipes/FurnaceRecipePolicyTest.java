package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.definition.SourceOrigin;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FurnaceRecipePolicyTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void exemptionIsOwnedByNativeValuesAndDoesNotLeakToChangedOutputs() {
        ItemStack input = new ItemStack(Items.IRON_INGOT, 2);
        ItemStack output = new ItemStack(Items.GOLD_INGOT, 3);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("policy", 42);
        output.setTagCompound(tag);
        ItemStack original = output.copy();
        try {
            FurnaceRecipePolicy.protect(input, output,
                    new SourceOrigin("groovy", "metals.groovy", 3, 1, "smelting", "postInit"));
            input.setCount(64);
            output.setCount(4);
            output.getTagCompound().setInteger("policy", 43);
            assertTrue(FurnaceRecipePolicy.isProtected(input, original));
            assertFalse(FurnaceRecipePolicy.isProtected(input, output));
            assertFalse(FurnaceRecipePolicy.isProtected(new ItemStack(Items.DIAMOND), original));
        } finally {
            FurnaceRecipePolicy.unprotect(input, original);
        }
        assertFalse(FurnaceRecipePolicy.isProtected(input, original));
    }
}
