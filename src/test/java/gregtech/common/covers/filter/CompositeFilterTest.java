package gregtech.common.covers.filter;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.stack.ItemAndMetadata;
import gregtech.common.items.MetaItems;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary.OreRegisterEvent;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static gregtech.common.covers.filter.CompositeFilterTestSupport.*;
import static gregtech.common.covers.filter.IFilter.FilterType.*;
import static org.junit.jupiter.api.Assertions.*;

class CompositeFilterTest {

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
    void emptyOrAndBothBlacklistLevelsHaveConsistentMatchResults() {
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        ItemStack gold = new ItemStack(Items.GOLD_INGOT);
        ItemStack coal = new ItemStack(Items.COAL);
        CompositeFilter empty = editable(ITEM);
        assertFalse(empty.test(iron));
        empty.setBlacklistFilter(true);
        assertTrue(empty.test(iron));
        assertTrue(empty.match(iron).isMatched());

        CompositeFilter union = editable(ITEM, itemLeaf(iron), itemLeaf(gold));
        for (boolean blacklist : new boolean[] { false, true }) {
            union.setBlacklistFilter(blacklist);
            assertEquals(!blacklist, union.test(iron));
            assertEquals(!blacklist, union.test(gold));
            assertEquals(blacklist, union.test(coal));
            for (ItemStack input : new ItemStack[] { iron, gold, coal }) {
                assertEquals(union.test(input), union.match(input).isMatched());
            }
        }
        ItemStack invertedLeaf = itemLeaf(iron);
        new SimpleItemFilter(invertedLeaf).setBlacklistFilter(true);
        CompositeFilter mixed = editable(ITEM, invertedLeaf, itemLeaf(gold));
        assertFalse(mixed.test(iron));
        assertTrue(mixed.test(gold));
        assertTrue(mixed.test(coal));
        mixed.setBlacklistFilter(true);
        assertTrue(mixed.test(iron));
        assertFalse(mixed.test(gold));
        assertFalse(mixed.test(coal));
        assertFalse(mixed.test(new FluidStack(FluidRegistry.WATER, 1)));
    }

    @Test
    void physicalChildrenKeepAmountsWhileTheCompositeUsesOnlyTheGlobalAmount() {
        ItemStack child = itemLeaf(new ItemStack(Items.IRON_INGOT, 42));
        NBTTagCompound before = child.writeToNBT(new NBTTagCompound());
        CompositeFilter composite = editable(ITEM, child);
        composite.setMaxTransferSize(1);
        assertTrue(composite.showGlobalTransferLimitSlider());
        assertTrue(composite.test(new ItemStack(Items.IRON_INGOT)));
        assertEquals(7, composite.getTransferLimit(new ItemStack(Items.IRON_INGOT), 7));
        assertEquals(11, composite.getTransferLimit(-1, 11));
        assertEquals(-1, composite.match(new ItemStack(Items.IRON_INGOT)).getFilterIndex());
        assertEquals(before, child.writeToNBT(new NBTTagCompound()));
        assertEquals(before, composite.getInventory().getStackInSlot(0).writeToNBT(new NBTTagCompound()));

        CompositeFilter fluid = editable(FLUID, fluidLeaf(new FluidStack(FluidRegistry.WATER, 321)));
        assertTrue(fluid.test(new FluidStack(FluidRegistry.WATER, 8000)));
        assertFalse(fluid.test(new FluidStack(FluidRegistry.LAVA, 1)));
        fluid.setBlacklistFilter(true);
        assertFalse(fluid.match(new FluidStack(FluidRegistry.WATER, 1)).isMatched());
        assertTrue(fluid.match(new FluidStack(FluidRegistry.LAVA, 1)).isMatched());
        assertEquals(17, fluid.getTransferLimit((FluidStack) null, 17));
        assertEquals(321, new SimpleFluidFilter(fluid.getInventory().getStackInSlot(0))
                .getFilterReader().getFluidStack(0).amount);
    }

    @Test
    void existingOreGlobAndSmartFiltersParticipateAsBooleanLeaves() throws ReflectiveOperationException {
        OreDictUnifier.registerOre(new ItemStack(Items.QUARTZ), "a4CompositeQuartz");
        // The test bootstrap does not install the production ore-registration event listener.
        OreDictUnifier.onItemRegistration(new OreRegisterEvent("a4CompositeQuartz", new ItemStack(Items.QUARTZ)));
        ItemStack ore = MetaItems.ORE_DICTIONARY_FILTER.getStackForm();
        var oreReader = new OreDictionaryItemFilter(ore).getFilterReader();
        oreReader.setMatchAll(false);
        oreReader.setExpression("a4CompositeQuartz");
        Field cacheField = SmartItemFilter.SmartFilteringMode.class.getDeclaredField("transferStackSizesCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<ItemAndMetadata, Integer> cache = (Map<ItemAndMetadata, Integer>) cacheField
                .get(SmartItemFilter.SmartFilteringMode.ELECTROLYZER);
        Map<ItemAndMetadata, Integer> before = new HashMap<>(cache);
        var built = RecipeMaps.ELECTROLYZER_RECIPES.recipeBuilder()
                .inputs(new ItemStack(Items.PRISMARINE_SHARD, 12)).outputs(new ItemStack(Items.CLAY_BALL))
                .duration(20).EUt(8).build();
        Recipe recipe = built.getResult();
        RecipeMaps.ELECTROLYZER_RECIPES.addRecipe(built);
        try {
            CompositeFilter filter = editable(ITEM, itemLeaf(new ItemStack(Items.IRON_INGOT)), ore,
                    MetaItems.SMART_FILTER.getStackForm());
            assertTrue(filter.test(new ItemStack(Items.IRON_INGOT)));
            assertTrue(filter.test(new ItemStack(Items.QUARTZ)));
            assertTrue(filter.test(new ItemStack(Items.PRISMARINE_SHARD)));
            assertFalse(filter.test(new ItemStack(Items.STICK)));
            assertEquals(3, filter.getTransferLimit(new ItemStack(Items.PRISMARINE_SHARD), 3));
        } finally {
            RecipeMaps.ELECTROLYZER_RECIPES.removeRecipe(recipe);
            cache.clear();
            cache.putAll(before);
        }
    }

    @Test
    void invalidRetainedChildrenFailClosedUnderEitherBlacklistAndCanBeRemoved() {
        ItemStack nested = editable(ITEM, itemLeaf(new ItemStack(Items.IRON_INGOT))).getContainerStack();
        ItemStack brokenSmart = MetaItems.SMART_FILTER.getStackForm();
        NBTTagCompound invalidMode = new NBTTagCompound();
        invalidMode.setInteger("FilterMode", Integer.MAX_VALUE);
        brokenSmart.setTagCompound(invalidMode);
        for (ItemStack invalid : new ItemStack[] { nested, MetaItems.FLUID_FILTER.getStackForm(),
                new ItemStack(Items.DIAMOND), MetaItems.ITEM_FILTER.getStackForm(2), brokenSmart }) {
            ItemStack root = rawParent(ITEM, invalid, itemLeaf(new ItemStack(Items.IRON_INGOT)));
            CompositeFilter composite = new CompositeFilter(root, ITEM);
            composite.getInventory().setOwner(() -> true);
            assertFalse(composite.isValid());
            for (boolean blacklist : new boolean[] { false, true }) {
                composite.setBlacklistFilter(blacklist);
                assertFalse(composite.test(new ItemStack(Items.IRON_INGOT)));
                assertFalse(composite.test(new ItemStack(Items.COAL)));
                assertFalse(composite.match(new ItemStack(Items.COAL)).isMatched());
            }
            ItemStack removed = composite.getInventory().extractItem(0, Integer.MAX_VALUE, false);
            assertEquals(invalid.writeToNBT(new NBTTagCompound()), removed.writeToNBT(new NBTTagCompound()));
            assertTrue(composite.isValid());
        }
    }

    @Test
    void fixedNineSlotsRejectNestingWithoutConstructingRecursiveFilters() {
        CompositeFilter filter = editable(ITEM);
        ItemStack children = MetaItems.ITEM_FILTER.getStackForm(12);
        for (int i = 0; i < 9; i++) {
            children = filter.getInventory().insertItem(i, children, false);
            assertEquals(1, filter.getInventory().getStackInSlot(i).getCount());
        }
        assertEquals(3, children.getCount());
        assertEquals(9, filter.getInventory().getSlots());
        NBTTagCompound before = filter.getContainerStack().writeToNBT(new NBTTagCompound());
        assertEquals(3, filter.getInventory().insertItem(8, children, false).getCount());
        assertEquals(before, filter.getContainerStack().writeToNBT(new NBTTagCompound()));
        NBTTagCompound untrusted = new NBTTagCompound();
        untrusted.setInteger("Size", 1000000);
        assertThrows(UnsupportedOperationException.class, () -> filter.getInventory().deserializeNBT(untrusted));
        assertThrows(UnsupportedOperationException.class, () -> filter.getInventory().setSize(1000000));
        assertEquals(9, filter.getInventory().getSlots());

        ItemStack nested = MetaItems.COMPOSITE_ITEM_FILTER.getStackForm();
        for (int i = 0; i < 100; i++) nested = rawParent(ITEM, nested);
        CompositeFilter bounded = new CompositeFilter(nested, ITEM);
        bounded.setBlacklistFilter(true);
        assertFalse(bounded.isValid());
        assertFalse(bounded.test(new ItemStack(Items.IRON_INGOT)));
    }

    @Test
    void partiallyMovedInvalidContentsPersistTheirRemainderInsteadOfDuplicatingTheMovedPart() {
        for (ItemStack invalid : new ItemStack[] { new ItemStack(Items.DIAMOND, 9),
                MetaItems.ITEM_FILTER.getStackForm(9), MetaItems.COMPOSITE_ITEM_FILTER.getStackForm(9) }) {
            CompositeFilter filter = new CompositeFilter(rawParent(ITEM, invalid), ITEM);
            filter.getInventory().setOwner(() -> true);
            ItemStack remainder = filter.getInventory().getStackInSlot(0).copy();
            remainder.shrink(3);
            // MUI's normal shift transfer puts its actual remainder back through IItemHandlerModifiable.
            filter.getInventory().setStackInSlot(0, remainder);
            CompositeFilter loaded = new CompositeFilter(filter.getContainerStack().copy(), ITEM);
            assertEquals(6, loaded.getInventory().getStackInSlot(0).getCount());
            ItemStack oversized = remainder.copy();
            oversized.setCount(10);
            filter.getInventory().setStackInSlot(0, oversized);
            assertEquals(6, filter.getInventory().getStackInSlot(0).getCount());
            assertFalse(filter.isValid());
        }
    }

    @Test
    void productionRegistrationHasReservedIdsTypesAndSingleStackLimits() {
        assertEquals(294, MetaItems.COMPOSITE_ITEM_FILTER.getMetaValue());
        assertEquals(295, MetaItems.COMPOSITE_FLUID_FILTER.getMetaValue());
        assertEquals(ITEM, BaseFilter.getFilterFromStack(MetaItems.COMPOSITE_ITEM_FILTER.getStackForm()).getType());
        assertEquals(FLUID, BaseFilter.getFilterFromStack(MetaItems.COMPOSITE_FLUID_FILTER.getStackForm()).getType());
        assertEquals(1, MetaItems.COMPOSITE_ITEM_FILTER.getStackForm().getMaxStackSize());
        assertEquals(1, MetaItems.COMPOSITE_FLUID_FILTER.getStackForm().getMaxStackSize());
    }

    @Test
    void malformedStructureRemainsUntouchedAndIgnoresParentBlacklist() {
        for (int count : new int[] { 0, 1, 10, 100 }) {
            ItemStack root = MetaItems.COMPOSITE_ITEM_FILTER.getStackForm();
            NBTTagList list = new NBTTagList();
            for (int i = 0; i < count; i++) list.appendTag(new NBTTagCompound());
            NBTTagCompound tag = new NBTTagCompound();
            tag.setTag(CompositeFilterInventory.ITEMS_KEY, list);
            tag.setBoolean("IsBlacklist", true);
            root.setTagCompound(tag);
            NBTTagCompound before = tag.copy();
            CompositeFilter filter = new CompositeFilter(root, ITEM);
            filter.getInventory().setOwner(() -> true);
            assertFalse(filter.isValid());
            assertFalse(filter.test(new ItemStack(Items.IRON_INGOT)));
            assertFalse(
                    filter.getInventory().insertItem(0, itemLeaf(new ItemStack(Items.IRON_INGOT)), false).isEmpty());
            assertTrue(filter.getInventory().extractItem(0, 1, false).isEmpty());
            assertEquals(before, root.getTagCompound());
        }
    }

    @Test
    void simulatedRejectedAndRealTransfersPreservePhysicalChildrenAcrossReload() {
        CompositeFilter filter = editable(ITEM);
        ItemStack parent = filter.getContainerStack();
        ItemStack child = itemLeaf(new ItemStack(Items.IRON_INGOT, 42));
        assertTrue(filter.getInventory().insertItem(0, child, true).isEmpty());
        assertTrue(filter.getInventory().getStackInSlot(0).isEmpty());
        assertFalse(parent.hasTagCompound());
        assertTrue(filter.getInventory().insertItem(0, child, false).isEmpty());
        NBTTagCompound before = parent.writeToNBT(new NBTTagCompound());
        assertEquals(1, filter.getInventory().extractItem(0, 1, true).getCount());
        assertFalse(filter.getInventory().insertItem(0, itemLeaf(new ItemStack(Items.GOLD_INGOT)), false).isEmpty());
        assertFalse(filter.getInventory().insertItem(1, parent, false).isEmpty());
        assertEquals(before, parent.writeToNBT(new NBTTagCompound()));
        CompositeFilter reloaded = new CompositeFilter(new ItemStack(before), ITEM);
        assertTrue(reloaded.test(new ItemStack(Items.IRON_INGOT)));
        reloaded.getInventory().setOwner(() -> true);
        assertEquals(child.writeToNBT(new NBTTagCompound()),
                reloaded.getInventory().extractItem(0, 1, false).writeToNBT(new NBTTagCompound()));
        assertFalse(reloaded.test(new ItemStack(Items.IRON_INGOT)));
        assertTrue(new CompositeFilter(reloaded.getContainerStack().copy(), ITEM).getInventory().getStackInSlot(0)
                .isEmpty());
    }

    @Test
    void standardItemAndFluidContainersKeepTheirRealFilterDuringSimulationOrRejectedReplacement() {
        for (boolean fluid : new boolean[] { false, true }) {
            AtomicInteger dirty = new AtomicInteger();
            BaseFilterContainer container = fluid ? new FluidFilterContainer(dirty::incrementAndGet) :
                    new ItemFilterContainer(dirty::incrementAndGet);
            ItemStack first = container instanceof ItemFilterContainer ? itemLeaf(new ItemStack(Items.IRON_INGOT)) :
                    fluidLeaf(new FluidStack(FluidRegistry.WATER, 12));
            ItemStack second = container instanceof ItemFilterContainer ? itemLeaf(new ItemStack(Items.GOLD_INGOT)) :
                    fluidLeaf(new FluidStack(FluidRegistry.LAVA, 23));
            assertTrue(container.insertItem(0, first, true).isEmpty());
            assertEquals(0, dirty.get());
            assertFalse(container.hasFilter());
            container.insertItem(0, first, false);
            assertEquals(1, dirty.get());
            BaseFilter active = container.getFilter();
            NBTTagCompound before = container.serializeNBT().copy();
            assertFalse(container.extractItem(0, 1, true).isEmpty());
            assertSame(active, container.getFilter());
            assertFalse(container.insertItem(0, second, false).isEmpty());
            assertSame(active, container.getFilter());
            assertEquals(before, container.serializeNBT());
            assertEquals(1, dirty.get());
            assertFalse(container.extractItem(0, 1, false).isEmpty());
            assertFalse(container.hasFilter());
            assertEquals(2, dirty.get());
        }
    }
}
