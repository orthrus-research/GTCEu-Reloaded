package gregtech.common.metatileentities.multi.electric.generator;

import gregtech.api.GTValues;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.IRotorHolder;
import gregtech.api.capability.impl.MultiblockFuelRecipeLogic;
import gregtech.api.metatileentity.multiblock.FuelMultiblockController;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.logic.RecipeFailureReason;
import gregtech.api.recipes.modifiers.RecipeModifierCopies;
import gregtech.api.util.GTUtility;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public class LargeTurbineWorkableHandler extends MultiblockFuelRecipeLogic {

    private final int BASE_EU_OUTPUT;

    private long excessVoltage;

    public LargeTurbineWorkableHandler(RecipeMapMultiblockController metaTileEntity, int tier) {
        super(metaTileEntity);
        this.BASE_EU_OUTPUT = (int) GTValues.V[tier] * 2;
    }

    @Override
    protected void updateRecipeProgress() {
        if (canRecipeProgress) {
            // turbines can void energy
            drawEnergy(recipeEUt, false);
            // as recipe starts with progress on 1 this has to be > only not => to compensate for it
            if (++progressTime > maxProgressTime) {
                tryCompleteRecipe();
            }
        }
    }

    public FluidStack getInputFluidStack() {
        // Previous Recipe is always null on first world load, so try to acquire a new recipe
        if (previousRecipe == null) {
            // previousRecipe is set whenever a valid recipe is found
            // if it's not set, find *any* recipe we have at least the base (non-parallel) inputs for
            Recipe recipe = super.findRecipe(Integer.MAX_VALUE, getInputInventory(), getInputTank());

            return recipe == null ? null : getInputTank().drain(
                    new FluidStack(recipe.getFluidInputs().get(0).getInputFluidStack().getFluid(), Integer.MAX_VALUE),
                    false);
        }
        FluidStack fuelStack = previousRecipe.getFluidInputs().get(0).getInputFluidStack();
        return getInputTank().drain(new FluidStack(fuelStack.getFluid(), Integer.MAX_VALUE), false);
    }

    @Override
    public long getMaxVoltage() {
        IRotorHolder rotorHolder = ((MetaTileEntityLargeTurbine) metaTileEntity).getRotorHolder();
        if (rotorHolder != null && rotorHolder.hasRotor())
            return (long) BASE_EU_OUTPUT * rotorHolder.getTotalPower() / 100;
        return 0;
    }

    @Override
    protected long boostProduction(long production) {
        IRotorHolder rotorHolder = ((MetaTileEntityLargeTurbine) metaTileEntity).getRotorHolder();
        if (rotorHolder != null && rotorHolder.hasRotor()) {
            int maxSpeed = rotorHolder.getMaxRotorHolderSpeed();
            int currentSpeed = rotorHolder.getRotorSpeed();
            if (currentSpeed >= maxSpeed)
                return production;
            return (long) (production * Math.pow(1.0 * currentSpeed / maxSpeed, 2));
        }
        return 0;
    }

    private int getParallel(@NotNull Recipe recipe, double totalHolderEfficiencyCoefficient, long turbineMaxVoltage) {
        if (hasRecipeModifierAttempt()) {
            double required = ((double) turbineMaxVoltage - this.excessVoltage) /
                    (recipe.getEUt() * totalHolderEfficiencyCoefficient);
            if (!Double.isFinite(required) || required > Integer.MAX_VALUE || totalHolderEfficiencyCoefficient <= 0) {
                recordRecipeFailure(RecipeFailureReason.MODIFIER_INVALID);
                return -1;
            }
            return Math.max(0, MathHelper.ceil(required));
        }
        return MathHelper.ceil((turbineMaxVoltage - this.excessVoltage) /
                (recipe.getEUt() * totalHolderEfficiencyCoefficient));
    }

    private boolean canDoRecipeWithParallel(Recipe recipe) {
        IRotorHolder rotorHolder = ((MetaTileEntityLargeTurbine) metaTileEntity).getRotorHolder();
        if (rotorHolder == null || !rotorHolder.hasRotor())
            return false;
        double totalHolderEfficiencyCoefficient = rotorHolder.getTotalEfficiency() / 100.0;
        long turbineMaxVoltage = getMaxVoltage();
        int parallel = getParallel(recipe, totalHolderEfficiencyCoefficient, turbineMaxVoltage);
        if (parallel < 0) return false;

        FluidStack recipeFluidStack = recipe.getFluidInputs().get(0).getInputFluidStack();

        // Intentionally not using this.getInputFluidStack because that is locked to the previous recipe
        FluidStack inputFluid = getInputTank().drain(
                new FluidStack(recipeFluidStack.getFluid(), Integer.MAX_VALUE),
                false);
        return inputFluid != null && inputFluid.amount >= (hasRecipeModifierAttempt() ?
                (long) recipeFluidStack.amount * parallel : recipeFluidStack.amount * parallel);
    }

    @Override
    protected boolean checkModifiedRecipeInputs(Recipe recipe, IItemHandlerModifiable inputs,
                                                IMultipleTankHandler fluids) {
        if (!hasRecipeModifierAttempt()) return true;
        if (recipe.getEUt() <= 0 || !recipe.getInputs().isEmpty() || recipe.getFluidInputs().size() != 1 ||
                recipe.getFluidInputs().get(0).isNonConsumable())
            return recordRecipeFailure(RecipeFailureReason.MODIFIER_UNSUPPORTED);
        return super.checkModifiedRecipeInputs(recipe, inputs, fluids) && canDoRecipeWithParallel(recipe);
    }

    @Override
    protected boolean checkPreviousRecipe() {
        return super.checkPreviousRecipe() && canDoRecipeWithParallel(this.previousRecipe);
    }

    @Override
    protected @Nullable Recipe findRecipe(long maxVoltage, IItemHandlerModifiable inputs,
                                          IMultipleTankHandler fluidInputs) {
        RecipeMap<?> map = getRecipeMap();
        if (map == null || !isRecipeMapValid(map)) {
            return null;
        }

        final List<ItemStack> items = GTUtility.itemHandlerToList(inputs).stream().filter(s -> !s.isEmpty()).collect(
                Collectors.toList());
        final List<FluidStack> fluids = GTUtility.fluidHandlerToList(fluidInputs).stream()
                .filter(f -> f != null && f.amount != 0)
                .collect(Collectors.toList());

        return map.findWithEligibility(items, fluids, recipe -> {
            if (recipe.getEUt() > maxVoltage) return false;
            return recipe.matches(false, inputs, fluidInputs) && this.canDoRecipeWithParallel(recipe);
        }, this::isRecipeDiscoveryCandidateEligible).getRecipeForCache();
    }

    @Override
    public boolean prepareRecipe(Recipe recipe) {
        return withRecipeModifiers(getInputInventory(), getInputTank(), () -> {
            long previousExcess = this.excessVoltage;
            boolean started = false;
            try {
                started = prepareTurbineRecipe(recipe);
                return started;
            } finally {
                if (hasRecipeModifierAttempt() && !started) this.excessVoltage = previousExcess;
            }
        });
    }

    private boolean prepareTurbineRecipe(Recipe recipe) {
        if (hasRecipeModifierAttempt()) {
            recipe = adjustRecipeForModifiers(recipe);
            if (recipe == null ||
                    !checkModifiedRecipeInputs(recipe, getModifierInputInventory(), getModifierInputTank()))
                return false;
        }
        IRotorHolder rotorHolder = ((MetaTileEntityLargeTurbine) metaTileEntity).getRotorHolder();
        if (rotorHolder == null || !rotorHolder.hasRotor())
            return false;

        long turbineMaxVoltage = getMaxVoltage();
        FluidStack recipeFluidStack = recipe.getFluidInputs().get(0).getInputFluidStack();
        int parallel = 0;

        if (this.excessVoltage >= turbineMaxVoltage) {
            this.excessVoltage -= turbineMaxVoltage;
        } else {
            double holderEfficiency = rotorHolder.getTotalEfficiency() / 100.0;
            // get the amount of parallel required to match the desired output voltage
            parallel = getParallel(recipe, holderEfficiency, turbineMaxVoltage);
            if (parallel < 0 || (hasRecipeModifierAttempt() &&
                    RecipeModifierCopies.safeParallelLimit(recipe, parallel, false) != parallel))
                return recordRecipeFailure(RecipeFailureReason.MODIFIER_INVALID);

            // Null check fluid here, since it can return null on first join into world or first form
            FluidStack inputFluid = hasRecipeModifierAttempt() ? getModifierInputTank().drain(
                    new FluidStack(recipeFluidStack.getFluid(), Integer.MAX_VALUE), false) : getInputFluidStack();
            if (inputFluid == null || inputFluid.amount < (hasRecipeModifierAttempt() ?
                    (long) recipeFluidStack.amount * parallel : recipeFluidStack.amount * parallel)) {
                return false;
            }

            // this is necessary to prevent over-consumption of fuel
            this.excessVoltage += (long) (parallel * recipe.getEUt() * holderEfficiency - turbineMaxVoltage);
        }

        // rebuild the recipe and adjust voltage to match the turbine
        RecipeBuilder<?> recipeBuilder = getRecipeMap().recipeBuilder();
        recipeBuilder.append(recipe, parallel, false)
                .EUt(turbineMaxVoltage);
        applyParallelBonus(recipeBuilder);
        recipe = retainRecipeModifierMetadata(recipeBuilder.build().getResult(), recipe);

        if (recipe != null) {
            recipe = setupAndConsumeRecipeInputs(recipe, getInputInventory());
            if (recipe != null) {
                setupRecipe(recipe);
                return true;
            }
        }
        return false;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        excessVoltage = 0;
    }

    public void updateTanks() {
        FuelMultiblockController controller = (FuelMultiblockController) this.metaTileEntity;
        List<IFluidHandler> tanks = controller.getNotifiedFluidInputList();
        for (IFluidTank tank : controller.getAbilities(MultiblockAbility.IMPORT_FLUIDS)) {
            tanks.add((IFluidHandler) tank);
        }
    }
}
