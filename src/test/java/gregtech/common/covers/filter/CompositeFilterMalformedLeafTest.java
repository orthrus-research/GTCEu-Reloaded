package gregtech.common.covers.filter;

import gregtech.common.items.MetaItems;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static gregtech.common.covers.filter.CompositeFilterTestSupport.*;
import static gregtech.common.covers.filter.IFilter.FilterType.*;
import static org.junit.jupiter.api.Assertions.*;

class CompositeFilterMalformedLeafTest {

    private static CompositeFilterTestSupport support;

    @BeforeAll
    static void setup() throws ReflectiveOperationException {
        support = new CompositeFilterTestSupport();
    }

    @AfterAll
    static void restore() throws ReflectiveOperationException {
        support.close();
    }

    @Test
    void malformedPresentItemAndFluidSlotListsCannotBecomeEmptyBlacklists() {
        for (IFilter.FilterType type : new IFilter.FilterType[] { ITEM, FLUID }) {
            ItemStack leaf = leaf(type);
            leaf.getTagCompound().setString("FilterSlots", "invalid");
            assertInvalidLeaf(leaf, type);
            for (int size : new int[] { 0, 1, 8, 10 }) {
                leaf = leaf(type);
                NBTTagList slots = new NBTTagList();
                for (int i = 0; i < size; i++) slots.appendTag(new NBTTagCompound());
                leaf.getTagCompound().setTag("FilterSlots", slots);
                assertInvalidLeaf(leaf, type);
            }
            leaf = leaf(type);
            NBTTagList strings = new NBTTagList();
            for (int i = 0; i < 9; i++) strings.appendTag(new NBTTagString("invalid"));
            leaf.getTagCompound().setTag("FilterSlots", strings);
            assertInvalidLeaf(leaf, type);
        }
    }

    @Test
    void malformedCriteriaAndKnownScalarsFailClosedWithoutRewritingTheChild() {
        for (String field : new String[] { "id", "Count", "Damage", "tag", "ForgeCaps" }) {
            ItemStack leaf = leaf(ITEM);
            NBTTagCompound criterion = leaf.getTagCompound().getTagList("FilterSlots", 10).getCompoundTagAt(0);
            if (field.equals("id")) criterion.setInteger(field, 1);
            else criterion.setString(field, "invalid");
            assertInvalidLeaf(leaf, ITEM);
        }
        ItemStack missingItem = leaf(ITEM);
        missingItem.getTagCompound().getTagList("FilterSlots", 10).getCompoundTagAt(0)
                .setString("id", "a4:missing_item");
        assertInvalidLeaf(missingItem, ITEM);

        for (String field : new String[] { "Fluid", "FluidName", "Amount", "Tag" }) {
            ItemStack leaf = leaf(FLUID);
            NBTTagCompound tank = leaf.getTagCompound().getTagList("FilterSlots", 10).getCompoundTagAt(0);
            if (field.equals("Fluid")) tank.setString(field, "invalid");
            else if (field.equals("FluidName")) tank.getCompoundTag("Fluid").setInteger(field, 1);
            else tank.getCompoundTag("Fluid").setString(field, "invalid");
            assertInvalidLeaf(leaf, FLUID);
        }
        ItemStack missingFluid = leaf(FLUID);
        missingFluid.getTagCompound().getTagList("FilterSlots", 10).getCompoundTagAt(0).getCompoundTag("Fluid")
                .setString("FluidName", "a4:missing_fluid");
        assertInvalidLeaf(missingFluid, FLUID);

        for (String field : new String[] { "IsBlacklist", "IgnoreNBT", "IgnoreDamage" }) {
            ItemStack leaf = leaf(ITEM);
            leaf.getTagCompound().setString(field, "invalid");
            assertInvalidLeaf(leaf, ITEM);
        }
        for (String field : new String[] { "IsBlacklist", "Capacity" }) {
            ItemStack leaf = leaf(FLUID);
            leaf.getTagCompound().setString(field, "invalid");
            assertInvalidLeaf(leaf, FLUID);
        }
        ItemStack smart = MetaItems.SMART_FILTER.getStackForm();
        NBTTagCompound smartTag = new NBTTagCompound();
        smartTag.setString("FilterMode", "0");
        smart.setTagCompound(smartTag);
        assertInvalidLeaf(smart, ITEM);
        for (int mode : new int[] { -1, SmartItemFilter.SmartFilteringMode.VALUES.length }) {
            smartTag.setInteger("FilterMode", mode);
            assertInvalidLeaf(smart, ITEM);
        }
        for (String field : new String[] { "OreDictionaryFilter", "caseSensitive", "matchAll" }) {
            ItemStack ore = MetaItems.ORE_DICTIONARY_FILTER.getStackForm();
            NBTTagCompound tag = new NBTTagCompound();
            if (field.equals("OreDictionaryFilter")) tag.setInteger(field, 7);
            else tag.setString(field, "invalid");
            ore.setTagCompound(tag);
            assertInvalidLeaf(ore, ITEM);
        }
    }

    @Test
    void missingDefaultsUnknownItemDataAndLegacyIntegerCountsRemainSupported() {
        for (ItemStack child : new ItemStack[] { MetaItems.ITEM_FILTER.getStackForm(),
                MetaItems.FLUID_FILTER.getStackForm(), MetaItems.SMART_FILTER.getStackForm(),
                MetaItems.ORE_DICTIONARY_FILTER.getStackForm() }) {
            IFilter.FilterType type = MetaItems.FLUID_FILTER.isItemEqual(child) ? FLUID : ITEM;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setBoolean("IsBlacklist", true);
            tag.setString("UnrelatedModData", "retained");
            child.setTagCompound(tag);
            NBTTagCompound before = child.writeToNBT(new NBTTagCompound());
            CompositeFilter filter = editable(type, child);
            assertTrue(filter.isValid());
            assertEquals(before, child.writeToNBT(new NBTTagCompound()));
            assertEquals(before, filter.getInventory().getStackInSlot(0).writeToNBT(new NBTTagCompound()));
        }
        ItemStack child = leaf(ITEM);
        child.getTagCompound().getTagList("FilterSlots", 10).getCompoundTagAt(0).setInteger("Count", 42);
        CompositeFilter filter = editable(ITEM, child);
        assertTrue(filter.isValid());
        assertFalse(filter.test(new ItemStack(Items.IRON_INGOT)));
        assertTrue(filter.test(new ItemStack(Items.COAL)));
    }

    private static ItemStack leaf(IFilter.FilterType type) {
        ItemStack leaf = type == ITEM ? itemLeaf(new ItemStack(Items.IRON_INGOT)) :
                fluidLeaf(new FluidStack(FluidRegistry.WATER, 100));
        leaf.getTagCompound().setBoolean("IsBlacklist", true);
        return leaf;
    }

    private static void assertInvalidLeaf(ItemStack child, IFilter.FilterType type) {
        ItemStack valid = type == ITEM ? itemLeaf(new ItemStack(Items.IRON_INGOT)) :
                fluidLeaf(new FluidStack(FluidRegistry.WATER, 100));
        CompositeFilter filter = new CompositeFilter(rawParent(type, valid, child), type);
        filter.getInventory().setOwner(() -> true);
        NBTTagCompound before = child.writeToNBT(new NBTTagCompound());
        assertFalse(filter.isValid());
        for (boolean blacklist : new boolean[] { false, true }) {
            filter.setBlacklistFilter(blacklist);
            assertFalse(filter.test(new ItemStack(Items.IRON_INGOT)));
            assertFalse(filter.test(new ItemStack(Items.COAL)));
            assertFalse(filter.test(new FluidStack(FluidRegistry.WATER, 100)));
            assertFalse(filter.test(new FluidStack(FluidRegistry.LAVA, 100)));
        }
        assertEquals(before, child.writeToNBT(new NBTTagCompound()));
        assertEquals(before, filter.getInventory().extractItem(1, 1, false).writeToNBT(new NBTTagCompound()));
        assertTrue(filter.isValid());
    }
}
