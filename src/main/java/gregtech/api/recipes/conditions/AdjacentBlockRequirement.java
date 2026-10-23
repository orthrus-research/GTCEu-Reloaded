package gregtech.api.recipes.conditions;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Distinct neighboring blocks must satisfy each group. Alternatives inside one group are interchangeable. */
public final class AdjacentBlockRequirement {

    public static final int MAX_GROUPS = 8;
    public static final int MAX_ALTERNATIVES = 8;
    private static final List<BlockPos> OFFSETS = createOffsets();

    private final List<List<BlockRequirement>> groups;

    public AdjacentBlockRequirement(List<? extends List<BlockRequirement>> groups) {
        if (groups.isEmpty() || groups.size() > MAX_GROUPS) {
            throw new IllegalArgumentException("Adjacent blocks require 1..8 groups");
        }
        List<List<BlockRequirement>> owned = new ArrayList<>(groups.size());
        for (List<BlockRequirement> group : groups) {
            if (group.isEmpty() || group.size() > MAX_ALTERNATIVES) {
                throw new IllegalArgumentException("Each adjacent block group requires 1..8 alternatives");
            }
            List<BlockRequirement> entries = new ArrayList<>(group.size());
            for (BlockRequirement entry : group) entries.add(Objects.requireNonNull(entry));
            owned.add(Collections.unmodifiableList(entries));
        }
        this.groups = Collections.unmodifiableList(owned);
    }

    public List<List<BlockRequirement>> groups() {
        return groups;
    }

    ConditionState test(RecipeConditionContext context) {
        boolean unresolved = false;
        for (List<BlockRequirement> group : groups) {
            for (BlockRequirement entry : group) unresolved |= !entry.isRegistered();
        }
        IBlockState[] neighbors = new IBlockState[OFFSETS.size()];
        boolean unloaded = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos origin = context.position();
        for (int i = 0; i < neighbors.length; i++) {
            BlockPos offset = OFFSETS.get(i);
            cursor.setPos(origin.getX() + offset.getX(), origin.getY() + offset.getY(), origin.getZ() + offset.getZ());
            neighbors[i] = context.blockAt(cursor);
            unloaded |= neighbors[i] == null;
        }
        boolean[][] edges = new boolean[groups.size()][neighbors.length];
        for (int group = 0; group < groups.size(); group++) {
            for (int neighbor = 0; neighbor < neighbors.length; neighbor++) {
                if (neighbors[neighbor] == null) continue;
                for (BlockRequirement alternative : groups.get(group)) {
                    if (alternative.matches(neighbors[neighbor])) {
                        edges[group][neighbor] = true;
                        break;
                    }
                }
            }
        }
        int[] owners = new int[neighbors.length];
        Arrays.fill(owners, -1);
        for (int group = 0; group < groups.size(); group++) {
            if (!assign(group, edges, owners, new boolean[neighbors.length])) {
                return unloaded || unresolved ? ConditionState.UNAVAILABLE : ConditionState.FAIL;
            }
        }
        return ConditionState.PASS;
    }

    // Each search visits at most 18 neighbors and 8 groups; reassignment avoids greedy false negatives.
    private static boolean assign(int group, boolean[][] edges, int[] owners, boolean[] visited) {
        for (int neighbor = 0; neighbor < owners.length; neighbor++) {
            if (!edges[group][neighbor] || visited[neighbor]) continue;
            visited[neighbor] = true;
            if (owners[neighbor] < 0 || assign(owners[neighbor], edges, owners, visited)) {
                owners[neighbor] = group;
                return true;
            }
        }
        return false;
    }

    NBTTagCompound serialize() {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList required = new NBTTagList();
        for (List<BlockRequirement> group : groups) {
            NBTTagList alternatives = new NBTTagList();
            for (BlockRequirement entry : group) alternatives.appendTag(entry.serialize());
            required.appendTag(alternatives);
        }
        tag.setTag("Groups", required);
        return tag;
    }

    static AdjacentBlockRequirement deserialize(NBTTagCompound tag) {
        ConditionPayloads.requireKeys(tag, "Groups");
        ConditionPayloads.requireTag(tag, "Groups", 9);
        NBTTagList required = tag.getTagList("Groups", 9);
        if (required.tagCount() < 1 || required.tagCount() > MAX_GROUPS) {
            throw new IllegalArgumentException("Adjacent blocks require 1..8 groups");
        }
        List<List<BlockRequirement>> groups = new ArrayList<>(required.tagCount());
        for (int i = 0; i < required.tagCount(); i++) {
            NBTTagList alternatives = (NBTTagList) required.get(i);
            if (alternatives.getTagType() != 10 || alternatives.tagCount() < 1 ||
                    alternatives.tagCount() > MAX_ALTERNATIVES) {
                throw new IllegalArgumentException("Invalid adjacent block alternatives");
            }
            List<BlockRequirement> group = new ArrayList<>(alternatives.tagCount());
            for (int j = 0; j < alternatives.tagCount(); j++) {
                group.add(BlockRequirement.deserialize(alternatives.getCompoundTagAt(j)));
            }
            groups.add(group);
        }
        return new AdjacentBlockRequirement(groups);
    }

    ConditionDescription description() {
        List<String> summary = new ArrayList<>();
        for (List<BlockRequirement> group : groups) {
            StringBuilder names = new StringBuilder();
            for (BlockRequirement entry : group) {
                if (names.length() != 0) names.append(" | ");
                names.append(entry);
            }
            summary.add("(" + names + ")");
        }
        String names = String.join("; ", summary);
        if (names.length() > 128) names = names.substring(0, 125) + "...";
        return new ConditionDescription("gregtech.recipe.condition.adjacent", Integer.toString(groups.size()), names);
    }

    private static List<BlockPos> createOffsets() {
        List<BlockPos> offsets = new ArrayList<>(18);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    int distance = Math.abs(x) + Math.abs(y) + Math.abs(z);
                    if (distance == 1 || distance == 2) offsets.add(new BlockPos(x, y, z));
                }
            }
        }
        return Collections.unmodifiableList(offsets);
    }
}
