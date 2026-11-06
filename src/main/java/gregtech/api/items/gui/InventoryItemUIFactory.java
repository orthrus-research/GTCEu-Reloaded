package gregtech.api.items.gui;

import gregtech.api.items.metaitem.stats.IItemComponent;
import gregtech.api.mui.GTGuiTheme;
import gregtech.api.mui.GregTechGuiScreen;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import org.jetbrains.annotations.ApiStatus;

/** A meta-item UI anchored to an explicit inventory and slot, independent of the selected hand. */
public interface InventoryItemUIFactory extends IItemComponent, IGuiHolder<PlayerInventoryGuiData> {

    @Override
    ModularPanel buildUI(PlayerInventoryGuiData data, PanelSyncManager syncManager, UISettings settings);

    @ApiStatus.NonExtendable
    @SideOnly(Side.CLIENT)
    @Override
    default ModularScreen createScreen(PlayerInventoryGuiData data, ModularPanel mainPanel) {
        return new GregTechGuiScreen(mainPanel, getUITheme());
    }

    default GTGuiTheme getUITheme() {
        return GTGuiTheme.STANDARD;
    }
}
