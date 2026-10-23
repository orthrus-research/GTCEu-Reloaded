package gregtech.api.configuration;

import gregtech.api.cover.Cover;
import gregtech.api.cover.CoverableView;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

/** Versioned, fresh snapshots. Whole-machine NBT and cover/filter inventories are never accepted. */
public final class ConfigurationClipboard {

    public static final String ITEM_KEY = "GTConfigurationCard";
    private static final String[] SIDES = { "0", "1", "2", "3", "4", "5" };

    private ConfigurationClipboard() {}

    public static NBTTagCompound capture(String kind, ConfigurationAdapter adapter, CoverableView holder) {
        NBTTagCompound clipboard = new NBTTagCompound();
        clipboard.setInteger("Version", 1);
        clipboard.setString("Kind", kind);
        clipboard.setString("Schema", adapter.getSchema());
        clipboard.setTag("Settings", adapter.capture().copy());
        NBTTagCompound covers = new NBTTagCompound();
        for (EnumFacing side : EnumFacing.VALUES) {
            NBTTagCompound entry = new NBTTagCompound();
            Cover cover = holder.getCoverAtSide(side);
            if (cover != null) {
                ConfigurationAdapter coverAdapter = cover.getConfigurationAdapter();
                ConfigurationData.require(coverAdapter != null);
                entry.setString("Id", cover.getDefinition().getResourceLocation().toString());
                entry.setString("Schema", coverAdapter.getSchema());
                entry.setTag("Settings", coverAdapter.capture().copy());
            }
            covers.setTag(SIDES[side.getIndex()], entry);
        }
        clipboard.setTag("Covers", covers);
        return clipboard;
    }

    public static void prepare(NBTTagCompound clipboard, String kind, ConfigurationAdapter adapter,
                               CoverableView holder, ConfigurationContext context,
                               ConfigurationTransaction transaction) {
        ConfigurationData.keys(clipboard, "Version", "Kind", "Schema", "Settings", "Covers");
        ConfigurationData.integer(clipboard, "Version", 1, 1);
        ConfigurationData.require(kind.equals(ConfigurationData.string(clipboard, "Kind")));
        ConfigurationData.require(adapter.getSchema().equals(ConfigurationData.string(clipboard, "Schema")));
        NBTTagCompound covers = ConfigurationData.compound(clipboard, "Covers");
        ConfigurationData.keys(covers, SIDES);
        // Every adapter prepares without mutation, so even the last rejected cover leaves all settings unchanged.
        adapter.prepare(ConfigurationData.compound(clipboard, "Settings"), context, transaction);
        for (EnumFacing side : EnumFacing.VALUES) {
            context.requireEditable(holder.getPos(), side, transaction);
            NBTTagCompound entry = ConfigurationData.compound(covers, SIDES[side.getIndex()]);
            Cover cover = holder.getCoverAtSide(side);
            transaction.guard(() -> holder.getCoverAtSide(side) == cover);
            if (cover == null) {
                ConfigurationData.require(entry.isEmpty());
                continue;
            }
            ConfigurationData.keys(entry, "Id", "Schema", "Settings");
            ConfigurationData.require(cover.getDefinition().getResourceLocation().toString()
                    .equals(ConfigurationData.string(entry, "Id")));
            ConfigurationAdapter coverAdapter = cover.getConfigurationAdapter();
            ConfigurationData.require(coverAdapter != null &&
                    coverAdapter.getSchema().equals(ConfigurationData.string(entry, "Schema")));
            coverAdapter.prepare(ConfigurationData.compound(entry, "Settings"), context, transaction);
        }
    }
}
