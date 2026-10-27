package gregtech.api.configuration;

import gregtech.api.cover.CoverDefinition;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.common.covers.CoverConveyor;
import gregtech.common.covers.DistributionMode;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ItemPipeDistributionRegressionTest {

    @BeforeAll
    static void bootstrap() {
        ItemPipeTransportFixture.bootstrap();
    }

    @Test
    void roundRobinModesConserveAcrossSmallPartialAndFullDestinations() {
        for (DistributionMode mode : new DistributionMode[] {
                DistributionMode.ROUND_ROBIN_PRIO, DistributionMode.ROUND_ROBIN_GLOBAL }) {
            for (int firstCapacity = 0; firstCapacity <= 4; firstCapacity++) {
                for (int secondCapacity = 0; secondCapacity <= 4; secondCapacity++) {
                    for (int amount = 1; amount <= 10; amount++) {
                        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
                        ItemStackHandler first = limited(firstCapacity);
                        ItemStackHandler second = limited(secondCapacity);
                        fixture.output(EnumFacing.EAST, first);
                        fixture.output(EnumFacing.SOUTH, second);
                        CoverDefinition definition = new CoverDefinition(GTUtility.gregtechId("distribution_probe"),
                                (def, holder, side) -> new CoverConveyor(def, holder, side, 1, 64), ItemStack.EMPTY);
                        CoverConveyor conveyor = (CoverConveyor) definition.createCover(
                                fixture.pipe.getCoverableImplementation(), EnumFacing.WEST);
                        conveyor.setConveyorMode(CoverConveyor.ConveyorMode.IMPORT);
                        conveyor.setDistributionMode(mode);
                        fixture.pipe.getCoverableImplementation().addCover(EnumFacing.WEST, conveyor);
                        fixture.source.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, amount));

                        GTTransferUtils.moveInventoryItems(fixture.source, fixture.input);

                        assertEquals(amount, fixture.source.getStackInSlot(0).getCount() +
                                first.getStackInSlot(0).getCount() + second.getStackInSlot(0).getCount(),
                                mode + " capacities=" + firstCapacity + "," + secondCapacity + " input=" + amount);
                    }
                }
            }
        }
    }

    @Test
    void simulatedGlobalRoundRobinDoesNotMutateLiveHistory() {
        ItemPipeTransportFixture fixture = new ItemPipeTransportFixture(0);
        fixture.output(EnumFacing.SOUTH, new ItemStackHandler(1));
        CoverDefinition definition = new CoverDefinition(GTUtility.gregtechId("distribution_history_probe"),
                (def, holder, side) -> new CoverConveyor(def, holder, side, 1, 64), ItemStack.EMPTY);
        CoverConveyor conveyor = (CoverConveyor) definition.createCover(fixture.pipe.getCoverableImplementation(),
                EnumFacing.WEST);
        conveyor.setConveyorMode(CoverConveyor.ConveyorMode.IMPORT);
        conveyor.setDistributionMode(DistributionMode.ROUND_ROBIN_GLOBAL);
        fixture.pipe.getCoverableImplementation().addCover(EnumFacing.WEST, conveyor);
        for (int probe = 0; probe < 20; probe++) {
            assertTrue(fixture.input.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 8), true).isEmpty());
        }
        assertTrue(fixture.pipe.getTransferred().isEmpty());
        assertEquals(0, fixture.pipe.getTransferredItems());
    }

    private static ItemStackHandler limited(int capacity) {
        return new ItemStackHandler(1) {

            @Override
            public int getSlotLimit(int slot) {
                return capacity;
            }
        };
    }
}
