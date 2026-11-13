package gregtech.common.covers.filter.readers;

import gregtech.common.covers.filter.SmartItemFilter;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class SmartItemFilterReader extends SimpleItemFilterReader {

    private static final String FILTER_MODE = "FilterMode";

    public SmartItemFilterReader(ItemStack container) {
        super(container, 0);
    }

    public SmartItemFilter.SmartFilteringMode getFilteringMode() {
        if (!getStackTag().hasKey(FILTER_MODE))
            setFilteringMode(SmartItemFilter.SmartFilteringMode.ELECTROLYZER);

        return modeOf(getStackTag().getInteger(FILTER_MODE));
    }

    public void setFilteringMode(SmartItemFilter.SmartFilteringMode filteringMode) {
        getStackTag().setInteger(FILTER_MODE, filteringMode.ordinal());
        markDirty();
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        super.deserializeNBT(nbt);
        this.setFilteringMode(modeOf(nbt.getInteger(FILTER_MODE)));
    }

    @Override
    public void handleLegacyNBT(NBTTagCompound tag) {
        super.handleLegacyNBT(tag);
        var legacyFilter = tag.getCompoundTag(KEY_LEGACY_FILTER);
        this.setFilteringMode(modeOf(legacyFilter.getInteger(FILTER_MODE)));
    }

    /** The mode saved at {@code ordinal}, or the default electrolyzer mode for a value that names none. */
    private static SmartItemFilter.SmartFilteringMode modeOf(int ordinal) {
        var values = SmartItemFilter.SmartFilteringMode.VALUES;
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] :
                SmartItemFilter.SmartFilteringMode.ELECTROLYZER;
    }
}
