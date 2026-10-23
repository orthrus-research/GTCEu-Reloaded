package gregtech.common.items.behaviors.filter;

import gregtech.api.cover.CoverWithUI;
import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.mui.GTGuiTheme;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaItemGuiFactory;
import gregtech.common.covers.filter.CompositeFilter;
import gregtech.common.covers.filter.IFilter.FilterType;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import java.util.List;

/** Unlike ordinary filter items, a composite owns real contents and must never clear its NBT as a shortcut. */
public final class CompositeFilterUIManager implements IItemBehaviour, ItemUIFactory {

    private final FilterType type;

    public CompositeFilterUIManager(FilterType type) {
        this.type = type;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote && !player.isSneaking() && stack.getCount() == 1) {
            MetaItemGuiFactory.open(player, hand);
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public ModularPanel buildUI(HandGuiData data, PanelSyncManager manager, UISettings settings) {
        ItemStack parent = data.getUsedItemStack();
        CompositeFilter filter = new CompositeFilter(parent, type);
        filter.setDirtyNotifiable(data.getPlayer().inventory::markDirty);
        CompositeFilterContainer container = new CompositeFilterContainer(data.getPlayer(), data.getHand(), parent);
        settings.customContainer(() -> container);
        settings.canInteractWith(container::canInteractWith);
        manager.bindPlayerInventory(data.getPlayer(), (inventory, index) -> new ModularSlot(inventory, index)
                .accessibility(index != container.getParentHotbarSlot(), index != container.getParentHotbarSlot()));
        return GTGuis.createPanel(parent, 176, 226)
                .child(CoverWithUI.createTitleRow(parent))
                .child(filter.createEditableWidgets(manager, container::ownsParent).pos(7, 22))
                .child(SlotGroupWidget.playerInventory(true));
    }

    @Override
    public GTGuiTheme getUITheme() {
        return GTGuiTheme.COVER;
    }

    @Override
    public void addInformation(ItemStack stack, List<String> lines) {
        lines.add(I18n.format("cover.composite_filter.tooltip"));
        lines.add(I18n.format("cover.composite_filter.amount"));
        lines.add(I18n.format("cover.composite_filter.edit"));
    }
}
