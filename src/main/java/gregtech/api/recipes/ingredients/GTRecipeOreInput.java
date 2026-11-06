package gregtech.api.recipes.ingredients;

import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.UnificationEntry;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GTRecipeOreInput extends GTRecipeInput {

    private final int ore;
    private final List<ItemStack> entries;
    private final String oreName;
    private final @Nullable ResourceLocation materialIdentity;

    public GTRecipeOreInput(String ore) {
        this(ore, 1);
    }

    public GTRecipeOreInput(String ore, int amount) {
        this(OreDictionary.getOreID(ore), amount);
    }

    public GTRecipeOreInput(OrePrefix prefix, Material material) {
        this(prefix, material, 1);
    }

    public GTRecipeOreInput(OrePrefix prefix, Material material, int amount) {
        this(OreDictionary.getOreID(new UnificationEntry(prefix, material).toString()),
                material == null ? null : material.getResourceLocation(), amount);
    }

    public GTRecipeOreInput(String oreName, ResourceLocation materialIdentity, int amount) {
        this(OreDictionary.getOreID(oreName), Objects.requireNonNull(materialIdentity, "materialIdentity"), amount);
    }

    protected GTRecipeOreInput(int ore, int amount) {
        this(ore, null, amount);
    }

    private GTRecipeOreInput(int ore, @Nullable ResourceLocation materialIdentity, int amount) {
        this.ore = ore;
        this.oreName = OreDictionary.getOreName(ore);
        this.materialIdentity = materialIdentity;
        // Cleanroom retains this list across registration, removal, reload and reverse-map rebakes.
        this.entries = OreDictionary.getOres(oreName);
        this.amount = amount;
    }

    /**
     * @deprecated Use constructors
     */
    @Deprecated
    public static GTRecipeInput getOrCreate(String ore, int amount) {
        return new GTRecipeOreInput(ore, amount);
    }

    /**
     * @deprecated Use constructors
     */
    @Deprecated
    public static GTRecipeInput getOrCreate(String ore) {
        return new GTRecipeOreInput(ore);
    }

    /**
     * @deprecated Use constructors
     */
    @Deprecated
    public static GTRecipeInput getOrCreate(OrePrefix prefix, Material material, int amount) {
        return new GTRecipeOreInput(prefix, material, amount);
    }

    /**
     * @deprecated Use constructors
     */
    @Deprecated
    public static GTRecipeInput getOrCreate(OrePrefix prefix, Material material) {
        return new GTRecipeOreInput(prefix, material);
    }

    @Override
    protected GTRecipeOreInput copy() {
        GTRecipeOreInput copy = new GTRecipeOreInput(ore, materialIdentity, this.amount);
        copy.isConsumable = this.isConsumable;
        copy.nbtMatcher = this.nbtMatcher;
        copy.nbtCondition = this.nbtCondition;
        return copy;
    }

    @Override
    public GTRecipeInput copyWithAmount(int amount) {
        GTRecipeOreInput copy = new GTRecipeOreInput(ore, materialIdentity, amount);
        copy.isConsumable = this.isConsumable;
        copy.nbtMatcher = this.nbtMatcher;
        copy.nbtCondition = this.nbtCondition;
        return copy;
    }

    /** Returns an owned, quantity-adjusted snapshot; matching reads native membership directly. */
    @Override
    public ItemStack[] getInputStacks() {
        if (materialIdentity == null) {
            ItemStack[] snapshot = new ItemStack[entries.size()];
            for (int i = 0; i < snapshot.length; i++) {
                snapshot[i] = entries.get(i).copy();
                snapshot[i].setCount(amount);
            }
            return snapshot;
        }
        List<ItemStack> snapshot = new ArrayList<>(entries.size());
        for (ItemStack entry : entries) {
            if (!matchesMaterial(entry)) continue;
            ItemStack copy = entry.copy();
            copy.setCount(amount);
            snapshot.add(copy);
        }
        return snapshot.toArray(ItemStack[]::new);
    }

    @Override
    public @Nullable ResourceLocation getMaterialIdentity() {
        return materialIdentity;
    }

    private boolean matchesMaterial(ItemStack member) {
        return materialIdentity == null || materialIdentity.equals(
                OreDictUnifier.getOreMaterial(oreName, member.getItem(), member.getItemDamage()));
    }

    @Override
    public boolean isOreDict() {
        return true;
    }

    @Override
    public int getOreDict() {
        return ore;
    }

    @Override
    public boolean acceptsStack(@Nullable ItemStack input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        for (int i = 0; i < entries.size(); i++) {
            ItemStack target = entries.get(i);
            if (OreDictionary.itemMatches(target, input, false) && matchesMaterial(target)) {
                return nbtMatcher == null || nbtMatcher.evaluate(input, nbtCondition);
            }
        }
        return false;
    }

    @Override
    protected int computeHash() {
        return Objects.hash(amount, ore, materialIdentity, isConsumable, nbtMatcher, nbtCondition);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof GTRecipeOreInput)) {
            return false;
        }
        GTRecipeOreInput other = (GTRecipeOreInput) obj;

        if (this.amount != other.amount || this.isConsumable != other.isConsumable) return false;
        if (!Objects.equals(this.nbtMatcher, other.nbtMatcher)) return false;
        if (!Objects.equals(this.nbtCondition, other.nbtCondition)) return false;
        return ore == other.ore && Objects.equals(materialIdentity, other.materialIdentity);
    }

    @Override
    public boolean equalIgnoreAmount(GTRecipeInput input) {
        if (this == input) return true;
        if (!(input instanceof GTRecipeOreInput)) {
            return false;
        }
        GTRecipeOreInput other = (GTRecipeOreInput) input;

        if (!Objects.equals(this.nbtMatcher, other.nbtMatcher)) return false;
        if (!Objects.equals(this.nbtCondition, other.nbtCondition)) return false;
        return ore == other.ore && Objects.equals(materialIdentity, other.materialIdentity);
    }

    @Override
    public String toString() {
        // noinspection StringConcatenationMissingWhitespace
        return amount + "x" + oreName + (materialIdentity == null ? "" : " [" + materialIdentity + "]");
    }
}
