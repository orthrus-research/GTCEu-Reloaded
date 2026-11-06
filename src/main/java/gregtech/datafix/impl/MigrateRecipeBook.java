package gregtech.datafix.impl;

import gregtech.datafix.GTDataVersion;
import gregtech.datafix.RecipeBookMigrationTable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.datafix.IFixableData;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.function.Predicate;

/** Preserve all surviving discoveries when an old recipe ID had multiple possible material owners. */
public final class MigrateRecipeBook implements IFixableData {

    private final RecipeBookMigrationTable mappings;
    private final Predicate<ResourceLocation> available;

    public MigrateRecipeBook() {
        this(RecipeBookMigrationTable.GENERATED, name -> {
            var recipe = ForgeRegistries.RECIPES.getValue(name);
            return recipe != null && !recipe.isDynamic() && !recipe.getRecipeOutput().isEmpty();
        });
    }

    public MigrateRecipeBook(RecipeBookMigrationTable mappings, Predicate<ResourceLocation> available) {
        this.mappings = Objects.requireNonNull(mappings, "recipe migration mappings");
        this.available = Objects.requireNonNull(available, "current recipe predicate");
    }

    @Override
    public int getFixVersion() {
        return GTDataVersion.V3_MATERIAL_RECIPE_IDENTITIES.ordinal();
    }

    @Override
    public @NotNull NBTTagCompound fixTagCompound(@NotNull NBTTagCompound compound) {
        if (!compound.hasKey("recipeBook", 10)) return compound;
        var book = compound.getCompoundTag("recipeBook");
        migrate(book, "recipes");
        migrate(book, "toBeDisplayed");
        return compound;
    }

    private void migrate(NBTTagCompound book, String key) {
        if (!(book.getTag(key) instanceof NBTTagList entries) || entries.getTagType() != 8) return;
        var names = new LinkedHashSet<String>();
        boolean mapped = false;
        for (int index = 0; index < entries.tagCount(); index++) {
            String previous = entries.getStringTagAt(index);
            var targets = mappings.targets(new ResourceLocation(previous));
            if (targets.isEmpty()) {
                names.add(previous);
            } else {
                mapped = true;
                for (ResourceLocation target : targets) {
                    if (available.test(target)) names.add(target.toString());
                }
            }
        }
        if (!mapped) return;
        var migrated = new NBTTagList();
        names.forEach(name -> migrated.appendTag(new NBTTagString(name)));
        book.setTag(key, migrated);
    }
}
