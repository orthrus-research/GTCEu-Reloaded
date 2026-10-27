package gregtech.common.pipelike.itempipe.tile;

import gregtech.api.configuration.ConfigurationAdapter;
import gregtech.api.configuration.PipeConfigurationAdapter;
import gregtech.api.pipenet.block.material.TileEntityMaterialPipeBase;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.unification.material.properties.ItemPipeProperties;
import gregtech.api.util.FacingPos;
import gregtech.common.pipelike.itempipe.ItemPipeType;
import gregtech.common.pipelike.itempipe.net.ItemNetHandler;
import gregtech.common.pipelike.itempipe.net.ItemPipeNet;
import gregtech.common.pipelike.itempipe.net.WorldItemPipeNet;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class TileEntityItemPipe extends TileEntityMaterialPipeBase<ItemPipeType, ItemPipeProperties> {

    private final EnumMap<EnumFacing, ItemNetHandler> handlers = new EnumMap<>(EnumFacing.class);
    private final Object2IntMap<FacingPos> transferred = new Object2IntOpenHashMap<>();
    private ItemNetHandler defaultHandler;
    // the ItemNetHandler can only be created on the server so we have a empty placeholder for the client
    private final IItemHandler clientCapability = new ItemStackHandler(0);
    private WeakReference<ItemPipeNet> currentPipeNet = new WeakReference<>(null);

    private int transferredItems = 0;
    private long timer = 0;
    private final Map<Integer, ItemTransferBudget> exactTransferBudgets = new HashMap<>();
    // A handoff shares this holder so either owner's unwind releases the same active operation.
    private AtomicBoolean transferInProgress = new AtomicBoolean();
    private TileEntityItemPipe accountingSuccessor;

    public long getWorldTime() {
        return hasWorld() ? getWorld().getTotalWorldTime() : 0L;
    }

    @Override
    public @Nullable ConfigurationAdapter getConfigurationAdapter() {
        return getClass() == TileEntityItemPipe.class || getClass() == TileEntityItemPipeTickable.class ?
                new PipeConfigurationAdapter(this, "gregtech:item_pipe/1") : null;
    }

    @Override
    public Class<ItemPipeType> getPipeTypeClass() {
        return ItemPipeType.class;
    }

    @Override
    public boolean supportsTicking() {
        return false;
    }

    @Override
    public IPipeTile<ItemPipeType, ItemPipeProperties> setSupportsTicking() {
        IPipeTile<ItemPipeType, ItemPipeProperties> replacement = super.setSupportsTicking();
        if (replacement != this) this.accountingSuccessor = (TileEntityItemPipe) replacement;
        return replacement;
    }

    private TileEntityItemPipe accountingOwner() {
        TileEntityItemPipe owner = this;
        while (owner.accountingSuccessor != null) owner = owner.accountingSuccessor;
        return owner;
    }

    private void initHandlers() {
        ItemPipeNet net = getItemPipeNet();
        if (net == null) {
            return;
        }
        for (EnumFacing facing : EnumFacing.values()) {
            handlers.put(facing, new ItemNetHandler(net, this, facing));
        }
        defaultHandler = new ItemNetHandler(net, this, null);
    }

    @Nullable
    @Override
    public <T> T getCapabilityInternal(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {

            if (world.isRemote)
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(clientCapability);

            if (handlers.size() == 0)
                initHandlers();
            checkNetwork();
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(handlers.getOrDefault(facing, defaultHandler));
        }
        return super.getCapabilityInternal(capability, facing);
    }

    public void checkNetwork() {
        if (defaultHandler != null) {
            ItemPipeNet current = getItemPipeNet();
            if (defaultHandler.getNet() != current) {
                defaultHandler.updateNetwork(current);
                for (ItemNetHandler handler : handlers.values()) {
                    handler.updateNetwork(current);
                }
            }
        }
    }

    public ItemPipeNet getItemPipeNet() {
        if (world == null || world.isRemote)
            return null;
        ItemPipeNet currentPipeNet = this.currentPipeNet.get();
        if (currentPipeNet != null && currentPipeNet.isValid() &&
                currentPipeNet.containsNode(getPipePos()))
            return currentPipeNet; // if current net is valid and does contain position, return it
        WorldItemPipeNet worldFluidPipeNet = (WorldItemPipeNet) getPipeBlock().getWorldPipeNet(getPipeWorld());
        currentPipeNet = worldFluidPipeNet.getNetFromPos(getPipePos());
        if (currentPipeNet != null) {
            this.currentPipeNet = new WeakReference<>(currentPipeNet);
        }
        return currentPipeNet;
    }

    public void resetTransferred() {
        accountingOwner().transferred.clear();
    }

    public Object2IntMap<FacingPos> getTransferred() {
        return accountingOwner().transferred;
    }

    @Override
    public void transferDataFrom(IPipeTile<ItemPipeType, ItemPipeProperties> tileEntity) {
        super.transferDataFrom(tileEntity);
        TileEntityItemPipe itemPipe = (TileEntityItemPipe) tileEntity;
        TileEntityItemPipe accounting = itemPipe.accountingOwner();
        this.transferredItems = accounting.transferredItems;
        this.timer = accounting.timer;
        this.transferred.clear();
        this.transferred.putAll(accounting.transferred);
        this.exactTransferBudgets.clear();
        accounting.exactTransferBudgets
                .forEach((rate, budget) -> this.exactTransferBudgets.put(rate, new ItemTransferBudget(budget)));
        this.transferInProgress = itemPipe.transferInProgress;
        // take handlers from old pipe
        if (!itemPipe.handlers.isEmpty()) {
            this.handlers.clear();
            for (ItemNetHandler handler : itemPipe.handlers.values()) {
                handler.updatePipe(this);
                this.handlers.put(handler.getFacing(), handler);
            }
        }
        if (itemPipe.defaultHandler != null) {
            itemPipe.defaultHandler.updatePipe(this);
            this.defaultHandler = itemPipe.defaultHandler;
        }
    }

    // every time the transferred variable is accessed this method should be called
    // if 20 ticks passed since the last access it will reset it
    // this method is equal to
    // if (++time % 20 == 0) {
    // this.transferredItems = 0;
    // }
    // if it was in a ticking TileEntity
    private void updateTransferredState() {
        long currentTime = getWorldTime();
        long dif = currentTime - this.timer;
        if (dif >= 20 || dif < 0) {
            this.transferredItems = 0;
            this.timer = currentTime;
        }
    }

    public void addTransferredItems(int amount) {
        TileEntityItemPipe owner = accountingOwner();
        if (owner != this) {
            owner.addTransferredItems(amount);
            return;
        }
        updateTransferredState();
        if (amount < 0) throw new IllegalArgumentException("Transferred item count cannot be negative");
        if (amount == 0) return;
        long now = getWorldTime();
        for (ItemTransferBudget budget : exactTransferBudgets.values()) {
            budget.spend(amount, now);
        }
        this.transferredItems = (int) Math.min(Integer.MAX_VALUE, (long) transferredItems + amount);
    }

    public boolean tryBeginItemTransfer() {
        return transferInProgress.compareAndSet(false, true);
    }

    public void endItemTransfer() {
        this.transferInProgress.set(false);
    }

    public int getAvailableExactTransferItems(int rate, int batch) {
        TileEntityItemPipe owner = accountingOwner();
        if (owner != this) return owner.getAvailableExactTransferItems(rate, batch);
        if (rate <= 0 || batch <= 0) return 0;
        updateTransferredState();
        long now = getWorldTime();
        ItemTransferBudget budget = exactTransferBudgets.computeIfAbsent(rate,
                ignored -> new ItemTransferBudget(rate, transferredItems, batch, now));
        return budget.available(batch, now);
    }

    public int getTransferredItems() {
        TileEntityItemPipe owner = accountingOwner();
        if (owner != this) return owner.getTransferredItems();
        updateTransferredState();
        return this.transferredItems;
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        this.handlers.clear();
    }
}
