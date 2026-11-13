package gregtech.mixins.minecraft;

import gregtech.common.worldgen.LootTableHelper;

import net.minecraft.world.storage.loot.LootEntry;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Type;

/**
 * Lets GregTech's own loot entry types through the vanilla loot entry serializer. The Gson method names are not
 * obfuscated, and the full descriptors pick the real methods over their bridges.
 */
@Mixin(LootEntry.Serializer.class)
public class LootEntrySerializerMixin {

    @Inject(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/world/storage/loot/LootEntry;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void gregtech$deserializeCustomEntry(JsonElement json, Type type, JsonDeserializationContext context,
                                                 CallbackInfoReturnable<LootEntry> cir) {
        LootEntry entry = LootTableHelper.deserializeCustomEntry(json, context);
        if (entry != null) cir.setReturnValue(entry);
    }

    @Inject(method = "serialize(Lnet/minecraft/world/storage/loot/LootEntry;Ljava/lang/reflect/Type;Lcom/google/gson/JsonSerializationContext;)Lcom/google/gson/JsonElement;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void gregtech$serializeCustomEntry(LootEntry entry, Type type, JsonSerializationContext context,
                                               CallbackInfoReturnable<JsonElement> cir) {
        JsonElement json = LootTableHelper.serializeCustomEntry(entry, context);
        if (json != null) cir.setReturnValue(json);
    }
}
