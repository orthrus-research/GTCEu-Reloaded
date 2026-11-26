package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.sync.GTFluidSyncHandler;
import gregtech.api.util.LocalizationUtils;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEPatternBuffer;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The pattern buffer's screen: its patterns, its shared ingredients and its private batches on three tabs. The server
 * keeps which batch this screen shows and sends only that batch's bounded text rows.
 */
public final class PatternBufferUI {

    private static final int ENTRY_ROWS = 6;
    private static final int MAX_TEXT = 100;
    private final MetaTileEntityMEPatternBuffer buffer;
    private long selectedBatch;
    private int entryPage;

    private PatternBufferUI(MetaTileEntityMEPatternBuffer buffer) {
        this.buffer = buffer;
        if (!buffer.getPatternStorage().getBatches().isEmpty())
            this.selectedBatch = buffer.getPatternStorage().getBatches().get(0).getId();
    }

    public static ModularPanel create(MetaTileEntityMEPatternBuffer buffer, PanelSyncManager syncManager) {
        return new PatternBufferUI(buffer).build(syncManager);
    }

    private ModularPanel build(PanelSyncManager syncManager) {
        PatternBufferStorage storage = buffer.getPatternStorage();
        PagedWidget.Controller tabs = new PagedWidget.Controller();
        return GTGuis.createPanel(buffer, 176, 264)
                .child(IKey.lang(buffer.getMetaFullName()).asWidget().pos(7, 6))
                .child(text(syncManager, "pattern_buffer_status", () -> status(storage)).pos(7, 22))
                .child(Flow.column()
                        .coverChildren()
                        .leftRel(0f, 3, 1f).top(36)
                        .child(tab(0, tabs, Items.PAPER, "gregtech.pattern_buffer.patterns"))
                        .child(tab(1, tabs, Items.BUCKET, "gregtech.pattern_buffer.shared"))
                        .child(tab(2, tabs, Items.BOOK, "gregtech.pattern_buffer.batches")))
                .child(new PagedWidget<>()
                        .controller(tabs)
                        .pos(0, 36).size(176, 118)
                        .addPage(patternsPage(syncManager, storage))
                        .addPage(sharedPage(syncManager, storage))
                        .addPage(batchesPage(syncManager)))
                .child(button(syncManager, "pattern_buffer_toggle", "gregtech.pattern_buffer.toggle", player -> {
                    if (buffer.canAccess(player)) buffer.setWorkingEnabled(!buffer.isWorkingEnabled());
                }).pos(7, 156).size(77, 18))
                .child(button(syncManager, "pattern_buffer_refund_all", "gregtech.pattern_buffer.refund_all",
                        player -> buffer.requestRefund(player, 0)).pos(87, 156).size(82, 18))
                .bindPlayerInventory();
    }

    private static PageButton tab(int index, PagedWidget.Controller tabs, Item icon, String name) {
        return new PageButton(index, tabs)
                .tab(GuiTextures.TAB_LEFT, 0)
                .size(GuiTextures.TAB_LEFT.getWidth(), GuiTextures.TAB_LEFT.getHeight())
                .overlay(new ItemDrawable(new ItemStack(icon)).asIcon().size(16))
                .addTooltipLine(IKey.lang(name));
    }

    private IWidget patternsPage(PanelSyncManager syncManager, PatternBufferStorage storage) {
        PatternBufferGuiItems patterns = new PatternBufferGuiItems(storage.getPatterns());
        syncManager.registerSlotGroup("pattern_buffer_patterns", 9, true);
        ParentWidget<?> page = new ParentWidget<>().size(176, 118);
        for (int i = 0; i < patterns.getSlots(); i++) {
            page.child(slot(patterns, i, "pattern_buffer_patterns").pos(7 + i % 9 * 18, 3 + i / 9 * 18));
        }
        return page.child(text("gregtech.pattern_buffer.patterns_help").pos(7, 64))
                .child(text("gregtech.pattern_buffer.patterns_locked").pos(7, 79));
    }

    private IWidget sharedPage(PanelSyncManager syncManager, PatternBufferStorage storage) {
        PatternBufferGuiItems shared = new PatternBufferGuiItems(storage.getSharedItems());
        syncManager.registerSlotGroup("pattern_buffer_shared", 9, true);
        ParentWidget<?> page = new ParentWidget<>().size(176, 118)
                .child(IKey.lang("gregtech.pattern_buffer.shared_items").asWidget().pos(7, 2))
                .child(IKey.lang("gregtech.pattern_buffer.shared_fluids").asWidget().pos(7, 42))
                .child(text("gregtech.pattern_buffer.shared_help").pos(7, 86));
        for (int i = 0; i < shared.getSlots(); i++) {
            page.child(slot(shared, i, "pattern_buffer_shared").pos(7 + i * 18, 15));
        }
        for (int i = 0; i < storage.getSharedFluids().getTanks(); i++) {
            GTFluidSyncHandler tank = new SharedTankSyncHandler(storage.getSharedFluids().getTankAt(i))
                    .accessibility(true, true);
            syncManager.syncValue("pattern_buffer_tank", i, tank);
            page.child(new GTFluidSlot()
                    .syncHandler(tank)
                    .background(GTGuiTextures.FLUID_SLOT)
                    .pos(7 + i * 18, 55).size(18));
        }
        return page;
    }

    private IWidget batchesPage(PanelSyncManager syncManager) {
        ParentWidget<?> page = new ParentWidget<>().size(176, 118)
                .child(text(syncManager, "pattern_buffer_batch", () -> batchTitle(selected())).pos(7, 2));
        for (int row = 0; row < ENTRY_ROWS; row++) {
            int index = row;
            page.child(text(syncManager, "pattern_buffer_entry_" + row, () -> entry(index)).pos(7, 16 + row * 10));
        }
        return page
                .child(button(syncManager, "pattern_buffer_previous", "<", player -> select(player, -1))
                        .pos(7, 80).size(25, 16))
                .child(button(syncManager, "pattern_buffer_next", ">", player -> select(player, 1))
                        .pos(35, 80).size(25, 16))
                .child(button(syncManager, "pattern_buffer_next_entries", "gregtech.pattern_buffer.next_entries",
                        player -> {
                            if (buffer.canAccess(player)) this.entryPage = (entryPage + 1) % 34;
                        }).pos(65, 80).size(104, 16))
                .child(button(syncManager, "pattern_buffer_refund_batch", "gregtech.pattern_buffer.refund_batch",
                        player -> {
                            if (selectedBatch > 0) buffer.requestRefund(player, selectedBatch);
                        }).pos(7, 99).size(162, 16));
    }

    private ItemSlot slot(PatternBufferGuiItems items, int index, String group) {
        return new ItemSlot()
                .slot(new PatternBufferSlot(items, index, buffer).slotGroup(group))
                .background(GTGuiTextures.SLOT);
    }

    /** A button whose press runs on the server, for the player who pressed it. */
    private static ButtonWidget<?> button(PanelSyncManager syncManager, String name, String label,
                                          Consumer<EntityPlayer> action) {
        InteractionSyncHandler press = new InteractionSyncHandler().setOnMousePressed(mouse -> {
            if (!mouse.isClient()) action.accept(syncManager.getPlayer());
        });
        syncManager.syncValue(name, press);
        return new ButtonWidget<>()
                .overlay(IKey.lang(label))
                .syncHandler(press);
    }

    /** One bounded text row the server computes: the transport is capped and the client clips by glyph width. */
    private static TextWidget<?> text(PanelSyncManager syncManager, String name, Supplier<String> text) {
        StringSyncValue value = new StringSyncValue(() -> bounded(text.get()));
        syncManager.syncValue(name, value);
        return text(value::getStringValue);
    }

    private static TextWidget<?> text(String langKey) {
        return text(() -> langKey);
    }

    /**
     * A text row as the pattern buffer and its proxy show them: a lang key or text, at 0.8 scale, clipped to the
     * panel's inner width.
     */
    public static TextWidget<?> text(Supplier<String> text) {
        return IKey.dynamic(() -> clip(text.get())).asWidget().scale(0.8f).width(162);
    }

    /** Caps a row before it is sent. */
    public static String bounded(String text) {
        return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT - 3) + "..." : text;
    }

    private static String clip(String text) {
        if (text == null) return "";
        String resolved = I18n.hasKey(text) ? I18n.format(text) : text;
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int pixels = 202; // 162 screen pixels at 0.8 scale.
        return font.getStringWidth(resolved) <= pixels ? resolved :
                font.trimStringToWidth(resolved, pixels - font.getStringWidth("...")) + "...";
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
        int index = entryPage * ENTRY_ROWS + row;
        return index < entries.size() ? entries.get(index) : "";
    }

    private void select(EntityPlayer player, int direction) {
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

    /**
     * A shared tank the screen fills and drains with containers, only for a player who may access the buffer. The
     * tank hands out copies, so the client's tank takes each amount the server sends as a whole new stack.
     */
    private final class SharedTankSyncHandler extends GTFluidSyncHandler {

        private SharedTankSyncHandler(IFluidTank tank) {
            super(tank);
        }

        @Override
        public void readOnServer(int id, PacketBuffer buf) {
            if (buffer.canAccess(getSyncManager().getPlayer())) super.readOnServer(id, buf);
        }

        @Override
        public void setAmount(int amount) {
            FluidStack fluid = getFluid();
            if (fluid == null) return;
            fluid.amount = amount;
            setFluid(fluid);
        }
    }
}
