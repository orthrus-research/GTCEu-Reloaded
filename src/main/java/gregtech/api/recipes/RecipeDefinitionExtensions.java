package gregtech.api.recipes;

import gregtech.api.recipes.definition.RecipeAttributeCodec;

import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Explicit native addon contracts, installed once before authoring. Registration does not prove purity:
 * addon copy/build methods, codecs and adapters must not publish recipes, mutate registries, retain builders,
 * or perform external side effects. Such effects cannot be rolled back by the recipe registrar.
 * Registrations cannot be replaced or removed during script reload.
 */
public final class RecipeDefinitionExtensions {

    private static final Set<Class<?>> BUILDERS = new HashSet<>();
    private static final Map<ResourceLocation, RecipeAttributeType<?, ?>> ATTRIBUTES = new HashMap<>();
    private static final Set<String> PROPERTY_KEYS = new HashSet<>();

    private RecipeDefinitionExtensions() {}

    /**
     * Opts in this exact concrete builder class, never its subclasses. copy() must preserve the exact class
     * and detach mutable authoring state; build() must produce an unpublished native recipe.
     */
    public static synchronized <B extends RecipeBuilder<?>> void registerBuilder(Class<B> builderType) {
        Objects.requireNonNull(builderType, "builderType");
        if (Modifier.isAbstract(builderType.getModifiers()) ||
                RecipeDefinitionCompiler.SUPPORTED_BUILDERS.contains(builderType) || !BUILDERS.add(builderType)) {
            throw new IllegalArgumentException("Builder is abstract or already registered: " + builderType.getName());
        }
    }

    /**
     * Declares one native property and the typed setter that validates and applies it. Use the property's actual
     * storage key. The adapter may add required ingredients but must not overwrite other properties. Codecs
     * consume a private payload copy; the resulting value must be immutable or privately owned by the builder.
     * A base builder type can share a setter across explicitly registered concrete builders.
     */
    public static synchronized <B extends RecipeBuilder<?>, V> RecipeAttributeType<B, V> registerAttribute(
                                                                                                           ResourceLocation id,
                                                                                                           String propertyKey,
                                                                                                           Class<B> builderType,
                                                                                                           RecipeAttributeCodec<V> codec,
                                                                                                           BiConsumer<B, V> apply) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(propertyKey, "propertyKey");
        Objects.requireNonNull(builderType, "builderType");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(apply, "apply");
        ResourceLocation key = new ResourceLocation(id.toString());
        if (propertyKey.isBlank() || ATTRIBUTES.containsKey(key) || PROPERTY_KEYS.contains(propertyKey)) {
            throw new IllegalArgumentException("Attribute ID or property key is empty or already registered: " + key);
        }
        var type = new RecipeAttributeType<>(key, propertyKey, builderType, codec, apply);
        ATTRIBUTES.put(key, type);
        PROPERTY_KEYS.add(propertyKey);
        return type;
    }

    static synchronized boolean supportsBuilder(Class<?> builderType) {
        return RecipeDefinitionCompiler.SUPPORTED_BUILDERS.contains(builderType) || BUILDERS.contains(builderType);
    }

    static synchronized RecipeAttributeType<?, ?> attribute(ResourceLocation id) {
        return ATTRIBUTES.get(id);
    }
}
