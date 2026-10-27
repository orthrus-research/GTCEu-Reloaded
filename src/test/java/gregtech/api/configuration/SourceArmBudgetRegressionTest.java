package gregtech.api.configuration;

import gregtech.api.cover.CoverDefinition;
import gregtech.api.util.GTUtility;
import gregtech.common.covers.CoverConveyor;
import gregtech.common.covers.CoverRoboticArm;
import gregtech.common.covers.TransferMode;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SourceArmBudgetRegressionTest {

    @BeforeAll
    static void bootstrap() {
        ItemPipeTransportFixture.bootstrap();
    }

    @Test
    void sourceExact64And128UseEachScheduledAllowanceOnlyOnce() {
        for (int batch : new int[] { 64, 128 }) {
            for (int firstTick : new int[] { 0, 1 }) {
                SourceArmTestView view = filled();
                CoverRoboticArm arm = arm(view, batch);
                int firstTransferTick = (batch == 64 ? 25 : 65) - (firstTick == 0 ? 20 : 0);
                for (int tick = firstTick; tick <= 200; tick++) {
                    view.tick = tick;
                    arm.update();
                    int moved = count(view.destination);
                    assertEquals(512, count(view.source) + moved);
                    // Legacy scheduler refills after its transfer, including a fresh cover's tick zero.
                    int seededWindows = firstTick == 0 ? 2 : 1;
                    assertTrue(moved <= ((tick / 20) + seededWindows) * 32,
                            "batch=" + batch + " firstTick=" + firstTick + " tick=" + tick + " moved=" + moved);
                    if (tick < firstTransferTick) assertEquals(0, moved);
                    if (tick == firstTransferTick) assertEquals(batch, moved);
                }
                assertTrue(count(view.destination) >= batch, "valid exact work must make bounded progress");
            }
        }
    }

    @Test
    void repeatedCapabilityProbesDoNotBankSourceArmCredit() {
        SourceArmTestView view = filled();
        CoverRoboticArm arm = arm(view, 64);
        view.tick = 5;
        arm.update();
        int buffer = arm.getBuffer();
        IItemHandler exposed = arm.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, view.source);
        assertNotNull(exposed);
        for (int probe = 0; probe < 100; probe++) {
            assertEquals(64, exposed.extractItem(0, 64, true).getCount());
            assertEquals(1, exposed.insertItem(0, new ItemStack(Blocks.COBBLESTONE), true).getCount());
        }
        assertEquals(buffer, arm.getBuffer());
        assertEquals(512, count(view.source));
        assertEquals(0, count(view.destination));
        for (int tick = 6; tick < 25; tick++) {
            view.tick = tick;
            arm.update();
            assertEquals(buffer, arm.getBuffer());
        }
    }

    @Test
    void ordinarySourceConveyorKeepsItsCurrentWindowRate() {
        for (int firstTick : new int[] { 0, 1 }) {
            SourceArmTestView view = filled();
            CoverDefinition definition = new CoverDefinition(GTUtility.gregtechId("source_conveyor_budget"),
                    (def, holder, side) -> new CoverConveyor(def, holder, side, 1, 32), ItemStack.EMPTY);
            CoverConveyor conveyor = (CoverConveyor) definition.createCover(view, EnumFacing.EAST);
            view.cover = conveyor;
            for (int tick = firstTick; tick <= 200; tick++) {
                view.tick = tick;
                conveyor.update();
                assertEquals(512, count(view.source) + count(view.destination));
                int seededWindows = firstTick == 0 ? 2 : 1;
                assertTrue(count(view.destination) <= ((tick / 20) + seededWindows) * 32);
                if (tick == 5) assertEquals(firstTick == 0 ? 64 : 32, count(view.destination));
            }
            assertTrue(count(view.destination) > 0);
        }
    }

    private static SourceArmTestView filled() {
        SourceArmTestView view = new SourceArmTestView();
        for (int slot = 0; slot < view.source.getSlots(); slot++) {
            view.source.setStackInSlot(slot, new ItemStack(Blocks.COBBLESTONE, 64));
        }
        return view;
    }

    private static CoverRoboticArm arm(SourceArmTestView view, int batch) {
        CoverDefinition definition = new CoverDefinition(GTUtility.gregtechId("source_arm_budget"),
                (def, holder, side) -> new CoverRoboticArm(def, holder, side, 1, 32), ItemStack.EMPTY);
        CoverRoboticArm arm = (CoverRoboticArm) definition.createCover(view, EnumFacing.EAST);
        arm.setTransferMode(TransferMode.TRANSFER_EXACT);
        arm.getItemFilterContainer().setTransferSize(batch);
        view.cover = arm;
        return arm;
    }

    private static int count(ItemStackHandler inventory) {
        int total = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) total += inventory.getStackInSlot(slot).getCount();
        return total;
    }
}
