package gregtech.api.items.toolitem;

import gregtech.Bootstrap;
import gregtech.api.items.toolitem.aoe.AoESymmetrical;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumHand;

import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static gregtech.api.items.toolitem.ToolHelper.*;
import static org.junit.jupiter.api.Assertions.*;

class ToolAreaReadTest {

    private static final AtomicInteger IDS = new AtomicInteger();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void sparseReadsUseDefaultsWithoutWritingThem() {
        var tag = new NBTTagCompound();
        tag.setInteger(AOE_COLUMN_KEY, 0);
        tag.setString(AOE_ROW_KEY, "preserve invalid input");
        var before = tag.copy();
        assertArea(AoESymmetrical.read(tag, AoESymmetrical.of(2, 3, 4)), 0, 3, 4);
        assertEquals(before, tag);
        assertSame(AoESymmetrical.none(), AoESymmetrical.read(new NBTTagCompound(), null));
    }

    @ParameterizedTest
    @ValueSource(strings = { "absent", "empty", "wrong_type" })
    void rawToolAndConfigurationReadsDoNotAttachBehaviorData(String state) {
        var tool = tool();
        var stack = new ItemStack(tool);
        stack.setTagCompound(null);
        if (state.equals("empty")) stack.setTagCompound(new NBTTagCompound());
        if (state.equals("wrong_type")) {
            var tag = new NBTTagCompound();
            tag.setString(BEHAVIOURS_TAG_KEY, "preserve me");
            stack.setTagCompound(tag);
        }
        var before = stack.serializeNBT().copy();
        boolean hadTag = stack.hasTagCompound();
        assertSame(AoESymmetrical.none(), tool.getMaxAoEDefinition(stack));
        assertSame(AoESymmetrical.none(), tool.getAoEDefinition(stack));
        assertSame(AoESymmetrical.none(), ToolHelper.getAoEDefinition(stack));
        var manager = panel(tool, stack);
        for (var key : new String[] { "column_value", "row_value", "layer_value" }) {
            var value = (IntSyncValue) manager.findSyncHandler(key);
            value.updateCacheFromSource(true);
            assertEquals(0, value.getIntValue());
        }
        assertEquals(hadTag, stack.hasTagCompound());
        assertEquals(before, stack.serializeNBT());
    }

    @Test
    void allReadersUseBehaviorSettingsAndLeaveObsoleteToolFieldsAlone() {
        var tool = tool();
        var stack = tool.get(Materials.Iron);
        var behaviors = getBehaviorsTag(stack);
        behaviors.setInteger(AOE_COLUMN_KEY, 1);
        behaviors.setInteger(AOE_ROW_KEY, 0);
        behaviors.setInteger(AOE_LAYER_KEY, 2);
        var old = getToolTag(stack);
        old.setInteger(AOE_COLUMN_KEY, 8);
        old.setInteger(AOE_ROW_KEY, 9);
        old.setInteger(AOE_LAYER_KEY, 10);
        var before = stack.serializeNBT().copy();
        assertArea(tool.getAoEDefinition(stack), 1, 0, 2);
        assertArea(ToolHelper.getAoEDefinition(stack), 1, 0, 2);
        assertArea(tool.getMaxAoEDefinition(stack), 2, 3, 4);
        assertEquals(before, stack.serializeNBT());
    }

    @Test
    void explicitAdjustmentsClampAndSurviveNbtRoundTrip() throws Exception {
        var tag = new NBTTagCompound();
        var maximum = AoESymmetrical.of(2, 3, 4);
        AoESymmetrical.decreaseColumn(tag, maximum);
        AoESymmetrical.increaseRow(tag, maximum);
        AoESymmetrical.setLayer(tag, Integer.MIN_VALUE, maximum);
        assertArea(AoESymmetrical.read(tag, maximum), 1, 3, 0);
        AoESymmetrical.setColumn(tag, Integer.MAX_VALUE, maximum);
        AoESymmetrical.decreaseRow(tag, maximum);
        AoESymmetrical.increaseLayer(tag, maximum);
        var bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(tag, bytes);
        var restored = CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes.toByteArray()));
        var before = restored.copy();
        assertArea(AoESymmetrical.read(restored, maximum), 2, 2, 1);
        assertEquals(before, restored);
    }

    @Test
    void configurationPacketsWriteCanonicalClampedSettingsAfterReadOnlySetup() throws Exception {
        var tool = tool();
        var stack = tool.get(Materials.Iron);
        var behaviors = getBehaviorsTag(stack);
        for (var key : new String[] { AOE_COLUMN_KEY, AOE_ROW_KEY, AOE_LAYER_KEY }) behaviors.removeTag(key);
        var before = stack.serializeNBT().copy();
        var manager = panel(tool, stack);
        var column = (IntSyncValue) manager.findSyncHandler("column_value");
        column.updateCacheFromSource(true);
        assertEquals(2, column.getIntValue());
        assertArea(tool.getAoEDefinition(stack), 2, 3, 4);
        assertEquals(before, stack.serializeNBT());
        send(column, Integer.MAX_VALUE);
        send((IntSyncValue) manager.findSyncHandler("row_value"), Integer.MIN_VALUE);
        send((IntSyncValue) manager.findSyncHandler("layer_value"), 1);
        assertArea(tool.getAoEDefinition(stack), 2, 0, 1);
        assertArea(ToolHelper.getAoEDefinition(stack), 2, 0, 1);
        assertFalse(getToolTag(stack).hasKey(AOE_COLUMN_KEY));
        assertArea(tool.getMaxAoEDefinition(stack), 2, 3, 4);
    }

    private static void send(IntSyncValue receiver, int amount) throws Exception {
        var sender = new IntSyncValue(() -> amount);
        sender.updateCacheFromSource(true);
        var buffer = new PacketBuffer(Unpooled.buffer());
        try {
            sender.write(buffer);
            receiver.readOnServer(0, buffer);
        } finally {
            buffer.release();
        }
    }

    private static PanelSyncManager panel(IGTTool tool, ItemStack stack) {
        var player = new EntityPlayer(new DummyWorld(), new GameProfile(UUID.randomUUID(), "area_test")) {

            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        player.setHeldItem(EnumHand.MAIN_HAND, stack);
        var manager = new PanelSyncManager(new ModularSyncManager(false), true);
        assertNotNull(tool.buildUI(new HandGuiData(player, EnumHand.MAIN_HAND), manager, new UISettings()));
        return manager;
    }

    private static ItemGTTool tool() {
        return ItemGTTool.Builder.of("area_test", "tool_" + IDS.incrementAndGet())
                .toolStats(builder -> builder.aoe(2, 3, 4)).build();
    }

    private static void assertArea(AoESymmetrical area, int column, int row, int layer) {
        assertEquals(column, area.column);
        assertEquals(row, area.row);
        assertEquals(layer, area.layer);
    }
}
