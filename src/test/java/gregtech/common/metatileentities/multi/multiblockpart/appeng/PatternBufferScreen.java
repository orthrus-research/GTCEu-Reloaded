package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;

import com.cleanroommc.modularui.core.mixins.early.minecraft.ContainerAccessor;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.WidgetTree;
import io.netty.buffer.Unpooled;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A pattern buffer screen opened on the server as the game opens one, with its real container and sync values. */
final class PatternBufferScreen {

    final ModularContainer container;
    final PanelSyncManager syncManager;

    private PatternBufferScreen(ModularContainer container, PanelSyncManager syncManager) {
        this.container = container;
        this.syncManager = syncManager;
    }

    static PatternBufferScreen open(MetaTileEntityMEPatternBuffer buffer, EntityPlayer player) {
        ModularSyncManager modularSyncManager = new ModularSyncManager(false);
        PanelSyncManager syncManager = new PanelSyncManager(modularSyncManager, true);
        UISettings settings = new UISettings();
        PosGuiData guiData = new PosGuiData(player, 0, 0, 0);
        ModularPanel panel = buffer.buildUI(guiData, syncManager, settings);
        WidgetTree.collectSyncValues(syncManager, panel);
        ModularContainer container = new ScreenContainer();
        container.construct(player, modularSyncManager, settings, panel.getName(), guiData);
        player.openContainer = container;
        return new PatternBufferScreen(container, syncManager);
    }

    /**
     * The screen's container, standing in for what a running game adds to it: the mixin accessor that tells a drag
     * from a click (never a drag here), and no packets to send.
     */
    private static final class ScreenContainer extends ModularContainer implements ContainerAccessor {

        @Override
        public int getDragEvent() {
            return 0;
        }

        @Override
        public void detectAndSendChanges() {}
    }

    /** Presses one of the screen's buttons as its client would. */
    void press(String button) throws Exception {
        SyncHandler handler = syncManager.findSyncHandler(button);
        PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
        try {
            new MouseData(Side.CLIENT, 0, false, false, false).writeToPacket(packet);
            handler.readOnServer(1, packet);
            assertEquals(0, packet.readableBytes());
        } finally {
            packet.release();
        }
    }
}
