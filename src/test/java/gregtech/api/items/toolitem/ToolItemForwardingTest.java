package gregtech.api.items.toolitem;

import net.minecraft.item.Item;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ItemGTTool}, {@link ItemGTAxe}, {@link ItemGTHoe} and {@link ItemGTSword} extend different vanilla items, so
 * each forwards the {@link Item} methods a tool handles to {@link IGTTool}'s {@code definition$} methods by hand. This
 * keeps them from drifting apart: each overrides the Item methods {@link ItemGTTool} overrides and no others, each
 * override forwards to the same {@code definition$} method as {@link ItemGTTool}'s, and {@link ItemGTTool} forwards to
 * every {@code definition$} method, except for the differences {@link #INTENDED} lists.
 */
public class ToolItemForwardingTest {

    private static final String FORWARD_PREFIX = "definition$";

    private static final List<Class<? extends Item>> TOOLS = List.of(ItemGTTool.class, ItemGTAxe.class,
            ItemGTHoe.class, ItemGTSword.class);

    /** The overrides that differ from {@link ItemGTTool}'s on purpose, by class and method name, with the reason. */
    private static final Map<String, String> INTENDED = Map.of(
            "ItemGTHoe.hitEntity", "a hoe takes one point of damage per hit, as vanilla hoes do",
            "ItemGTSword.canDestroyBlockInCreative", "swords do not break blocks in creative mode, as vanilla swords",
            "ItemGTSword.onItemRightClick", "a sword's right click does not open the tool's area settings");

    @Test
    public void toolItemsForwardTheSameItemMethods() throws IOException {
        Map<String, String> reference = forwards(ItemGTTool.class);
        List<String> drift = new ArrayList<>();
        Set<String> differences = new HashSet<>();

        for (Method method : IGTTool.class.getDeclaredMethods()) {
            if (method.getName().startsWith(FORWARD_PREFIX) && !reference.containsValue(method.getName())) {
                drift.add("ItemGTTool forwards no Item method to IGTTool." + method.getName());
            }
        }

        for (Class<? extends Item> tool : TOOLS) {
            Map<String, String> overrides = forwards(tool);
            Set<String> methods = new TreeSet<>(reference.keySet());
            methods.addAll(overrides.keySet());
            for (String method : methods) {
                String expected = reference.get(method);
                String actual = overrides.get(method);
                if (Objects.equals(expected, actual)) continue;

                String difference = tool.getSimpleName() + "." + method.substring(0, method.indexOf('('));
                if (INTENDED.containsKey(difference)) {
                    differences.add(difference);
                } else {
                    drift.add(String.format("%s.%s %s, but ItemGTTool's %s", tool.getSimpleName(), method,
                            describe(actual), describe(expected)));
                }
            }
        }

        for (String difference : INTENDED.keySet()) {
            if (!differences.contains(difference)) {
                drift.add(difference + " is listed as an intended difference, but it matches ItemGTTool's");
            }
        }
        assertEquals(List.of(), drift, "The tool items' Item overrides drifted apart");
    }

    private static String describe(String forward) {
        if (forward == null) return "is not overridden";
        return forward.isEmpty() ? "runs its own code" : "forwards to " + forward;
    }

    /**
     * For each Item method the class itself overrides, the {@code definition$} method its override calls, or an empty
     * string when it calls none.
     */
    private static Map<String, String> forwards(Class<?> tool) throws IOException {
        Map<String, String> calls = forwardCalls(tool);
        Map<String, String> forwards = new TreeMap<>();
        for (Method method : tool.getDeclaredMethods()) {
            if (method.isSynthetic() || Modifier.isStatic(method.getModifiers()) || !overridesItem(method)) continue;
            String descriptor = MethodType.methodType(method.getReturnType(), method.getParameterTypes())
                    .toMethodDescriptorString();
            forwards.put(signature(method), calls.getOrDefault(method.getName() + descriptor, ""));
        }
        return forwards;
    }

    private static boolean overridesItem(Method method) {
        try {
            int modifiers = Item.class.getDeclaredMethod(method.getName(), method.getParameterTypes())
                    .getModifiers();
            return !Modifier.isStatic(modifiers) && (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers));
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** The {@code definition$} methods each method of the class calls, by method name and descriptor. */
    private static Map<String, String> forwardCalls(Class<?> tool) throws IOException {
        byte[] bytes;
        try (InputStream in = tool.getResourceAsStream(tool.getSimpleName() + ".class")) {
            bytes = Objects.requireNonNull(in, tool.getName()).readAllBytes();
        }
        Map<String, String> calls = new HashMap<>();
        for (MethodModel method : ClassFile.of().parse(bytes).methods()) {
            Set<String> called = new TreeSet<>();
            method.code().ifPresent(code -> code.forEach(element -> {
                if (element instanceof InvokeInstruction invoke &&
                        invoke.name().stringValue().startsWith(FORWARD_PREFIX)) {
                    called.add(invoke.name().stringValue());
                }
            }));
            calls.put(method.methodName().stringValue() + method.methodType().stringValue(), String.join(", ", called));
        }
        return calls;
    }

    private static String signature(Method method) {
        return method.getName() + Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName)
                .collect(Collectors.joining(", ", "(", ")"));
    }
}
