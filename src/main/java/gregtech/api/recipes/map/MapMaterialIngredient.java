package gregtech.api.recipes.map;

import gregtech.api.recipes.ingredients.GTRecipeInput;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Qualified ore identity; queries evaluate native NBT policy after identity selection. */
public final class MapMaterialIngredient extends AbstractMapIngredient {

    private final int ore;
    private final ResourceLocation material;
    private final @Nullable GTRecipeInput declaration;
    private final @Nullable ItemStack candidate;

    public MapMaterialIngredient(GTRecipeInput declaration) {
        this.ore = declaration.getOreDict();
        this.material = Objects.requireNonNull(declaration.getMaterialIdentity());
        this.declaration = declaration;
        this.candidate = null;
    }

    public MapMaterialIngredient(int ore, ResourceLocation material, ItemStack candidate) {
        this.ore = ore;
        this.material = material;
        this.declaration = null;
        this.candidate = candidate;
    }

    @Override
    protected int hash() {
        return 31 * ore + material.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof MapMaterialIngredient other) || ore != other.ore || !material.equals(other.material))
            return false;
        if (declaration != null && other.declaration != null) return declaration.equalIgnoreAmount(other.declaration);
        if (candidate != null && other.declaration != null) return other.declaration.acceptsStack(candidate);
        return false;
    }

    @Override
    public boolean isSpecialIngredient() {
        return true;
    }
}
