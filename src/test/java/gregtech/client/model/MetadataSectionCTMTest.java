package gregtech.client.model;

import gregtech.client.model.customtexture.MetadataSectionCTM;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;

public class MetadataSectionCTMTest {

    private static MetadataSectionCTM parse(String json) {
        JsonObject object = new JsonParser().parse(json).getAsJsonObject();
        return MetadataSectionCTM.fromJson(object);
    }

    @Test
    public void lightObjectSetsBlockAndSkyLightSeparately() {
        MetadataSectionCTM meta = parse("{\"gregtech\":true,\"extra\":{\"light\":{\"block\":15,\"sky\":3}}}");
        MatcherAssert.assertThat(meta.blockLight, is(15));
        MatcherAssert.assertThat(meta.skyLight, is(3));
    }

    @Test
    public void lightNumberSetsBoth() {
        MetadataSectionCTM meta = parse("{\"gregtech\":true,\"extra\":{\"light\":7}}");
        MatcherAssert.assertThat(meta.blockLight, is(7));
        MatcherAssert.assertThat(meta.skyLight, is(7));
    }
}
