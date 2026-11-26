package gregtech;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Ratchets on what the main sources depend on: each rule names the files it covers and the packages they may use
 * only as far as a checked-in baseline under {@code src/test/resources/gregtech/ratchet} lists, file by file.
 * <p>
 * A file's dependencies are its imports and the classes its code names by their qualified name; comments and string
 * literals do not count. A rule fails on a dependency its baseline does not list, and on a listed one that is gone, so
 * the baseline only shrinks.
 */
class ImportRatchetTest {

    private static final String BASELINES = "/gregtech/ratchet/";
    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+(static\\s+)?([\\w.]+(?:\\.\\*)?)\\s*;");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+[\\w.]+\\s*;");
    /** A qualified class name: package segments, then the first segment that starts with a capital. */
    private static final Pattern QUALIFIED = Pattern.compile("(?<![\\w.])([a-z_]\\w*(?:\\.[a-z_]\\w*)*\\.[A-Z]\\w*)");

    /** The packages that make up GTCEu's implementation, which addons do not build against. */
    private static final List<String> IMPLEMENTATION = List.of("gregtech.common", "gregtech.client", "gregtech.core",
            "gregtech.loaders", "gregtech.integration");

    @Test
    void apiImportsNoImplementationBeyondItsBaseline() throws IOException {
        check(new Rule("api_implementation_imports.list",
                List.of("Imports of GTCEu's implementation packages (gregtech.common, gregtech.client, gregtech.core,",
                        "gregtech.loaders, gregtech.integration) that files in gregtech.api still have, as <file> " +
                                "<imported name>."),
                file -> file.startsWith("gregtech/api/"), IMPLEMENTATION,
                "gregtech.api is the API addons build against, so its files must not depend on GTCEu's " +
                        "implementation packages (" + String.join(", ", IMPLEMENTATION) + ").",
                "Fix each one in the code: move what the API needs into gregtech.api, leaving a deprecated " +
                        "forwarder where addons may use the old name, or let the API reach the implementation " +
                        "through an API type the implementation provides, as GTConfig.get() does for the " +
                        "configuration."));
    }

    @Test
    void dependenciesAreImportsAndQualifiedNamesInCode() {
        String source = """
                package gregtech.api.example;

                import gregtech.common.ConfigHolder;
                import static gregtech.client.utils.TooltipHelper.isShiftDown;
                import codechicken.lib.vec.*;

                /** Uses {@link gregtech.common.blocks.MetaBlocks}. */
                class Example {
                    // gregtech.core.CoreModule in a comment
                    String text = "gregtech.loaders.recipe.RecyclingRecipes in a string";
                    char quote = '"';
                    String block = \"""
                            gregtech.integration.IntegrationModule in a text block \\\"""
                            \""";
                    Object qualified = gregtech.common.items.MetaItems.SHAPE_EMPTY;
                }
                """;
        assertEquals(new TreeSet<>(List.of("codechicken.lib.vec.*", "gregtech.common.ConfigHolder",
                "gregtech.common.items.MetaItems", "static gregtech.client.utils.TooltipHelper.isShiftDown")),
                dependencies(source));
    }

    private static void check(Rule rule) throws IOException {
        Path sources = findSources();
        assertNotNull(sources, "main sources not found from the working directory");

        SortedSet<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
                String relative = sources.relativize(file).toString().replace('\\', '/');
                if (!rule.files().test(relative)) continue;
                for (String dependency : dependencies(Files.readString(file))) {
                    if (rule.covers(dependency)) found.add(relative + ' ' + dependency);
                }
            }
        }

        SortedSet<String> baseline = read(rule.baseline());
        if (baseline.equals(found)) return;

        List<String> added = new ArrayList<>(found);
        added.removeAll(baseline);
        List<String> gone = new ArrayList<>(baseline);
        gone.removeAll(found);

        StringBuilder message = new StringBuilder(rule.why()).append('\n')
                .append("The baseline is src/test/resources").append(BASELINES).append(rule.baseline()).append(".\n");
        if (!added.isEmpty()) {
            message.append("\nNew dependencies the baseline does not allow:\n");
            added.forEach(line -> message.append("  ").append(line).append('\n'));
            message.append(rule.fix()).append('\n')
                    .append("Add a line to the baseline only for a dependency a review accepts.\n");
        }
        if (!gone.isEmpty()) {
            SortedSet<String> shrunk = new TreeSet<>(baseline);
            shrunk.removeAll(gone);
            Path written = write(sources, rule, shrunk);
            message.append("\nDependencies the baseline lists that the sources no longer have:\n");
            gone.forEach(line -> message.append("  ").append(line).append('\n'));
            message.append("Delete these lines from the baseline, which only shrinks. The baseline without them is ")
                    .append(written.toAbsolutePath()).append('\n');
        }
        fail(message.toString());
    }

    /** The classes, packages and static members a source file imports or names by their qualified name. */
    static SortedSet<String> dependencies(String source) {
        String code = PACKAGE.matcher(code(source)).replaceAll("");
        SortedSet<String> dependencies = new TreeSet<>();
        Matcher imports = IMPORT.matcher(code);
        while (imports.find()) {
            dependencies.add(imports.group(1) == null ? imports.group(2) : "static " + imports.group(2));
        }
        Matcher qualified = QUALIFIED.matcher(imports.replaceAll(""));
        while (qualified.find()) {
            dependencies.add(qualified.group(1));
        }
        return dependencies;
    }

    /** The source without its comments, and with its string, text block and character literals emptied. */
    static String code(String source) {
        StringBuilder code = new StringBuilder(source.length());
        int length = source.length();
        int i = 0;
        while (i < length) {
            char c = source.charAt(i);
            if (source.startsWith("//", i)) {
                int end = source.indexOf('\n', i);
                i = end < 0 ? length : end;
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? length : end + 2;
                code.append(' ');
            } else if (c == '"' || c == '\'') {
                String quote = source.startsWith("\"\"\"", i) ? "\"\"\"" : String.valueOf(c);
                int j = i + quote.length();
                while (j < length && !source.startsWith(quote, j)) {
                    j += source.charAt(j) == '\\' ? 2 : 1;
                }
                i = Math.min(length, j + quote.length());
                code.append(c).append(c);
            } else {
                code.append(c);
                i++;
            }
        }
        return code.toString();
    }

    private static Path findSources() {
        for (Path directory = Paths.get("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
            Path sources = directory.resolve("src/main/java");
            if (Files.isDirectory(sources.resolve("gregtech/api"))) return sources;
        }
        return null;
    }

    private static SortedSet<String> read(String baseline) throws IOException {
        InputStream stream = ImportRatchetTest.class.getResourceAsStream(BASELINES + baseline);
        assertNotNull(stream, "baseline " + BASELINES + baseline + " not found among the test resources");
        SortedSet<String> lines = new TreeSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank() && !line.startsWith("#")) lines.add(line.strip());
            }
        }
        return lines;
    }

    /** Writes a baseline under the project's {@code build/ratchet}, in the checked-in form. */
    private static Path write(Path sources, Rule rule, SortedSet<String> entries) throws IOException {
        List<String> lines = new ArrayList<>();
        rule.header().forEach(line -> lines.add("# " + line));
        lines.add("# ImportRatchetTest fails on a dependency not listed here and on a listed one that is gone: fix a");
        lines.add("# new one in the code rather than listing it, and delete the lines of one you removed.");
        lines.addAll(entries);
        Path directory = sources.getParent().getParent().getParent().resolve("build").resolve("ratchet");
        Files.createDirectories(directory);
        Path path = directory.resolve(rule.baseline());
        Files.write(path, lines, StandardCharsets.UTF_8);
        return path;
    }

    /**
     * @param baseline the baseline's file name
     * @param header   what the baseline lists, for its first comment lines
     * @param files    the source files the rule covers, by their path under {@code src/main/java}
     * @param packages the packages those files may use only as the baseline lists
     * @param why      why the rule holds, for the failure message
     * @param fix      how to fix a new dependency, for the failure message
     */
    private record Rule(String baseline, List<String> header, Predicate<String> files, List<String> packages,
                        String why, String fix) {

        boolean covers(String dependency) {
            String name = dependency.startsWith("static ") ? dependency.substring("static ".length()) : dependency;
            return packages.stream().anyMatch(prefix -> name.startsWith(prefix + '.'));
        }
    }
}
