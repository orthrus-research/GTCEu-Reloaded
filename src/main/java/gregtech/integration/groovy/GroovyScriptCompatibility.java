package gregtech.integration.groovy;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.DefaultArtifactVersion;
import net.minecraftforge.fml.common.versioning.InvalidVersionSpecificationException;
import net.minecraftforge.fml.common.versioning.VersionRange;

/**
 * GroovyScript versions whose private internals the GTCEu Groovy mixins were verified against.
 * <p>
 * The mod dependency stays open-ended so a newer GroovyScript still loads. Outside this range the mixin config is not
 * applied, GroovyScript keeps its own script cache, and typed Groovy definitions report a clear error when used.
 * Imperative GroovyScript compat is unaffected. Widen the range only after verifying the mixin targets.
 */
public final class GroovyScriptCompatibility {

    /** Read by the mixin connector before mods construct; keep it a compile-time constant. */
    public static final String TESTED_RANGE = "[1.5.0]";

    private GroovyScriptCompatibility() {}

    public static boolean isTested(String version) {
        try {
            return VersionRange.createFromVersionSpec(TESTED_RANGE)
                    .containsVersion(new DefaultArtifactVersion(version));
        } catch (InvalidVersionSpecificationException impossible) {
            throw new AssertionError(impossible);
        }
    }

    /** Whether the loaded GroovyScript received the GTCEu mixins that typed definitions rely on. */
    public static boolean typedDefinitionsAvailable() {
        ModContainer groovy = Loader.instance().getIndexedModList().get("groovyscript");
        return groovy != null && isTested(groovy.getVersion());
    }

    static String describeLoaded() {
        ModContainer groovy = Loader.instance().getIndexedModList().get("groovyscript");
        return groovy == null ? "none" : groovy.getVersion();
    }
}
