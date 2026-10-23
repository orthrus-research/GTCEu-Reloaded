package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.Bootstrap;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.TextFieldWidget2;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
    void untrustedWidgetPacketsCannotBypassServerBoundsOrCauseOverflow() {
        MEStockingConfig config = new MEStockingConfig();
        AtomicInteger changes = new AtomicInteger();
        ModularUI.Builder builder = ModularUI.builder(GuiTextures.BACKGROUND, 176, 228);
        config.addWidgets(builder, false, changes::incrementAndGet);
        List<TextFieldWidget2> fields = builder.build(null, null).guiWidgets.values().stream()
                .filter(TextFieldWidget2.class::isInstance).map(TextFieldWidget2.class::cast)
                .collect(Collectors.toList());
        send(fields.get(0), "100");
        send(fields.get(1), "200");
        assertSettings(config, 100, 200);
        assertEquals(2, changes.get());
        for (String invalid : new String[] { "", "NaN", "1.5", "99999999999999999999" }) {
            send(fields.get(0), invalid);
            send(fields.get(1), invalid);
        }
        assertSettings(config, 100, 200);
        assertEquals(2, changes.get());
        send(fields.get(0), "9223372036854775807");
        send(fields.get(1), "9999");
        assertSettings(config, Integer.MAX_VALUE, 200);
        send(fields.get(0), "-1");
        send(fields.get(1), "-1");
        assertSettings(config, 1, 0);
        NBTTagCompound saved = new NBTTagCompound();
        config.writeToNBT(saved);
        assertEquals(1, saved.getInteger("MinimumStock"));
        assertEquals(0, saved.getInteger("StockingRefreshTicks"));
    }

    static NBTTagCompound settings(int minimum, int interval) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("MinimumStock", minimum);
        tag.setInteger("StockingRefreshTicks", interval);
        return tag;
    }

    private static void send(TextFieldWidget2 widget, String value) {
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            packet.writeString(value);
            widget.handleClientAction(-1, packet);
        } finally {
            packet.release();
        }
    }

    private static void assertSettings(MEStockingConfig config, int minimum, int interval) {
        assertEquals(minimum, config.getMinimumStock());
        assertEquals(interval, config.getRefreshTicks());
    }
}
