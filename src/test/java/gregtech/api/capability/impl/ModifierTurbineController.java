package gregtech.api.capability.impl;

import gregtech.api.GTValues;
import gregtech.api.capability.IRotorHolder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.modifiers.IRecipeModifierProvider;
import gregtech.api.recipes.modifiers.RecipeModifierList;
import gregtech.api.util.GTUtility;
import gregtech.common.metatileentities.multi.electric.generator.MetaTileEntityLargeTurbine;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.ItemStackHandler;

final class ModifierTurbineController extends MetaTileEntityLargeTurbine implements IRecipeModifierProvider {

    final ConditionRuntimeWorld environment = new ConditionRuntimeWorld();
    final FluidTank fuel = new FluidTank(10000);
    final ItemStackHandler products = new ItemStackHandler(1);
    RecipeModifierList modifiers = RecipeModifierList.EMPTY;
    int providerReads;
    int efficiency = 100;
    private final PendingRotorTestPart rotor;

    ModifierTurbineController(RecipeMap<?> map) {
        super(GTUtility.gregtechId("modifier_turbine"), map, GTValues.LV, null, null, null, false, null);
        this.energyContainer = new EnergyContainerHandler(this, 10000, 0, 0, 32, 1);
        this.inputFluidInventory = new FluidTankList(true, fuel);
        this.extendedFluidInputs = this.inputFluidInventory;
        this.outputInventory = products;
        this.outputFluidInventory = new FluidTankList(true, new FluidTank(10000));
        IdleRecipeTestMachine.attachHolder(this);
        this.rotor = new PendingRotorTestPart(this) {

            @Override
            public int getTotalPower() {
                return 100;
            }

            @Override
            public int getTotalEfficiency() {
                return efficiency;
            }

            @Override
            public int getRotorSpeed() {
                return getMaxRotorHolderSpeed();
            }
        };
    }

    @Override
    public IRotorHolder getRotorHolder() {
        return rotor;
    }

    @Override
    public World getWorld() {
        return environment;
    }

    @Override
    public BlockPos getPos() {
        return new BlockPos(20, 64, 30);
    }

    @Override
    public boolean isStructureFormed() {
        return true;
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return false;
    }

    @Override
    public boolean hasMufflerMechanics() {
        return false;
    }

    @Override
    public boolean canVoidRecipeItemOutputs() {
        return false;
    }

    @Override
    public boolean isStructureObstructed() {
        return false;
    }

    @Override
    public void markDirty() {}

    @Override
    public RecipeModifierList getRecipeModifiers() {
        providerReads++;
        return modifiers;
    }
}
