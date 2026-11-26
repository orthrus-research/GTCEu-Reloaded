package gregtech.integration.probe;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import java.util.Objects;

/**
 * A bar from zero to {@code max} with the maximum and its {@code unit} after the current value. {@code grouped}
 * groups the numbers in thousands; {@code compact} lets The One Probe shorten values of 10,000 or more (to
 * {@code 12.3 kEU}) while the player is not sneaking; {@code color} is the bar's ARGB colour. A probe that draws no
 * bars shows the {@code fallback} text instead.
 */
public record ProbeProgress(long current, long max, String unit, boolean grouped, boolean compact, int color,
                            ProbeText fallback) {

    public ProbeProgress {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(fallback, "fallback");
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("Current", current);
        tag.setLong("Max", max);
        tag.setString("Unit", unit);
        tag.setBoolean("Grouped", grouped);
        tag.setBoolean("Compact", compact);
        tag.setInteger("Color", color);
        tag.setTag("Fallback", fallback.writeToNBT());
        return tag;
    }

    public static ProbeProgress readFromNBT(NBTTagCompound tag) {
        return new ProbeProgress(tag.getLong("Current"), tag.getLong("Max"), tag.getString("Unit"),
                tag.getBoolean("Grouped"), tag.getBoolean("Compact"), tag.getInteger("Color"),
                ProbeText.readFromNBT(tag.getTagList("Fallback", Constants.NBT.TAG_COMPOUND)));
    }
}
