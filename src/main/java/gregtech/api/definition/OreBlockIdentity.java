package gregtech.api.definition;

import net.minecraft.util.ResourceLocation;

import java.util.Objects;

/** Startup identity of one material's generated ore blocks; the stone group is appended to the base. */
public record OreBlockIdentity(SourceOrigin origin, ResourceLocation material, ResourceLocation registryBase) {

    public OreBlockIdentity {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(registryBase, "registryBase");
    }

    public ResourceLocation blockName(int stoneGroup) {
        if (stoneGroup < 0) throw new IllegalArgumentException("Stone group must be nonnegative");
        return new ResourceLocation(registryBase.getNamespace(), registryBase.getPath() + "_" + stoneGroup);
    }
}
