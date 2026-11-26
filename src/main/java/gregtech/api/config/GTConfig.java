package gregtech.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Where the API reads GTCEu's configuration.
 * <p>
 * GTCEu provides the {@link GTConfigView} as a service ({@code META-INF/services/gregtech.api.config.GTConfigView}),
 * so the API needs no part of the implementation and the view is there from the first read on, before GTCEu's
 * construction and in tests that do not start the game.
 */
public final class GTConfig {

    private static volatile @Nullable GTConfigView view;

    private GTConfig() {}

    /**
     * @return the configuration values as they are now
     * @throws IllegalStateException when the jar provides no view, or more than one
     */
    public static @NotNull GTConfigView get() {
        GTConfigView current = view;
        if (current == null) {
            current = load();
            view = current;
        }
        return current;
    }

    private static @NotNull GTConfigView load() {
        List<GTConfigView> views = new ArrayList<>();
        try {
            ServiceLoader.load(GTConfigView.class, GTConfig.class.getClassLoader()).forEach(views::add);
        } catch (ServiceConfigurationError e) {
            throw new IllegalStateException("GTCEu's configuration view could not be loaded", e);
        }
        if (views.size() != 1) {
            throw new IllegalStateException("Expected GTCEu's one configuration view (" +
                    GTConfigView.class.getName() + "), found " + views.size() + ": " + views);
        }
        return views.get(0);
    }
}
