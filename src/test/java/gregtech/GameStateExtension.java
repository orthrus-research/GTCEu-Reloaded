package gregtech;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Runs the test {@link Bootstrap}, then gives back the shared game state a test class or a test changes: a
 * {@link GameStateSnapshot} is taken before the class's own set-up and each test, and restored after each test and
 * after the class's own tear-down.
 * <p>
 * Use it with {@code @ExtendWith(GameStateExtension.class)} on a test that changes configuration, registers recipe
 * maps or recipes, or creates MTE registries or machines.
 */
public final class GameStateExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback,
                                      AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
            .create(GameStateExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        Bootstrap.perform();
        save(context);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        restore(context);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        save(context);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        restore(context);
    }

    private static void save(ExtensionContext context) {
        context.getStore(NAMESPACE).put(context.getUniqueId(), GameStateSnapshot.take());
    }

    private static void restore(ExtensionContext context) {
        GameStateSnapshot snapshot = context.getStore(NAMESPACE).remove(context.getUniqueId(),
                GameStateSnapshot.class);
        if (snapshot != null) snapshot.restore();
    }
}
