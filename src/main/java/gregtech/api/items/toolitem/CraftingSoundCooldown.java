package gregtech.api.items.toolitem;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Session-only sound state; values must not retain the player keys. */
final class CraftingSoundCooldown {

    private static final long INTERVAL_NANOS = 1_000_000_000L;
    private static final Map<EntityPlayer, Map<SoundEvent, Long>> LAST_PLAYED = new WeakHashMap<>();

    private CraftingSoundCooldown() {}

    static synchronized boolean acquire(EntityPlayer player, SoundEvent sound, long now) {
        var sounds = LAST_PLAYED.computeIfAbsent(player, ignored -> new HashMap<>());
        Long previous = sounds.get(sound);
        if (previous != null && now - previous < INTERVAL_NANOS) return false;
        sounds.put(sound, now);
        return true;
    }
}
