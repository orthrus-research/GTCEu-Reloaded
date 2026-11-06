package gregtech.api.recipes;

import gregtech.api.recipes.definition.RecipeAttribute;
import gregtech.api.recipes.definition.RecipeAttributeCodec;

import net.minecraft.util.ResourceLocation;

import java.util.Objects;
import java.util.function.BiConsumer;

/** A typed authoring handle. Its codec and native builder adapter run only while preparing recipes. */
public final class RecipeAttributeType<B extends RecipeBuilder<?>, V> {

    private final ResourceLocation id;
    private final String propertyKey;
    private final Class<B> builderType;
    private final RecipeAttributeCodec<V> codec;
    private final BiConsumer<B, V> apply;

    RecipeAttributeType(ResourceLocation id, String propertyKey, Class<B> builderType,
                        RecipeAttributeCodec<V> codec, BiConsumer<B, V> apply) {
        this.id = id;
        this.propertyKey = propertyKey;
        this.builderType = builderType;
        this.codec = codec;
        this.apply = apply;
    }

    public RecipeAttribute.Custom descriptor(V value) {
        return new RecipeAttribute.Custom(id, Objects.requireNonNull(codec.encode(value), "encoded value"));
    }

    String propertyKey() {
        return propertyKey;
    }

    boolean supports(RecipeBuilder<?> builder) {
        return builderType.isInstance(builder);
    }

    void apply(RecipeBuilder<?> builder, RecipeAttribute.Custom attribute) {
        V value = Objects.requireNonNull(codec.decode(attribute.payload()), "decoded value");
        apply.accept(builderType.cast(builder), value);
    }
}
