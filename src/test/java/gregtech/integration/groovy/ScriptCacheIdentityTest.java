package gregtech.integration.groovy;

import gregtech.integration.groovy.cache.ScriptCacheIdentity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScriptCacheIdentityTest {

    @TempDir
    Path root;

    @Test
    void identicalInputsReuseButEqualAndOlderTimestampEditsDoNot() throws Exception {
        Path source = Files.createDirectories(root.resolve("source"));
        Path script = source.resolve("Recipe.groovy");
        Files.writeString(script, "version1");
        FileTime timestamp = Files.getLastModifiedTime(script);
        Path cache = cache();
        var first = inputs(source, "java25");
        ScriptCacheIdentity.seal(cache, first);
        assertTrue(ScriptCacheIdentity.reusable(cache, inputs(source, "java25")));
        Files.writeString(script, "version2");
        Files.setLastModifiedTime(script, timestamp);
        assertFalse(ScriptCacheIdentity.reusable(cache, inputs(source, "java25")));
        Files.setLastModifiedTime(script, FileTime.fromMillis(timestamp.toMillis() - 100000));
        assertFalse(ScriptCacheIdentity.reusable(cache, inputs(source, "java25")));
        Files.writeString(script, "version1");
        assertTrue(ScriptCacheIdentity.reusable(cache, inputs(source, "java25")));
    }

    @Test
    void helperChangesDeletionAndNewSourcesInvalidateTogether() throws Exception {
        Path source = Files.createDirectories(root.resolve("source/helpers"));
        Files.writeString(source.resolve("Helper.groovy"), "class Helper {}");
        Path cache = cache();
        var initial = inputs(root.resolve("source"), "compiler");
        ScriptCacheIdentity.seal(cache, initial);
        Files.writeString(source.resolve("Helper.groovy"), "class Helper { int changed }");
        assertFalse(ScriptCacheIdentity.reusable(cache, inputs(root.resolve("source"), "compiler")));
        Files.delete(source.resolve("Helper.groovy"));
        assertFalse(ScriptCacheIdentity.reusable(cache, inputs(root.resolve("source"), "compiler")));
        Files.writeString(source.resolve("Helper.groovy"), "class Helper {}");
        Files.writeString(source.resolve("Added.groovy"), "class Added {}");
        assertFalse(ScriptCacheIdentity.reusable(cache, inputs(root.resolve("source"), "compiler")));
    }

    @Test
    void dependencyBytesOrderAndCompilerSettingsParticipateWithoutTimestamps() throws Exception {
        Path first = Files.writeString(root.resolve("one.jar"), "dependency1");
        Path second = Files.writeString(root.resolve("two.jar"), "dependency2");
        String baseline = ScriptCacheIdentity.environment(List.of(first, second), "java25;groovy4");
        assertNotEquals(baseline, ScriptCacheIdentity.environment(List.of(second, first), "java25;groovy4"));
        assertNotEquals(baseline, ScriptCacheIdentity.environment(List.of(first, second), "java25;groovy5"));
        FileTime timestamp = Files.getLastModifiedTime(first);
        Files.writeString(first, "dependency3");
        Files.setLastModifiedTime(first, timestamp);
        assertNotEquals(baseline, ScriptCacheIdentity.environment(List.of(first, second), "java25;groovy4"));
    }

    @Test
    void corruptOrMissingCompiledBytesAndIncompleteManifestCannotBeReused() throws Exception {
        Path source = Files.createDirectories(root.resolve("source"));
        var inputs = inputs(source, "compiler");
        Path cache = cache();
        ScriptCacheIdentity.seal(cache, inputs);
        Files.writeString(cache.resolve("Recipe.class"), "corrupt bytecode");
        assertFalse(ScriptCacheIdentity.reusable(cache, inputs));
        ScriptCacheIdentity.seal(cache, inputs);
        Files.delete(cache.resolve("Recipe.class"));
        assertFalse(ScriptCacheIdentity.reusable(cache, inputs));
        ScriptCacheIdentity.invalidate(cache);
        assertFalse(ScriptCacheIdentity.reusable(cache, inputs));
        Files.write(cache.resolve("_gtceu_content_fingerprint"), new byte[] { 0, 0, 0, 1 });
        assertFalse(ScriptCacheIdentity.reusable(cache, inputs));
    }

    @Test
    void relocationReusesMatchingContentAndSideOnlyConfigurationInvalidatesIt() throws Exception {
        Path source = Files.createDirectories(root.resolve("source"));
        Files.writeString(source.resolve("Recipe.groovy"), "recipe()");
        Path relocated = Files.createDirectories(root.resolve("relocated"));
        Files.copy(source.resolve("Recipe.groovy"), relocated.resolve("Recipe.groovy"));
        var inputs = inputs(source, "compiler");
        Path cache = cache();
        ScriptCacheIdentity.seal(cache, inputs);
        assertTrue(ScriptCacheIdentity.reusable(cache, inputs(relocated, "compiler")));
        // Generated side-only rules alter transformed method visibility and therefore compilation semantics.
        Files.writeString(relocated.resolve("sideOnlyGenerated.json"), "generated metadata");
        assertFalse(ScriptCacheIdentity.reusable(cache, inputs(relocated, "compiler")));
        Files.delete(relocated.resolve("sideOnlyGenerated.json"));
        Files.writeString(relocated.resolve("runConfig.json"), "changed compiler stages");
        assertFalse(ScriptCacheIdentity.reusable(cache, inputs(relocated, "compiler")));
    }

    private ScriptCacheIdentity.Inputs inputs(Path source, String configuration) throws Exception {
        return new ScriptCacheIdentity.Inputs(ScriptCacheIdentity.sources(source),
                ScriptCacheIdentity.environment(List.of(), configuration));
    }

    private Path cache() throws Exception {
        Path cache = Files.createDirectories(root.resolve("cache"));
        Files.writeString(cache.resolve("_index.json"), "index");
        Files.writeString(cache.resolve("Recipe.class"), "compiled bytes");
        return cache;
    }
}
