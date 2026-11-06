package gregtech.api.recipes.definition;

import gregtech.Bootstrap;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NbtPolicyTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void policiesHaveExplicitAbsentEmptyAndSubsetSemantics() {
        NBTTagCompound required = tag(1);
        NBTTagCompound extra = tag(1);
        extra.setInteger("extra", 2);
        assertTrue(NbtPolicy.any().matches(null));
        assertTrue(NbtPolicy.contains(new NBTTagCompound()).matches(null));
        assertTrue(NbtPolicy.exact(null).matches(null));
        assertFalse(NbtPolicy.exact(new NBTTagCompound()).matches(null));
        assertFalse(NbtPolicy.contains(required).matches(null));
        assertFalse(NbtPolicy.exact(required).matches(extra));
        assertTrue(NbtPolicy.contains(required).matches(extra));
        assertFalse(NbtPolicy.contains(required).matches(tag(2)));
    }

    @Test
    void nestedValuesAreOwnedAndDoNotBecomeRecursiveWildcards() {
        NBTTagCompound nested = tag(1);
        NBTTagCompound source = new NBTTagCompound();
        source.setTag("nested", nested);
        NbtPolicy policy = NbtPolicy.contains(source);
        NBTTagCompound original = source.copy();
        int hash = policy.hashCode();
        nested.setInteger("extra", 2);
        assertTrue(policy.matches(original));
        assertFalse(policy.matches(source));
        assertEquals(hash, policy.hashCode());
        assertEquals(policy.compile(), NbtPolicy.contains(original).compile());
    }

    @Test
    void javaAndGroovyProduceTheSameNativeMatcherRegardlessOfInventoryCount() {
        Binding binding = new Binding();
        binding.setVariable("required", tag(1));
        NbtPolicy fromGroovy = (NbtPolicy) new GroovyShell(getClass().getClassLoader(), binding).evaluate(
                "gregtech.api.recipes.definition.NbtPolicy.contains(required)");
        assertEquals(NbtPolicy.contains(tag(1)), fromGroovy);
        var ingredient = new GTRecipeItemInput(new ItemStack(Items.IRON_INGOT), 2)
                .setNBTMatchingCondition(fromGroovy.compile(), null);
        for (int count : new int[] { 1, 2, 17, 64 }) {
            ItemStack stack = new ItemStack(Items.IRON_INGOT, count);
            NBTTagCompound nbt = tag(1);
            nbt.setInteger("extra", 9);
            stack.setTagCompound(nbt);
            assertTrue(ingredient.acceptsStack(stack));
            stack.getTagCompound().setInteger("audit", 3);
            assertFalse(ingredient.acceptsStack(stack));
        }
    }

    private static NBTTagCompound tag(int value) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("audit", value);
        return tag;
    }
}
