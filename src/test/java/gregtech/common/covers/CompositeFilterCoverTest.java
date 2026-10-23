package gregtech.common.covers;

import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.util.GTUtility;
import gregtech.common.covers.filter.BaseFilter;
import gregtech.common.covers.filter.CompositeFilter;
import gregtech.common.covers.filter.CompositeFilterTestSupport;
import gregtech.common.items.MetaItems;
import gregtech.common.metatileentities.electric.MetaTileEntityHull;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static gregtech.common.covers.filter.CompositeFilterTestSupport.*;
import static gregtech.common.covers.filter.IFilter.FilterType.*;
import static org.junit.jupiter.api.Assertions.*;

class CompositeFilterCoverTest {

    private static CompositeFilterTestSupport support;

    @BeforeAll
    static void setup() throws ReflectiveOperationException {
        support = new CompositeFilterTestSupport();
    }

    @AfterAll
    static void restore() throws ReflectiveOperationException {
        support.close();
    }

    private static MetaTileEntityHull holder() {
        return new MetaTileEntityHull(GTUtility.gregtechId("composite_filter_test"), 1);
    }

    @Test
    void roboticArmTransfersGlobalBatchPerTypeAndKeepsOneUnionTarget() {
        CoverRoboticArm arm = new CoverRoboticArm(null, holder(), EnumFacing.UP, 1, 64);
        CompositeFilter filter = editable(ITEM, itemLeaf(new ItemStack(Items.IRON_INGOT, 2)),
                itemLeaf(new ItemStack(Items.GOLD_INGOT, 9)));
        arm.itemFilterContainer.setFilterStack(filter.getContainerStack());
        arm.itemFilterContainer.setMaxTransferSize(64);
        arm.itemFilterContainer.setTransferSize(5);
        ItemStackHandler source = new ItemStackHandler(3);
        source.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 20));
        source.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 20));
        source.setStackInSlot(2, new ItemStack(Items.COAL, 20));
        ItemStackHandler target = new ItemStackHandler(3);
        assertEquals(10, arm.doTransferExact(target, source, 64));
        assertEquals(15, source.getStackInSlot(0).getCount());
        assertEquals(15, source.getStackInSlot(1).getCount());
        assertEquals(20, source.getStackInSlot(2).getCount());
        target.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        target.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 1));
        assertEquals(2, arm.doKeepExact(target, source, 64));
        assertEquals(5, target.getStackInSlot(0).getCount() + target.getStackInSlot(1).getCount());
        assertEquals(0, arm.doKeepExact(target, source, 64));
        assertEquals(53, total(source) + total(target));
    }

    @Test
    void regulatorUsesGlobalAmountsForEachFluidIncludingAfterAnEmptyTank() {
        CoverFluidRegulator regulator = new CoverFluidRegulator(null, holder(), EnumFacing.UP, 1, 1000);
        CompositeFilter filter = editable(FLUID, fluidLeaf(new FluidStack(FluidRegistry.WATER, 3)),
                fluidLeaf(new FluidStack(FluidRegistry.LAVA, 9)));
        regulator.fluidFilterContainer.setFilterStack(filter.getContainerStack());
        regulator.fluidFilterContainer.setMaxTransferSize(1000);
        regulator.fluidFilterContainer.setTransferSize(100);
        FluidTank water = new FluidTank(new FluidStack(FluidRegistry.WATER, 500), 1000);
        FluidTank lava = new FluidTank(new FluidStack(FluidRegistry.LAVA, 500), 1000);
        FluidTankList source = new FluidTankList(false, new FluidTank(1000), water, lava);
        FluidTankList target = new FluidTankList(false, new FluidTank(1000), new FluidTank(1000));
        assertEquals(200, regulator.doTransferExact(1000, source, target, regulator.fluidFilterContainer::test, 100));
        assertEquals(400, water.getFluidAmount());
        assertEquals(400, lava.getFluidAmount());
        assertEquals(100, regulator.doKeepExact(1000, source, target, regulator.fluidFilterContainer::test, 150));
        assertEquals(350, water.getFluidAmount());
        assertEquals(350, lava.getFluidAmount());
        assertEquals(150, target.getTankAt(0).getFluidAmount());
        assertEquals(150, target.getTankAt(1).getFluidAmount());
    }

    @Test
    void advancedItemVoidingRetainsGlobalAmountPerExactTypeOnly() {
        CoverItemVoidingAdvanced cover = new CoverItemVoidingAdvanced(null, holder(), EnumFacing.UP);
        cover.itemFilterContainer.setFilterStack(editable(ITEM, itemLeaf(new ItemStack(Items.IRON_INGOT)),
                itemLeaf(new ItemStack(Items.GOLD_INGOT))).getContainerStack());
        cover.itemFilterContainer.setMaxTransferSize(1000);
        cover.itemFilterContainer.setTransferSize(5);
        ItemStackHandler items = new ItemStackHandler(4);
        items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
        items.setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 9));
        items.setStackInSlot(2, new ItemStack(Items.GOLD_INGOT, 4));
        items.setStackInSlot(3, new ItemStack(Items.COAL, 10));
        cover.voidOverflow(items);
        assertEquals(5, items.getStackInSlot(0).getCount() + items.getStackInSlot(1).getCount());
        assertEquals(4, items.getStackInSlot(2).getCount());
        assertEquals(10, items.getStackInSlot(3).getCount());
        NBTTagCompound before = items.serializeNBT();
        cover.itemFilterContainer.setFilterStack(rawParent(ITEM, MetaItems.COMPOSITE_ITEM_FILTER.getStackForm()));
        cover.itemFilterContainer.setBlacklistFilter(true);
        cover.voidOverflow(items);
        assertEquals(before, items.serializeNBT());
    }

    @Test
    void advancedFluidVoidingKeepsGlobalAmountAcrossTanksAndExactNbtTypes() {
        CoverFluidVoidingAdvanced cover = new CoverFluidVoidingAdvanced(null, holder(), EnumFacing.UP);
        CompositeFilter filter = editable(FLUID, fluidLeaf(new FluidStack(FluidRegistry.WATER, 1)));
        cover.fluidFilterContainer.setFilterStack(filter.getContainerStack());
        cover.fluidFilterContainer.setMaxTransferSize(Integer.MAX_VALUE);
        cover.fluidFilterContainer.setTransferSize(100);
        FluidTank first = new FluidTank(new FluidStack(FluidRegistry.WATER, 80), 1000);
        FluidTank second = new FluidTank(new FluidStack(FluidRegistry.WATER, 90), 1000);
        FluidStack taggedWater = new FluidStack(FluidRegistry.WATER, 400);
        taggedWater.tag = new NBTTagCompound();
        taggedWater.tag.setString("Grade", "retained");
        FluidTank third = new FluidTank(taggedWater, 1000);
        FluidTank fourth = new FluidTank(new FluidStack(FluidRegistry.LAVA, 200), 1000);
        FluidTankList tanks = new FluidTankList(true, first, second, third, fourth);
        cover.voidOverflow(tanks, cover.fluidFilterContainer::test, 0);
        assertEquals(100, first.getFluidAmount() + second.getFluidAmount());
        assertEquals(400, third.getFluidAmount());
        assertEquals(200, fourth.getFluidAmount());
    }

    @Test
    void corruptBlacklistedLeavesNeverVoidItemsOrFluidsUnderEitherParentMode() {
        for (boolean malformedList : new boolean[] { false, true }) {
            ItemStack itemChild = itemLeaf(new ItemStack(Items.IRON_INGOT));
            ItemStack fluidChild = fluidLeaf(new FluidStack(FluidRegistry.WATER, 1));
            for (ItemStack child : new ItemStack[] { itemChild, fluidChild }) {
                child.getTagCompound().setBoolean("IsBlacklist", true);
                if (malformedList) child.getTagCompound().setTag("FilterSlots", new NBTTagList());
                else child.getTagCompound().setString("FilterSlots", "invalid");
            }
            CoverItemVoidingAdvanced items = new CoverItemVoidingAdvanced(null, holder(), EnumFacing.UP);
            CoverFluidVoidingAdvanced fluids = new CoverFluidVoidingAdvanced(null, holder(), EnumFacing.UP);
            items.setVoidingMode(VoidingMode.VOID_OVERFLOW);
            fluids.setVoidingMode(VoidingMode.VOID_OVERFLOW);
            items.itemFilterContainer.setFilterStack(rawParent(ITEM, itemChild));
            fluids.fluidFilterContainer.setFilterStack(rawParent(FLUID, fluidChild));
            items.itemFilterContainer.setTransferSize(1);
            fluids.fluidFilterContainer.setTransferSize(1);
            ItemStackHandler inventory = new ItemStackHandler(1);
            inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND, 64));
            FluidTank tank = new FluidTank(new FluidStack(FluidRegistry.LAVA, 1000), 1000);
            for (boolean blacklist : new boolean[] { false, true }) {
                items.itemFilterContainer.setBlacklistFilter(blacklist);
                fluids.fluidFilterContainer.setBlacklistFilter(blacklist);
                items.voidOverflow(inventory);
                fluids.voidOverflow(tank, fluids.fluidFilterContainer::test, 1);
                assertEquals(64, inventory.getStackInSlot(0).getCount());
                assertEquals(1000, tank.getFluidAmount());
                assertEquals(itemChild.writeToNBT(new NBTTagCompound()),
                        ((CompositeFilter) items.itemFilterContainer.getFilter()).getInventory().getStackInSlot(0)
                                .writeToNBT(new NBTTagCompound()));
                assertEquals(fluidChild.writeToNBT(new NBTTagCompound()),
                        ((CompositeFilter) fluids.fluidFilterContainer.getFilter()).getInventory().getStackInSlot(0)
                                .writeToNBT(new NBTTagCompound()));
            }
        }
    }

    @Test
    void partialFluidDrainMakesAtMostOneRequestPerTypeOnEachVoidingTick() {
        AtomicInteger requests = new AtomicInteger();
        FluidStack contents = new FluidStack(FluidRegistry.WATER, Integer.MAX_VALUE);
        contents.tag = new NBTTagCompound();
        contents.tag.setString("Grade", "bounded");
        FluidTank tank = new FluidTank(contents.copy(), Integer.MAX_VALUE) {

            @Override
            public FluidStack drain(FluidStack request, boolean doDrain) {
                assertTrue(requests.incrementAndGet() <= 1, "A partial handler must not be polled in a drain loop");
                assertTrue(request.isFluidEqual(contents));
                assertTrue(request.amount > 0);
                FluidStack limited = request.copy();
                limited.amount = 1;
                return super.drain(limited, doDrain);
            }
        };
        CoverFluidVoidingAdvanced cover = new CoverFluidVoidingAdvanced(null, holder(), EnumFacing.UP);
        cover.setVoidingMode(VoidingMode.VOID_OVERFLOW);
        cover.fluidFilterContainer.setFilterStack(editable(FLUID, fluidLeaf(contents)).getContainerStack());
        cover.fluidFilterContainer.setTransferSize(100);
        for (int tick = 1; tick <= 2; tick++) {
            requests.set(0);
            cover.voidOverflow(tank, cover.fluidFilterContainer::test, 100);
            assertEquals(1, requests.get());
            assertEquals(Integer.MAX_VALUE - tick, tank.getFluidAmount());
            assertEquals(contents.tag, tank.getFluid().tag);
        }
        FluidStack retained = contents.copy();
        retained.amount = 100;
        tank.setFluid(retained);
        requests.set(0);
        cover.voidOverflow(tank, cover.fluidFilterContainer::test, 100);
        assertEquals(0, requests.get());
        assertEquals(100, tank.getFluidAmount());
    }

    @Test
    void filterCoverSaveAndSingleDropRetainEveryPhysicalChild() {
        ItemStack root = editable(ITEM, itemLeaf(new ItemStack(Items.IRON_INGOT, 42)),
                itemLeaf(new ItemStack(Items.GOLD_INGOT))).getContainerStack();
        CoverDefinition definition = new CoverDefinition(GTUtility.gregtechId("a4_composite_item"),
                (def, view, side) -> new CoverItemFilter(def, view, side, "test", null),
                MetaItems.COMPOSITE_ITEM_FILTER.getStackForm());
        var holder = holder();
        CoverItemFilter cover = (CoverItemFilter) definition.createCover(holder, EnumFacing.UP);
        cover.onAttachment(holder, EnumFacing.UP, null, root);
        NBTTagCompound saved = new NBTTagCompound();
        cover.writeToNBT(saved);
        CoverItemFilter loaded = (CoverItemFilter) definition.createCover(holder, EnumFacing.UP);
        loaded.readFromNBT(saved);
        assertEquals(1, loaded.getDrops().size());
        assertEquals(root.writeToNBT(new NBTTagCompound()), loaded.getDrops().get(0).writeToNBT(new NBTTagCompound()));
        assertTrue(((CompositeFilter) BaseFilter.getFilterFromStack(loaded.getDrops().get(0)))
                .test(new ItemStack(Items.IRON_INGOT)));
    }

    private static int total(ItemStackHandler handler) {
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) total += handler.getStackInSlot(i).getCount();
        return total;
    }
}
