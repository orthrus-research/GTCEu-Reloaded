package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.mui.GTGuiTextures;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAESlot;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentTranslation;

import appeng.api.config.Actionable;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ToggleButton;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static gregtech.api.capability.GregtechDataCodes.UPDATE_AUTO_PULL;

/**
 * What the item and fluid stocking parts share: their slots show what the network holds instead of buffering it,
 * recipes take straight from the network, and auto-pull fills the configs from the network's contents. Each part
 * keeps extending its ordinary input part and hands these calls over; only the duplicate guard differs.
 */
final class MEStockingLogic<T extends IAEStack<T>, N> {

    private static final int CONFIG_SIZE = MetaTileEntityMEInputPart.CONFIG_SIZE;
    private final MetaTileEntityMEInputPart<T, ?> part;
    private final MEStackType<T, N> stackType;
    private final Predicate<N> configuredElsewhere;
    private final MEStockingConfig settings = new MEStockingConfig();
    private boolean autoPull;
    private Predicate<N> autoPullTest = $ -> false;

    /**
     * @param configuredElsewhere whether another stocking part of the multiblock is configured with the stack
     */
    MEStockingLogic(MetaTileEntityMEInputPart<T, ?> part, MEStackType<T, N> stackType,
                    Predicate<N> configuredElsewhere) {
        this.part = part;
        this.stackType = stackType;
        this.configuredElsewhere = configuredElsewhere;
    }

    boolean isAutoPull() {
        return autoPull;
    }

    /** Replaces the input part's polling: whether to sync the slots this tick. */
    boolean shouldUpdate() {
        // Check connectivity even while disabled, without tying it to an expensive storage scan.
        if (!part.updateMEStatus() || !part.isWorkingEnabled()) {
            clearUnavailableStock();
            return false;
        }
        int interval = settings.getRefreshTicks();
        if (interval == 0) return part.shouldSyncME();
        if (part.getOffsetTimer() % interval != 0) return false;
        if (autoPull) refreshList();
        return true;
    }

    /** Runs after the input part's update: auto-pull on the legacy clock refreshes its list every 100 ticks. */
    void update() {
        if (!part.getWorld().isRemote && part.isOnline && part.isWorkingEnabled() && autoPull &&
                settings.getRefreshTicks() == 0 && part.getOffsetTimer() % 100 == 0) {
            refreshList();
            part.syncME();
        }
    }

    private void clearUnavailableStock() {
        if (autoPull) {
            clearInventory(0);
        } else {
            for (var slot : part.getAESlots()) {
                slot.setStack(null);
            }
        }
    }

    private void onSettingsChanged() {
        if (part.getWorld() != null && !part.getWorld().isRemote) {
            part.markDirty();
            part.onConfigPasted();
        }
    }

    void onConfigPasted() {
        if (!part.updateMEStatus() || !part.isWorkingEnabled()) {
            clearUnavailableStock();
            return;
        }
        if (autoPull) refreshList();
        part.syncME();
    }

    /** Shows in each configured slot what the network could give, without taking it. */
    void sync() {
        IMEMonitor<T> monitor = part.getMonitor();
        if (monitor == null) {
            clearUnavailableStock();
            return;
        }

        for (var slot : part.getAESlots()) {
            if (slot.getConfig() == null) {
                slot.setStack(null);
            } else {
                // Try to fill the slot
                T request = stackType.request(slot.getConfig());
                request.setStackSize(Integer.MAX_VALUE);
                T result = monitor.extractItems(request, Actionable.SIMULATE, part.getActionSource());
                slot.setStack(result != null && result.getStackSize() >= settings.getMinimumStock() ? result : null);
            }
        }
    }

    /**
     * Takes up to {@code amount} of a configured resource straight from the network, for a recipe.
     *
     * @return what the network gave, or null when it gave nothing or is unreachable
     */
    @Nullable
    T extract(T config, int amount, boolean simulate) {
        IMEMonitor<T> monitor = part.getMonitor();
        if (monitor == null) return null;

        Actionable action = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
        T request = stackType.request(config);
        request.setStackSize(amount);
        return monitor.extractItems(request, action, part.getActionSource());
    }

    /** Runs when the part joins a multiblock. */
    void attach() {
        // ensure that no other stocking part on this multiblock is configured to hold the same resource
        // that we have in our own part.
        this.autoPullTest = stack -> !configuredElsewhere.test(stack);
        // also ensure that our current config is valid given other inputs
        validateConfig();
    }

    /** Runs when the part leaves a multiblock. */
    void detach() {
        // block auto-pull from working when not in a formed multiblock
        this.autoPullTest = $ -> false;
        if (this.autoPull) {
            // may as well clear if we are auto-pull, no reason to preserve the config
            clearConfig();
        }
    }

    /**
     * Clears any configured slot that another stocking part on the multiblock is configured with. Prevents dupes in
     * certain situations.
     */
    void validateConfig() {
        for (var slot : part.getAESlots()) {
            if (slot.getConfig() != null) {
                N configured = stackType.toNative(slot.getConfig());
                if (configured != null && configuredElsewhere.test(configured)) {
                    slot.setConfig(null);
                    slot.setStock(null);
                }
            }
        }
    }

    private void clearConfig() {
        for (var slot : part.getAESlots()) {
            slot.setConfig(null);
            slot.setStock(null);
        }
    }

    private void setAutoPull(boolean autoPull) {
        setAutoPull(autoPull, true);
    }

    private void setAutoPull(boolean autoPull, boolean refresh) {
        this.autoPull = autoPull;
        part.markDirty();
        if (!part.getWorld().isRemote) {
            if (!this.autoPull) {
                clearConfig();
            } else if (refresh) {
                part.onConfigPasted();
            }
            part.writeCustomData(UPDATE_AUTO_PULL, buf -> buf.writeBoolean(this.autoPull));
        }
    }

    /**
     * Refresh the configuration list in auto-pull mode.
     * Sets the config to the first 16 valid stacks found in the network.
     */
    private void refreshList() {
        IMEMonitor<T> monitor = part.getMonitor();
        if (monitor == null) {
            clearInventory(0);
            return;
        }

        IItemList<T> storageList = monitor.getStorageList();
        if (storageList == null) {
            clearInventory(0);
            return;
        }

        if (settings.isPreferAbundant()) {
            refreshAbundant(monitor, storageList);
            return;
        }

        int index = 0;
        for (T stack : storageList) {
            if (index >= CONFIG_SIZE) break;
            if (stack.getStackSize() < settings.getMinimumStock()) continue;
            stack = monitor.extractItems(stack.copy(), Actionable.SIMULATE, part.getActionSource());
            if (stack == null || stack.getStackSize() < settings.getMinimumStock()) continue;

            N nativeStack = stackType.toNative(stack);
            if (nativeStack == null) continue;
            // Ensure that it is valid to configure with this stack
            if (autoPullTest != null && !autoPullTest.test(nativeStack)) continue;
            T selectedStack = stackType.wrap(nativeStack);
            if (selectedStack == null) continue;
            T configStack = selectedStack.copy().setStackSize(1);
            ExportOnlyAESlot<T> slot = part.getAESlots()[index];
            slot.setConfig(configStack);
            slot.setStack(selectedStack);
            index++;
        }

        clearInventory(index);
    }

    private void refreshAbundant(IMEMonitor<T> monitor, IItemList<T> storageList) {
        MEStockingSelection<MEStockingSelection.Candidate<T>> selected = new MEStockingSelection<>(
                CONFIG_SIZE,
                Comparator.<MEStockingSelection.Candidate<T>>naturalOrder());
        for (T stored : storageList) {
            if (stored.getStackSize() < settings.getMinimumStock()) continue;
            T extracted = monitor.extractItems(stored.copy(), Actionable.SIMULATE, part.getActionSource());
            if (extracted == null || extracted.getStackSize() < settings.getMinimumStock()) continue;
            N identity = stackType.toNative(extracted.copy().setStackSize(1));
            if (identity == null) continue;
            if (autoPullTest != null && !autoPullTest.test(identity)) continue;
            selected.offer(new MEStockingSelection.Candidate<>(extracted.copy(), extracted.getStackSize(),
                    stackType::identity));
        }

        int index = 0;
        for (var candidate : selected.sorted()) {
            T stack = candidate.getStack();
            ExportOnlyAESlot<T> slot = part.getAESlots()[index++];
            // Configs are kept in GT's wrapper form, as configs set by hand are.
            slot.setConfig(stackType.wrap(stackType.toNative(stack.copy().setStackSize(1))));
            // Rank using the full AE quantity; only legacy visible stacks need an integer cap.
            slot.setStack(stack.copy().setStackSize(Math.min(Integer.MAX_VALUE, stack.getStackSize())));
        }
        clearInventory(index);
    }

    private void clearInventory(int startIndex) {
        for (int i = startIndex; i < CONFIG_SIZE; i++) {
            ExportOnlyAESlot<T> slot = part.getAESlots()[i];
            slot.setConfig(null);
            slot.setStack(null);
        }
    }

    void receiveCustomData(int dataId, PacketBuffer buf) {
        if (dataId == UPDATE_AUTO_PULL) {
            this.autoPull = buf.readBoolean();
        }
    }

    /** Adds the auto-pull button and the settings below the input part's screen. */
    void addWidgets(ModularPanel panel, PanelSyncManager syncManager, boolean fluids) {
        BooleanSyncValue autoPullValue = new BooleanSyncValue(() -> autoPull, value -> {
            // the client's screen only shows the state; the server applies it
            if (!part.getWorld().isRemote && value != autoPull) setAutoPull(value);
        });
        syncManager.syncValue("me_auto_pull", autoPullValue);
        panel.child(new ToggleButton()
                .pos(7 + 18 * 4 + 1, 26).size(16)
                .value(autoPullValue)
                .background(false, GTGuiTextures.BUTTON_AUTO_PULL[0])
                .background(true, GTGuiTextures.BUTTON_AUTO_PULL[1])
                .disableHoverBackground()
                .addTooltipLine(IKey.lang("gregtech.gui.me_bus.auto_pull_button")));
        settings.addWidgets(panel, syncManager, fluids, this::onSettingsChanged);
    }

    /** A screwdriver toggles auto-pull. */
    boolean onScrewdriverClick(EntityPlayer player) {
        if (!part.getWorld().isRemote) {
            setAutoPull(!autoPull);
            if (autoPull) {
                player.sendStatusMessage(
                        new TextComponentTranslation("gregtech.machine.me.stocking_auto_pull_enabled"), false);
            } else {
                player.sendStatusMessage(
                        new TextComponentTranslation("gregtech.machine.me.stocking_auto_pull_disabled"), false);
            }
        }
        return true;
    }

    void writeToNBT(NBTTagCompound data) {
        data.setBoolean("AutoPull", autoPull);
        settings.writeToNBT(data);
    }

    void readFromNBT(NBTTagCompound data) {
        this.autoPull = data.getBoolean("AutoPull");
        settings.readFromNBT(data, false);
    }

    void writeInitialSyncData(PacketBuffer buf) {
        buf.writeBoolean(autoPull);
    }

    void receiveInitialSyncData(PacketBuffer buf) {
        this.autoPull = buf.readBoolean();
    }

    /**
     * A data stick copy: the configured slots only when they were set by hand, since auto-pull would replace them,
     * then the stocking settings.
     */
    NBTTagCompound writeConfigToTag(Supplier<NBTTagCompound> slots) {
        NBTTagCompound tag = autoPull ? new NBTTagCompound() : slots.get();
        tag.setBoolean("AutoPull", autoPull);
        settings.writeToNBT(tag);
        return tag;
    }

    /**
     * Reads a data stick copy's settings and auto-pull mode.
     *
     * @return whether the copy was in auto-pull, which leaves the slots to the network
     */
    boolean readConfigFromTag(NBTTagCompound tag) {
        settings.readFromNBT(tag, true);
        // set auto pull first to avoid issues with clearing the config after reading from the data stick
        setAutoPull(tag.getBoolean("AutoPull"), false);
        return autoPull;
    }
}
