package gregtech.api.worldgen.shape;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;

public class EllipsoidGeneratorTest {

    private static int countBlocks(int radius) {
        JsonArray range = new JsonArray();
        range.add(radius);
        range.add(radius);
        JsonObject config = new JsonObject();
        config.add("radius", range);
        EllipsoidGenerator generator = new EllipsoidGenerator();
        generator.loadFromConfig(config);

        int[] count = { 0 };
        generator.generate(new Random(1), (x, y, z, withRandom) -> {
            count[0]++;
            return true;
        });
        return count[0];
    }

    @Test
    public void Test_Large_Radius_Fills_Its_Volume() {
        // radii 50, 25, 50: an ellipsoid of about 4/3 * pi * 50 * 25 * 50 = 261,800 blocks
        int blocks = countBlocks(50);
        assertThat(blocks, greaterThan(200_000));
        assertThat(blocks, lessThan(300_000));
    }

    @Test
    public void Test_Small_Radius_Is_Unchanged_In_Scale() {
        // radii 10, 5, 10: about 2,094 blocks
        int blocks = countBlocks(10);
        assertThat(blocks, greaterThan(1_200));
        assertThat(blocks, lessThan(2_400));
    }
}
