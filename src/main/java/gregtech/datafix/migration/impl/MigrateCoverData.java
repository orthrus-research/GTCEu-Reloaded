package gregtech.datafix.migration.impl;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.datafix.IFixableData;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

import static gregtech.datafix.util.DataFixConstants.*;

/**
 * Fixes the saved covers of every tile entity in a chunk. A machine keeps its covers in its {@code MetaTileEntity}
 * tag and a pipe at the top of its own tag, both as the list {@code CoverSaveHandler} writes: one compound per cover
 * with its {@code CoverId}, its {@code Side} and the cover's own keys.
 */
public class MigrateCoverData implements IFixableData {

    private static final String META_TILE_ENTITY = "MetaTileEntity";
    private static final String COVERS = "Covers";
    private static final String COVER_ID = "CoverId";

    private final int fixVersion;
    private final BiConsumer<@NotNull ResourceLocation, @NotNull NBTTagCompound> coverFixer;

    /**
     * @param fixVersion the version this fix brings the data to
     * @param coverFixer fixes one cover's tag in place, given the cover's ID
     */
    public MigrateCoverData(int fixVersion,
                            @NotNull BiConsumer<@NotNull ResourceLocation, @NotNull NBTTagCompound> coverFixer) {
        this.fixVersion = fixVersion;
        this.coverFixer = coverFixer;
    }

    @Override
    public int getFixVersion() {
        return fixVersion;
    }

    @Override
    public @NotNull NBTTagCompound fixTagCompound(@NotNull NBTTagCompound compound) {
        if (!compound.hasKey(LEVEL_TAG, Constants.NBT.TAG_COMPOUND)) {
            return compound;
        }

        NBTTagList tileEntities = compound.getCompoundTag(LEVEL_TAG).getTagList(TILE_ENTITIES_TAG,
                Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tileEntities.tagCount(); i++) {
            NBTTagCompound tileEntity = tileEntities.getCompoundTagAt(i);
            fixCovers(tileEntity);
            if (tileEntity.hasKey(META_TILE_ENTITY, Constants.NBT.TAG_COMPOUND)) {
                fixCovers(tileEntity.getCompoundTag(META_TILE_ENTITY));
            }
        }
        return compound;
    }

    /**
     * @param holder the tag of a machine or pipe, whose covers are fixed in place
     */
    public void fixCovers(@NotNull NBTTagCompound holder) {
        NBTTagList covers = holder.getTagList(COVERS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < covers.tagCount(); i++) {
            NBTTagCompound cover = covers.getCompoundTagAt(i);
            if (cover.hasKey(COVER_ID, Constants.NBT.TAG_STRING)) {
                coverFixer.accept(new ResourceLocation(cover.getString(COVER_ID)), cover);
            }
        }
    }
}
