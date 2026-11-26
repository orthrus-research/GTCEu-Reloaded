package gregtech.datafix.walker;

import gregtech.datafix.GTFixType;
import gregtech.datafix.util.DataFixHelper;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.datafix.IDataFixer;
import net.minecraft.util.datafix.IDataWalker;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.NotNull;

import static gregtech.datafix.util.DataFixConstants.*;

/**
 * Runs the {@link GTFixType#ITEM_STACK_LIKE} fixes on every item stack nested in a block entity, entity or player.
 * An item stack saves its count as a byte and its damage as a short, and inventories with larger stacks save the
 * count as an int, so any number is accepted for both.
 */
public final class WalkItemStackLike implements IDataWalker {

    @Override
    public @NotNull NBTTagCompound process(@NotNull IDataFixer fixer, @NotNull NBTTagCompound compound, int versionIn) {
        DataFixHelper.rewriteCompoundTags(compound, tag -> {
            if (tag.hasKey(ITEM_ID, Constants.NBT.TAG_STRING) &&
                    tag.hasKey(ITEM_COUNT, Constants.NBT.TAG_ANY_NUMERIC) &&
                    tag.hasKey(ITEM_DAMAGE, Constants.NBT.TAG_ANY_NUMERIC)) {
                return fixer.process(GTFixType.ITEM_STACK_LIKE, tag, versionIn);
            }
            return null;
        });
        return compound;
    }
}
