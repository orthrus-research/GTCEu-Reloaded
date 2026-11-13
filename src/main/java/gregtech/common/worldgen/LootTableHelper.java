package gregtech.common.worldgen;

import net.minecraft.item.ItemStack;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.google.common.collect.Lists;
import com.google.gson.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class LootTableHelper {

    private static final Map<String, LootTableEntrySerializer<?>> serializerMap = new HashMap<>();

    public interface LootTableEntrySerializer<T extends LootEntry> {

        T deserialize(JsonObject object, JsonDeserializationContext deserializationContext, int weightIn, int qualityIn,
                      LootCondition[] conditionsIn);
    }

    public interface SerializableLootEntry {

        String getType();

        int getWeight();

        int getQuality();

        LootCondition[] getConditions();

        void serializeEntry(JsonObject object, JsonSerializationContext context);
    }

    public static void registerLootEntry(String type, LootTableEntrySerializer<?> serializer) {
        serializerMap.put(type, serializer);
    }

    /**
     * Registers GregTech's loot entry types. They are read and written through a head injection on the vanilla
     * loot entry serializer, see {@link #deserializeCustomEntry} and {@link #serializeCustomEntry}.
     */
    public static void initialize() {
        registerLootEntry("gregtech:meta_item", LootEntryMetaItem::deserialize);
        registerLootEntry("gregtech:ore_dict", LootEntryOreDict::deserialize);
    }

    /**
     * @return the entry for a registered GregTech type, or {@code null} to leave the JSON to vanilla
     */
    @Nullable
    public static LootEntry deserializeCustomEntry(JsonElement json, JsonDeserializationContext context) {
        if (!json.isJsonObject()) return null;
        JsonObject jsonobject = json.getAsJsonObject();
        if (!jsonobject.has("type") || !jsonobject.get("type").isJsonPrimitive()) return null;
        LootTableEntrySerializer<?> serializer = serializerMap.get(jsonobject.get("type").getAsString());
        if (serializer == null) return null;
        int weight = JsonUtils.getInt(jsonobject, "weight", 1);
        int quality = JsonUtils.getInt(jsonobject, "quality", 0);
        LootCondition[] lootConditions;
        if (jsonobject.has("conditions")) {
            lootConditions = JsonUtils.deserializeClass(jsonobject, "conditions", context, LootCondition[].class);
        } else {
            lootConditions = new LootCondition[0];
        }
        return serializer.deserialize(jsonobject, context, weight, quality, lootConditions);
    }

    /**
     * @return the JSON for a GregTech entry, or {@code null} to leave the entry to vanilla
     */
    @Nullable
    public static JsonElement serializeCustomEntry(LootEntry src, JsonSerializationContext context) {
        if (!(src instanceof SerializableLootEntry entry)) return null;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("weight", entry.getWeight());
        jsonObject.addProperty("quality", entry.getQuality());
        LootCondition[] conditions = entry.getConditions();
        if (conditions.length > 0) {
            jsonObject.add("conditions", context.serialize(entry.getConditions()));
        }
        jsonObject.addProperty("type", entry.getType());
        entry.serializeEntry(jsonObject, context);
        return jsonObject;
    }

    public static void fillInventory(IItemHandlerModifiable inventory, Random rand, List<ItemStack> lootList) {
        List<Integer> randomSlots = getEmptySlotsRandomized(inventory, rand);
        shuffleItems(lootList, randomSlots.size(), rand);

        for (ItemStack itemstack : lootList) {
            if (randomSlots.isEmpty()) {
                return;
            }
            if (itemstack.isEmpty()) {
                inventory.setStackInSlot(randomSlots.remove(randomSlots.size() - 1), ItemStack.EMPTY);
            } else {
                inventory.setStackInSlot(randomSlots.remove(randomSlots.size() - 1), itemstack);
            }
        }
    }

    /**
     * shuffles items by changing their order and splitting stacks
     */
    private static void shuffleItems(List<ItemStack> stacks, int slotCount, Random rand) {
        List<ItemStack> list = Lists.newArrayList();
        Iterator<ItemStack> iterator = stacks.iterator();

        while (iterator.hasNext()) {
            ItemStack itemstack = iterator.next();

            if (itemstack.isEmpty()) {
                iterator.remove();
            } else if (itemstack.getCount() > 4) {
                list.add(itemstack);
                iterator.remove();
            }
        }

        slotCount = slotCount - stacks.size();

        while (slotCount > 0 && !list.isEmpty()) {
            ItemStack itemstack2 = list.remove(MathHelper.getInt(rand, 0, list.size() - 1));
            int i = MathHelper.getInt(rand, itemstack2.getCount() / 3, itemstack2.getCount() / 2);
            ItemStack itemstack1 = itemstack2.splitStack(i);

            if (itemstack2.getCount() > 1 && rand.nextBoolean()) {
                list.add(itemstack2);
            } else {
                stacks.add(itemstack2);
            }

            if (itemstack1.getCount() > 1 && rand.nextBoolean()) {
                list.add(itemstack1);
            } else {
                stacks.add(itemstack1);
            }
        }

        stacks.addAll(list);
        Collections.shuffle(stacks, rand);
    }

    private static List<Integer> getEmptySlotsRandomized(IItemHandler inventory, Random rand) {
        List<Integer> list = Lists.newArrayList();
        for (int i = 0; i < inventory.getSlots(); ++i) {
            if (inventory.getStackInSlot(i).isEmpty()) {
                list.add(i);
            }
        }
        Collections.shuffle(list, rand);
        return list;
    }
}
