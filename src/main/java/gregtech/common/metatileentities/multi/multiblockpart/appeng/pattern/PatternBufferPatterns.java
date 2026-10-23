package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import static gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferCodec.*;

public final class PatternBufferPatterns {

    private PatternBufferPatterns() {}

    public static ICraftingPatternDetails decode(ItemStack stack, World world, boolean bridge) {
        try {
            int inputLimit = bridge ? MAX_ENTRIES : 16;
            require(!stack.isEmpty() && stack.getCount() == 1 && stack.getItem() instanceof ICraftingPatternItem &&
                    fits(writeItem(stack), MAX_STACK_BYTES), "Invalid pattern stack");
            ResourceLocation identity = stack.getItem().getRegistryName();
            if (identity != null && "ae2fc".equals(identity.getNamespace())) {
                require(bridge && ("dense_encoded_pattern".equals(identity.getPath()) ||
                        "large_item_encoded_pattern".equals(identity.getPath())), "Unsupported FC pattern");
            }
            if (stack.hasTagCompound()) {
                NBTTagCompound tag = stack.getTagCompound();
                for (String key : new String[] { "crafting", "substitute" }) {
                    if (tag.hasKey(key)) require(tag.hasKey(key, 1) && (tag.getByte(key) == 0 || tag.getByte(key) == 1),
                            "Malformed pattern mode");
                }
                // Do not inherit upstream decoders' silent truncation or malformed-list defaults.
                for (String key : new String[] { "Inputs", "Outputs", "in", "out" }) {
                    if (tag.hasKey(key)) {
                        boolean input = "Inputs".equals(key) || "in".equals(key);
                        NBTTagList entries = list(tag, key, input ? inputLimit : MAX_ENTRIES);
                        require(entries.tagCount() > 0, "Empty pattern list");
                    }
                }
            }
            ICraftingPatternDetails details = ((ICraftingPatternItem) stack.getItem()).getPatternForItem(stack.copy(),
                    world);
            require(details != null && !details.isCraftable() && !details.canSubstitute(),
                    "Not an exact processing pattern");
            require(ItemStack.areItemStacksEqual(details.getPattern(), stack), "Mismatched pattern identity");
            // The CPU indexes the full input array into its table before calling this medium. Reject oversized
            // advertisements here: a later push rejection cannot recover an already out-of-range CPU table write.
            IAEItemStack[] inputs = details.getInputs();
            require(inputs != null && inputs.length <= inputLimit, "Processing inputs exceed CPU table capacity");
            // AE's ordinary 4x4 processing input array includes null empty slots; condensed arrays do not.
            require(PatternBufferStaging.requirements(inputs, bridge, true).sameContents(
                    PatternBufferStaging.requirements(details.getCondensedInputs(), bridge)),
                    "Inconsistent pattern inputs");
            require(PatternBufferStaging.requirements(details.getOutputs(), bridge, true).sameContents(
                    PatternBufferStaging.requirements(details.getCondensedOutputs(), bridge)),
                    "Inconsistent pattern outputs");
            return details;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
