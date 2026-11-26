package gregtech.api.recipes.ui;

import com.cleanroommc.modularui.drawable.UITexture;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Describes recipe map layouts one line per element, and compares them with the listings under
 * {@code src/test/resources/gregtech/layouts}, which record the layouts the screens of the old GUI system drew.
 */
public final class RecipeLayoutListing {

    private static final String LISTINGS = "/gregtech/layouts/";
    private static final Path ACTUAL = Path.of("build", "layouts");

    private RecipeLayoutListing() {}

    /**
     * @return one line for each element, in the order they are drawn: which inventory a slot shows, where it is and
     *         the textures under it; where a progress bar is, its texture, the direction it fills in and whether it
     *         shows recipes when clicked; where an image is and its texture
     */
    public static @NotNull List<String> describe(@NotNull RecipeMapLayout layout) {
        List<String> lines = new ArrayList<>();
        for (RecipeLayoutElement element : layout.getElements()) {
            String place = String.format("at %d,%d %dx%d", element.x(), element.y(), element.width(),
                    element.height());
            if (element instanceof RecipeLayoutSlot slot) {
                List<String> textures = new ArrayList<>();
                for (UITexture texture : slot.background()) textures.add(texture.location.toString());
                lines.add(String.format("%s %s %d %s %s", slot.isFluid() ? "fluid" : "item",
                        slot.isOutput() ? "out" : "in", slot.index(), place, textures));
            } else if (element instanceof RecipeLayoutProgress progress) {
                lines.add(String.format("progress %s %s %s%s", place, progress.texture().location,
                        progress.direction(), progress.recipeMap() == null ? "" : " recipes"));
            } else if (element instanceof RecipeLayoutImage image) {
                lines.add(String.format("image %s %s", place, image.texture().location));
            }
        }
        return lines;
    }

    /**
     * Compares a listing found now with the recorded one, ignoring its comment lines. On a difference, writes the
     * listing found under {@code build/layouts} and fails at the first differing line.
     *
     * @param file  the listing's file name
     * @param found the listing found now, one entry per line
     */
    public static void check(@NotNull String file, @NotNull List<String> found) throws IOException {
        List<String> expected = new ArrayList<>();
        try (InputStream stream = RecipeLayoutListing.class.getResourceAsStream(LISTINGS + file)) {
            assertNotNull(stream, "No listing " + file);
            for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank() && !line.startsWith("#")) expected.add(line);
            }
        }
        if (expected.equals(found)) return;

        Files.createDirectories(ACTUAL);
        Path written = ACTUAL.resolve(file);
        Files.write(written, found, StandardCharsets.UTF_8);
        int line = 0;
        while (line < expected.size() && line < found.size() && expected.get(line).equals(found.get(line))) line++;
        assertEquals(line < expected.size() ? expected.get(line) : "(end of listing)",
                line < found.size() ? found.get(line) : "(end of layouts)",
                file + " differs at entry line " + (line + 1) + "; the layouts found are in " +
                        written.toAbsolutePath());
    }
}
