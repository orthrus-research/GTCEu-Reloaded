package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.metatileentity.multiblock.CleanroomDimensions;
import gregtech.api.metatileentity.multiblock.ICleanroomProvider;
import gregtech.api.metatileentity.multiblock.ICleanroomReceiver;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.util.GTUtility;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityCleanroom;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CleanroomExtensionTest {

    @BeforeAll
    static void setup() {
        Bootstrap.perform();
    }

    static class Room extends MetaTileEntityCleanroom {

        IEnergyContainer supply;

        Room() {
            super(GTUtility.gregtechId("cleanroom_extension_test"));
            IdleRecipeTestMachine.attachHolder(this);
        }

        void dimensions(CleanroomDimensions dimensions) {
            setCleanroomDimensions(dimensions);
        }

        CleanroomDimensions dimensions() {
            return getCleanroomDimensions();
        }

        void receiver(ICleanroomReceiver receiver) {
            registerCleanroomReceiver(receiver);
        }

        void attachEnergy(IEnergyContainer energy) {
            this.supply = energy;
            initializeAbilities();
        }

        @Override
        protected BlockPattern createStructurePattern() {
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> getAbilities(MultiblockAbility<T> ability) {
            return ability == MultiblockAbility.INPUT_ENERGY && supply != null ? (List<T>) List.of(supply) : List.of();
        }
    }

    static class Receiver implements ICleanroomReceiver {

        ICleanroomProvider provider;

        @Override
        public ICleanroomProvider getCleanroom() {
            return provider;
        }

        @Override
        public void setCleanroom(ICleanroomProvider provider) {
            this.provider = provider;
        }

        @Override
        public void unsetCleanroom() {
            this.provider = null;
        }
    }

    @Test
    void dimensionsUseNativeInitialSyncAndPreserveExistingSaveFieldConvention() {
        var server = new Room();
        var dimensions = new CleanroomDimensions(3, 5, 4, 2, 6);
        server.dimensions(dimensions);
        assertEquals(dimensions, server.dimensions());
        var data = server.writeToNBT(new NBTTagCompound());
        assertEquals(3, data.getInteger("lDist"));
        assertEquals(5, data.getInteger("rDist"));
        assertEquals(2, data.getInteger("bDist"));
        assertEquals(4, data.getInteger("fDist"));
        assertEquals(6, data.getInteger("hDist"));
        var client = new Room();
        var buffer = new PacketBuffer(Unpooled.buffer());
        try {
            server.writeInitialSyncData(buffer);
            client.receiveInitialSyncData(buffer);
            assertEquals(dimensions, client.dimensions());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
        assertThrows(IllegalArgumentException.class, () -> new CleanroomDimensions(-1, 2, 2, 2, 4));
        assertThrows(IllegalArgumentException.class, () -> new CleanroomDimensions(2, 2, 2, 2, -1));
    }

    @Test
    void invalidationDetachesRegisteredReceiversWithoutStealingTransferredOwnership() {
        var original = new Room();
        var replacement = new Room();
        var owned = new Receiver();
        var transferred = new Receiver();
        owned.setCleanroom(original);
        original.receiver(owned);
        original.receiver(transferred);
        replacement.receiver(transferred);
        original.invalidateStructure();
        assertNull(owned.getCleanroom());
        assertSame(replacement, transferred.getCleanroom());
        replacement.invalidateStructure();
        assertNull(transferred.getCleanroom());
    }

    @Test
    void energyAccessFollowsTheNativeAbilityLifecycle() {
        var room = new Room();
        assertEquals(0, room.getEnergyContainer().getEnergyCapacity());
        var energy = EnergyContainerHandler.receiverContainer(room, 100000, 2048, 1);
        energy.setEnergyStored(50000);
        room.attachEnergy(energy);
        assertEquals(50000, room.getEnergyContainer().getEnergyStored());
        assertEquals(-1536, room.getEnergyContainer().changeEnergy(-1536));
        assertEquals(48464, energy.getEnergyStored());
        room.invalidateStructure();
        assertEquals(0, room.getEnergyContainer().getEnergyCapacity());
        assertEquals(48464, energy.getEnergyStored());
    }
}
