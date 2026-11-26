package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.mui.GTGuiTextures;

import net.minecraft.nbt.NBTTagCompound;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

/** Settings shared by item and fluid stocking parts. Zero ticks preserves the two legacy polling clocks. */
final class MEStockingConfig {

    static final int UI_EXTRA_HEIGHT = 44;
    private static final String MINIMUM_KEY = "MinimumStock";
    private static final String REFRESH_KEY = "StockingRefreshTicks";
    private static final String ABUNDANCE_KEY = "PreferAbundant";
    private boolean preferAbundant;
    private int minimumStock = 1;
    private int refreshTicks;

    int getMinimumStock() {
        return minimumStock;
    }

    int getRefreshTicks() {
        return refreshTicks;
    }

    boolean isPreferAbundant() {
        return preferAbundant;
    }

    void writeToNBT(NBTTagCompound tag) {
        tag.setBoolean(ABUNDANCE_KEY, preferAbundant);
        tag.setInteger(MINIMUM_KEY, minimumStock);
        tag.setInteger(REFRESH_KEY, refreshTicks);
    }

    void readFromNBT(NBTTagCompound tag, boolean preserveMissing) {
        this.preferAbundant = tag.hasKey(ABUNDANCE_KEY, 1) ? tag.getBoolean(ABUNDANCE_KEY) :
                preserveMissing && preferAbundant;
        this.minimumStock = readNumber(tag, MINIMUM_KEY, 1, Integer.MAX_VALUE,
                preserveMissing ? minimumStock : 1);
        this.refreshTicks = readNumber(tag, REFRESH_KEY, 0, 200, preserveMissing ? refreshTicks : 0);
    }

    private static int readNumber(NBTTagCompound tag, String key, int min, int max, int fallback) {
        return tag.hasKey(key, 99) ? clamp(tag.getLong(key), min, max) : fallback;
    }

    private static int clamp(long value, int min, int max) {
        return (int) Math.max(min, Math.min(max, value));
    }

    /** Sets the minimum stock; whatever a client sends is kept within 1 and the largest int. */
    boolean setMinimumStock(long value) {
        int minimum = clamp(value, 1, Integer.MAX_VALUE);
        if (minimumStock == minimum) return false;
        this.minimumStock = minimum;
        return true;
    }

    /** Sets the refresh interval; whatever a client sends is kept within 0 and 200 ticks. */
    boolean setRefreshTicks(long value) {
        int ticks = clamp(value, 0, 200);
        if (refreshTicks == ticks) return false;
        this.refreshTicks = ticks;
        return true;
    }

    boolean setPreferAbundant(boolean value) {
        if (preferAbundant == value) return false;
        this.preferAbundant = value;
        return true;
    }

    void addWidgets(ModularPanel panel, PanelSyncManager syncManager, boolean fluids, Runnable onChanged) {
        IntSyncValue minimum = new IntSyncValue(() -> minimumStock, value -> {
            if (setMinimumStock(value)) onChanged.run();
        });
        IntSyncValue refresh = new IntSyncValue(() -> refreshTicks, value -> {
            if (setRefreshTicks(value)) onChanged.run();
        });
        BooleanSyncValue abundant = new BooleanSyncValue(() -> preferAbundant, value -> {
            if (setPreferAbundant(value)) onChanged.run();
        });
        syncManager.syncValue("me_stocking_minimum", minimum);
        syncManager.syncValue("me_stocking_refresh", refresh);
        syncManager.syncValue("me_stocking_abundant", abundant);

        panel.child(IKey.lang(fluids ? "gregtech.gui.me_stocking.minimum_fluids" :
                "gregtech.gui.me_stocking.minimum_items").asWidget().pos(7, 106))
                .child(IKey.lang("gregtech.gui.me_stocking.refresh").asWidget().pos(7, 126))
                .child(new TextFieldWidget()
                        .pos(96, 102).size(73, 16)
                        .background(GTGuiTextures.DISPLAY)
                        .setTextColor(Color.WHITE.main)
                        .setMaxLength(20)
                        .setNumbers(1, Integer.MAX_VALUE)
                        .value(minimum)
                        .addTooltipLine(IKey.lang("gregtech.gui.me_stocking.minimum.tooltip")))
                .child(new TextFieldWidget()
                        .pos(40, 122).size(36, 16)
                        .background(GTGuiTextures.DISPLAY)
                        .setTextColor(Color.WHITE.main)
                        .setMaxLength(20)
                        .setNumbers(0, 200)
                        .value(refresh)
                        .addTooltipLine(IKey.lang("gregtech.gui.me_stocking.refresh.tooltip")))
                .child(new CycleButtonWidget()
                        .stateCount(2)
                        .value(abundant)
                        .stateOverlay(0, IKey.lang("gregtech.gui.me_stocking.first"))
                        .stateOverlay(1, IKey.lang("gregtech.gui.me_stocking.abundant"))
                        .addTooltipLine(IKey.lang("gregtech.gui.me_stocking.selection.tooltip"))
                        .pos(82, 122).size(87, 16));
    }
}
