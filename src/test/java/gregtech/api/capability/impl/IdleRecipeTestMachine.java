package gregtech.api.capability.impl;

import gregtech.api.capability.IEnergyContainer;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.SimpleMachineMetaTileEntity;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.ItemStackHandler;

class IdleRecipeTestMachine extends SimpleMachineMetaTileEntity {

    boolean scheduling = true;
    long tick = 1;
    int coverUpdates;
    int weatherChecks;
    int itemPushes;
    int fluidPushes;
    Runnable coverAction;
    final ItemStackHandler adjacentItems = new ItemStackHandler(2);
    final FluidTank adjacentFluid = new FluidTank(10000);

    IdleRecipeTestMachine(RecipeMap<?> map) {
        super(GTUtility.gregtechId("idle_recipe_test"), map, null, 1, true);
        attachHolder(this);
        setFrontFacing(EnumFacing.NORTH);
    }

    static MetaTileEntityHolder attachHolder(MetaTileEntity machine) {
        return new MetaTileEntityHolder() {

            {
                setRawMetaTileEntity(machine);
                setWorld(DummyWorld.INSTANCE);
            }
        };
    }

    @Override
    protected AbstractRecipeLogic createWorkable(RecipeMap<?> map) {
        return new IdleRecipeTestLogic(this, map);
    }

    IdleRecipeTestLogic logic() {
        return (IdleRecipeTestLogic) workable;
    }

    EnergyContainerHandler energy() {
        return (EnergyContainerHandler) energyContainer;
    }

    void setBattery(ItemStack stack) {
        chargerInventory.setStackInSlot(0, stack);
    }

    void tick(long tick) {
        this.tick = tick;
        update();
    }

    @Override
    public long getOffsetTimer() {
        return tick < 0 ? super.getOffsetTimer() : tick;
    }

    @Override
    public boolean supportsIdleRecipeScheduling() {
        return scheduling;
    }

    @Override
    public void updateCovers() {
        coverUpdates++;
        if (coverAction != null) coverAction.run();
        super.updateCovers();
    }

    @Override
    public void checkWeatherOrTerrainExplosion(float power, double chance, IEnergyContainer container) {
        weatherChecks++;
    }

    @Override
    public void pushItemsIntoNearbyHandlers(EnumFacing... sides) {
        itemPushes++;
        GTTransferUtils.moveInventoryItems(getExportItems(), adjacentItems);
    }

    @Override
    public void pushFluidsIntoNearbyHandlers(EnumFacing... sides) {
        fluidPushes++;
        GTTransferUtils.transferFluids(getExportFluids(), adjacentFluid);
    }

    @Override
    public void markDirty() {}
}
