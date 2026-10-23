package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IElectricItem;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.SimpleCapabilityManager;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.items.ItemStackHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.Type;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class EnergyContainerHandlerTest {

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.perform();
        if (GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM == null) {
            ASMDataTable capabilities = new ASMDataTable();
            capabilities.addASMData(null, CapabilityInject.class.getName(), GregtechCapabilities.class.getName(),
                    "CAPABILITY_ELECTRIC_ITEM", Collections.singletonMap("value", Type.getType(IElectricItem.class)));
            CapabilityManager.INSTANCE.injectCapabilities(capabilities);
            SimpleCapabilityManager.registerCapabilityWithNoDefault(IElectricItem.class);
        }
        assertNotNull(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM);
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 64, 128 })
    public void equalValuesDoNotDirtyOrNotify(long stored) {
        RecordingMachine machine = new RecordingMachine();
        EnergyContainerHandler container = createContainer(machine);
        loadEnergy(container, stored);
        machine.resetObservations();

        container.setEnergyStored(stored);
        assertEquals(0, container.addEnergy(0));
        assertEquals(0, container.removeEnergy(0));

        assertEquals(stored, container.getEnergyStored());
        assertEquals(0, machine.dirtyCalls);
        assertEquals(0, machine.changeCalls);
        assertEquals(0, machine.initialCalls);
        assertEquals(0, container.energyInputPerSec);
        assertEquals(0, container.energyOutputPerSec);
    }

    @Test
    public void transfersClampedToTheCurrentValueDoNotDirtyOrNotify() {
        RecordingMachine machine = new RecordingMachine();
        EnergyContainerHandler container = createContainer(machine);
        loadEnergy(container, 128);
        machine.resetObservations();
        assertEquals(0, container.addEnergy(32));
        assertEquals(128, container.getEnergyStored());
        assertEquals(0, machine.dirtyCalls);
        assertEquals(0, machine.changeCalls);

        loadEnergy(container, 0);
        machine.resetObservations();
        assertEquals(0, container.removeEnergy(32));
        assertEquals(0, container.getEnergyStored());
        assertEquals(0, machine.dirtyCalls);
        assertEquals(0, machine.changeCalls);
        assertEquals(0, container.energyInputPerSec);
        assertEquals(0, container.energyOutputPerSec);
    }

    @Test
    public void realChangesNotifyOnceAndAccountOnlyActualTransfers() {
        RecordingMachine machine = new RecordingMachine();
        EnergyContainerHandler container = createContainer(machine);
        container.setEnergyStored(32);
        assertChange(machine, container, 1, 32);
        assertEquals(64, container.addEnergy(64));
        assertChange(machine, container, 2, 96);
        assertEquals(-40, container.removeEnergy(40));
        assertChange(machine, container, 3, 56);
        assertEquals(72, container.addEnergy(100));
        assertChange(machine, container, 4, 128);
        assertEquals(0, container.addEnergy(1));
        assertChange(machine, container, 4, 128);
        assertEquals(-128, container.removeEnergy(200));
        assertChange(machine, container, 5, 0);
        assertEquals(0, container.removeEnergy(1));
        assertChange(machine, container, 5, 0);

        container.update();
        assertEquals(168, container.getInputPerSec());
        assertEquals(168, container.getOutputPerSec());
        assertEquals(0, container.energyInputPerSec);
        assertEquals(0, container.energyOutputPerSec);
        container.addEnergy(0);
        container.update();
        assertEquals(0, container.getInputPerSec());
        assertEquals(0, container.getOutputPerSec());
        assertChange(machine, container, 5, 0);
    }

    @Test
    public void nbtLoadsStillSendInitialNotificationsForUnchangedValues() {
        RecordingMachine machine = new RecordingMachine();
        EnergyContainerHandler container = createContainer(machine);
        loadEnergy(container, 0);
        assertEquals(1, machine.initialCalls);
        loadEnergy(container, 64);
        assertEquals(2, machine.initialCalls);
        loadEnergy(container, 64);
        assertEquals(3, machine.initialCalls);
        assertEquals(64, container.serializeNBT().getLong("EnergyStored"));
        assertEquals(64, machine.lastObservedEnergy);
        assertSame(container, machine.lastContainer);
        assertEquals(0, machine.dirtyCalls);
        assertEquals(0, machine.changeCalls);
        assertEquals(0, container.energyInputPerSec);
        assertEquals(0, container.energyOutputPerSec);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    public void zeroBatteryTransfersDoNotDirtyOrNotify(boolean full) {
        RecordingMachine machine = new RecordingMachine();
        EnergyContainerHandler container = createContainer(machine);
        Item batteryItem = new Item() {

            @Nullable
            @Override
            public ICapabilityProvider initCapabilities(@NotNull ItemStack stack, @Nullable NBTTagCompound nbt) {
                return new ElectricItem(stack, 128, 1, true, true);
            }
        };
        ItemStack battery = new ItemStack(batteryItem);
        IElectricItem electricItem = battery.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        assertNotNull(electricItem);
        if (full) assertEquals(128, electricItem.charge(128, 1, true, false));
        ItemStackHandler inventory = new ItemStackHandler(1);
        inventory.setStackInSlot(0, battery);
        loadEnergy(container, full ? 128 : 0);
        machine.resetObservations();

        assertFalse(container.dischargeOrRechargeEnergyContainers(inventory, 0));
        assertEquals(full ? 128 : 0, container.getEnergyStored());
        assertEquals(full ? 128 : 0, electricItem.getCharge());
        assertEquals(0, machine.dirtyCalls);
        assertEquals(0, machine.changeCalls);
        assertEquals(0, container.energyInputPerSec);
        assertEquals(0, container.energyOutputPerSec);
    }

    @Test
    public void changeEnergyStillDispatchesToOverriddenSettersForNoOpRequests() {
        RecordingMachine machine = new RecordingMachine();
        AtomicInteger setterCalls = new AtomicInteger();
        EnergyContainerHandler container = new EnergyContainerHandler(machine, 128, 32, 1, 0, 0) {

            @Override
            public void setEnergyStored(long energyStored) {
                setterCalls.incrementAndGet();
                super.setEnergyStored(energyStored);
            }
        };
        assertEquals(0, container.changeEnergy(0));
        assertEquals(1, setterCalls.get());
        assertEquals(0, machine.dirtyCalls);
        assertEquals(0, machine.changeCalls);
    }

    private static EnergyContainerHandler createContainer(RecordingMachine machine) {
        return EnergyContainerHandler.receiverContainer(machine, 128, 32, 1);
    }

    private static void loadEnergy(EnergyContainerHandler container, long stored) {
        NBTTagCompound saved = new NBTTagCompound();
        saved.setLong("EnergyStored", stored);
        container.deserializeNBT(saved);
    }

    private static void assertChange(RecordingMachine machine, EnergyContainerHandler container, int calls,
                                     long stored) {
        assertEquals(calls, machine.dirtyCalls);
        assertEquals(calls, machine.changeCalls);
        assertEquals(0, machine.initialCalls);
        assertEquals(stored, container.getEnergyStored());
        assertEquals(stored, machine.lastObservedEnergy);
        assertSame(container, machine.lastContainer);
    }

    private static class RecordingMachine extends MetaTileEntity
                                          implements EnergyContainerHandler.IEnergyChangeListener {

        private int dirtyCalls;
        private int changeCalls;
        private int initialCalls;
        private long lastObservedEnergy;
        private IEnergyContainer lastContainer;

        private RecordingMachine() {
            super(GTUtility.gregtechId("energy_notification_test"));
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new RecordingMachine();
        }

        @Override
        public World getWorld() {
            return DummyWorld.INSTANCE;
        }

        @Override
        public void markDirty() {
            this.dirtyCalls++;
        }

        @Override
        public void onEnergyChanged(IEnergyContainer container, boolean isInitialChange) {
            if (isInitialChange) this.initialCalls++;
            else this.changeCalls++;
            this.lastObservedEnergy = container.getEnergyStored();
            this.lastContainer = container;
        }

        private void resetObservations() {
            this.dirtyCalls = 0;
            this.changeCalls = 0;
            this.initialCalls = 0;
        }
    }
}
