package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import java.util.ArrayList;
import java.util.List;

import static gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferCodec.*;

/** An entirely owned, validated table snapshot; constructing one never changes a live handler. */
final class PatternBufferStaging {

    final List<ItemStack> items = new ArrayList<>();
    final List<FluidStack> fluids = new ArrayList<>();
    private final List<ItemStack> original = new ArrayList<>();

    static PatternBufferStaging table(ICraftingPatternDetails pattern, InventoryCrafting table, boolean bridge) {
        // Both pinned CPU implementations inherit vanilla's direct, callback-free clear(). Unknown overrides
        // cannot participate in a handoff that commits the owned ledger before clearing the delivered table.
        require(table != null && (table.getClass() == InventoryCrafting.class || (bridge &&
                table.getClass().getName().equals("com.glodblock.github.inventory.FluidConvertingInventoryCrafting") &&
                table.getClass().getSuperclass() == InventoryCrafting.class)), "Unsupported processing table");
        require(table.getSizeInventory() <= MAX_ENTRIES, "Processing table too large");
        PatternBufferStaging expected = requirements(pattern.getCondensedInputs(), bridge);
        PatternBufferStaging actual = new PatternBufferStaging();
        for (int i = 0; i < table.getSizeInventory(); i++) {
            ItemStack stack = table.getStackInSlot(i).copy();
            actual.original.add(stack);
            if (!stack.isEmpty()) actual.add(stack, bridge);
        }
        require(actual.sameContents(expected), "Delivered processing inputs differ from pattern");
        return actual;
    }

    static PatternBufferStaging requirements(IAEItemStack[] inputs, boolean bridge) {
        return requirements(inputs, bridge, false);
    }

    static PatternBufferStaging requirements(IAEItemStack[] inputs, boolean bridge, boolean allowEmptySlots) {
        require(inputs != null && inputs.length > 0 && inputs.length <= MAX_ENTRIES, "Invalid pattern entry count");
        PatternBufferStaging result = new PatternBufferStaging();
        for (IAEItemStack input : inputs) {
            if (input == null && allowEmptySlots) continue;
            require(input != null && input.getStackSize() > 0 && input.getStackSize() <= Integer.MAX_VALUE,
                    "Invalid pattern amount");
            ItemStack stack = input.createItemStack().copy();
            require(!stack.isEmpty(), "Invalid pattern item");
            stack.setCount((int) input.getStackSize());
            result.add(stack, bridge);
        }
        require(!result.items.isEmpty() || !result.fluids.isEmpty(), "Empty pattern requirements");
        return result;
    }

    boolean unchanged(InventoryCrafting table) {
        if (table.getSizeInventory() != original.size()) return false;
        for (int i = 0; i < original.size(); i++)
            if (!ItemStack.areItemStacksEqual(original.get(i), table.getStackInSlot(i))) return false;
        return true;
    }

    private void add(ItemStack stack, boolean bridge) {
        require(stack.getCount() > 0 && fits(writeItem(stack), MAX_BATCH_BYTES), "Invalid or oversized input");
        FluidStack fluid = PatternBufferTokens.fluid(stack, bridge);
        if (fluid != null) {
            for (FluidStack existing : fluids) {
                if (existing.isFluidEqual(fluid)) {
                    require((long) existing.amount + fluid.amount <= Integer.MAX_VALUE, "Fluid sum overflow");
                    existing.amount += fluid.amount;
                    return;
                }
            }
            fluids.add(fluid.copy());
        } else {
            for (ItemStack existing : items) {
                if (sameItem(existing, stack)) {
                    require((long) existing.getCount() + stack.getCount() <= Integer.MAX_VALUE, "Item sum overflow");
                    existing.grow(stack.getCount());
                    return;
                }
            }
            items.add(stack.copy());
        }
    }

    boolean sameContents(PatternBufferStaging other) {
        if (items.size() != other.items.size() || fluids.size() != other.fluids.size()) return false;
        for (ItemStack item : items) {
            boolean found = false;
            for (ItemStack expected : other.items)
                if (ItemStack.areItemStacksEqual(item, expected)) {
                    found = true;
                    break;
                }
            if (!found) return false;
        }
        for (FluidStack fluid : fluids) {
            boolean found = false;
            for (FluidStack expected : other.fluids)
                if (fluid.amount == expected.amount && fluid.isFluidEqual(expected)) {
                    found = true;
                    break;
                }
            if (!found) return false;
        }
        return true;
    }

    static boolean sameItem(ItemStack first, ItemStack second) {
        ItemStack a = first.copy(), b = second.copy();
        a.setCount(1);
        b.setCount(1);
        return ItemStack.areItemStacksEqual(a, b);
    }
}
