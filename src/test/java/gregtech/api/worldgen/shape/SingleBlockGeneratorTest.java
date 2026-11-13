package gregtech.api.worldgen.shape;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

public class SingleBlockGeneratorTest {

    @Test
    public void Test_Walk_Leaves_The_Origin() {
        JsonArray range = new JsonArray();
        range.add(8);
        range.add(8);
        JsonObject config = new JsonObject();
        config.add("blocks_count", range);
        SingleBlockGenerator generator = new SingleBlockGenerator();
        generator.loadFromConfig(config);

        Set<List<Integer>> positions = new HashSet<>();
        generator.generate(new Random(7), (x, y, z, withRandom) -> positions.add(List.of(x, y, z)));

        // a random walk that never steps back places its blocks at more than one position
        assertThat(positions.size(), greaterThan(1));
    }
}
