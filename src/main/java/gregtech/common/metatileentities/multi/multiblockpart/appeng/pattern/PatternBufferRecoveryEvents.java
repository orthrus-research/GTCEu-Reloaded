package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import gregtech.api.util.GTUtility;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEPatternBuffer;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Registered only with the AE parts. Oversized quarantine is retained in world NBT for explicit repair. */
public final class PatternBufferRecoveryEvents {

    private PatternBufferRecoveryEvents() {}

    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (blocked(event.getWorld(), event.getPos())) {
            event.setCanceled(true);
            event.getPlayer()
                    .sendStatusMessage(new TextComponentTranslation("gregtech.pattern_buffer.recovery_blocked"), true);
        }
    }

    @SubscribeEvent
    public static void explode(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> blocked(event.getWorld(), pos));
    }

    private static boolean blocked(World world, BlockPos pos) {
        if (!world.isBlockLoaded(pos)) return false;
        Object tile = GTUtility.getMetaTileEntity(world, pos);
        return tile instanceof MetaTileEntityMEPatternBuffer &&
                ((MetaTileEntityMEPatternBuffer) tile).isRecoveryBlocked();
    }
}
