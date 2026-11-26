package gregtech.common.metatileentities.storage;

import gregtech.BootstrapExtension;
import gregtech.api.GTValues;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.ValueSyncHandler;
import com.cleanroommc.modularui.widget.WidgetTree;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(BootstrapExtension.class)
class CreativeEnergyScreenTest {

    private MetaTileEntityCreativeEnergy energy;
    private PanelSyncManager manager;

    @BeforeEach
    void open() {
        this.energy = new MetaTileEntityCreativeEnergy();
        new MetaTileEntityHolder() {

            {
                setRawMetaTileEntity(energy);
                setWorld(DummyWorld.INSTANCE);
            }
        };
        this.manager = new PanelSyncManager(new ModularSyncManager(false), true);
        ModularPanel panel = energy.buildUI(null, manager, new UISettings());
        assertDoesNotThrow(() -> WidgetTree.collectSyncValues(manager, panel));
    }

    @Test
    void tierPacketsSelectAnExistingTierAndItsVoltage() throws Exception {
        send("creative_energy_tier", new IntSyncValue(() -> GTValues.HV));
        assertEquals(GTValues.V[GTValues.HV], saved().getLong("Voltage"));
        assertEquals(GTValues.HV, saved().getByte("Tier"));

        send("creative_energy_tier", new IntSyncValue(() -> 99));
        assertEquals(GTValues.MAX, saved().getByte("Tier"));
        assertEquals(GTValues.V[GTValues.MAX], saved().getLong("Voltage"));

        send("creative_energy_tier", new IntSyncValue(() -> -3));
        assertEquals(GTValues.ULV, saved().getByte("Tier"));
    }

    @Test
    void voltageAndAmperagePacketsAreNeverNegative() throws Exception {
        send("creative_energy_voltage", new LongSyncValue(() -> GTValues.V[GTValues.EV]));
        assertEquals(GTValues.V[GTValues.EV], saved().getLong("Voltage"));
        assertEquals(GTValues.EV, saved().getByte("Tier"), "a typed voltage selects its tier");

        send("creative_energy_voltage", new LongSyncValue(() -> -5L));
        assertEquals(0, saved().getLong("Voltage"));

        send("creative_energy_amperage", new IntSyncValue(() -> 16));
        assertEquals(16, saved().getInteger("Amps"));
        send("creative_energy_amperage", new IntSyncValue(() -> Integer.MIN_VALUE));
        assertEquals(0, saved().getInteger("Amps"));
    }

    @Test
    void switchingToSinkOrSourceResetsTheOutputAsBefore() throws Exception {
        send("creative_energy_source", new BooleanSyncValue(() -> false));
        NBTTagCompound sink = saved();
        assertFalse(sink.getBoolean("Source"));
        assertEquals(GTValues.V[GTValues.MAX], sink.getLong("Voltage"));
        assertEquals(Integer.MAX_VALUE, sink.getInteger("Amps"));

        send("creative_energy_source", new BooleanSyncValue(() -> true));
        NBTTagCompound source = saved();
        assertTrue(source.getBoolean("Source"));
        assertEquals(0, source.getLong("Voltage"));
        assertEquals(0, source.getInteger("Amps"));

        send("creative_energy_active", new BooleanSyncValue(() -> true));
        assertTrue(energy.isWorkingEnabled());
    }

    private NBTTagCompound saved() {
        return energy.writeToNBT(new NBTTagCompound());
    }

    private void send(String key, ValueSyncHandler<?> sender) throws Exception {
        sender.updateCacheFromSource(true);
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            sender.write(buffer);
            manager.findSyncHandler(key).readOnServer(0, buffer);
        } finally {
            buffer.release();
        }
    }
}
