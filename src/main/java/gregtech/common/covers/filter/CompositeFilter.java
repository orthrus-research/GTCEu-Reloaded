package gregtech.common.covers.filter;

import gregtech.api.cover.CoverWithUI;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.common.covers.filter.readers.BaseFilterReader;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;

/** A bounded OR of existing boolean leaf filters; child quantity settings remain on their physical items. */
public final class CompositeFilter extends BaseFilter {

    private final BaseFilterReader reader;
    private final FilterType type;
    private final CompositeFilterInventory inventory;
    private final BaseFilter[] leaves = new BaseFilter[CompositeFilterInventory.SIZE];
    private boolean validChildren;

    public CompositeFilter(ItemStack stack, FilterType type) {
        this.reader = new BaseFilterReader(stack, 0);
        this.type = type;
        this.inventory = new CompositeFilterInventory(stack, type, this::contentsChanged);
        rebuildLeaves();
    }

    public CompositeFilterInventory getInventory() {
        return inventory;
    }

    @Override
    public BaseFilterReader getFilterReader() {
        return reader;
    }

    @Override
    public FilterType getType() {
        return type;
    }

    public boolean isValid() {
        return inventory.isValidShape() && validChildren;
    }

    private void contentsChanged() {
        rebuildLeaves();
        markDirty();
    }

    private void rebuildLeaves() {
        this.validChildren = true;
        for (int i = 0; i < leaves.length; i++) {
            leaves[i] = null;
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getCount() != 1 || !inventory.isLeaf(stack) || !CompositeFilterValidation.isValid(stack)) {
                this.validChildren = false;
                continue;
            }
            try {
                // Leaf readers may initialize defaults. Never let those writes alter the stored physical child.
                BaseFilter leaf = BaseFilter.getFilterFromStack(stack.copy());
                if (leaf instanceof SmartItemFilter smart) smart.getFilteringMode();
                if (leaf == ERROR_FILTER || leaf.getType() != type) {
                    this.validChildren = false;
                } else {
                    leaves[i] = leaf;
                }
            } catch (RuntimeException invalid) {
                this.validChildren = false;
            }
        }
    }

    private boolean rawMatch(Object value) {
        // BaseFilter.test applies the parent blacklist after this method. Cancel that inversion on invalid data.
        if (!isValid()) return isBlacklistFilter();
        boolean matched = false;
        for (BaseFilter leaf : leaves) {
            if (leaf == null) continue;
            try {
                matched |= leaf.test(value);
            } catch (RuntimeException invalid) {
                this.validChildren = false;
                return isBlacklistFilter();
            }
        }
        return matched;
    }

    @Override
    public boolean testItem(ItemStack stack) {
        return type == FilterType.ITEM ? rawMatch(stack) : isBlacklistFilter();
    }

    @Override
    public boolean testFluid(FluidStack stack) {
        return type == FilterType.FLUID ? rawMatch(stack) : isBlacklistFilter();
    }

    @Override
    public MatchResult matchItem(ItemStack stack) {
        return test(stack) ? MatchResult.create(true, stack.copy(), -1) : MatchResult.NONE;
    }

    @Override
    public MatchResult matchFluid(FluidStack stack) {
        return test(stack) ? MatchResult.create(true, stack.copy(), -1) : MatchResult.NONE;
    }

    @Override
    public int getTransferLimit(ItemStack stack, int transferSize) {
        return transferSize;
    }

    @Override
    public int getTransferLimit(FluidStack stack, int transferSize) {
        return transferSize;
    }

    @Override
    public boolean showGlobalTransferLimitSlider() {
        return true;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        // Physical contents are never configuration data. Only the existing parent blacklist is transferable here.
        super.readFromNBT(tag);
    }

    @Override
    public @NotNull ModularPanel createPopupPanel(PanelSyncManager syncManager, String panelName) {
        return GTGuis.createPopupPanel(panelName, 176, 84, false)
                .child(CoverWithUI.createTitleRow(getContainerStack()))
                .child(createWidgets(syncManager).pos(7, 22));
    }

    @Override
    public @NotNull ModularPanel createPanel(PanelSyncManager syncManager) {
        return GTGuis.createPanel(getContainerStack(), 176, 226);
    }

    @Override
    public @NotNull Widget<?> createWidgets(PanelSyncManager syncManager) {
        return createWidgets(syncManager, () -> false, false);
    }

    public Widget<?> createEditableWidgets(PanelSyncManager syncManager, BooleanSupplier owner) {
        inventory.setOwner(owner);
        return createWidgets(syncManager, owner, true);
    }

    private Widget<?> createWidgets(PanelSyncManager syncManager, BooleanSupplier owner, boolean editable) {
        inventory.setClient(syncManager.isClient());
        SlotGroup group = new SlotGroup("composite_children", 3, 1000, editable).setAllowSorting(false);
        syncManager.registerSlotGroup(group);
        BooleanSyncValue valid = new BooleanSyncValue(this::isValid);
        syncManager.syncValue("composite_valid", valid);
        BooleanSyncValue blacklist = new BooleanSyncValue(this::isBlacklistFilter, value -> {
            if (editable && owner.getAsBoolean() && inventory.isValidShape()) setBlacklistFilter(value);
        });
        syncManager.syncValue("composite_blacklist", blacklist);
        Widget<?> slots = SlotGroupWidget.builder().matrix("XXX", "XXX", "XXX")
                .key('X', i -> new ItemSlot().slot(SyncHandlers.itemSlot(inventory, i)
                        .accessibility(editable, editable).slotGroup(group)))
                .build().marginRight(6);
        if (!editable) {
            return Flow.row().width(162).height(54)
                    .child(slots)
                    .child(Flow.column().width(102).height(54)
                            .child(IKey.lang(() -> valid.getBoolValue() ?
                                    blacklist.getBoolValue() ? "cover.filter.blacklist.enabled" :
                                            "cover.filter.blacklist.disabled" :
                                    "cover.composite_filter.invalid_short")
                                    .asWidget().width(102).height(24)
                                    .addTooltipLine(IKey.lang("cover.composite_filter.invalid")))
                            .child(IKey.lang("cover.composite_filter.read_only").asWidget().width(102).height(30)));
        }
        return Flow.column().width(162).coverChildrenHeight()
                .child(Flow.row().coverChildrenHeight().width(162)
                        .child(slots)
                        .child(new CycleButtonWidget().syncHandler("composite_blacklist").stateCount(2)
                                .stateBackground(0, GTGuiTextures.BUTTON_BLACKLIST[0])
                                .stateBackground(1, GTGuiTextures.BUTTON_BLACKLIST[1])
                                .setEnabledIf(widget -> editable && owner.getAsBoolean() && inventory.isValidShape())
                                .addTooltip(0, IKey.lang("cover.filter.blacklist.disabled"))
                                .addTooltip(1, IKey.lang("cover.filter.blacklist.enabled"))))
                .child(IKey.lang(() -> valid.getBoolValue() ? "cover.composite_filter.or" :
                        "cover.composite_filter.invalid_short").asWidget().width(162).height(24).marginTop(3)
                        .addTooltipLine(IKey.lang("cover.composite_filter.invalid")))
                .child(IKey.lang("cover.composite_filter.amount_short")
                        .asWidget().width(162).height(24).addTooltipLine(IKey.lang("cover.composite_filter.amount")));
    }
}
