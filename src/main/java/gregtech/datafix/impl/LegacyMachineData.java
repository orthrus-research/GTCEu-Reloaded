package gregtech.datafix.impl;

import gregtech.common.metatileentities.multi.electric.MetaTileEntityPowerSubstation;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.NotNull;

/**
 * Rewrites machine NBT saved in formats that machines used to convert each time they loaded. Each method takes the
 * machine's {@code MetaTileEntity} tag and changes it in place; a tag already in the current format is left alone.
 */
public final class LegacyMachineData {

    private static final String SLOT = "Slot";
    private static final String ITEMS = "Items";
    private static final String SIZE = "Size";
    private static final String COUNT = "Count";
    private static final String IMPORT_INVENTORY = "ImportInventory";
    private static final String EXPORT_INVENTORY = "ExportInventory";

    private LegacyMachineData() {}

    /**
     * Before GTCEu 2.2.2 (upstream #933), quantum tanks and fluid, steam and pump hatches kept their fluid container
     * slots in one {@code ContainerInventory}, input at slot 0 and output at slot 1. They are the first slots of the
     * machine's import and export inventories now.
     */
    public static void tankContainerInventory(@NotNull NBTTagCompound machine) {
        if (!machine.hasKey("ContainerInventory", Constants.NBT.TAG_COMPOUND)) return;
        NBTTagList items = machine.getCompoundTag("ContainerInventory").getTagList(ITEMS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound item = items.getCompoundTagAt(i);
            int slot = item.getInteger(SLOT);
            if (slot == 0) {
                putFirstSlot(machine, IMPORT_INVENTORY, item);
            } else if (slot == 1) {
                putFirstSlot(machine, EXPORT_INVENTORY, item);
            }
        }
        machine.removeTag("ContainerInventory");
    }

    /** Puts an item in slot 0 of an inventory saved as a slot list, replacing what that slot held. */
    private static void putFirstSlot(@NotNull NBTTagCompound machine, @NotNull String key,
                                     @NotNull NBTTagCompound item) {
        NBTTagList saved = machine.getTagList(key, Constants.NBT.TAG_COMPOUND);
        NBTTagList slots = new NBTTagList();
        for (int i = 0; i < saved.tagCount(); i++) {
            if (saved.getCompoundTagAt(i).getInteger(SLOT) != 0) slots.appendTag(saved.getCompoundTagAt(i));
        }
        NBTTagCompound first = item.copy();
        first.setInteger(SLOT, 0);
        slots.appendTag(first);
        machine.setTag(key, slots);
    }

    /**
     * Before GTCEu 2.6.0 (upstream #1610), a quantum tank saved partial voiding separately. Voiding modes were merged,
     * and a tank with partial voiding on loads with voiding on.
     */
    public static void quantumPartialVoiding(@NotNull NBTTagCompound machine) {
        if (!machine.hasKey("IsPartialVoiding")) return;
        if (machine.getBoolean("IsPartialVoiding")) machine.setBoolean("IsVoiding", true);
        machine.removeTag("IsPartialVoiding");
    }

    /**
     * Before upstream #2554, the creative chest kept its item in an {@code ItemStackHandler}. It now keeps a single
     * item the way quantum chests keep their contents.
     */
    public static void creativeChestItem(@NotNull NBTTagCompound machine) {
        if (!machine.hasKey("ItemStackHandler", Constants.NBT.TAG_COMPOUND)) return;
        NBTTagCompound handler = machine.getCompoundTag("ItemStackHandler");
        machine.removeTag("ItemStackHandler");
        // As before, a chest that already holds an item in the current format keeps it.
        if (machine.hasKey("ItemStack", Constants.NBT.TAG_COMPOUND)) return;
        NBTTagList items = handler.getTagList(ITEMS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound item = items.getCompoundTagAt(i);
            if (item.getInteger(SLOT) != 0) continue;
            NBTTagCompound stack = item.copy();
            stack.removeTag(SLOT);
            stack.setByte(COUNT, (byte) 1);
            machine.setTag("ItemStack", stack);
            machine.setLong("ItemAmount", 1L);
            return;
        }
    }

    /**
     * Before upstream #2753, the power substation saved a {@code Stored} and {@code Max} pair per battery under
     * their index, with their count in {@code Size}. It saves the total stored energy as two longs (high bits first)
     * and its passive drain now; the capacity is rebuilt from the batteries when the structure forms.
     */
    public static void powerSubstationBank(@NotNull NBTTagCompound machine) {
        if (!machine.hasKey("EnergyBank", Constants.NBT.TAG_COMPOUND)) return;
        NBTTagCompound legacy = machine.getCompoundTag("EnergyBank");
        if (!legacy.hasKey(SIZE, Constants.NBT.TAG_INT)) return;

        long[] stored = new long[2];
        long drain = 0;
        long drainMod = 0;
        int size = legacy.getInteger(SIZE);
        for (int i = 0; i < size; i++) {
            NBTTagCompound battery = legacy.getCompoundTag(String.valueOf(i));
            if (battery.hasKey("Stored")) {
                // the representation the bank keeps: stored[0] << 63 | stored[1]
                stored[1] += battery.getLong("Stored");
                if (stored[1] < 0) {
                    stored[0]++;
                    stored[1] -= Long.MIN_VALUE;
                }
            }
            long capacity = battery.getLong("Max");
            if (capacity / MetaTileEntityPowerSubstation.PASSIVE_DRAIN_DIVISOR >=
                    MetaTileEntityPowerSubstation.PASSIVE_DRAIN_MAX_PER_STORAGE) {
                drain += MetaTileEntityPowerSubstation.PASSIVE_DRAIN_MAX_PER_STORAGE;
            } else {
                drain += capacity / MetaTileEntityPowerSubstation.PASSIVE_DRAIN_DIVISOR;
                drainMod += capacity % MetaTileEntityPowerSubstation.PASSIVE_DRAIN_DIVISOR;
                if (drainMod >= MetaTileEntityPowerSubstation.PASSIVE_DRAIN_DIVISOR) {
                    drain++;
                    drainMod -= MetaTileEntityPowerSubstation.PASSIVE_DRAIN_DIVISOR;
                }
            }
        }

        NBTTagCompound bank = new NBTTagCompound();
        bank.setLong("Stored0", stored[0]);
        bank.setLong("Stored1", stored[1]);
        bank.setLong("drain", drain);
        bank.setLong("drainMod", drainMod);
        machine.setTag("EnergyBank", bank);
    }

    /**
     * Before GTCEu 2.5.4 (upstream #1552), a maintenance hatch kept its tape in its import inventory, which machines
     * save as a slot list. Its tape has its own inventory now, saved since 2.6.0 (upstream #1662), so a hatch without
     * one gets the tape its import inventory held.
     */
    public static void maintenanceTape(@NotNull NBTTagCompound machine) {
        if (machine.hasKey("tapeInventory", Constants.NBT.TAG_COMPOUND) ||
                !machine.hasKey(IMPORT_INVENTORY, Constants.NBT.TAG_LIST)) {
            return;
        }
        NBTTagList imported = machine.getTagList(IMPORT_INVENTORY, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < imported.tagCount(); i++) {
            NBTTagCompound item = imported.getCompoundTagAt(i);
            if (item.getInteger(SLOT) != 0) continue;
            NBTTagList tape = new NBTTagList();
            tape.appendTag(item.copy());
            NBTTagCompound inventory = new NBTTagCompound();
            inventory.setTag(ITEMS, tape);
            inventory.setInteger(SIZE, 1);
            machine.setTag("tapeInventory", inventory);
            machine.setTag(IMPORT_INVENTORY, new NBTTagList());
            return;
        }
    }
}
