package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.Bootstrap;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.util.world.DummyWorld;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.common.ConfigHolder;

import net.minecraft.util.Tuple;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static gregtech.api.util.GTUtility.gregtechId;
import static org.junit.jupiter.api.Assertions.*;

class MaintenanceHatchDisplayTest {

    private boolean previousMaintenanceEnabled;
    private TestController controller;
    private MultiblockControllerBase attachedController;
    private MetaTileEntityMaintenanceHatch hatch;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @BeforeEach
    void setUp() {
        this.previousMaintenanceEnabled = ConfigHolder.machines.enableMaintenance;
        ConfigHolder.machines.enableMaintenance = true;
        this.controller = new TestController();
        this.attachedController = controller;
        this.hatch = new MetaTileEntityMaintenanceHatch(gregtechId("maintenance_display_hatch"), false) {

            @Override
            public MultiblockControllerBase getController() {
                return attachedController;
            }

            @Override
            public World getWorld() {
                return DummyWorld.INSTANCE;
            }
        };
    }

    @AfterEach
    void restoreConfiguration() {
        ConfigHolder.machines.enableMaintenance = previousMaintenanceEnabled;
    }

    @Test
    void zeroBitsRequireTheirCorrespondingToolsForEveryFaultCombination() {
        for (int fixedMask = 0; fixedMask < 64; fixedMask++) {
            controller.setProblems(fixedMask);
            for (int toolIndex = 0; toolIndex < 6; toolIndex++) {
                assertEquals((fixedMask & (1 << toolIndex)) == 0, hatch.isMaintenanceToolRequired(toolIndex),
                        "fixed mask " + fixedMask + ", tool " + toolIndex);
            }
            assertEquals(fixedMask == 63 ? "gregtech.top.maintenance_fixed" : "gregtech.top.maintenance_broken",
                    statusKey(hatch::addMaintenanceStatusText));
        }
    }

    @Test
    void detachedAndUnformedHatchesDoNotUseAHealthyRecoveryBackup() {
        hatch.storeMaintenanceData((byte) 63, 42);
        this.attachedController = null;
        assertUnavailable("gregtech.machine.maintenance_hatch.unformed");

        this.attachedController = controller;
        controller.formed = false;
        assertUnavailable("gregtech.machine.maintenance_hatch.unformed");

        ConfigHolder.machines.enableMaintenance = false;
        assertUnavailable("gregtech.machine.maintenance_hatch.unformed");
        this.attachedController = null;
        assertUnavailable("gregtech.machine.maintenance_hatch.unformed");
    }

    @Test
    void disabledMaintenanceIsDistinctFromHealthyMaintenance() {
        controller.setProblems(0);
        ConfigHolder.machines.enableMaintenance = false;
        assertUnavailable("gregtech.machine.maintenance_hatch.disabled");

        ConfigHolder.machines.enableMaintenance = true;
        controller.maintenanceEnabled = false;
        assertUnavailable("gregtech.machine.maintenance_hatch.disabled");
    }

    @Test
    void existingSuppliersObserveControllerRepairsDetachAndReform() {
        controller.setProblems(62);
        BooleanSupplier needsWrench = () -> hatch.isMaintenanceToolRequired(0);
        Consumer<List<ITextComponent>> status = hatch::addMaintenanceStatusText;
        assertTrue(needsWrench.getAsBoolean());
        assertEquals("gregtech.top.maintenance_broken", statusKey(status));

        controller.setMaintenanceFixed(0);
        assertFalse(needsWrench.getAsBoolean());
        assertEquals("gregtech.top.maintenance_fixed", statusKey(status));

        this.attachedController = null;
        assertFalse(needsWrench.getAsBoolean());
        assertEquals("gregtech.machine.maintenance_hatch.unformed", statusKey(status));

        controller.setProblems(62);
        this.attachedController = controller;
        assertTrue(needsWrench.getAsBoolean());
        assertEquals("gregtech.top.maintenance_broken", statusKey(status));
    }

    @Test
    void displayReadsPreserveControllerFaultsAndRecoveryData() {
        controller.setProblems(0b010101);
        hatch.storeMaintenanceData((byte) 0b101010, 123);
        for (int i = 0; i < 6; i++) {
            hatch.isMaintenanceToolRequired(i);
            statusKey(hatch::addMaintenanceStatusText);
        }
        assertEquals(0b010101, controller.getMaintenanceProblems());
        assertEquals(0, controller.repairCalls);
        assertTrue(hatch.hasMaintenanceData());
        Tuple<Byte, Integer> stored = hatch.readMaintenanceData();
        assertEquals((byte) 0b101010, stored.getFirst().byteValue());
        assertEquals(123, stored.getSecond().intValue());
    }

    private void assertUnavailable(String expectedKey) {
        assertEquals(expectedKey, statusKey(hatch::addMaintenanceStatusText));
        for (int i = 0; i < 6; i++) {
            assertFalse(hatch.isMaintenanceToolRequired(i));
        }
    }

    private static String statusKey(Consumer<List<ITextComponent>> supplier) {
        List<ITextComponent> text = new ArrayList<>();
        supplier.accept(text);
        assertEquals(1, text.size());
        return ((TextComponentTranslation) text.get(0)).getKey();
    }

    private static class TestController extends MultiblockWithDisplayBase {

        private boolean formed = true;
        private boolean maintenanceEnabled = true;
        private int repairCalls;

        private TestController() {
            super(gregtechId("maintenance_display_controller"));
        }

        private void setProblems(int fixedMask) {
            this.maintenance_problems = (byte) fixedMask;
        }

        @Override
        public boolean isStructureFormed() {
            return formed;
        }

        @Override
        public boolean hasMaintenanceMechanics() {
            return maintenanceEnabled;
        }

        @Override
        public void setMaintenanceFixed(int index) {
            this.repairCalls++;
            super.setMaintenanceFixed(index);
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new TestController();
        }

        @Override
        protected void updateFormedValid() {}

        @Override
        protected BlockPattern createStructurePattern() {
            return null;
        }

        @Override
        public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
            return null;
        }
    }
}
