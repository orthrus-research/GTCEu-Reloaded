package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.ClickButtonWidget;
import gregtech.api.gui.widgets.LabelWidget;
import gregtech.api.gui.widgets.SimpleTextWidget;
import gregtech.api.gui.widgets.SlotWidget;
import gregtech.api.gui.widgets.TabGroup;
import gregtech.api.gui.widgets.TankWidget;
import gregtech.api.gui.widgets.WidgetGroup;
import gregtech.api.gui.widgets.tab.ItemTabInfo;
import gregtech.api.util.LocalizationUtils;
import gregtech.api.util.Position;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEPatternBuffer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Existing GT ModularUI transport; only the selected bounded text page is synchronized. */
public final class PatternBufferUI {

    private final MetaTileEntityMEPatternBuffer buffer;
    private final EntityPlayer player;
    private long selectedBatch;
    private int entryPage;

    private PatternBufferUI(MetaTileEntityMEPatternBuffer buffer, EntityPlayer player) {
        this.buffer = buffer;
        this.player = player;
        if (!buffer.getPatternStorage().getBatches().isEmpty())
            this.selectedBatch = buffer.getPatternStorage().getBatches().get(0).getId();
    }

    public static ModularUI create(MetaTileEntityMEPatternBuffer buffer, EntityPlayer player) {
        return new PatternBufferUI(buffer, player).build();
    }

    private ModularUI build() {
        PatternBufferStorage storage = buffer.getPatternStorage();
        PatternBufferGuiItems patternSlots = new PatternBufferGuiItems(storage.getPatterns());
        PatternBufferGuiItems sharedSlots = new PatternBufferGuiItems(storage.getSharedItems());
        ModularUI.Builder ui = ModularUI.builder(GuiTextures.BACKGROUND, 176, 264)
                .label(7, 6, buffer.getMetaFullName()).widget(text(7, 22, () -> status(storage)));
        TabGroup<WidgetGroup> tabs = new TabGroup<>(TabGroup.TabLocation.VERTICAL_LEFT_TOP, new Position(0, 36));
        WidgetGroup patterns = new WidgetGroup(0, 0, 176, 118);
        for (int i = 0; i < 27; i++) patterns.addWidget(slot(patternSlots, i, 7 + i % 9 * 18, 3 + i / 9 * 18));
        patterns.addWidget(text(7, 64, () -> "gregtech.pattern_buffer.patterns_help"));
        patterns.addWidget(text(7, 79, () -> "gregtech.pattern_buffer.patterns_locked"));
        tabs.addTab(new ItemTabInfo("gregtech.pattern_buffer.patterns", new ItemStack(Items.PAPER)), patterns);

        WidgetGroup shared = new WidgetGroup(0, 0, 176, 118);
        shared.addWidget(new LabelWidget(7, 2, "gregtech.pattern_buffer.shared_items"));
        for (int i = 0; i < 9; i++) shared.addWidget(slot(sharedSlots, i, 7 + i * 18, 15));
        shared.addWidget(new LabelWidget(7, 42, "gregtech.pattern_buffer.shared_fluids"));
        for (int i = 0; i < 9; i++) {
            TankWidget tank = new TankWidget(storage.getSharedFluids().getTankAt(i), 7 + i * 18, 55, 18, 18) {

                @Override
                public void handleClientAction(int id, PacketBuffer packet) {
                    if (buffer.canAccess(player)) super.handleClientAction(id, packet);
                }
            };
            shared.addWidget(tank.setContainerClicking(true, true).setBackgroundTexture(GuiTextures.FLUID_SLOT));
        }
        shared.addWidget(text(7, 86, () -> "gregtech.pattern_buffer.shared_help"));
        tabs.addTab(new ItemTabInfo("gregtech.pattern_buffer.shared", new ItemStack(Items.BUCKET)), shared);

        WidgetGroup batches = new WidgetGroup(0, 0, 176, 118);
        batches.addWidget(text(7, 2, () -> batchTitle(selected())));
        for (int row = 0; row < 6; row++) {
            final int index = row;
            batches.addWidget(text(7, 16 + row * 10, () -> entry(index)));
        }
        batches.addWidget(new ClickButtonWidget(7, 80, 25, 16, "<", data -> select(-1)));
        batches.addWidget(new ClickButtonWidget(35, 80, 25, 16, ">", data -> select(1)));
        batches.addWidget(new ClickButtonWidget(65, 80, 104, 16, "gregtech.pattern_buffer.next_entries", data -> {
            if (buffer.canAccess(player)) this.entryPage = (entryPage + 1) % 34;
        }));
        batches.addWidget(new ClickButtonWidget(7, 99, 162, 16, "gregtech.pattern_buffer.refund_batch",
                data -> { if (selectedBatch > 0) buffer.requestRefund(player, selectedBatch); }));
        tabs.addTab(new ItemTabInfo("gregtech.pattern_buffer.batches", new ItemStack(Items.BOOK)), batches);
        ui.widget(tabs);
        ui.widget(new ClickButtonWidget(7, 156, 77, 18, "gregtech.pattern_buffer.toggle", data -> {
            if (buffer.canAccess(player)) buffer.setWorkingEnabled(!buffer.isWorkingEnabled());
        }));
        ui.widget(new ClickButtonWidget(87, 156, 82, 18, "gregtech.pattern_buffer.refund_all",
                data -> buffer.requestRefund(player, 0)));
        return ui.bindPlayerInventory(player.inventory, 182).build(buffer.getHolder(), player);
    }

    private SlotWidget slot(PatternBufferGuiItems handler, int index, int x, int y) {
        return new PatternBufferSlotWidget(handler, index, x, y, buffer, player).setBackgroundTexture(GuiTextures.SLOT);
    }

    private static SimpleTextWidget text(int x, int y, Supplier<String> text) {
        return new PatternBufferTextWidget(x, y, text);
    }

    private String status(PatternBufferStorage storage) {
        if (buffer.isRecoveryBlocked()) return "gregtech.pattern_buffer.status.recovery_blocked";
        if (storage.isQuarantined()) return "gregtech.pattern_buffer.status.quarantined";
        if (PatternBufferTokens.isUnsupportedBridgePresent())
            return "gregtech.pattern_buffer.status.unsupported_bridge";
        return LocalizationUtils.format("gregtech.pattern_buffer.status",
                LocalizationUtils.format(buffer.isWorkingEnabled() ?
                        "gregtech.pattern_buffer.status.enabled" : "gregtech.pattern_buffer.status.paused"),
                LocalizationUtils.format(buffer.isOnline() ? "gregtech.pattern_buffer.status.online" :
                        "gregtech.pattern_buffer.status.offline"),
                storage.getBatches().size());
    }

    private PatternBufferBatch selected() {
        for (PatternBufferBatch batch : buffer.getPatternStorage().getBatches())
            if (batch.getId() == selectedBatch) return batch;
        return null;
    }

    private String batchTitle(PatternBufferBatch batch) {
        if (batch == null) return "gregtech.pattern_buffer.status.no_batch";
        return LocalizationUtils.format(batch.isRefundPending() ? "gregtech.pattern_buffer.status.refund_batch" :
                "gregtech.pattern_buffer.status.batch", batch.getId(), batch.getPattern() + 1);
    }

    private String entry(int row) {
        PatternBufferBatch batch = selected();
        if (batch == null) return "";
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < batch.getItems().getSlots(); i++) {
            ItemStack stack = batch.getItems().getStackInSlot(i);
            if (!stack.isEmpty()) entries.add(stack.getCount() + " x " + stack.getDisplayName());
        }
        for (int i = 0; i < batch.getFluids().getTanks(); i++) {
            FluidStack fluid = batch.getFluids().getTankAt(i).getFluid();
            if (fluid != null) entries.add(fluid.amount + " mB " + fluid.getLocalizedName());
        }
        int index = entryPage * 6 + row;
        return index < entries.size() ? entries.get(index) : "";
    }

    private void select(int direction) {
        if (!buffer.canAccess(player)) return;
        List<PatternBufferBatch> batches = buffer.getPatternStorage().getBatches();
        if (batches.isEmpty()) {
            this.selectedBatch = 0;
            return;
        }
        int index = -1;
        for (int i = 0; i < batches.size(); i++) if (batches.get(i).getId() == selectedBatch) index = i;
        this.selectedBatch = batches.get(Math.floorMod(index + direction, batches.size())).getId();
        this.entryPage = 0;
    }
}
