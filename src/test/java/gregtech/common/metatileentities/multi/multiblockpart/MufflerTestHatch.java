package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

final class MufflerTestHatch extends MetaTileEntityMufflerHatch {

    final World world;
    final MetaTileEntityHolder tile;
    final List<Boolean> updates = new ArrayList<>();
    long tick;

    MufflerTestHatch(World world) {
        super(new ResourceLocation("gregtech", "muffler_test"), 1);
        this.world = world;
        this.tile = new MetaTileEntityHolder() {

            {
                setRawMetaTileEntity(MufflerTestHatch.this);
                setWorld(MufflerTestHatch.this.world);
                setPos(BlockPos.ORIGIN);
            }
        };
    }

    MufflerTestHatch() {
        this(new DummyWorld());
    }

    @Override
    public World getWorld() {
        return world;
    }

    @Override
    public BlockPos getPos() {
        return BlockPos.ORIGIN;
    }

    @Override
    public long getOffsetTimer() {
        return tick;
    }

    @Override
    public void update() {
        super.update();
        var packet = tile.getUpdatePacket();
        if (packet == null) return;
        for (var entry : packet.getNbtCompound().getTagList("d", 10)) {
            var compound = (NBTTagCompound) entry;
            String key = Integer.toString(GregtechDataCodes.MUFFLER_OBSTRUCTED);
            if (compound.hasKey(key)) updates.add(compound.getByteArray(key)[0] != 0);
        }
    }
}
