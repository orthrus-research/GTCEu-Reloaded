package gregtech.api.recipes.conditions;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Common-side factories and extension registry. Register addon adapters before recipes or saved work are read. */
public final class RecipeConditions {

    private static final Map<ResourceLocation, RecipeConditionType<?>> TYPES = new ConcurrentHashMap<>();

    public static final RecipeConditionType<ResourceLocation> BIOME = register(new RecipeConditionType<>(
            new ResourceLocation("gregtech", "biome"), true, tag -> registryName(readString(tag, "Biome")),
            value -> stringTag("Biome", value.toString()), (value, context) -> {
                if (!Biome.REGISTRY.containsKey(value)) return ConditionState.UNAVAILABLE;
                ResourceLocation actual = context.biome();
                return actual == null ? ConditionState.UNAVAILABLE : result(value.equals(actual));
            }, value -> new ConditionDescription("gregtech.recipe.condition.biome", value.toString())));

    public static final RecipeConditionType<ConditionRange> HEIGHT = register(new RecipeConditionType<>(
            new ResourceLocation("gregtech", "height"), false, tag -> {
                ConditionRange range = readRange(tag);
                if (range.minimum() > range.maximum())
                    throw new IllegalArgumentException("Height minimum exceeds maximum");
                return range;
            }, RecipeConditions::writeRange, (range, context) -> result(context.position().getY() >= range.minimum() &&
                    context.position().getY() <= range.maximum()),
            range -> new ConditionDescription("gregtech.recipe.condition.height",
                    Integer.toString(range.minimum()), Integer.toString(range.maximum()))));

    public static final RecipeConditionType<Boolean> DAYTIME = register(new RecipeConditionType<>(
            new ResourceLocation("gregtech", "daytime"), false, tag -> {
                ConditionPayloads.requireKeys(tag, "Day");
                ConditionPayloads.requireTag(tag, "Day", 1);
                if (tag.getByte("Day") != 0 && tag.getByte("Day") != 1)
                    throw new IllegalArgumentException("Invalid daytime value");
                return tag.getBoolean("Day");
            }, value -> {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setBoolean("Day", value);
                return tag;
            }, (value, context) -> result(context.isDaytime() == value),
            value -> new ConditionDescription(
                    value ? "gregtech.recipe.condition.daytime" : "gregtech.recipe.condition.nighttime")));

    public static final RecipeConditionType<ConditionRange> TIME = register(new RecipeConditionType<>(
            new ResourceLocation("gregtech", "time"), false, tag -> {
                ConditionRange range = readRange(tag);
                if (range.minimum() < 0 || range.minimum() >= 24000 || range.maximum() < 0 ||
                        range.maximum() >= 24000 || range.minimum() == range.maximum()) {
                    throw new IllegalArgumentException("Time window endpoints must differ and be in 0..23999");
                }
                return range;
            }, RecipeConditions::writeRange, (range, context) -> {
                long tick = Math.floorMod(context.worldTime(), 24000L);
                return result(range.minimum() < range.maximum() ? tick >= range.minimum() && tick < range.maximum() :
                        tick >= range.minimum() || tick < range.maximum());
            }, range -> new ConditionDescription("gregtech.recipe.condition.time",
                    Integer.toString(range.minimum()), Integer.toString(range.maximum()))));

    public static final RecipeConditionType<Float> RAIN = weather("rain", false);
    public static final RecipeConditionType<Float> THUNDER = weather("thunder", true);

    public static final RecipeConditionType<AdjacentBlockRequirement> ADJACENT = register(new RecipeConditionType<>(
            new ResourceLocation("gregtech", "adjacent_blocks"), false, AdjacentBlockRequirement::deserialize,
            AdjacentBlockRequirement::serialize, AdjacentBlockRequirement::test,
            AdjacentBlockRequirement::description));

    private RecipeConditions() {}

    public static <T> RecipeConditionType<T> register(RecipeConditionType<T> type) {
        if (TYPES.putIfAbsent(type.id(), type) != null) {
            throw new IllegalArgumentException("Duplicate condition type: " + type.id());
        }
        return type;
    }

    @Nullable
    public static RecipeConditionType<?> type(ResourceLocation id) {
        return TYPES.get(id);
    }

    public static RecipeCondition<?> biome(String id, boolean inverted) {
        return BIOME.create(registryName(id), inverted);
    }

    public static RecipeCondition<?> height(int minimum, int maximum, boolean inverted) {
        return HEIGHT.create(new ConditionRange(minimum, maximum), inverted);
    }

    public static RecipeCondition<?> daytime(boolean inverted) {
        return DAYTIME.create(Boolean.TRUE, inverted);
    }

    public static RecipeCondition<?> time(int start, int end, boolean inverted) {
        return TIME.create(new ConditionRange(start, end), inverted);
    }

    public static RecipeCondition<?> rain(float strength, boolean inverted) {
        return RAIN.create(strength, inverted);
    }

    public static RecipeCondition<?> thunder(float strength, boolean inverted) {
        return THUNDER.create(strength, inverted);
    }

    public static RecipeCondition<?> adjacent(AdjacentBlockRequirement requirement, boolean inverted) {
        return ADJACENT.create(requirement, inverted);
    }

    /** Script form: each row is one required group, each entry is a registry ID with optional @metadata. */
    public static RecipeCondition<?> adjacent(String[][] groups, boolean inverted) {
        if (groups == null || groups.length == 0 || groups.length > AdjacentBlockRequirement.MAX_GROUPS) {
            throw new IllegalArgumentException("Adjacent blocks require 1..8 groups");
        }
        List<List<BlockRequirement>> required = new ArrayList<>();
        for (String[] group : groups) {
            if (group == null || group.length == 0 || group.length > AdjacentBlockRequirement.MAX_ALTERNATIVES) {
                throw new IllegalArgumentException("Each adjacent block group requires 1..8 alternatives");
            }
            List<BlockRequirement> alternatives = new ArrayList<>();
            for (String entry : group) {
                if (entry == null || entry.length() > 132) throw new IllegalArgumentException("Invalid block identity");
                int split = entry.indexOf('@');
                alternatives.add(split < 0 ? new BlockRequirement(entry) :
                        new BlockRequirement(entry.substring(0, split), Integer.parseInt(entry.substring(split + 1))));
            }
            required.add(alternatives);
        }
        return adjacent(new AdjacentBlockRequirement(required), inverted);
    }

    static ResourceLocation registryName(String name) {
        if (name == null || name.length() > 128 || !name.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Expected a namespaced registry ID of at most 128 characters");
        }
        return new ResourceLocation(name);
    }

    private static RecipeConditionType<Float> weather(String name, boolean thunder) {
        return register(new RecipeConditionType<>(new ResourceLocation("gregtech", name), false, tag -> {
            ConditionPayloads.requireKeys(tag, "Strength");
            ConditionPayloads.requireTag(tag, "Strength", 5);
            float strength = tag.getFloat("Strength");
            if (!Float.isFinite(strength) || strength < 0 || strength > 1) {
                throw new IllegalArgumentException("Weather strength must be finite and in 0..1");
            }
            return strength;
        }, value -> {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setFloat("Strength", value);
            return tag;
        }, (strength, context) -> {
            float actual = thunder ? context.thunderStrength() : context.rainStrength();
            return !Float.isFinite(actual) ? ConditionState.UNAVAILABLE : result(actual >= strength);
        },
                strength -> new ConditionDescription("gregtech.recipe.condition." + name, Float.toString(strength))));
    }

    private static ConditionState result(boolean matches) {
        return matches ? ConditionState.PASS : ConditionState.FAIL;
    }

    private static String readString(NBTTagCompound tag, String key) {
        ConditionPayloads.requireKeys(tag, key);
        ConditionPayloads.requireTag(tag, key, 8);
        return tag.getString(key);
    }

    private static NBTTagCompound stringTag(String key, String value) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(key, value);
        return tag;
    }

    private static ConditionRange readRange(NBTTagCompound tag) {
        ConditionPayloads.requireKeys(tag, "Minimum", "Maximum");
        ConditionPayloads.requireTag(tag, "Minimum", 3);
        ConditionPayloads.requireTag(tag, "Maximum", 3);
        return new ConditionRange(tag.getInteger("Minimum"), tag.getInteger("Maximum"));
    }

    private static NBTTagCompound writeRange(ConditionRange range) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Minimum", range.minimum());
        tag.setInteger("Maximum", range.maximum());
        return tag;
    }
}
