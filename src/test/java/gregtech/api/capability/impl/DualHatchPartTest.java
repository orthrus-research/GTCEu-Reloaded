package gregtech.api.capability.impl;

import gregtech.api.GTValues;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.recipes.ingredients.IntCircuitIngredient;
import gregtech.api.recipes.logic.RecipeFailureReason;
import gregtech.api.util.GTTransferUtils;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.WidgetTree;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static gregtech.common.covers.filter.CompositeFilterTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class DualHatchPartTest {

    private static DualHatchTestItems filters;

    @BeforeAll
    static void setup() throws ReflectiveOperationException {
        filters = new DualHatchTestItems();
    }

    @AfterAll
    static void restore() throws ReflectiveOperationException {
        filters.close();
    }

    @Test
    void admittedTiersHaveCheckedDimensionsAndExactlyTheTwoDirectionalAbilities() {
        for (int tier = GTValues.LuV; tier <= GTValues.OpV; tier++) {
            for (boolean output : new boolean[] { false, true }) {
                DualHatchTestPart part = new DualHatchTestPart(tier, output);
                int rows = tier - 4;
                assertEquals(rows * rows, part.getItemInventory().getSlots());
                assertEquals(rows * rows + (output ? 0 : 1), part.getRecipeHandler().getSlots());
                assertEquals(rows, part.getRecipeHandler().getTanks());
                for (var tank : part.getRecipeHandler()) {
                    assertEquals(16_000 * (1 << (tier - GTValues.LuV)), tank.getCapacity());
                }
                var itemAbility = output ? MultiblockAbility.EXPORT_ITEMS : MultiblockAbility.IMPORT_ITEMS;
                var fluidAbility = output ? MultiblockAbility.EXPORT_FLUIDS : MultiblockAbility.IMPORT_FLUIDS;
                assertEquals(Arrays.asList(itemAbility, fluidAbility), part.getAbilities());
                AbilityInstances items = new AbilityInstances(itemAbility);
                AbilityInstances fluids = new AbilityInstances(fluidAbility);
                part.registerAbilities(items);
                part.registerAbilities(fluids);
                assertEquals(1, items.size());
                assertSame(part.getRecipeHandler(), items.get(0));
                assertEquals(rows, fluids.size());
                assertTrue(part.isWorkingEnabled());
            }
        }
        for (int tier : new int[] { Integer.MIN_VALUE, GTValues.IV, GTValues.MAX, Integer.MAX_VALUE }) {
            assertThrows(IllegalArgumentException.class, () -> new DualHatchTestPart(tier, false));
        }
    }

    @Test
    void physicalAutomationAndDropsNeverContainTheGhostCircuit() {
        DualHatchTestPart part = new DualHatchTestPart(GTValues.LuV, false);
        part.setGhostCircuitConfig(7);
        part.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 9));
        assertEquals(4, part.getItemInventory().getSlots());
        assertEquals(7, IntCircuitIngredient.getCircuitConfiguration(part.getRecipeHandler().getStackInSlot(4)));
        List<ItemStack> drops = new ArrayList<>();
        part.clearMachineInventory(drops);
        assertEquals(1, drops.size());
        assertEquals(Items.IRON_INGOT, drops.get(0).getItem());
        assertEquals(9, drops.get(0).getCount());
        part.clearMachineInventory(drops);
        assertEquals(1, drops.size());
        assertTrue(IntCircuitIngredient.isIntegratedCircuit(part.getRecipeHandler().getStackInSlot(4)));
    }

    @Test
    void realPhysicalAndRecipeMutationsNotifyTheDualIdentityButSimulationDoesNot() {
        DualHatchTestPart part = new DualHatchTestPart(GTValues.LuV, false);
        RecipeFailureTestMachine receiver = new RecipeFailureTestMachine();
        part.getRecipeHandler().addNotifiableMetaTileEntity(receiver);
        part.getImportItems().insertItem(0, new ItemStack(Items.IRON_INGOT), true);
        part.getImportFluids().fill(water(100), false);
        assertTrue(receiver.getNotifiedItemInputList().isEmpty());
        assertTrue(receiver.getNotifiedFluidInputList().isEmpty());
        part.getImportItems().insertItem(0, new ItemStack(Items.IRON_INGOT), false);
        assertDualNotification(part, receiver);
        clear(receiver);
        part.getImportFluids().fill(water(100), true);
        assertDualNotification(part, receiver);
        clear(receiver);
        part.setGhostCircuitConfig(5);
        assertDualNotification(part, receiver);
        clear(receiver);
        part.getRecipeHandler().getTankAt(0).drain(1, false);
        assertTrue(receiver.getNotifiedItemInputList().isEmpty());
        part.getRecipeHandler().getTankAt(0).drain(1, true);
        assertDualNotification(part, receiver);
        part.getRecipeHandler().removeNotifiableMetaTileEntity(receiver);
        clear(receiver);
        part.getImportFluids().fill(water(10), true);
        assertTrue(receiver.getNotifiedItemInputList().isEmpty());
    }

    @Test
    void outputFiltersRejectInsertionRetainExistingContentsAndDropEachPhysicalFilterOnce() {
        DualHatchTestPart part = new DualHatchTestPart(GTValues.LuV, true);
        part.getExportItems().insertItem(0, new ItemStack(Items.IRON_INGOT, 5), false);
        part.getExportFluids().fill(water(100), true);
        part.getItemFilter().setFilterStack(itemLeaf(new ItemStack(Items.GOLD_INGOT)));
        part.getFluidFilter().setFilterStack(fluidLeaf(new FluidStack(FluidRegistry.LAVA, 1)));
        assertFalse(part.getRecipeHandler().isItemValid(0, new ItemStack(Items.IRON_INGOT)));
        assertFalse(GTTransferUtils.addItemsToItemHandler(part.getRecipeHandler(), true,
                Collections.singletonList(new ItemStack(Items.IRON_INGOT))));
        assertFalse(GTTransferUtils.addFluidsToFluidHandler(part.getExportFluids(), true,
                Collections.singletonList(water(1))));
        assertEquals(1, part.getRecipeHandler().insertItem(0, new ItemStack(Items.IRON_INGOT), false).getCount());
        assertEquals(0, part.getExportFluids().fill(water(1), true));
        assertEquals(5, part.getRecipeHandler().extractItem(0, 5, false).getCount());
        assertEquals(100, part.getExportFluids().drain(water(100), true).amount);
        List<ItemStack> drops = new ArrayList<>();
        part.clearMachineInventory(drops);
        assertEquals(2, drops.size());
        part.clearMachineInventory(drops);
        assertEquals(2, drops.size());
        assertFalse(part.getItemFilter().hasFilter());
        assertFalse(part.getFluidFilter().hasFilter());
    }

    @Test
    void worldAndInitialPacketRoundtripsRetainPhysicalStateAndIndependentFilters() {
        for (boolean output : new boolean[] { false, true }) {
            DualHatchTestPart source = new DualHatchTestPart(GTValues.UHV, output);
            source.setWorkingEnabled(false);
            source.getRecipeHandler().setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 33));
            source.getRecipeHandler().fill(water(321), true);
            if (output) {
                source.getItemFilter().setFilterStack(itemLeaf(new ItemStack(Items.IRON_INGOT)));
                source.getFluidFilter().setFilterStack(fluidLeaf(new FluidStack(FluidRegistry.LAVA, 1)));
            } else source.setGhostCircuitConfig(12);
            NBTTagCompound saved = source.writeToNBT(new NBTTagCompound());
            DualHatchTestPart loaded = new DualHatchTestPart(GTValues.UHV, output);
            loaded.readFromNBT(saved);
            assertEquals(saved, loaded.writeToNBT(new NBTTagCompound()));
            assertEquals(33, loaded.getRecipeHandler().getStackInSlot(0).getCount());
            assertEquals(321, loaded.getRecipeHandler().getTankAt(0).getFluidAmount());
            PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
            try {
                source.writeInitialSyncData(packet);
                DualHatchTestPart client = new DualHatchTestPart(GTValues.UHV, output);
                client.receiveInitialSyncData(packet);
                assertEquals(0, packet.readableBytes());
                assertFalse(client.isWorkingEnabled());
                assertEquals(321, client.getRecipeHandler().getTankAt(0).getFluidAmount());
                if (output) {
                    assertTrue(ItemStack.areItemStacksEqual(source.getItemFilter().getFilterStack(),
                            client.getItemFilter().getFilterStack()));
                    assertTrue(ItemStack.areItemStacksEqual(source.getFluidFilter().getFilterStack(),
                            client.getFluidFilter().getFilterStack()));
                } else assertEquals(12,
                        IntCircuitIngredient.getCircuitConfiguration(client.getRecipeHandler().getStackInSlot(25)));
            } finally {
                packet.release();
            }
        }
        DualHatchTestPart fresh = new DualHatchTestPart(GTValues.LuV, false);
        fresh.readFromNBT(new NBTTagCompound());
        assertTrue(fresh.isWorkingEnabled());
        assertTrue(fresh.getRecipeHandler().getStackInSlot(4).isEmpty());
    }

    @Test
    void bothChannelsShareFiveTickWorkingControl() {
        for (boolean output : new boolean[] { false, true }) {
            DualHatchTestPart part = new DualHatchTestPart(GTValues.LuV, output);
            for (part.tick = 1; part.tick <= 10; part.tick++) part.update();
            assertEquals(2, part.itemTransfers);
            assertEquals(2, part.fluidTransfers);
            part.setWorkingEnabled(false);
            part.tick = 15;
            part.update();
            assertEquals(2, part.itemTransfers);
            assertEquals(2, part.fluidTransfers);
        }
    }

    @Test
    void bothOutputFilterRowsCollectRealUiSyncHandlersTogetherAtEveryLayoutBound() throws Exception {
        for (int tier : new int[] { GTValues.LuV, GTValues.UHV, GTValues.OpV }) {
            for (boolean output : new boolean[] { false, true }) {
                DualHatchTestPart part = new DualHatchTestPart(tier, output);
                if (output) {
                    part.getItemFilter().setFilterStack(itemLeaf(new ItemStack(Items.IRON_INGOT)));
                    part.getFluidFilter().setFilterStack(fluidLeaf(water(1)));
                }
                PanelSyncManager manager = new PanelSyncManager(false);
                ModularPanel panel = part.buildUI(null, manager, null);
                assertDoesNotThrow(() -> WidgetTree.collectSyncValues(manager, panel));
                if (output) {
                    String itemKey = part.getItemFilter().getFilterStack().getTranslationKey();
                    String fluidKey = part.getFluidFilter().getFilterStack().getTranslationKey();
                    assertNull(manager.findSyncHandlerNullable(itemKey, 1));
                    assertNull(manager.findSyncHandlerNullable(fluidKey, 1));
                    PacketBuffer action = new PacketBuffer(Unpooled.buffer());
                    try {
                        manager.receiveWidgetUpdate("dual_item_filter", true, 0, action);
                        var itemPanel = manager.findSyncHandlerNullable(itemKey, 1);
                        assertNotNull(itemPanel, "Item row action must rebuild its own popup");
                        assertNull(manager.findSyncHandlerNullable(fluidKey, 1));
                        manager.receiveWidgetUpdate("dual_fluid_filter", true, 0, action);
                        assertNotNull(manager.findSyncHandlerNullable(fluidKey, 1),
                                "Fluid row action must rebuild its own popup");
                        assertSame(itemPanel, manager.findSyncHandlerNullable(itemKey, 1));
                    } finally {
                        action.release();
                    }
                }
            }
        }
    }

    @Test
    void a3CompletionWaitsOnEitherCombinedFilterAndReleasesBothProductsExactlyOnce() {
        DualHatchTestPart part = new DualHatchTestPart(GTValues.LuV, true);
        part.getItemFilter().setFilterStack(itemLeaf(new ItemStack(Items.DIAMOND)));
        part.getFluidFilter().setFilterStack(fluidLeaf(new FluidStack(FluidRegistry.LAVA, 1)));
        PendingOutputTestLogic logic = new PendingOutputTestLogic(PendingRecipeOutputTest.machine(),
                RecipeFailureTest.map()) {

            @Override
            protected IItemHandlerModifiable getOutputInventory() {
                return part.getRecipeHandler();
            }

            @Override
            protected IMultipleTankHandler getOutputTank() {
                return part.getExportFluids();
            }
        };
        logic.progressTime = logic.maxProgressTime = 1;
        logic.itemOutputs = Collections.singletonList(new ItemStack(Items.GOLD_INGOT, 3));
        logic.fluidOutputs = Collections.singletonList(water(123));
        logic.tryCompleteRecipe();
        assertTrue(logic.isAwaitingOutputs());
        assertEquals(RecipeFailureReason.ITEM_OUTPUT_FULL, logic.getRecipeFailure().getReason());
        part.getItemFilter().extractItem(0, 1, false);
        logic.tryCompleteRecipe();
        assertEquals(RecipeFailureReason.FLUID_OUTPUT_FULL, logic.getRecipeFailure().getReason());
        assertTrue(part.getExportItems().getStackInSlot(0).isEmpty());
        part.getFluidFilter().extractItem(0, 1, false);
        logic.tryCompleteRecipe();
        assertFalse(logic.isAwaitingOutputs());
        assertEquals(3, part.getExportItems().getStackInSlot(0).getCount());
        assertEquals(123, part.getExportFluids().getTankAt(0).getFluidAmount());
        assertEquals(1, logic.completions);
    }

    private static FluidStack water(int amount) {
        return new FluidStack(FluidRegistry.WATER, amount);
    }

    private static void clear(RecipeFailureTestMachine receiver) {
        receiver.getNotifiedItemInputList().clear();
        receiver.getNotifiedFluidInputList().clear();
    }

    private static void assertDualNotification(DualHatchTestPart part, RecipeFailureTestMachine receiver) {
        assertEquals(Collections.singletonList(part.getRecipeHandler()), receiver.getNotifiedItemInputList());
        assertEquals(Collections.singletonList(part.getRecipeHandler()), receiver.getNotifiedFluidInputList());
    }
}
