package gregtech.integration.probe;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * One line a probe shows: text, an item icon followed by text or by the item's name, or a progress bar. A line may
 * be limited to the sides of the block it describes, such as an input line shown only while looking at an input.
 * Instances are immutable.
 */
public final class ProbeLine {

    /** Every side of a block, as a mask of {@link EnumFacing#getIndex()} bits. */
    public static final int ALL_SIDES = (1 << EnumFacing.VALUES.length) - 1;

    private final ItemStack icon;
    @Nullable
    private final ProbeText text;
    @Nullable
    private final ProbeProgress progress;
    private final int sides;

    private ProbeLine(ItemStack icon, @Nullable ProbeText text, @Nullable ProbeProgress progress, int sides) {
        this.icon = icon;
        this.text = text;
        this.progress = progress;
        this.sides = sides;
    }

    public static ProbeLine text(ProbeText text) {
        return new ProbeLine(ItemStack.EMPTY, Objects.requireNonNull(text), null, ALL_SIDES);
    }

    /** An item icon followed by text. */
    public static ProbeLine item(ItemStack icon, ProbeText text) {
        return new ProbeLine(icon.copy(), Objects.requireNonNull(text), null, ALL_SIDES);
    }

    /** An item icon followed by the item's name, which the client looks up. */
    public static ProbeLine namedItem(ItemStack icon) {
        return new ProbeLine(icon.copy(), null, null, ALL_SIDES);
    }

    public static ProbeLine progress(ProbeProgress progress) {
        return new ProbeLine(ItemStack.EMPTY, null, Objects.requireNonNull(progress), ALL_SIDES);
    }

    /** The sides whose faces pass the filter, as a mask for {@link #onSides(int)}. */
    public static int sides(Predicate<EnumFacing> filter) {
        int sides = 0;
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (filter.test(facing)) sides |= 1 << facing.getIndex();
        }
        return sides;
    }

    /** This line, shown only while looking at one of the given sides. */
    public ProbeLine onSides(int sides) {
        return new ProbeLine(icon, text, progress, sides & ALL_SIDES);
    }

    /** Whether the line is shown while looking at a side; a line on every side is shown without one too. */
    public boolean isVisibleFrom(@Nullable EnumFacing side) {
        if (sides == ALL_SIDES) return true;
        return side != null && (sides & (1 << side.getIndex())) != 0;
    }

    /** The icon, or an empty stack for a line without one. */
    public ItemStack getIcon() {
        return icon;
    }

    /** The text, or null for a progress bar or an icon followed by its name. */
    @Nullable
    public ProbeText getText() {
        return text;
    }

    @Nullable
    public ProbeProgress getProgress() {
        return progress;
    }

    public int getSides() {
        return sides;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        if (!icon.isEmpty()) tag.setTag("Icon", icon.writeToNBT(new NBTTagCompound()));
        if (text != null) tag.setTag("Text", text.writeToNBT());
        if (progress != null) tag.setTag("Progress", progress.writeToNBT());
        if (sides != ALL_SIDES) tag.setByte("Sides", (byte) sides);
        return tag;
    }

    public static ProbeLine readFromNBT(NBTTagCompound tag) {
        ItemStack icon = tag.hasKey("Icon") ? new ItemStack(tag.getCompoundTag("Icon")) : ItemStack.EMPTY;
        ProbeText text = null;
        if (tag.hasKey("Text")) text = ProbeText.readFromNBT(tag.getTagList("Text", Constants.NBT.TAG_COMPOUND));
        ProbeProgress progress = null;
        if (tag.hasKey("Progress")) progress = ProbeProgress.readFromNBT(tag.getCompoundTag("Progress"));
        int sides = tag.hasKey("Sides") ? tag.getByte("Sides") & ALL_SIDES : ALL_SIDES;
        return new ProbeLine(icon, text, progress, sides);
    }

    public static NBTTagList writeList(List<ProbeLine> lines) {
        NBTTagList list = new NBTTagList();
        for (ProbeLine line : lines) {
            list.appendTag(line.writeToNBT());
        }
        return list;
    }

    public static List<ProbeLine> readList(NBTTagList list) {
        List<ProbeLine> lines = new ArrayList<>(list.tagCount());
        for (NBTBase element : list) {
            lines.add(readFromNBT((NBTTagCompound) element));
        }
        return lines;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProbeLine line)) return false;
        return sides == line.sides && ItemStack.areItemStacksEqual(icon, line.icon) &&
                Objects.equals(text, line.text) && Objects.equals(progress, line.progress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(icon.getItem(), icon.getMetadata(), icon.getCount(), text, progress, sides);
    }

    @Override
    public String toString() {
        return "ProbeLine{icon=" + icon + ", text=" + text + ", progress=" + progress + ", sides=" + sides + "}";
    }
}
