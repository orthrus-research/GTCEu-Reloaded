package gregtech.api.capability.impl;

import gregtech.api.GTValues;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.common.ConfigHolder;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * The effects of steam vented from a side of a machine, shared by {@link RecipeLogicSteam} and
 * {@link SteamMultiblockRecipeLogic}. Only the server calls it.
 */
final class SteamVenting {

    private SteamVenting() {}

    /**
     * Steam leaves through a block without a collision box, and melts snow by a layer, a snow block to seven layers,
     * to get through; it vents only through those.
     *
     * @return whether steam can leave through the block at the position
     */
    static boolean clearVent(World world, BlockPos ventPos) {
        IBlockState state = world.getBlockState(ventPos);
        return state.getCollisionBoundingBox(world, ventPos) == Block.NULL_AABB ||
                GTUtility.tryBreakSnow(world, ventPos, state, false);
    }

    /**
     * Shows a cloud of steam leaving the machine through the side, and plays its hiss unless machine sounds are off
     * or the machine is muffled.
     */
    static void playEffects(MetaTileEntity machine, BlockPos machinePos, EnumFacing side) {
        WorldServer world = (WorldServer) machine.getWorld();
        Vec3d position = getVentPosition(machinePos, side);

        world.spawnParticle(EnumParticleTypes.CLOUD, position.x, position.y, position.z,
                getCloudSize(),
                side.getXOffset() / 2.0,
                side.getYOffset() / 2.0,
                side.getZOffset() / 2.0, 0.1);
        if (ConfigHolder.machines.machineSounds && !machine.isMuffled()) {
            world.playSound(null, position.x, position.y, position.z, SoundEvents.BLOCK_LAVA_EXTINGUISH,
                    SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
    }

    /** @return where the steam leaves the machine: just outside the middle of the side */
    static Vec3d getVentPosition(BlockPos machinePos, EnumFacing side) {
        return new Vec3d(machinePos.getX() + 0.5 + side.getXOffset() * 0.6,
                machinePos.getY() + 0.5 + side.getYOffset() * 0.6,
                machinePos.getZ() + 0.5 + side.getZOffset() * 0.6);
    }

    /**
     * @return the number of cloud particles, 7 to 9, drawn from {@link GTValues#RNG} so cosmetic effects leave the
     *         world's own random alone
     */
    static int getCloudSize() {
        return 7 + GTValues.RNG.nextInt(3);
    }
}
