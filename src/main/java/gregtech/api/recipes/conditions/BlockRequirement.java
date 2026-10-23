package gregtech.api.recipes.conditions;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/** One block registry identity, optionally restricted to its 1.12.2 metadata value. */
public final class BlockRequirement {

    private final ResourceLocation block;
    private final int metadata;

    public BlockRequirement(String block) {
        this(block, -1);
    }

    public BlockRequirement(String block, int metadata) {
        this.block = RecipeConditions.registryName(block);
        if (metadata < -1 || metadata > 15) throw new IllegalArgumentException("Block metadata must be -1 or 0..15");
        this.metadata = metadata;
    }

    public ResourceLocation block() {
        return block;
    }

    public int metadata() {
        return metadata;
    }

    public boolean isRegistered() {
        return Block.REGISTRY.containsKey(block);
    }

    public boolean matches(IBlockState state) {
        return block.equals(state.getBlock().getRegistryName()) &&
                (metadata == -1 || state.getBlock().getMetaFromState(state) == metadata);
    }

    NBTTagCompound serialize() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Block", block.toString());
        tag.setInteger("Metadata", metadata);
        return tag;
    }

    static BlockRequirement deserialize(NBTTagCompound tag) {
        ConditionPayloads.requireKeys(tag, "Block", "Metadata");
        ConditionPayloads.requireTag(tag, "Block", 8);
        ConditionPayloads.requireTag(tag, "Metadata", 3);
        return new BlockRequirement(tag.getString("Block"), tag.getInteger("Metadata"));
    }

    @Override
    public String toString() {
        return block + (metadata < 0 ? "" : "@" + metadata);
    }
}
