package gregtech.mixins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MixinConfigurationTest {

    @Test
    void everyMixinConfigRequiresItsInjectionTargets() throws Exception {
        Path resources = Path.of(GregTechMixinConnector.class.getResource("/mixins.gregtech.forge.json").toURI())
                .getParent();
        List<Path> configs;
        try (Stream<Path> files = Files.list(resources)) {
            configs = files.filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith("mixins.gregtech.") && name.endsWith(".json") && !name.contains("refmap");
            }).sorted().toList();
        }
        assertFalse(configs.isEmpty());
        for (Path path : configs) {
            JsonObject config;
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                config = new JsonParser().parse(reader).getAsJsonObject();
            }
            // ModularUI 3.1.6 writes BigIntegers with writeByteArray itself, so BigIntAdapterMixin's redirect finds
            // no target there; the config that lists it can require its targets only once that mixin is removed.
            if (lists(config, "BigIntAdapterMixin")) continue;
            JsonObject injectors = config.getAsJsonObject("injectors");
            assertTrue(injectors != null && injectors.has("defaultRequire") &&
                    injectors.get("defaultRequire").getAsInt() == 1,
                    path.getFileName() + " must set injectors.defaultRequire to 1 so a missed injection target fails");
        }
    }

    private static boolean lists(JsonObject config, String mixin) {
        for (String side : new String[] { "mixins", "client", "server" }) {
            JsonArray names = config.getAsJsonArray(side);
            if (names == null) continue;
            for (JsonElement name : names) {
                if (mixin.equals(name.getAsString())) return true;
            }
        }
        return false;
    }
}
