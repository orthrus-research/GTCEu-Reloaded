package gregtech.api.util.virtualregistry;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public abstract class VirtualEntry implements INBTSerializable<NBTTagCompound> {

    public static final String DEFAULT_COLOR = "FFFFFFFF";
    protected static final String COLOR_KEY = "color";
    protected static final String DESC_KEY = "description";

    private int color = 0xFFFFFFFF;
    private String colorStr = DEFAULT_COLOR;
    private @NotNull String description = "";

    public abstract EntryTypes<? extends VirtualEntry> getType();

    public String getColorStr() {
        return colorStr;
    }

    public int getColor() {
        return this.color;
    }

    /**
     * @param color up to eight hex digits; anything else, which can come from damaged data or a client, is
     *              ignored and the current colour kept
     */
    public void setColor(String color) {
        if (color == null || color.isEmpty() || color.length() > 8) return;
        int parsed;
        try {
            parsed = parseColor(color);
        } catch (NumberFormatException e) {
            return;
        }
        this.color = parsed;
        this.colorStr = color.toUpperCase(Locale.ROOT);
    }

    public void setColor(int color) {
        setColor(Integer.toHexString(color));
    }

    private int parseColor(String s) {
        // stupid java not having actual unsigned ints
        long tmp = Long.parseLong(s, 16);
        if (tmp > 0x7FFFFFFF) {
            tmp -= 0x100000000L;
        }
        return (int) tmp;
    }

    public @NotNull String getDescription() {
        return this.description;
    }

    public void setDescription(@NotNull String desc) {
        this.description = desc;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VirtualEntry other)) return false;
        return this.getType() == other.getType() &&
                this.color == other.color;
    }

    /** Hashes the fields equals compares here; subclasses that compare more stay consistent with it. */
    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(getType()) + color;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        var tag = new NBTTagCompound();
        tag.setString(COLOR_KEY, this.colorStr);

        if (description != null && !description.isEmpty())
            tag.setString(DESC_KEY, this.description);

        return tag;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        setColor(nbt.getString(COLOR_KEY));

        if (nbt.hasKey(DESC_KEY))
            setDescription(nbt.getString(DESC_KEY));
    }
}
