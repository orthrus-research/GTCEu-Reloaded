package gregtech.api.worldgen.shape;

import gregtech.api.worldgen.config.OreConfigUtils;

import net.minecraft.util.math.Vec3i;

import com.google.gson.JsonObject;

import java.util.Random;

public class EllipsoidGenerator extends ShapeGenerator {

    private int radiusMin;
    private int radiusMax;

    public EllipsoidGenerator() {}

    @Override
    public void loadFromConfig(JsonObject object) {
        int[] data = OreConfigUtils.getIntRange(object.get("radius"));
        this.radiusMin = data[0];
        this.radiusMax = data[1];
    }

    public int getYRadius() {
        return Integer.MAX_VALUE;
    }

    @Override
    public Vec3i getMaxSize() {
        return new Vec3i(radiusMax * 2, radiusMax, radiusMax * 2);
    }

    @Override
    public void generate(Random gridRandom, IBlockGeneratorAccess blockAccess) {
        int a = radiusMin == radiusMax ? radiusMax : (gridRandom.nextInt(radiusMax - radiusMin) + radiusMin);
        int b = radiusMin == radiusMax ? radiusMax / 2 : (gridRandom.nextInt(radiusMax - radiusMin) + radiusMin) / 2;
        int c = radiusMin == radiusMax ? radiusMax : (gridRandom.nextInt(radiusMax - radiusMin) + radiusMin);
        // a^2 b^2 c^2 passes Integer.MAX_VALUE from radii of about 46, so the products are long
        long ab2 = (long) a * a * b * b, ac2 = (long) a * a * c * c, bc2 = (long) b * b * c * c, abc2 = ab2 * c * c;

        int max = Math.max(a, Math.max(b, c));
        int yMax = Math.min(max, getYRadius());
        for (int x = -max; x <= max; x++) {
            long xr = bc2 * x * x;
            if (xr > abc2) continue;
            for (int y = -yMax; y <= yMax; y++) {
                long yr = xr + ac2 * y * y + ab2;
                if (yr > abc2) continue;
                for (int z = -max; z <= max; z++) {
                    long zr = yr + ab2 * z * z;
                    if (zr > abc2) continue;
                    generateBlock(x, y, z, blockAccess);
                }
            }
        }
    }

    // Used for overriding
    public void generateBlock(int x, int y, int z, IBlockGeneratorAccess blockAccess) {
        blockAccess.generateBlock(x, y, z);
    }
}
