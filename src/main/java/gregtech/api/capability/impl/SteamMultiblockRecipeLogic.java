package gregtech.api.capability.impl;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.metatileentity.multiblock.RecipeMapSteamMultiblockController;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.logic.RecipeFailureReason;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SteamMultiblockRecipeLogic extends AbstractRecipeLogic {

    private IMultipleTankHandler steamFluidTank;
    private IFluidTank steamFluidTankCombined;
    private final SteamAccounting steam;

    public SteamMultiblockRecipeLogic(RecipeMapSteamMultiblockController tileEntity, RecipeMap<?> recipeMap,
                                      IMultipleTankHandler steamFluidTank, double conversionRate) {
        super(tileEntity, recipeMap);
        this.steamFluidTank = steamFluidTank;
        this.steam = new SteamAccounting(conversionRate);
        setAllowOverclocking(false);
        combineSteamTanks();
    }

    public IFluidTank getSteamFluidTankCombined() {
        combineSteamTanks();
        return steamFluidTankCombined;
    }

    @Override
    protected IItemHandlerModifiable getInputInventory() {
        RecipeMapSteamMultiblockController controller = (RecipeMapSteamMultiblockController) metaTileEntity;
        return controller.getInputInventory();
    }

    @Override
    protected IItemHandlerModifiable getOutputInventory() {
        RecipeMapSteamMultiblockController controller = (RecipeMapSteamMultiblockController) metaTileEntity;
        return controller.getOutputInventory();
    }

    protected IMultipleTankHandler getSteamFluidTank() {
        RecipeMapSteamMultiblockController controller = (RecipeMapSteamMultiblockController) metaTileEntity;
        return controller.getSteamFluidTank();
    }

    /** Steam in the hatches, read straight from them; the combined tank is only built for the UI. */
    private long getSteamAmount() {
        IMultipleTankHandler tanks = steamFluidTank;
        if (tanks == null) return 0;
        long amount = 0;
        for (IMultipleTankHandler.ITankEntry tank : tanks.getFluidTanks()) {
            amount += tank.getFluidAmount();
        }
        return amount;
    }

    private long getSteamCapacity() {
        IMultipleTankHandler tanks = steamFluidTank;
        if (tanks == null) return 0;
        long capacity = 0;
        for (IMultipleTankHandler.ITankEntry tank : tanks.getFluidTanks()) {
            capacity += tank.getCapacity();
        }
        return capacity;
    }

    private void combineSteamTanks() {
        steamFluidTank = getSteamFluidTank();
        if (steamFluidTank == null)
            steamFluidTankCombined = new FluidTank(0);
        else {
            int capacity = steamFluidTank.getTanks() * 64000;
            steamFluidTankCombined = new FluidTank(capacity);
            steamFluidTankCombined.fill(steamFluidTank.drain(capacity, false), true);
        }
    }

    @Override
    public void update() {
        // Fixes an annoying GTCE bug in AbstractRecipeLogic
        RecipeMapSteamMultiblockController controller = (RecipeMapSteamMultiblockController) metaTileEntity;
        if (isActive && !controller.isStructureFormed()) {
            progressTime = 0;
            wasActiveAndNeedsUpdate = true;
        }

        steamFluidTank = getSteamFluidTank();
        super.update();
    }

    @Override
    protected long getEnergyInputPerSecond() {
        return 0;
    }

    @Override
    protected long getEnergyStored() {
        return steam.getEnergyStored(getSteamAmount());
    }

    @Override
    protected long getEnergyCapacity() {
        return steam.getEnergyCapacity(getSteamCapacity());
    }

    @Override
    protected boolean drawEnergy(long recipeEUt, boolean simulate) {
        steamFluidTank = getSteamFluidTank();
        if (steamFluidTank == null) return false;
        return steam.drawEnergy(recipeEUt, simulate, getSteamAmount(), steamFluidTank::drain);
    }

    @Override
    public long getMaxVoltage() {
        return SteamAccounting.MAX_VOLTAGE;
    }

    @Override
    public boolean isAllowOverclocking() {
        return false;
    }

    @Override
    protected RecipeFailureReason getPowerFailureReason(long eut) {
        return RecipeFailureReason.NOT_ENOUGH_STEAM;
    }

    @Override
    protected @Nullable Recipe setupAndConsumeRecipeInputs(@NotNull Recipe recipe,
                                                           @NotNull IItemHandlerModifiable importInventory) {
        RecipeMapSteamMultiblockController controller = (RecipeMapSteamMultiblockController) metaTileEntity;
        if (controller.checkRecipe(recipe, false)) {
            recipe = super.setupAndConsumeRecipeInputs(recipe, importInventory);
            if (recipe != null) {
                controller.checkRecipe(recipe, true);
                return recipe;
            }
        }
        return null;
    }

    @Override
    protected void completeRecipe() {
        super.completeRecipe();
        ventSteam();
    }

    private void ventSteam() {
        BlockPos machinePos = metaTileEntity.getPos();
        EnumFacing ventingSide = metaTileEntity.getFrontFacing();
        if (SteamVenting.clearVent(metaTileEntity.getWorld(), machinePos.offset(ventingSide))) {
            SteamVenting.playEffects(metaTileEntity, machinePos, ventingSide);
        }
    }

    @Override
    protected boolean hasEnoughPower(long eut, int duration) {
        // generation case unchanged
        if (!steam.consumesSteam(eut, duration)) return super.hasEnoughPower(eut, duration);
        return steam.hasSteamForRecipe(eut, duration, getSteamAmount(), getSteamCapacity());
    }
}
