package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.CycleButtonWidget;
import gregtech.api.gui.widgets.ImageWidget;
import gregtech.api.gui.widgets.TextFieldWidget2;

import net.minecraft.nbt.NBTTagCompound;

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

    private static int parseNumber(String text, int min, int max, int fallback) {
        try {
            return clamp(Long.parseLong(text), min, max);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    void addWidgets(ModularUI.Builder builder, boolean fluids, Runnable onChanged) {
        builder.label(7, 106, fluids ? "gregtech.gui.me_stocking.minimum_fluids" :
                "gregtech.gui.me_stocking.minimum_items");
        builder.label(7, 126, "gregtech.gui.me_stocking.refresh");
        builder.widget(new ImageWidget(96, 102, 73, 16, GuiTextures.DISPLAY)
                .setTooltip("gregtech.gui.me_stocking.minimum.tooltip"));
        builder.widget(new ImageWidget(40, 122, 36, 16, GuiTextures.DISPLAY)
                .setTooltip("gregtech.gui.me_stocking.refresh.tooltip"));
        builder.widget(new TextFieldWidget2(99, 106, 67, 10, () -> Integer.toString(minimumStock), text -> {
            int value = parseNumber(text, 1, Integer.MAX_VALUE, minimumStock);
            if (minimumStock != value) {
                this.minimumStock = value;
                onChanged.run();
            }
        }).setMaxLength(20).setNumbersOnly(1, Integer.MAX_VALUE));
        builder.widget(new TextFieldWidget2(43, 126, 30, 10, () -> Integer.toString(refreshTicks), text -> {
            int value = parseNumber(text, 0, 200, refreshTicks);
            if (refreshTicks != value) {
                this.refreshTicks = value;
                onChanged.run();
            }
        }).setMaxLength(20).setNumbersOnly(0, 200));
        builder.widget(new CycleButtonWidget(82, 122, 87, 16, () -> preferAbundant, value -> {
            if (preferAbundant != value) {
                this.preferAbundant = value;
                onChanged.run();
            }
        }, "gregtech.gui.me_stocking.first", "gregtech.gui.me_stocking.abundant")
                .setTooltipHoverString("gregtech.gui.me_stocking.selection.tooltip"));
    }
}
