package gregtech.api.capability.impl;

import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.WidgetTree;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static gregtech.api.capability.impl.PaintGroupSelectionTest.*;
import static org.junit.jupiter.api.Assertions.*;

class PaintGroupPersistenceTest {

    private static DualHatchTestItems items;

    @BeforeAll
    static void setup() throws Exception {
        items = new DualHatchTestItems();
    }

    @AfterAll
    static void restore() throws Exception {
        items.close();
    }

    @Test
    void modeRoundtripsThroughRealControllerWorldAndInitialPacketPaths() {
        for (boolean enabled : new boolean[] { false, true }) {
            PaintGroupTestController source = new PaintGroupTestController(map());
            source.setPaintGroupingEnabled(enabled);
            NBTTagCompound tag = source.writeToNBT(new NBTTagCompound());
            PaintGroupTestController loaded = new PaintGroupTestController(map());
            loaded.readFromNBT(tag);
            assertEquals(enabled, loaded.isPaintGroupingEnabled());
            PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
            try {
                source.writeInitialSyncData(packet);
                PaintGroupTestController client = new PaintGroupTestController(map());
                client.receiveInitialSyncData(packet);
                assertEquals(enabled, client.isPaintGroupingEnabled());
                assertEquals(0, packet.readableBytes());
            } finally {
                packet.release();
            }
        }
    }

    @Test
    void oldMissingAndMalformedModesRemainDisabledAndAddonSubclassesMustOptIn() {
        PaintGroupTestController source = new PaintGroupTestController(map());
        NBTTagCompound original = source.writeToNBT(new NBTTagCompound());
        for (int variant = 0; variant < 4; variant++) {
            NBTTagCompound tag = original.copy();
            if (variant == 0) tag.removeTag("PaintGroups");
            else if (variant == 1) tag.setString("PaintGroups", "true");
            else if (variant == 2) tag.setInteger("PaintGroups", 1);
            else tag.setByte("PaintGroups", (byte) 2);
            PaintGroupTestController loaded = new PaintGroupTestController(map());
            loaded.readFromNBT(tag);
            assertFalse(loaded.isPaintGroupingEnabled());
        }
        RecipeFailureTestController addon = new RecipeFailureTestController(map());
        assertFalse(addon.canUseRecipeInputViews());
        addon.setPaintGroupingEnabled(true);
        assertFalse(addon.isPaintGroupingEnabled());
    }

    @Test
    void actualCommonAndSpecializedScreensCollectAllModeAndStatusSyncHandlersTogether() {
        for (RecipeMapMultiblockController controller : new RecipeMapMultiblockController[] {
                new PaintGroupTestController(map()), new PaintGroupTestSmelter() }) {
            for (boolean enabled : new boolean[] { false, true }) {
                controller.setPaintGroupingEnabled(enabled);
                PanelSyncManager manager = new PanelSyncManager(false);
                ModularPanel panel = controller.buildUI(null, manager, null);
                assertDoesNotThrow(() -> WidgetTree.collectSyncValues(manager, panel));
                assertNotNull(manager.findSyncHandlerNullable("display", 0));
            }
        }
    }
}
