package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.items.toolitem.IGTTool;
import gregtech.api.items.toolitem.ItemGTTool;
import gregtech.api.items.toolitem.ToolHelper;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.DummyContainer;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.registries.IForgeRegistryModifiable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NativeShapedToolRecipeTest {

    private static final AtomicInteger IDS = new AtomicInteger();
    private Map<Character, IGTTool> symbols;
    private Map<Character, IGTTool> previousSymbols;
    private final List<ResourceLocation> names = new ArrayList<>();
    private String ore;
    private ItemStack tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        Bootstrap.perform();
        var field = ToolHelper.class.getDeclaredField("symbols");
        field.setAccessible(true);
        symbols = (Map<Character, IGTTool>) field.get(null);
        previousSymbols = new HashMap<>(symbols);
        ore = "craftingNativeToolOnly" + IDS.incrementAndGet();
        var item = ItemGTTool.Builder.of("native_tool_test", "tool_" + IDS.incrementAndGet())
                .toolStats(builder -> builder).oreDict(ore).build();
        ToolHelper.registerToolSymbol('~', item);
        tool = item.get(Materials.Steel);
        OreDictionary.registerOre(ore, tool);
    }

    @AfterEach
    void restore() {
        symbols.clear();
        symbols.putAll(previousSymbols);
        OreDictionary.getOres(ore).clear();
        OreDictionary.rebakeMap();
        names.forEach(((IForgeRegistryModifiable<IRecipe>) ForgeRegistries.RECIPES)::remove);
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 3 })
    void toolOnlyRowsRegisterAndReturnTheActualTool(int rows) {
        Object[] pattern = new Object[rows];
        java.util.Arrays.fill(pattern, "~");
        Object[] before = pattern.clone();
        var name = name();
        ModHandler.addShapedRecipe(name.getPath(), new ItemStack(Items.APPLE), pattern);
        assertArrayEquals(before, pattern);
        IRecipe recipe = ForgeRegistries.RECIPES.getValue(name);
        var inventory = new InventoryCrafting(new DummyContainer(), 3, 3);
        for (int row = 0; row < rows; row++) inventory.setInventorySlotContents(row * 3, tool.copy());
        assertTrue(recipe.matches(inventory, null));
        var remainders = recipe.getRemainingItems(inventory);
        for (int row = 0; row < rows; row++) {
            assertFalse(remainders.get(row * 3).isEmpty());
            assertSame(tool.getItem(), remainders.get(row * 3).getItem());
            assertEquals(tool.serializeNBT(), inventory.getStackInSlot(row * 3).serializeNBT());
        }
        inventory.setInventorySlotContents(0, new ItemStack(Items.STICK));
        assertFalse(recipe.matches(inventory, null));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void specializedHelpersKeepCallerArguments(boolean fluid) {
        Object[] pattern = { "~A", 'A', gregtech.api.unification.ore.OrePrefix.dust };
        Object[] before = pattern.clone();
        var name = name();
        if (fluid) ModHandler.addFluidReplaceRecipe(name.getPath(), new ItemStack(Items.WATER_BUCKET), pattern);
        else ModHandler.addShapedEnergyTransferRecipe(name.getPath(), new ItemStack(Items.APPLE),
                stack -> false, false, false, pattern);
        assertArrayEquals(before, pattern);
        assertNotNull(ForgeRegistries.RECIPES.getValue(name));
    }

    private ResourceLocation name() {
        var name = new ResourceLocation("gregtech", "native_tool_only_" + IDS.incrementAndGet());
        names.add(name);
        return name;
    }
}
