package gregtech.api.items.toolitem;

import gregtech.Bootstrap;
import gregtech.api.unification.material.Materials;

import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static gregtech.api.items.toolitem.ToolHelper.*;
import static org.junit.jupiter.api.Assertions.*;

class ToolStatReadTest {

    private static final AtomicInteger IDS = new AtomicInteger();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void freshToolReadsKeepOriginalNbtAndValues() {
        var tool = tool(new ToolDefinitionBuilder().baseEfficiency(3).efficiencyMultiplier(2)
                .attackDamage(4).attackSpeed(-2).baseQuality(1).build());
        var stack = tool.get(Materials.Iron);
        var before = stack.serializeNBT().copy();
        for (int i = 0; i < 3; i++) {
            assertEquals(7, tool.getTotalToolSpeed(stack));
            assertEquals(6, tool.getTotalAttackDamage(stack));
            assertEquals(-2, tool.getTotalAttackSpeed(stack));
            assertEquals(255, tool.getTotalMaxDurability(stack));
            assertEquals(14, tool.getTotalEnchantability(stack));
            assertEquals(3, tool.getTotalHarvestLevel(stack));
            assertEquals(0, stack.getItemDamage());
            assertSame(Materials.Iron, tool.getToolMaterial(stack));
            assertEquals(before, stack.serializeNBT());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "absent", "empty", "wrong_type", "unknown_material" })
    void fallbackReadsDoNotRepairOrAttachTags(String state) {
        var tool = tool(new ToolDefinitionBuilder().build());
        var stack = new ItemStack(tool);
        stack.setTagCompound(null);
        if (state.equals("empty")) stack.setTagCompound(new NBTTagCompound());
        if (state.equals("wrong_type")) {
            var root = new NBTTagCompound();
            root.setString(TOOL_TAG_KEY, "preserve me");
            stack.setTagCompound(root);
        }
        if (state.equals("unknown_material")) getToolTag(stack).setString(MATERIAL_KEY, "missing:material");
        var before = stack.serializeNBT().copy();
        boolean hadTag = stack.hasTagCompound();
        readStats(tool, stack);
        assertSame(Materials.Iron, tool.getToolMaterial(stack));
        assertEquals(hadTag, stack.hasTagCompound());
        assertEquals(before, stack.serializeNBT());
    }

    @Test
    void storedOverridesSurviveReadsAndSerialization() throws Exception {
        var tool = tool(new ToolDefinitionBuilder().build());
        var stack = tool.get(Materials.Iron);
        var tag = getToolTag(stack);
        tag.setFloat(TOOL_SPEED_KEY, 0);
        tag.setFloat(ATTACK_DAMAGE_KEY, 19.5f);
        tag.setFloat(ATTACK_SPEED_KEY, -3.5f);
        tag.setInteger(MAX_DURABILITY_KEY, 987);
        tag.setInteger(ENCHANTABILITY_KEY, 0);
        tag.setInteger(HARVEST_LEVEL_KEY, 7);
        tag.setInteger(DURABILITY_KEY, 123);
        var restored = stack.copy();
        var bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(stack.getTagCompound(), bytes);
        restored.setTagCompound(CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes.toByteArray())));
        var before = restored.serializeNBT().copy();
        assertEquals(0, tool.getTotalToolSpeed(restored));
        assertEquals(19.5f, tool.getTotalAttackDamage(restored));
        assertEquals(-3.5f, tool.getTotalAttackSpeed(restored));
        assertEquals(987, restored.getMaxDamage());
        assertEquals(0, tool.getTotalEnchantability(restored));
        assertEquals(7, tool.getTotalHarvestLevel(restored));
        assertEquals(123, restored.getItemDamage());
        assertEquals(before, restored.serializeNBT());
    }

    @Test
    void wrongTypedOverridesArePreservedWhileUsingTheExistingFallback() {
        var tool = tool(new ToolDefinitionBuilder().attackDamage(4).build());
        var stack = tool.get(Materials.Iron);
        getToolTag(stack).setString(ATTACK_DAMAGE_KEY, "not a float");
        getToolTag(stack).setFloat(DURABILITY_KEY, 42.5f);
        var before = stack.serializeNBT().copy();
        assertEquals(6, tool.getTotalAttackDamage(stack));
        assertEquals(0, stack.getItemDamage());
        assertEquals(before, stack.serializeNBT());
    }

    @Test
    void definitionCanDeriveFromCurrentStackWithoutAnImplicitFirstReadCache() {
        var delegate = new ToolDefinitionBuilder().build();
        var definition = (IGTToolDefinition) Proxy.newProxyInstance(IGTToolDefinition.class.getClassLoader(),
                new Class<?>[] { IGTToolDefinition.class }, (proxy, method, args) -> {
                    if (method.getName().equals("getBaseDamage")) return (float) ((ItemStack) args[0]).getCount();
                    return method.invoke(delegate, args);
                });
        var tool = tool(definition);
        var stack = tool.get(Materials.Iron);
        assertEquals(3, tool.getTotalAttackDamage(stack));
        stack.setCount(2);
        var before = stack.serializeNBT().copy();
        assertEquals(4, tool.getTotalAttackDamage(stack));
        assertEquals(before, stack.serializeNBT());
        getToolTag(stack).setFloat(ATTACK_DAMAGE_KEY, 12);
        assertEquals(12, tool.getTotalAttackDamage(stack));
    }

    @Test
    void attributeAndHarvestQueriesDoNotChangeTheStack() {
        var tool = tool(new ToolDefinitionBuilder().build());
        var stack = tool.get(Materials.Iron);
        var before = stack.serializeNBT().copy();
        assertFalse(stack.getAttributeModifiers(EntityEquipmentSlot.MAINHAND).isEmpty());
        assertEquals(2, tool.definition$getHarvestLevel(stack, "pickaxe", null, null));
        assertEquals(0, tool.definition$getDurabilityForDisplay(stack));
        assertEquals(before, stack.serializeNBT());
    }

    private static ItemGTTool tool(IGTToolDefinition definition) {
        return ItemGTTool.Builder.of("stat_read_test", "tool_" + IDS.incrementAndGet())
                .toolClasses("pickaxe").toolStats(definition).build();
    }

    private static void readStats(IGTTool tool, ItemStack stack) {
        tool.getTotalToolSpeed(stack);
        tool.getTotalAttackDamage(stack);
        tool.getTotalAttackSpeed(stack);
        tool.getTotalMaxDurability(stack);
        tool.getTotalEnchantability(stack);
        tool.getTotalHarvestLevel(stack);
        tool.definition$getDamage(stack);
    }
}
