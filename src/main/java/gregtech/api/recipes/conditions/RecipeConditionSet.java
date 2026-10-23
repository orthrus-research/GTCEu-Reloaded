package gregtech.api.recipes.conditions;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable conjunction of source recipes. Same-type alternatives are resolved inside each source, never across append.
 */
public final class RecipeConditionSet {

    public static final int MAX_LEAVES = 32;
    public static final int MAX_CLAUSES = 16;
    public static final int MAX_ALTERNATIVES = 8;
    private static final int MAX_BYTES = 65536;
    private static final int MAX_NODES = 4096;
    private static final int MAX_DEPTH = 12;

    public static final RecipeConditionSet EMPTY = new RecipeConditionSet(Collections.emptyList(),
            Collections.emptyList());

    private final List<RecipeCondition<?>> primary;
    private final List<List<RecipeCondition<?>>> appended;
    private final List<Clause> clauses;
    private final List<List<ConditionDescription>> descriptions;

    private RecipeConditionSet(List<RecipeCondition<?>> primary, List<List<RecipeCondition<?>>> appended) {
        this.primary = owned(primary);
        List<Clause> appendedClauses = new ArrayList<>();
        for (List<RecipeCondition<?>> source : appended) resolve(owned(source), appendedClauses);
        if (appendedClauses.size() > MAX_CLAUSES)
            throw new IllegalArgumentException("Too many appended condition groups");
        List<List<RecipeCondition<?>>> sources = new ArrayList<>();
        for (Clause clause : appendedClauses) sources.add(clause.conditions);
        this.appended = Collections.unmodifiableList(sources);

        Set<RecipeCondition<?>> leaves = new LinkedHashSet<>(this.primary);
        for (List<RecipeCondition<?>> source : sources) leaves.addAll(source);
        if (leaves.size() > MAX_LEAVES)
            throw new IllegalArgumentException("At most 32 distinct conditions are allowed");

        List<Clause> resolved = new ArrayList<>();
        resolve(this.primary, resolved);
        for (List<RecipeCondition<?>> source : sources) resolve(source, resolved);
        if (resolved.size() > MAX_CLAUSES)
            throw new IllegalArgumentException("At most 16 conjunctive condition groups are allowed");
        this.clauses = Collections.unmodifiableList(resolved);
        List<List<ConditionDescription>> descriptions = new ArrayList<>();
        for (Clause clause : resolved) descriptions.add(clause.failure.alternatives());
        this.descriptions = Collections.unmodifiableList(descriptions);
        if (!bounded(serialize()))
            throw new IllegalArgumentException("Combined condition payload exceeds 64 KiB or structural bounds");
    }

    public RecipeConditionSet add(RecipeCondition<?> condition) {
        Objects.requireNonNull(condition);
        if (primary.contains(condition)) return this;
        List<RecipeCondition<?>> updated = new ArrayList<>(primary);
        updated.add(condition);
        return new RecipeConditionSet(updated, appended);
    }

    public RecipeConditionSet and(RecipeConditionSet other) {
        Objects.requireNonNull(other);
        if (other.isEmpty()) return this;
        List<List<RecipeCondition<?>>> sources = new ArrayList<>(appended);
        if (!other.primary.isEmpty()) sources.add(other.primary);
        sources.addAll(other.appended);
        return new RecipeConditionSet(primary, sources);
    }

    public boolean isEmpty() {
        return clauses.isEmpty();
    }

    public int clauseCount() {
        return clauses.size();
    }

    public List<List<ConditionDescription>> descriptions() {
        return descriptions;
    }

    public ConditionEvaluation evaluate(RecipeConditionContext context) {
        for (Clause clause : clauses) {
            ConditionEvaluation result = clause.evaluate(context);
            if (!result.isSuccess()) return result;
        }
        return ConditionEvaluation.PASS;
    }

    public NBTTagCompound serialize() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Version", 1);
        tag.setTag("Primary", writeConditions(primary));
        NBTTagList sources = new NBTTagList();
        for (List<RecipeCondition<?>> source : appended) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setTag("Conditions", writeConditions(source));
            sources.appendTag(entry);
        }
        tag.setTag("Appended", sources);
        return tag;
    }

    public static RecipeConditionSet deserialize(NBTBase input) {
        if (!(input instanceof NBTTagCompound) || !bounded(input)) return unavailable(input);
        NBTTagCompound tag = (NBTTagCompound) input;
        try {
            ConditionPayloads.requireKeys(tag, "Version", "Primary", "Appended");
            ConditionPayloads.requireTag(tag, "Version", 3);
            ConditionPayloads.requireTag(tag, "Primary", 9);
            ConditionPayloads.requireTag(tag, "Appended", 9);
            if (tag.getInteger("Version") != 1) return unavailable(input);
            List<RecipeCondition<?>> primary = readConditions((NBTTagList) tag.getTag("Primary"));
            NBTTagList entries = (NBTTagList) tag.getTag("Appended");
            if (entries.tagCount() > MAX_CLAUSES || (entries.tagCount() > 0 && entries.getTagType() != 10)) {
                return unavailable(input);
            }
            List<List<RecipeCondition<?>>> sources = new ArrayList<>();
            for (int i = 0; i < entries.tagCount(); i++) {
                NBTTagCompound entry = entries.getCompoundTagAt(i);
                ConditionPayloads.requireKeys(entry, "Conditions");
                ConditionPayloads.requireTag(entry, "Conditions", 9);
                sources.add(readConditions((NBTTagList) entry.getTag("Conditions")));
            }
            return new RecipeConditionSet(primary, sources);
        } catch (RuntimeException exception) {
            return unavailable(input);
        }
    }

    private static RecipeConditionSet unavailable(NBTBase input) {
        // Keep a small malformed payload for future diagnosis; oversized/deep payloads become a fixed blocking marker.
        RecipeCondition<?> unavailable = RecipeCondition.unavailable();
        if (ConditionPayloads.bounded(input, 5, 8192, 256)) {
            NBTTagCompound envelope = new NBTTagCompound();
            envelope.setString("Type", "gregtech:invalid_condition_set");
            envelope.setInteger("Version", -1);
            envelope.setBoolean("Inverted", false);
            NBTTagCompound data = new NBTTagCompound();
            data.setTag("Payload", input.copy());
            envelope.setTag("Data", data);
            unavailable = RecipeCondition.deserialize(envelope);
        }
        return EMPTY.add(unavailable);
    }

    private static boolean bounded(NBTBase tag) {
        return ConditionPayloads.bounded(tag, MAX_DEPTH, MAX_BYTES, MAX_NODES);
    }

    private static List<RecipeCondition<?>> owned(List<RecipeCondition<?>> conditions) {
        if (conditions.size() > MAX_LEAVES) throw new IllegalArgumentException("Too many condition entries");
        Set<RecipeCondition<?>> unique = new LinkedHashSet<>();
        for (RecipeCondition<?> condition : conditions) unique.add(Objects.requireNonNull(condition));
        return Collections.unmodifiableList(new ArrayList<>(unique));
    }

    private static NBTTagList writeConditions(List<RecipeCondition<?>> conditions) {
        NBTTagList list = new NBTTagList();
        for (RecipeCondition<?> condition : conditions) list.appendTag(condition.serialize());
        return list;
    }

    private static List<RecipeCondition<?>> readConditions(NBTTagList entries) {
        if (entries.tagCount() > MAX_LEAVES || (entries.tagCount() > 0 && entries.getTagType() != 10)) {
            throw new IllegalArgumentException("Invalid condition list");
        }
        List<RecipeCondition<?>> conditions = new ArrayList<>();
        for (int i = 0; i < entries.tagCount(); i++) conditions.add(RecipeCondition.deserialize(entries.get(i)));
        return conditions;
    }

    private static void resolve(List<RecipeCondition<?>> source, List<Clause> target) {
        List<List<RecipeCondition<?>>> groups = new ArrayList<>();
        Map<String, List<RecipeCondition<?>>> alternatives = new LinkedHashMap<>();
        for (RecipeCondition<?> condition : source) {
            if (!condition.isAlternative()) {
                groups.add(Collections.singletonList(condition));
            } else {
                List<RecipeCondition<?>> group = alternatives.get(condition.typeId());
                if (group == null) {
                    group = new ArrayList<>();
                    alternatives.put(condition.typeId(), group);
                    groups.add(group);
                }
                group.add(condition);
                if (group.size() > MAX_ALTERNATIVES)
                    throw new IllegalArgumentException("At most 8 same-type alternatives are allowed");
            }
        }
        for (List<RecipeCondition<?>> group : groups) {
            Clause clause = new Clause(group);
            boolean present = false;
            for (Clause existing : target) {
                if (existing.conditions.size() == clause.conditions.size() &&
                        existing.conditions.containsAll(clause.conditions)) {
                    present = true;
                    break;
                }
            }
            if (!present) target.add(clause);
        }
    }

    private static final class Clause {

        private final List<RecipeCondition<?>> conditions;
        private final ConditionEvaluation failure;
        private final ConditionEvaluation unavailable;

        private Clause(List<RecipeCondition<?>> conditions) {
            this.conditions = Collections.unmodifiableList(new ArrayList<>(conditions));
            List<ConditionDescription> descriptions = new ArrayList<>();
            for (RecipeCondition<?> condition : conditions) descriptions.add(condition.description());
            this.failure = new ConditionEvaluation(ConditionState.FAIL, descriptions);
            this.unavailable = new ConditionEvaluation(ConditionState.UNAVAILABLE, descriptions);
        }

        private ConditionEvaluation evaluate(RecipeConditionContext context) {
            boolean missing = false;
            for (RecipeCondition<?> condition : conditions) {
                ConditionState result = condition.test(context);
                if (result == ConditionState.PASS) return ConditionEvaluation.PASS;
                missing |= result == ConditionState.UNAVAILABLE;
            }
            return missing ? unavailable : failure;
        }
    }
}
