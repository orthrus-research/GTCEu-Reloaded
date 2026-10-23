package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.IObjectHolder;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityDistillationTower;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityResearchStation;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityObjectHolder;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEOutputBus;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEOutputHatch;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class PendingSpecialOutputTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void steamVentsOnceAfterOutputAdmissionAndNeverConsumesSteamWhilePending() {
        PendingOutputTestMachine machine = PendingRecipeOutputTest.machine();
        FluidTank steam = new FluidTank(Materials.Steam.getFluid(100), 1000);
        int[] vents = { 0 };
        RecipeLogicSteam logic = new RecipeLogicSteam(machine, RecipeFailureTest.map(), false, steam, 1) {

            @Override
            public void tryDoVenting() {
                vents[0]++;
                setNeedsVenting(false);
            }

            @Override
            protected boolean shouldSearchForRecipes() {
                return false;
            }
        };
        logic.progressTime = 1;
        logic.maxProgressTime = 1;
        logic.recipeEUt = 1;
        logic.itemOutputs = Collections.singletonList(new ItemStack(Items.GOLD_INGOT));
        machine.acceptItems = false;
        machine.tick = 1;
        logic.update();
        assertTrue(logic.isAwaitingOutputs());
        assertEquals(99, steam.getFluidAmount());
        assertEquals(0, vents[0]);
        for (int tick = 20; tick <= 60; tick++) {
            machine.tick = tick;
            logic.update();
        }
        assertEquals(0, vents[0]);
        assertEquals(99, steam.getFluidAmount());
        machine.acceptItems = true;
        machine.tick = 61;
        logic.update();
        assertEquals(1, vents[0]);
        assertFalse(logic.isAwaitingOutputs());
        assertEquals(1, machine.getExportItems().getStackInSlot(0).getCount());
        assertEquals(99, steam.getFluidAmount());
    }

    @Test
    void distillationUsesItsActualLayerRoutingBeforeCompleting() {
        FluidTank firstLayer = new FluidTank(new FluidStack(FluidRegistry.LAVA, 1000), 1000);
        FluidTank secondLayer = new FluidTank(1000);
        MetaTileEntityDistillationTower tower = new MetaTileEntityDistillationTower(
                GTUtility.gregtechId("pending_layers"), true) {

            {
                handler = new DistillationTowerLogicHandler(this) {

                    {
                        setOrderedFluidOutputs(Arrays.asList(firstLayer, secondLayer));
                        setFluidTanks(new FluidTankList(true, firstLayer, secondLayer));
                    }
                };
            }
        };
        MultiblockRecipeLogic logic = tower.getRecipeMapWorkable();
        logic.progressTime = 1;
        logic.maxProgressTime = 1;
        logic.fluidOutputs = Collections.singletonList(new FluidStack(FluidRegistry.WATER, 100));
        assertTrue(GTTransferUtils.addFluidsToFluidHandler(new FluidTankList(true, firstLayer, secondLayer), true,
                logic.fluidOutputs), "Aggregate capacity alone would incorrectly admit the wrong layer");
        logic.tryCompleteRecipe();
        assertTrue(logic.isAwaitingOutputs());
        assertEquals(0, secondLayer.getFluidAmount());
        firstLayer.drain(1000, true);
        logic.tryCompleteRecipe();
        assertFalse(logic.isAwaitingOutputs());
        assertEquals(100, firstLayer.getFluidAmount());
        assertEquals(FluidRegistry.WATER, firstLayer.getFluid().getFluid());
        assertEquals(0, secondLayer.getFluidAmount());
    }

    @Test
    void researchCompletionUsesItsLockedHolderInsteadOfOrdinaryOutputSlots() {
        MetaTileEntityObjectHolder holder = new MetaTileEntityObjectHolder(GTUtility.gregtechId("pending_holder"));
        IdleRecipeTestMachine.attachHolder(holder);
        holder.setHeldItem(new ItemStack(Items.IRON_INGOT));
        holder.setDataItem(new ItemStack(Items.PAPER));
        holder.setLocked(true);
        MetaTileEntityResearchStation station = new MetaTileEntityResearchStation(
                GTUtility.gregtechId("pending_research")) {

            @Override
            public IObjectHolder getObjectHolder() {
                return holder;
            }
        };
        IdleRecipeTestMachine.attachHolder(station);
        ComputationRecipeLogic logic = station.getRecipeMapWorkable();
        ItemStack result = new ItemStack(Items.GOLD_INGOT);
        logic.itemOutputs = Collections.singletonList(result);
        logic.progressTime = 1;
        logic.maxProgressTime = 1;
        assertFalse(GTTransferUtils.addItemsToItemHandler(station.getOutputInventory(), true, logic.itemOutputs));
        assertTrue(logic.canFitRecipeOutputs());
        logic.tryCompleteRecipe();
        assertFalse(logic.isAwaitingOutputs());
        assertEquals(0, logic.getProgress());
        assertTrue(holder.getHeldItem(false).isEmpty());
        assertTrue(ItemStack.areItemStacksEqual(result, holder.getDataItem(false)));
        assertFalse(holder.writeToNBT(new NBTTagCompound()).getBoolean("IsLocked"));
    }

    @Test
    void actualMeOutputAbilitiesReceiveCompletedProductsExactlyOnce() throws Exception {
        // AE2 resolves GT's tool interface through LaunchWrapper, which the standalone harness does not install.
        LaunchClassLoader previous = Launch.classLoader;
        try (LaunchClassLoader bridge = new LaunchClassLoader(new URL[0]) {

            @Override
            public Class<?> findClass(String name) throws ClassNotFoundException {
                return PendingSpecialOutputTest.class.getClassLoader().loadClass(name);
            }
        }) {
            Launch.classLoader = bridge;
            assertMeOutputDelivery();
        } finally {
            Launch.classLoader = previous;
        }
    }

    private void assertMeOutputDelivery() {
        MetaTileEntityMEOutputBus bus = new MetaTileEntityMEOutputBus(GTUtility.gregtechId("pending_me_items"));
        MetaTileEntityMEOutputHatch hatch = new MetaTileEntityMEOutputHatch(GTUtility.gregtechId("pending_me_fluids"));
        AbilityInstances itemAbilities = new AbilityInstances(MultiblockAbility.EXPORT_ITEMS);
        AbilityInstances fluidAbilities = new AbilityInstances(MultiblockAbility.EXPORT_FLUIDS);
        bus.registerAbilities(itemAbilities);
        hatch.registerAbilities(fluidAbilities);
        IItemHandlerModifiable items = (IItemHandlerModifiable) itemAbilities.get(0);
        FluidTankList fluids = new FluidTankList(true, (IFluidTank) fluidAbilities.get(0));
        PendingOutputTestMachine machine = PendingRecipeOutputTest.machine();
        PendingOutputTestLogic logic = new PendingOutputTestLogic(machine, RecipeFailureTest.map()) {

            @Override
            protected IItemHandlerModifiable getOutputInventory() {
                return items;
            }

            @Override
            protected IMultipleTankHandler getOutputTank() {
                return fluids;
            }
        };
        logic.progressTime = 1;
        logic.maxProgressTime = 1;
        logic.itemOutputs = Collections.singletonList(new ItemStack(Items.GOLD_INGOT, 3));
        logic.fluidOutputs = Collections.singletonList(new FluidStack(FluidRegistry.WATER, 456));
        assertTrue(logic.canFitRecipeOutputs());
        assertEquals(0, bus.writeToNBT(new NBTTagCompound()).getTagList("ItemBuffer", 10).tagCount());
        assertEquals(0, hatch.writeToNBT(new NBTTagCompound()).getTagList("FluidBuffer", 10).tagCount());
        logic.tryCompleteRecipe();
        assertFalse(logic.isAwaitingOutputs());
        NBTTagList bufferedItems = bus.writeToNBT(new NBTTagCompound()).getTagList("ItemBuffer", 10);
        NBTTagList bufferedFluids = hatch.writeToNBT(new NBTTagCompound()).getTagList("FluidBuffer", 10);
        assertEquals(1, bufferedItems.tagCount());
        assertEquals(1, bufferedFluids.tagCount());
        assertEquals(3, appeng.util.item.AEItemStack.fromNBT(bufferedItems.getCompoundTagAt(0)).getStackSize());
        assertEquals(456, appeng.fluids.util.AEFluidStack.fromNBT(bufferedFluids.getCompoundTagAt(0)).getStackSize());
        machine.tick = 20;
        logic.update();
        assertEquals(bufferedItems, bus.writeToNBT(new NBTTagCompound()).getTag("ItemBuffer"));
        assertEquals(bufferedFluids, hatch.writeToNBT(new NBTTagCompound()).getTag("FluidBuffer"));
        assertEquals(1, logic.completions);
    }
}
