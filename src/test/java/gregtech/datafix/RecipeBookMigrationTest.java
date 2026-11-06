package gregtech.datafix;

import gregtech.Bootstrap;
import gregtech.datafix.impl.MigrateRecipeBook;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.util.datafix.FixTypes;
import net.minecraftforge.common.util.CompoundDataFixer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecipeBookMigrationTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void migrationPreservesBothDiscoveryListsAndUnrelatedPlayerDataWhileRespectingRemovedRecipes() {
        var table = new RecipeBookMigrationTable();
        table.register(id("gregtech:shared"), id("susy:shared"));
        table.register(id("gregtech:shared"), id("gregtech:shared"));
        table.register(id("gregtech:old_wire/71"), id("susy:new_wire"));
        table.register(id("gregtech:removed"), id("susy:removed"));
        var live = new HashSet<>(Set.of(id("gregtech:shared"), id("susy:shared"), id("susy:new_wire")));
        var fixer = new MigrateRecipeBook(table, live::contains);
        var original = player("gregtech:shared", "gregtech:old_wire/71", "gregtech:removed", "other:untouched");
        original.setInteger("Health", 17);
        original.setString("InventorySentinel", "unchanged");
        var result = fixer.fixTagCompound(original.copy());
        var expected = original.copy();
        for (String key : List.of("recipes", "toBeDisplayed")) {
            expected.getCompoundTag("recipeBook").setTag(key,
                    names("gregtech:shared", "susy:shared", "susy:new_wire", "other:untouched"));
        }
        assertEquals(expected, result);
        assertEquals(result, fixer.fixTagCompound(result.copy()));
        live.remove(id("susy:shared"));
        var afterRemoval = fixer.fixTagCompound(original.copy());
        assertEquals(List.of("gregtech:shared", "susy:new_wire", "other:untouched"),
                entries(afterRemoval.getCompoundTag("recipeBook"), "recipes"));
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 0, 1, 2, 3, 4 })
    void forgeVersionGateExpandsOnlyOlderPlayerDataAndResavedDataDoesNotExpandAgain(int savedVersion) {
        var table = new RecipeBookMigrationTable();
        table.register(id("gregtech:shared"), id("gregtech:shared"));
        table.register(id("gregtech:shared"), id("susy:shared"));
        var fixer = new CompoundDataFixer(new DataFixer(1343));
        fixer.init("gregtech", GTDataVersion.currentVersion().ordinal())
                .registerFix(FixTypes.PLAYER, new MigrateRecipeBook(table, ignored -> true));
        var original = player("gregtech:shared");
        original.setInteger("DataVersion", 1343);
        if (savedVersion >= 0) {
            var versions = new NBTTagCompound();
            versions.setInteger("gregtech", savedVersion);
            original.setTag("ForgeDataVersion", versions);
        }
        var migrated = fixer.process(FixTypes.PLAYER, original.copy());
        assertEquals(savedVersion < 3 ? List.of("gregtech:shared", "susy:shared") : List.of("gregtech:shared"),
                entries(migrated.getCompoundTag("recipeBook"), "recipes"));
        fixer.writeVersionData(migrated);
        assertEquals(3, migrated.getCompoundTag("ForgeDataVersion").getInteger("gregtech"));
        migrated.getCompoundTag("recipeBook").setTag("recipes", names("gregtech:shared"));
        assertEquals(migrated, fixer.process(FixTypes.PLAYER, migrated.copy()));
    }

    @Test
    void malformedOrUnrelatedTagsAreNotReplaced() {
        var table = new RecipeBookMigrationTable();
        table.register(id("gregtech:old"), id("susy:new"));
        var fixer = new MigrateRecipeBook(table, ignored -> true);
        var absent = new NBTTagCompound();
        absent.setString("recipeBook", "foreign value");
        assertEquals(absent, fixer.fixTagCompound(absent.copy()));
        var malformed = player();
        var integers = new NBTTagList();
        integers.appendTag(new NBTTagInt(42));
        malformed.getCompoundTag("recipeBook").setTag("recipes", integers);
        assertEquals(malformed, fixer.fixTagCompound(malformed.copy()));
        var unrelated = player("other:unchanged", "other:unchanged");
        assertEquals(unrelated, fixer.fixTagCompound(unrelated.copy()));
    }

    @Test
    void tableSnapshotsCannotMutateMappingsAndRetainTheirCapturedContents() {
        var table = new RecipeBookMigrationTable();
        var previous = id("gregtech:old");
        table.register(previous, id("susy:new"));
        var snapshot = table.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.get(previous).clear());
        assertThrows(UnsupportedOperationException.class, () -> table.targets(previous).clear());
        table.register(previous, id("gregtech:new"));
        assertEquals(List.of(id("susy:new")), snapshot.get(previous));
        assertEquals(List.of(id("gregtech:new"), id("susy:new")), table.targets(previous));
    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation(name);
    }

    private static NBTTagCompound player(String... recipes) {
        var player = new NBTTagCompound();
        var book = new NBTTagCompound();
        book.setBoolean("isGuiOpen", true);
        book.setBoolean("isFilteringCraftable", true);
        book.setTag("recipes", names(recipes));
        book.setTag("toBeDisplayed", names(recipes));
        player.setTag("recipeBook", book);
        return player;
    }

    private static NBTTagList names(String... values) {
        var result = new NBTTagList();
        for (String value : values) result.appendTag(new NBTTagString(value));
        return result;
    }

    private static List<String> entries(NBTTagCompound compound, String key) {
        var entries = compound.getTagList(key, 8);
        var result = new ArrayList<String>();
        for (int index = 0; index < entries.tagCount(); index++) result.add(entries.getStringTagAt(index));
        return result;
    }
}
