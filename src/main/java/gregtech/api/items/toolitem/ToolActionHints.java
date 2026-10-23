package gregtech.api.items.toolitem;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IControllable;
import gregtech.api.cover.Cover;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.SimpleMachineMetaTileEntity;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class ToolActionHints {

    private ToolActionHints() {}

    public static @Nullable ToolAction forMainHand(MetaTileEntity machine, ItemStack stack, boolean sneaking,
                                                   EnumFacing side) {
        if (stack.isEmpty()) return null;
        return machine.getToolActionHint(stack.getItem().getToolClasses(stack), sneaking, side);
    }

    public static boolean hasAnyAction(MetaTileEntity machine, ItemStack stack, boolean sneaking) {
        for (EnumFacing side : EnumFacing.VALUES) {
            if (forMainHand(machine, stack, sneaking, side) != null) return true;
        }
        return false;
    }

    /**
     * Describes the standard simple-machine handlers without invoking them. Subclasses which change a handler
     * must supply their own hints instead of inheriting this assumption.
     */
    public static @Nullable ToolAction forSimpleMachine(SimpleMachineMetaTileEntity machine, Set<String> toolClasses,
                                                        boolean sneaking, EnumFacing side) {
        if (side == null) return null;
        Cover cover = machine.getCoverAtSide(side);

        // Keep the same precedence as MetaTileEntity.onToolClick, including multi-purpose tools.
        if (toolClasses.contains(ToolClasses.SCREWDRIVER)) {
            // An unknown cover might consume the click; do not guess the machine fallback.
            if (cover != null) return cover.getToolActionHint(toolClasses, sneaking);
            // These permissions are not synchronized to the client, so this is deliberately a neutral toggle.
            return ToolAction.TOGGLE_INPUT;
        }
        if (toolClasses.contains(ToolClasses.SOFT_MALLET)) {
            if (cover != null) return cover.getToolActionHint(toolClasses, sneaking);
            IControllable controllable = machine.getCapability(GregtechTileCapabilities.CAPABILITY_CONTROLLABLE, null);
            if (controllable == null) return null;
            return controllable.isWorkingEnabled() ? ToolAction.DISABLE_WORKING : ToolAction.ENABLE_WORKING;
        }
        if (toolClasses.contains(ToolClasses.WRENCH)) {
            if (!sneaking) {
                if (side == machine.getOutputFacing() ||
                        (machine.hasFrontFacing() && side == machine.getFrontFacing())) {
                    return null;
                }
                return ToolAction.ROTATE_OUTPUT;
            }
            return machine.hasFrontFacing() && side != machine.getFrontFacing() && machine.isValidFrontFacing(side) ?
                    ToolAction.ROTATE_FRONT : null;
        }
        if (toolClasses.contains(ToolClasses.CROWBAR)) {
            return cover == null ? null : ToolAction.REMOVE_COVER;
        }
        if (toolClasses.contains(ToolClasses.HARD_HAMMER)) {
            return machine.isMuffled() ? ToolAction.UNMUFFLE : ToolAction.MUFFLE;
        }
        return null;
    }
}
