package gregtech.api.recipes.conditions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/** Bounded localization data, not a component or executable callback. */
public final class ConditionDescription {

    public static final int MAX_ARGUMENTS = 8;
    public static final int MAX_TEXT_LENGTH = 128;
    private final String typeId;
    private final String key;
    private final List<String> arguments;
    private final boolean inverted;

    public ConditionDescription(String key, String... arguments) {
        this("gregtech:condition", key, false, arguments);
    }

    ConditionDescription(String typeId, String key, boolean inverted, String[] arguments) {
        if (typeId == null || typeId.length() > MAX_TEXT_LENGTH || !typeId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid condition description type identity");
        }
        if (key == null || key.isEmpty() || key.length() > MAX_TEXT_LENGTH || arguments == null ||
                arguments.length > MAX_ARGUMENTS) {
            throw new IllegalArgumentException("Condition description exceeds its bounds");
        }
        String[] copy = arguments.clone();
        for (String argument : copy) {
            if (argument == null || argument.length() > MAX_TEXT_LENGTH) {
                throw new IllegalArgumentException("Invalid condition description argument");
            }
        }
        this.typeId = typeId;
        this.key = key;
        this.arguments = Collections.unmodifiableList(Arrays.asList(copy));
        this.inverted = inverted;
    }

    public ConditionDescription inverted(boolean inverted) {
        return new ConditionDescription(typeId, key, inverted, arguments.toArray(new String[0]));
    }

    ConditionDescription withType(String typeId) {
        return new ConditionDescription(typeId, key, inverted, arguments.toArray(new String[0]));
    }

    public String typeId() {
        return typeId;
    }

    public String key() {
        return key;
    }

    public List<String> arguments() {
        return arguments;
    }

    public boolean isInverted() {
        return inverted;
    }

    public String format(BiFunction<String, Object[], String> translator) {
        String description = translator.apply(key, arguments.toArray());
        return inverted ? translator.apply("gregtech.recipe.condition.not", new Object[] { description }) : description;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ConditionDescription)) return false;
        ConditionDescription that = (ConditionDescription) other;
        return inverted == that.inverted && typeId.equals(that.typeId) && key.equals(that.key) &&
                arguments.equals(that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeId, key, arguments, inverted);
    }
}
