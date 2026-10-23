package gregtech.api.metatileentity;

import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.capability.impl.EnergyContainerHandlerTest;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

class AutoOutputTestMachine extends SimpleMachineMetaTileEntity {

    private static int nextId;
    boolean scheduling = true;
    long tick;
    int itemPushes;
    int fluidPushes;
    int coverUpdates;
    int weatherChecks;
    final List<EnumFacing> neighborQueries = new ArrayList<>();
    final EnumMap<EnumFacing, TileEntity> neighbors = new EnumMap<>(EnumFacing.class);

    AutoOutputTestMachine() {
        super(GTUtility.gregtechId("auto_output_test"), map(), null, 1, true);
        attach(this);
        setFrontFacing(EnumFacing.NORTH);
        setOutputFacingItems(EnumFacing.SOUTH);
        setOutputFacingFluids(EnumFacing.EAST);
        workable.setWorkingEnabled(false);
    }

    static void bootstrap() {
        SimpleMachineOutputPermissionTest.bootstrap();
        EnergyContainerHandlerTest.bootstrap();
    }

    static RecipeMap<SimpleRecipeBuilder> map() {
        return new RecipeMapBuilder<>("auto_output_test_" + nextId++, new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(2).fluidInputs(1).fluidOutputs(1).build();
    }

    static void attach(MetaTileEntity machine) {
        new MetaTileEntityHolder() {

            {
                setRawMetaTileEntity(machine);
                setWorld(DummyWorld.INSTANCE);
                setPos(BlockPos.ORIGIN);
            }
        };
    }

    void tick(long tick) {
        this.tick = tick;
        update();
    }

    void ticks(int first, int last) {
        for (int tick = first; tick <= last; tick++) tick(tick);
    }

    @Override
    public long getOffsetTimer() {
        return tick;
    }

    @Override
    protected boolean supportsAutoOutputScheduling() {
        return scheduling;
    }

    @Override
    public TileEntity getNeighbor(@NotNull EnumFacing side) {
        neighborQueries.add(side);
        return neighbors.get(side);
    }

    @Override
    public void pushItemsIntoNearbyHandlers(EnumFacing... sides) {
        itemPushes++;
        super.pushItemsIntoNearbyHandlers(sides);
    }

    @Override
    public void pushFluidsIntoNearbyHandlers(EnumFacing... sides) {
        fluidPushes++;
        super.pushFluidsIntoNearbyHandlers(sides);
    }

    EnergyContainerHandler energy() {
        return (EnergyContainerHandler) energyContainer;
    }

    void setBattery(ItemStack battery) {
        chargerInventory.setStackInSlot(0, battery);
    }

    @Override
    public void updateCovers() {
        coverUpdates++;
        super.updateCovers();
    }

    @Override
    public void checkWeatherOrTerrainExplosion(float power, double chance, IEnergyContainer container) {
        weatherChecks++;
    }

    @Override
    public void markDirty() {}
}
