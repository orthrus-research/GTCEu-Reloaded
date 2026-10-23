package gregtech.api.items.toolitem;

import gregtech.api.util.GTUtility;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;

/** Coordinates for icons inside the existing wrench grid; its lines and connection markers are unchanged. */
public final class ToolGrid {

    private ToolGrid() {}

    public static Vec3d pointOnFace(EnumFacing face, double right, double up, double outward) {
        return switch (face) {
            case SOUTH -> new Vec3d(0.5 + right, 0.5 + up, 0.5 + outward);
            case NORTH -> new Vec3d(0.5 - right, 0.5 + up, 0.5 - outward);
            case WEST -> new Vec3d(0.5 - outward, 0.5 + up, 0.5 + right);
            case EAST -> new Vec3d(0.5 + outward, 0.5 + up, 0.5 - right);
            case UP -> new Vec3d(0.5 + right, 0.5 + outward, 0.5 - up);
            case DOWN -> new Vec3d(0.5 + right, 0.5 - outward, 0.5 + up);
        };
    }

    public static EnumFacing sideAtCell(EnumFacing face, int column, int row) {
        if (column < -1 || column > 1 || row < -1 || row > 1) {
            throw new IllegalArgumentException("Grid cell must be between -1 and 1");
        }
        Vec3d point = pointOnFace(face, column * 0.375, row * 0.375, 0.5);
        return GTUtility.determineWrenchingSide(face, (float) point.x, (float) point.y, (float) point.z);
    }
}
