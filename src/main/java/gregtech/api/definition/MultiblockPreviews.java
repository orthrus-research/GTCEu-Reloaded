package gregtech.api.definition;

import net.minecraft.util.ResourceLocation;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Server-safe preview visibility declarations, consumed by the client recipe viewer at registration. */
public final class MultiblockPreviews {

    private static final Set<ResourceLocation> hidden = new HashSet<>();

    private MultiblockPreviews() {}

    public static void hide(ResourceLocation machine) {
        hidden.add(Objects.requireNonNull(machine, "machine"));
    }

    public static void show(ResourceLocation machine) {
        hidden.remove(Objects.requireNonNull(machine, "machine"));
    }

    public static boolean isHidden(ResourceLocation machine) {
        return hidden.contains(machine);
    }
}
