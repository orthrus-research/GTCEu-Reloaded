package gregtech.api.config;

import gregtech.GameStateExtension;
import gregtech.common.ConfigHolder;
import gregtech.common.ConfigHolderView;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(GameStateExtension.class)
class GTConfigTest {

    @Test
    void theViewIsGTCEusConfigHolder() {
        assertInstanceOf(ConfigHolderView.class, GTConfig.get());
        assertSame(GTConfig.get(), GTConfig.get());
    }

    /**
     * Each value is the config entry of its name, read as it is now: a changed entry is read changed, so a value read
     * from another entry, or kept from an earlier read, fails.
     */
    @Test
    void eachValueIsTheCurrentEntryOfItsName() throws ReflectiveOperationException {
        Map<String, List<Entry>> entries = new LinkedHashMap<>();
        for (Field group : ConfigHolder.class.getDeclaredFields()) {
            if (Modifier.isStatic(group.getModifiers()) && !group.getType().isPrimitive()) {
                collect(group.get(null), entries);
            }
        }

        for (Method method : GTConfigView.class.getMethods()) {
            List<Entry> named = entries.getOrDefault(method.getName(), List.of());
            assertEquals(1, named.size(), "ConfigHolder's entries named " + method.getName() + ": " + named);
            Entry entry = named.get(0);
            Object changed = changed(entry.field().get(entry.owner()));
            entry.field().set(entry.owner(), changed);
            Object expected = changed instanceof String[] array ? List.of(array) : changed;
            assertEquals(expected, method.invoke(GTConfig.get()), method.getName());
        }
    }

    @Test
    void listsCannotChangeTheConfig() {
        ConfigHolder.machines.boilerFluids = new String[] { "water" };
        List<String> fluids = GTConfig.get().boilerFluids();
        assertThrows(UnsupportedOperationException.class, () -> fluids.set(0, "lava"));
        assertThrows(UnsupportedOperationException.class, () -> GTConfig.get().modPriorities().add("minecraft"));
        assertEquals("water", ConfigHolder.machines.boilerFluids[0]);
    }

    /** Collects the entries of an option object and of the option objects it holds, by name. */
    private static void collect(Object options, Map<String, List<Entry>> entries) throws IllegalAccessException {
        for (Field field : options.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
            Class<?> type = field.getType();
            if (type.isPrimitive() || type == String.class || type == String[].class || type.isEnum()) {
                entries.computeIfAbsent(field.getName(), name -> new ArrayList<>()).add(new Entry(options, field));
            } else {
                collect(field.get(options), entries);
            }
        }
    }

    private static Object changed(Object value) {
        return switch (value) {
            case Boolean flag -> !flag;
            case Integer number -> number + 1;
            case Double number -> number + 0.5;
            case String text -> text + "_changed";
            case String[] ignored -> new String[] { "first_changed", "second_changed" };
            default -> throw new IllegalArgumentException("no change for " + value);
        };
    }

    private record Entry(Object owner, Field field) {}
}
