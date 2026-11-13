package gregtech.common.items.armor;

import net.minecraft.entity.player.EntityPlayer;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * State an armour logic keeps per wearer. One logic object serves every stack of its item type, so state held in
 * its fields would be shared by all wearers, and in single-player by the client and server threads.
 *
 * @param <T> the state type
 */
final class PerPlayerState<T> {

    // weak keys: a player that logs out is not kept; synchronized: client and server threads both tick armour
    private final Map<EntityPlayer, T> states = Collections.synchronizedMap(new WeakHashMap<>());
    private final Supplier<T> factory;

    PerPlayerState(@NotNull Supplier<T> factory) {
        this.factory = factory;
    }

    @NotNull
    T get(@NotNull EntityPlayer player) {
        return states.computeIfAbsent(player, p -> factory.get());
    }
}
