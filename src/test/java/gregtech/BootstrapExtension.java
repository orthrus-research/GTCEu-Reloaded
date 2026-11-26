package gregtech;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Runs the test {@link Bootstrap} before a test class: Minecraft's bootstrap, the capabilities filled as the loader
 * would, and GregTech's content registered by the same routine the game runs.
 * <p>
 * Use it with {@code @ExtendWith(BootstrapExtension.class)} instead of calling {@link Bootstrap#perform()} and
 * injecting capabilities by hand.
 */
public final class BootstrapExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        Bootstrap.perform();
    }
}
