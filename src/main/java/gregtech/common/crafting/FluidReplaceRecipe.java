package gregtech.common.crafting;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.UniversalBucket;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import org.jetbrains.annotations.NotNull;

/**
 * A recipe which inputs a single Fluid Container, and outputs the same Fluid Container with a new contained Fluid
 */
public class FluidReplaceRecipe extends GTShapedOreRecipe {

    public FluidReplaceRecipe(boolean isClearing, ResourceLocation group, @NotNull ItemStack result, Object... recipe) {
        super(isClearing, group, result, recipe);
    }

    @NotNull
    @Override
    public NonNullList<ItemStack> getRemainingItems(@NotNull InventoryCrafting inv) {
        if (isClearing) {
            return NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
        } else {
            // modified behavior from net.minecraftforge.common.ForgeHooks.defaultRecipeGetRemainingItems(inv)
            NonNullList<ItemStack> ret = NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
            for (int i = 0; i < ret.size(); i++) {
                ItemStack stack = inv.getStackInSlot(i);
                // fluid containers are always consumed
                if (stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
                    ret.set(i, ItemStack.EMPTY);
                } else ret.set(i, ForgeHooks.getContainerItem(stack));
            }
            return ret;
        }
    }

    @NotNull
    @Override
    public ItemStack getCraftingResult(@NotNull InventoryCrafting inv) {
        IFluidHandlerItem recipeCap = output.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        if (recipeCap == null)
            throw new IllegalStateException("FluidReplaceRecipe output did not have an IFluidHandlerItem capability");

        FluidStack outputFluid = recipeCap.drain(Integer.MAX_VALUE, false);
        if (outputFluid == null) throw new IllegalStateException("FluidReplaceRecipe output did not have a fluid");
        if (outputFluid.amount != 1000)
            throw new IllegalStateException("FluidReplaceRecipe output must have exactly 1000mB of fluid");

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack input = inv.getStackInSlot(i);
            IFluidHandlerItem inputCap = input.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY,
                    null);
            if (inputCap == null) continue;

            // if the input has a fluid, it must only hold 1000mB
            FluidStack inputFluid = inputCap.drain(Integer.MAX_VALUE, false);
            if (inputFluid != null && inputFluid.amount != 1000) continue;

            FluidStack drained = inputCap.drain(1000, false);
            if (drained == null || drained.amount != 1000) return ItemStack.EMPTY;

            // Native bucket resolution also handles water, lava and milk without a universal-bucket entry.
            if (isBucket(input.getItem())) return FluidUtil.getFilledBucket(outputFluid.copy());

            ItemStack output = input.copy(); // copy the input to preserve its NBT
            IFluidHandlerItem outputCap = output.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY,
                    null);
            if (outputCap == null) return ItemStack.EMPTY;
            outputCap.drain(Integer.MAX_VALUE, true); // ensure the output is empty

            return outputCap.fill(outputFluid.copy(), true) == 1000 ? output : ItemStack.EMPTY;
        }

        return ItemStack.EMPTY;
    }

    private static boolean isBucket(@NotNull Item item) {
        return item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET || item == Items.MILK_BUCKET ||
                item instanceof UniversalBucket;
    }
}
