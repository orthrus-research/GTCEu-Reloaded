package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.Bootstrap;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.ValueSyncHandler;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MEStockingConfigTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void oldWorldsUseLegacyDefaultsButOldCardsPreserveDestinationSettings() {
        MEStockingConfig config = new MEStockingConfig();
        NBTTagCompound tag = settings(100, 200);
        config.readFromNBT(tag, false);
        config.readFromNBT(new NBTTagCompound(), true);
        assertSettings(config, 100, 200);
        config.readFromNBT(new NBTTagCompound(), false);
        assertSettings(config, 1, 0);
    }

    @Test
    void numericNbtIsClampedBeforeNarrowingAndWrongTypesUseDefaults() {
        MEStockingConfig config = new MEStockingConfig();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("MinimumStock", Long.MAX_VALUE);
        tag.setLong("StockingRefreshTicks", Long.MAX_VALUE);
        config.readFromNBT(tag, false);
        assertSettings(config, Integer.MAX_VALUE, 200);
        tag.setLong("MinimumStock", Long.MIN_VALUE);
        tag.setLong("StockingRefreshTicks", Long.MIN_VALUE);
        config.readFromNBT(tag, false);
        assertSettings(config, 1, 0);
        config.readFromNBT(settings(10, 50), false);
        tag.setString("MinimumStock", "100");
        tag.setString("StockingRefreshTicks", "200");
        config.readFromNBT(tag, true);
        assertSettings(config, 10, 50);
        config.readFromNBT(tag, false);
        assertSettings(config, 1, 0);
    }

    @Test
    void untrustedScreenValuesCannotBypassServerBoundsOrCauseOverflow() throws Exception {
        MEStockingConfig config = new MEStockingConfig();
        AtomicInteger changes = new AtomicInteger();
        PanelSyncManager manager = new PanelSyncManager(new ModularSyncManager(false), true);
        config.addWidgets(new ModularPanel("stocking_settings"), manager, false, changes::incrementAndGet);
        ValueSyncHandler<?> minimum = (ValueSyncHandler<?>) manager.findSyncHandler("me_stocking_minimum");
        ValueSyncHandler<?> refresh = (ValueSyncHandler<?>) manager.findSyncHandler("me_stocking_refresh");
        send(minimum, new IntSyncValue(() -> 100));
        send(refresh, new IntSyncValue(() -> 200));
        assertSettings(config, 100, 200);
        assertEquals(2, changes.get());
        // an unchanged value is no change
        send(minimum, new IntSyncValue(() -> 100));
        send(refresh, new IntSyncValue(() -> 200));
        assertEquals(2, changes.get());
        send(minimum, new IntSyncValue(() -> Integer.MAX_VALUE));
        send(refresh, new IntSyncValue(() -> 9999));
        assertSettings(config, Integer.MAX_VALUE, 200);
        send(minimum, new IntSyncValue(() -> -1));
        send(refresh, new IntSyncValue(() -> Integer.MIN_VALUE));
        assertSettings(config, 1, 0);
        NBTTagCompound saved = new NBTTagCompound();
        config.writeToNBT(saved);
        assertEquals(1, saved.getInteger("MinimumStock"));
        assertEquals(0, saved.getInteger("StockingRefreshTicks"));

        int before = changes.get();
        send(manager.findSyncHandler("me_stocking_abundant", BooleanSyncValue.class), new BooleanSyncValue(() -> true));
        assertTrue(config.isPreferAbundant());
        assertEquals(before + 1, changes.get());
    }

    static NBTTagCompound settings(int minimum, int interval) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("MinimumStock", minimum);
        tag.setInteger("StockingRefreshTicks", interval);
        return tag;
    }

    static void send(Object receiver, ValueSyncHandler<?> sender) throws Exception {
        sender.updateCacheFromSource(true);
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            sender.write(packet);
            ((ValueSyncHandler<?>) receiver).readOnServer(0, packet);
        } finally {
            packet.release();
        }
    }

    private static void assertSettings(MEStockingConfig config, int minimum, int interval) {
        assertEquals(minimum, config.getMinimumStock());
        assertEquals(interval, config.getRefreshTicks());
    }
}
