package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import gregtech.api.util.GTUtility;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEPatternBuffer;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

/** Loaded-only target identity; never contains a material ledger or loads a dimension/chunk. */
public final class PatternBufferLink {

    private final int dimension;
    private final BlockPos position;
    private final UUID identity;
    private final UUID incarnation;
    private final UUID owner;

    private PatternBufferLink(int dimension, BlockPos position, UUID identity, UUID incarnation, UUID owner) {
        this.dimension = dimension;
        this.position = position.toImmutable();
        this.identity = identity;
        this.incarnation = incarnation;
        this.owner = owner;
    }

    public static PatternBufferLink capture(MetaTileEntityMEPatternBuffer buffer) {
        PatternBufferCodec.require(buffer.getOwner() != null, "Unowned pattern buffer");
        return new PatternBufferLink(buffer.getWorld().provider.getDimension(), buffer.getPos(),
                buffer.getPatternStorage().getIdentity(), buffer.getPatternStorage().getIncarnation(),
                buffer.getOwner());
    }

    public static PatternBufferLink read(NBTTagCompound tag) {
        if (!tag.hasKey("Version", 3) || tag.getInteger("Version") != 1 || !tag.hasKey("Dimension", 3) ||
                !tag.hasKey("Position", 4) || !tag.hasUniqueId("Identity") || !tag.hasUniqueId("Incarnation") ||
                !tag.hasUniqueId("Owner"))
            return null;
        BlockPos pos = BlockPos.fromLong(tag.getLong("Position"));
        if (pos.getY() < 0 || pos.getY() >= 256) return null;
        return new PatternBufferLink(tag.getInteger("Dimension"), pos, tag.getUniqueId("Identity"),
                tag.getUniqueId("Incarnation"), tag.getUniqueId("Owner"));
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Version", 1);
        tag.setInteger("Dimension", dimension);
        tag.setLong("Position", position.toLong());
        tag.setUniqueId("Identity", identity);
        tag.setUniqueId("Incarnation", incarnation);
        tag.setUniqueId("Owner", owner);
        return tag;
    }

    public MetaTileEntityMEPatternBuffer resolve(World world, UUID proxyOwner) {
        if (world == null || world.provider.getDimension() != dimension || !owner.equals(proxyOwner) ||
                !world.isBlockLoaded(position))
            return null;
        Object target = GTUtility.getMetaTileEntity(world, position);
        if (!(target instanceof MetaTileEntityMEPatternBuffer)) return null;
        MetaTileEntityMEPatternBuffer buffer = (MetaTileEntityMEPatternBuffer) target;
        if (!owner.equals(buffer.getOwner()) || !identity.equals(buffer.getPatternStorage().getIdentity()) ||
                !incarnation.equals(buffer.getPatternStorage().getIncarnation()) || !buffer.inputsAvailable())
            return null;
        return buffer;
    }

    public String describe() {
        return dimension + ": " + position.getX() + ", " + position.getY() + ", " + position.getZ();
    }
}
