package gregtech.api.recipes.ingredients;

import gregtech.Bootstrap;
import gregtech.api.recipes.definition.NbtPolicy;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GTRecipeOreInputMembershipTest {

    private static final AtomicInteger IDS = new AtomicInteger();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void additionsAreVisibleAfterARecipeHasAlreadyReadItsAlternatives() {
        String name = name();
        OreDictionary.registerOre(name, new ItemStack(Items.COAL));
        var input = new GTRecipeOreInput(name, 4);
        assertEquals(1, input.getInputStacks().length);
        OreDictionary.registerOre(name, new ItemStack(Items.DIAMOND));
        assertTrue(input.acceptsStack(new ItemStack(Items.DIAMOND)));
        assertEquals(2, input.getInputStacks().length);
    }

    @Test
    void removalAndSameSizeReplacementAreImmediatelyVisible() {
        String name = name();
        OreDictionary.registerOre(name, new ItemStack(Items.COAL));
        var input = new GTRecipeOreInput(name);
        assertTrue(input.acceptsStack(new ItemStack(Items.COAL)));
        var entries = OreDictionary.getOres(name);
        entries.set(0, new ItemStack(Items.EMERALD));
        assertFalse(input.acceptsStack(new ItemStack(Items.COAL)));
        assertTrue(input.acceptsStack(new ItemStack(Items.EMERALD)));
        entries.clear();
        assertFalse(input.acceptsStack(new ItemStack(Items.EMERALD)));
        assertEquals(0, input.getInputStacks().length);
    }

    @Test
    void returnedAlternativeSnapshotsCannotMutateFutureMatchingOrDictionaryEntries() {
        String name = name();
        OreDictionary.registerOre(name, new ItemStack(Items.COAL));
        var input = new GTRecipeOreInput(name, 4);
        var snapshot = input.getInputStacks();
        snapshot[0].setCount(63);
        snapshot[0] = new ItemStack(Items.DIAMOND);
        assertTrue(input.acceptsStack(new ItemStack(Items.COAL)));
        assertFalse(input.acceptsStack(new ItemStack(Items.DIAMOND)));
        assertEquals(4, input.getInputStacks()[0].getCount());
        assertEquals(1, OreDictionary.getOres(name).getFirst().getCount());
    }

    @Test
    void emptyIngredientsAndQuantityChangesDoNotNeedAGlobalRefresh() {
        String name = name();
        var input = new GTRecipeOreInput(name);
        assertEquals(0, input.getInputStacks().length);
        OreDictionary.registerOre(name, new ItemStack(Items.PAPER));
        assertTrue(input.acceptsStack(new ItemStack(Items.PAPER)));
        assertEquals(1, input.getInputStacks()[0].getCount());
        var changed = input.withAmount(7);
        assertEquals(7, changed.getInputStacks()[0].getCount());
        assertEquals(1, OreDictionary.getOres(name).getFirst().getCount());
    }

    @Test
    void copiedInputsKeepNbtAndConsumptionPolicyWhileFollowingCurrentMembership() {
        String name = name();
        OreDictionary.registerOre(name, new ItemStack(Items.COAL));
        var required = new NBTTagCompound();
        required.setString("grade", "pure");
        var input = new GTRecipeOreInput(name, 2).setNonConsumable()
                .setNBTMatchingCondition(NbtPolicy.contains(required).compile(), null);
        var copy = input.copyWithAmount(3);
        assertEquals(1, copy.getInputStacks().length);
        OreDictionary.registerOre(name, new ItemStack(Items.DIAMOND));
        var candidate = new ItemStack(Items.DIAMOND);
        assertFalse(copy.acceptsStack(candidate));
        candidate.setTagCompound(required.copy());
        assertTrue(copy.acceptsStack(candidate));
        assertTrue(copy.isNonConsumable());
        assertTrue(copy.hasNBTMatchingCondition());
        assertEquals(3, copy.getInputStacks()[1].getCount());
        assertFalse(copy.acceptsStack(ItemStack.EMPTY));
        assertFalse(copy.acceptsStack(null));
    }

    private static String name() {
        return "oreInputMembershipTest" + IDS.incrementAndGet();
    }
}
