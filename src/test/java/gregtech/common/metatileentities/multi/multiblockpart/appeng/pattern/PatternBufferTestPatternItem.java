package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

final class PatternBufferTestPatternItem extends Item implements ICraftingPatternItem {

    private static int nextId;
    IAEItemStack[] full = new IAEItemStack[16];
    IAEItemStack[] condensed = { AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT)) };
    IAEItemStack[] outputs = { AEItemStack.fromItemStack(new ItemStack(Items.DIAMOND)) };
    boolean crafting;
    boolean substitute;
    boolean differentIdentity;
    int decodes;

    PatternBufferTestPatternItem() {
        setRegistryName("gregtech", "a6_test_pattern_" + nextId++);
        ForgeRegistries.ITEMS.register(this);
        full[0] = condensed[0];
    }

    @Override
    public ICraftingPatternDetails getPatternForItem(ItemStack stack, World world) {
        decodes++;
        return new ICraftingPatternDetails() {

            @Override
            public ItemStack getPattern() {
                return differentIdentity ? new ItemStack(Items.PAPER) : stack;
            }

            @Override
            public boolean isValidItemForSlot(int slot, ItemStack item, World environment) {
                return false;
            }

            @Override
            public boolean isCraftable() {
                return crafting;
            }

            @Override
            public boolean canSubstitute() {
                return substitute;
            }

            @Override
            public IAEItemStack[] getInputs() {
                return full;
            }

            @Override
            public IAEItemStack[] getCondensedInputs() {
                return condensed;
            }

            @Override
            public IAEItemStack[] getOutputs() {
                return outputs;
            }

            @Override
            public IAEItemStack[] getCondensedOutputs() {
                return outputs;
            }

            @Override
            public ItemStack getOutput(InventoryCrafting table, World environment) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getPriority() {
                return 0;
            }

            @Override
            public void setPriority(int value) {}
        };
    }
}
