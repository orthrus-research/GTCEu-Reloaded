package gregtech.api.recipes.conditions;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A registered pure adapter. Decoded values must be immutable and evaluation must be bounded and side-effect free.
 * Decoding receives an owned copy; neither user parameter objects nor mutable recipe NBT are retained.
 */
public final class RecipeConditionType<T> {

    private final ResourceLocation id;
    private final boolean alternatives;
    private final Function<NBTTagCompound, T> decoder;
    private final Function<T, NBTTagCompound> encoder;
    private final BiFunction<T, RecipeConditionContext, ConditionState> evaluator;
    private final Function<T, ConditionDescription> description;

    public RecipeConditionType(ResourceLocation id, boolean alternatives, Function<NBTTagCompound, T> decoder,
                               Function<T, NBTTagCompound> encoder,
                               BiFunction<T, RecipeConditionContext, ConditionState> evaluator,
                               Function<T, ConditionDescription> description) {
        this.id = Objects.requireNonNull(id);
        if (id.toString().length() > 128 || !id.toString().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Condition ID must be a bounded namespaced registry identity");
        }
        this.alternatives = alternatives;
        this.decoder = Objects.requireNonNull(decoder);
        this.encoder = Objects.requireNonNull(encoder);
        this.evaluator = Objects.requireNonNull(evaluator);
        this.description = Objects.requireNonNull(description);
    }

    public ResourceLocation id() {
        return id;
    }

    public boolean isAlternative() {
        return alternatives;
    }

    public RecipeCondition<T> create(T parameters) {
        return create(parameters, false);
    }

    public RecipeCondition<T> create(T parameters, boolean inverted) {
        if (RecipeConditions.type(id) != this) throw new IllegalStateException("Unregistered condition type: " + id);
        NBTTagCompound payload = Objects.requireNonNull(encoder.apply(parameters));
        if (!ConditionPayloads.bounded(payload))
            throw new IllegalArgumentException("Condition payload exceeds its bounds");
        return read(payload, inverted);
    }

    RecipeCondition<T> read(NBTTagCompound data, boolean inverted) {
        T value = Objects.requireNonNull(decoder.apply(data.copy()));
        NBTTagCompound canonical = Objects.requireNonNull(encoder.apply(value));
        if (!ConditionPayloads.bounded(canonical))
            throw new IllegalArgumentException("Condition payload exceeds its bounds");
        return new RecipeCondition<>(this, value, canonical, inverted,
                Objects.requireNonNull(description.apply(value)).withType(id.toString()).inverted(inverted));
    }

    ConditionState test(T value, RecipeConditionContext context) {
        return Objects.requireNonNull(evaluator.apply(value, context));
    }
}
