package gregtech.loaders.recipe;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins the whole recipe set: the recipes the game's recipe registration makes, as {@link RecipeSetListing} lists
 * them, against the SHA-256 in {@code src/test/resources/gregtech/recipes/recipe_set.sha256}, with the listing it was
 * taken from beside it to read a difference against.
 * <p>
 * A change to a recipe loader that should not change any recipe keeps the digest. One that changes recipes on
 * purpose writes both files again from the listing a failed run leaves in {@code build/recipes}.
 * <p>
 * The registration runs in a class loader of its own over this JVM's class path: the game's state there is fresh,
 * whatever the tests before this one changed, and its recipes do not reach the tests after it.
 */
class RecipeSetDigestTest {

    private static final String LISTING = "/gregtech/recipes/recipe_set.list";
    private static final String DIGEST = "/gregtech/recipes/recipe_set.sha256";
    private static final Path ACTUAL = Path.of("build", "recipes");
    private static final int SHOWN = 40;

    private static List<String> found;

    @BeforeAll
    static void registerRecipes() throws ReflectiveOperationException, IOException {
        found = takeIsolated();
    }

    @Test
    void recipeSetIsUnchanged() throws IOException {
        String digest = digest(found);
        String expected = readDigest();
        if (digest.equals(expected)) return;

        Path written = write(found, digest);
        List<String> listed = readListing();
        StringBuilder message = new StringBuilder()
                .append("The recipe set no longer matches recipe_set.sha256: ").append(found.size())
                .append(" recipes now with digest ").append(digest).append(", ").append(listed.size())
                .append(" in the checked-in listing with digest ").append(expected).append(".\n")
                .append("A refactor must leave every recipe as it was. If the change is meant, copy recipe_set.list ")
                .append("and recipe_set.sha256 from ").append(written.toAbsolutePath()).append(" to ")
                .append("src/test/resources/gregtech/recipes.\n");
        appendDifferences(message, listed, found);
        fail(message.toString());
    }

    @Test
    void listingMatchesItsDigest() throws IOException {
        assertEquals(readDigest(), digest(readListing()),
                "recipe_set.list and recipe_set.sha256 were not written from the same listing");
    }

    /**
     * Loads GregTech, Minecraft and the test bootstrap again from this JVM's class path in a class loader whose parent
     * is the platform's, and runs {@link RecipeSetListing#registerAndTake()} there.
     */
    @SuppressWarnings("unchecked")
    private static List<String> takeIsolated() throws ReflectiveOperationException, IOException {
        String[] entries = System.getProperty("java.class.path").split(File.pathSeparator);
        URL[] urls = new URL[entries.length];
        for (int i = 0; i < entries.length; i++) {
            urls[i] = Path.of(entries[i]).toUri().toURL();
        }
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader("recipe-set", urls, ClassLoader.getPlatformClassLoader())) {
            thread.setContextClassLoader(loader);
            Method take = Class.forName(RecipeSetListing.class.getName(), true, loader)
                    .getDeclaredMethod("registerAndTake");
            take.setAccessible(true);
            return List.copyOf((List<String>) take.invoke(null));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            if (e.getCause() instanceof Error error) throw error;
            throw e;
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    /** @return the SHA-256 of the lines, each ended by a line feed, as lower-case hex */
    private static String digest(List<String> lines) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String line : lines) {
                digest.update(line.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Walks both sorted listings and names the first lines only one of them has. */
    private static void appendDifferences(StringBuilder message, List<String> listed, List<String> found) {
        List<String> differences = new ArrayList<>();
        int removed = 0;
        int added = 0;
        int i = 0;
        int j = 0;
        while (i < listed.size() || j < found.size()) {
            int order = i == listed.size() ? 1 : j == found.size() ? -1 : listed.get(i).compareTo(found.get(j));
            if (order == 0) {
                i++;
                j++;
                continue;
            }
            if (order < 0) {
                removed++;
                if (differences.size() < SHOWN) differences.add("- " + listed.get(i));
                i++;
            } else {
                added++;
                if (differences.size() < SHOWN) differences.add("+ " + found.get(j));
                j++;
            }
        }
        message.append(removed).append(" lines gone, ").append(added).append(" lines new");
        message.append(differences.size() < removed + added ? "; the first " + SHOWN + ":\n" : ":\n");
        differences.forEach(line -> message.append(line).append('\n'));
    }

    private static String readDigest() throws IOException {
        List<String> lines = readResource(DIGEST);
        return lines.isEmpty() ? "" : lines.getFirst().trim();
    }

    private static List<String> readListing() throws IOException {
        return readResource(LISTING);
    }

    /** @return the resource's lines that are neither blank nor comments, or none when it is missing */
    private static List<String> readResource(String name) throws IOException {
        List<String> lines = new ArrayList<>();
        InputStream stream = RecipeSetDigestTest.class.getResourceAsStream(name);
        if (stream == null) return lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#")) lines.add(line);
            }
        }
        return lines;
    }

    private static Path write(List<String> found, String digest) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# Every recipe GregTech registers, one a line, sorted: recipe map recipes (machine), crafting");
        lines.add("# table recipes (crafting) and furnace recipes (furnace), as the test bootstrap's recipe");
        lines.add("# registration makes them with the default configuration and no optional mods. RecipeSetDigestTest");
        lines.add("# compares the SHA-256 of the lines that are not comments with recipe_set.sha256.");
        lines.addAll(found);
        Files.createDirectories(ACTUAL);
        Files.write(ACTUAL.resolve("recipe_set.list"), lines, StandardCharsets.UTF_8);
        Files.writeString(ACTUAL.resolve("recipe_set.sha256"), digest + '\n', StandardCharsets.UTF_8);
        return ACTUAL;
    }
}
