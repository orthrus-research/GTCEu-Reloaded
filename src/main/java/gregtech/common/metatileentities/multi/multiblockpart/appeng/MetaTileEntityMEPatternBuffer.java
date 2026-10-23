package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.GTValues;
import gregtech.api.capability.IDataStickIntractable;
import gregtech.api.capability.IRecipeInputViewProvider;
import gregtech.api.capability.RecipeInputView;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.gui.ModularUI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.*;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.config.SecurityPermissions;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Owns accepted local input batches; completed work belongs exclusively to its consuming controller. */
public class MetaTileEntityMEPatternBuffer extends MetaTileEntityAEHostablePart<IAEItemStack>
                                           implements IMultiblockAbilityPart<Object>, IRecipeInputViewProvider,
                                           IDataStickIntractable {

    private static final String DATA = "PatternBuffer";
    private final PatternBufferStorage storage;
    private final ItemStackHandler placeholderItems = new ItemStackHandler(0);
    private final FluidTank placeholderFluid = new FluidTank(0);
    private final Set<MetaTileEntityMEPatternBufferProxy> listeners = new HashSet<>();
    private PatternBufferHost craftingHost;
    private boolean workingEnabled = true;
    private boolean detached;
    private boolean securityBlocked;
    private boolean refreshPatterns = true;
    private long announcedPatterns = -1;

    public MetaTileEntityMEPatternBuffer(ResourceLocation id) {
        super(id, GTValues.IV, false, IItemStorageChannel.class);
        this.storage = new PatternBufferStorage(this::contentsChanged,
                stack -> PatternBufferPatterns.decode(stack, getWorld(), PatternBufferTokens.isBridgeAvailable()) !=
                        null);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity holder) {
        return new MetaTileEntityMEPatternBuffer(metaTileEntityId);
    }

    public PatternBufferStorage getPatternStorage() {
        return storage;
    }

    @Override
    protected IGridProxyable getGridProxyOwner() {
        if (!(getHolder() instanceof MetaTileEntityHolder)) return null;
        if (craftingHost == null) this.craftingHost = new PatternBufferHost((MetaTileEntityHolder) getHolder(), this);
        return craftingHost;
    }

    @Override
    public AENetworkProxy getProxy() {
        return securityBlocked ? null : super.getProxy();
    }

    @Override
    public void onPlacement(@Nullable EntityLivingBase placer) {
        super.onPlacement(placer);
        if (placer instanceof EntityPlayer && getProxy() != null) getProxy().setOwner((EntityPlayer) placer);
    }

    @Override
    public void update() {
        super.update();
        if (getWorld().isRemote) return;
        updateMEStatus();
        if (getOffsetTimer() % 20 != 0) return;
        AENetworkProxy proxy = getProxy();
        if (proxy == null || !proxy.isActive()) return;
        if (refreshPatterns || announcedPatterns != storage.getPatternRevision()) {
            try {
                proxy.getGrid().postEvent(new MENetworkCraftingPatternChange(craftingHost, proxy.getNode()));
                this.announcedPatterns = storage.getPatternRevision();
                this.refreshPatterns = false;
            } catch (GridAccessException ignored) {}
        }
        if (!storage.isQuarantined() && getHolder() instanceof MetaTileEntityHolder)
            storage.refund(new PatternBufferNetworkRefund(proxy, getActionSource()));
    }

    public void provideCrafting(ICraftingProviderHelper helper, ICraftingMedium medium) {
        if (!workingEnabled || securityBlocked || storage.isQuarantined()) return;
        for (int i = 0; i < storage.getPatterns().getSlots(); i++) {
            ICraftingPatternDetails details = decode(i);
            if (details != null) helper.addCraftingOption(medium, details);
        }
    }

    private ICraftingPatternDetails decode(int slot) {
        return PatternBufferPatterns.decode(storage.getPatterns().getStackInSlot(slot), getWorld(),
                PatternBufferTokens.isBridgeAvailable());
    }

    public boolean isBusy() {
        return !workingEnabled || detached || securityBlocked || !storage.canAccept() ||
                getWorld() == null || getWorld().isRemote || getProxy() == null ||
                !getProxy().isActive() || !getProxy().isPowered();
    }

    public boolean pushPattern(ICraftingPatternDetails details, InventoryCrafting table) {
        if (isBusy() || details == null || !getWorld().getMinecraftServer().isCallingFromMinecraftThread())
            return false;
        for (int slot = 0; slot < storage.getPatterns().getSlots(); slot++) {
            ItemStack pattern = storage.getPatterns().getStackInSlot(slot);
            if (!pattern.isEmpty() && ItemStack.areItemStacksEqual(pattern, details.getPattern()))
                return storage.accept(slot, decode(slot), table, PatternBufferTokens.isBridgeAvailable());
        }
        return false;
    }

    public boolean inputsAvailable() {
        return !detached && !securityBlocked && workingEnabled && !storage.isQuarantined() && isValid();
    }

    public List<RecipeInputView> createViews(IRecipeInputViewProvider provider, int color) {
        if (!inputsAvailable()) return Collections.emptyList();
        List<RecipeInputView> result = new ArrayList<>();
        for (PatternBufferBatch batch : storage.getBatches()) {
            if (batch.isRefundPending() || batch.isEmpty()) continue;
            List<IFluidTank> tanks = new ArrayList<>(batch.getFluids().getFluidTanks());
            tanks.addAll(storage.getSharedFluids().getFluidTanks());
            result.add(new RecipeInputView(provider, new PatternBufferKey(storage, batch.getId()),
                    new ItemHandlerList(Arrays.asList(batch.getItems(), storage.getSharedItems())),
                    FluidTankList.preservingTankOwners(true, tanks), color, true));
        }
        return result;
    }

    public boolean containsView(RecipeInputView view) {
        if (!inputsAvailable() || !(view.getSelectionKey() instanceof PatternBufferKey)) return false;
        PatternBufferKey key = (PatternBufferKey) view.getSelectionKey();
        for (PatternBufferBatch batch : storage.getBatches()) {
            if (batch.isRefundPending() || !key.matches(storage, batch) ||
                    !(view.getItems() instanceof ItemHandlerList))
                continue;
            ItemHandlerList items = (ItemHandlerList) view.getItems();
            if (items.getBackingHandlers().size() != 2 || !items.getBackingHandlers().contains(batch.getItems()) ||
                    !items.getBackingHandlers().contains(storage.getSharedItems()) || view.getFluids().getTanks() !=
                            batch.getFluids().getTanks() + storage.getSharedFluids().getTanks())
                return false;
            for (int i = 0; i < view.getFluids().getTanks(); i++) {
                IFluidTank expected = i < batch.getFluids().getTanks() ? batch.getFluids().getTankAt(i) :
                        storage.getSharedFluids().getTankAt(i - batch.getFluids().getTanks());
                if (view.getFluids().getTankAt(i) != expected) return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public List<RecipeInputView> getRecipeInputViews() {
        return getController() == null ? Collections.emptyList() : createViews(this, getPaintingColor());
    }

    @Override
    public long getRecipeInputRevision() {
        return storage.getRevision();
    }

    @Override
    public boolean isRecipeInputViewValid(RecipeInputView view) {
        return view.getProvider() == this && getController() != null && containsView(view);
    }

    @Override
    public void onRecipeInputViewStarted(RecipeInputView view) {
        storage.pruneEmpty();
    }

    public void addConsumer(MetaTileEntityMEPatternBufferProxy proxy) {
        listeners.add(proxy);
    }

    public void removeConsumer(MetaTileEntityMEPatternBufferProxy proxy) {
        listeners.remove(proxy);
    }

    private void contentsChanged() {
        markDirty();
        MultiblockControllerBase controller = getController();
        if (controller != null) controller.addNotifiedInput(placeholderItems);
        for (MetaTileEntityMEPatternBufferProxy proxy : new ArrayList<>(listeners)) proxy.sourceChanged();
    }

    @Override
    public void gridChanged() {
        this.refreshPatterns = true;
    }

    @Override
    public boolean isWorkingEnabled() {
        return workingEnabled;
    }

    @Override
    public void setWorkingEnabled(boolean enabled) {
        if (workingEnabled != enabled) {
            this.workingEnabled = enabled;
            this.refreshPatterns = true;
            storage.changed();
        }
    }

    public boolean canAccess(EntityPlayer player) {
        if (player == null || detached || !isValid() || getWorld() == null || player.world != getWorld() ||
                !getWorld().isBlockLoaded(getPos()) || getWorld().getTileEntity(getPos()) != getHolder() ||
                getHolder().getMetaTileEntity() != this ||
                !getWorld().isBlockModifiable(player, getPos()) ||
                (getOwner() != null && !getOwner().equals(player.getUniqueID()) && !player.canUseCommand(2, "")))
            return false;
        AENetworkProxy proxy = getProxy();
        if (proxy == null || !proxy.isReady()) return true;
        try {
            return !proxy.getSecurity().isAvailable() ||
                    proxy.getSecurity().hasPermission(player, SecurityPermissions.BUILD);
        } catch (GridAccessException ignored) {
            return !proxy.isActive();
        }
    }

    public void requestRefund(EntityPlayer player, long batch) {
        if (canAccess(player)) storage.requestRefund(batch);
    }

    @Override
    public void onDataStickLeftClick(EntityPlayer player, ItemStack dataStick) {
        if (getWorld().isRemote || !canAccess(player) || storage.isQuarantined()) return;
        if (getOwner() == null) {
            player.sendStatusMessage(new TextComponentTranslation("gregtech.pattern_buffer.link_unowned"), true);
            return;
        }
        NBTTagCompound tag = dataStick.hasTagCompound() ? dataStick.getTagCompound().copy() : new NBTTagCompound();
        tag.setTag("PatternBufferLink", PatternBufferLink.capture(this).write());
        dataStick.setTagCompound(tag);
        player.sendStatusMessage(new TextComponentTranslation("gregtech.pattern_buffer.link_copied"), true);
    }

    @Override
    public boolean onDataStickRightClick(EntityPlayer player, ItemStack stick) {
        return false;
    }

    @Override
    public @NotNull List<MultiblockAbility<?>> getAbilities() {
        return Arrays.asList(MultiblockAbility.IMPORT_ITEMS, MultiblockAbility.IMPORT_FLUIDS);
    }

    @Override
    public boolean requiresAllAbilityLimits() {
        return true;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilities) {
        if (abilities.isKey(MultiblockAbility.IMPORT_ITEMS)) abilities.add(placeholderItems);
        if (abilities.isKey(MultiblockAbility.IMPORT_FLUIDS)) abilities.add(placeholderFluid);
    }

    @Override
    public void addToMultiBlock(MultiblockControllerBase controller) {
        super.addToMultiBlock(controller);
        storage.changed();
    }

    @Override
    public void removeFromMultiBlock(MultiblockControllerBase controller) {
        super.removeFromMultiBlock(controller);
        storage.changed();
    }

    @Override
    public <T> T getCapability(@NotNull Capability<T> capability, @Nullable EnumFacing side) {
        if (storage != null && !storage.isQuarantined()) {
            if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(storage.getSharedItems());
            if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)
                return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(storage.getSharedFluids());
        }
        return super.getCapability(capability, side);
    }

    @Override
    protected ModularUI createUI(EntityPlayer player) {
        return PatternBufferUI.create(this, player);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState state, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(state, translation, pipeline);
        if (shouldRenderOverlay()) (isOnline ? Textures.ME_INPUT_BUS_ACTIVE : Textures.ME_INPUT_BUS)
                .renderSided(getFrontFacing(), state, translation, pipeline);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.pattern_buffer.tooltip.capacity"));
        tooltip.add(I18n.format("gregtech.pattern_buffer.tooltip.isolation"));
        tooltip.add(I18n.format("gregtech.pattern_buffer.tooltip.refund"));
        tooltip.add(I18n.format("gregtech.pattern_buffer.tooltip.bridge"));
        tooltip.add(I18n.format("gregtech.pattern_buffer.tooltip.link"));
    }

    @Override
    public boolean keepsInventory() {
        return true;
    }

    public boolean isRecoveryBlocked() {
        return !storage.isPortable();
    }

    @Override
    public float getBlockHardness() {
        return isRecoveryBlocked() ? -1 : super.getBlockHardness();
    }

    @Override
    public float getBlockResistance() {
        return isRecoveryBlocked() ? Float.MAX_VALUE : super.getBlockResistance();
    }

    @Override
    public boolean getWitherProof() {
        return isRecoveryBlocked() || super.getWitherProof();
    }

    @Override
    public void writeItemStackData(NBTTagCompound tag) {
        super.writeItemStackData(tag);
        // Overlimit quarantined data remains world-owned, never sent through normal item/UI packets.
        if (isRecoveryBlocked()) {
            tag.setBoolean("PatternRecoveryBlocked", true);
            return;
        }
        tag.setTag(DATA, storage.write());
        tag.setBoolean("PatternWorking", workingEnabled);
    }

    @Override
    public void initFromItemStackData(NBTTagCompound tag) {
        super.initFromItemStackData(tag);
        if (tag.hasKey("PatternRecoveryBlocked")) storage.read(tag.copy());
        else if (tag.hasKey(DATA)) storage.read(tag.hasKey(DATA, 10) ? tag.getCompoundTag(DATA) : tag.copy());
        storage.renewIncarnation();
        this.workingEnabled = !tag.hasKey("PatternWorking") || tag.getBoolean("PatternWorking");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag(DATA, storage.write());
        tag.setBoolean("PatternWorking", workingEnabled);
        tag.setBoolean("PatternSecurityBlocked", securityBlocked);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (tag.hasKey(DATA)) {
            if (tag.hasKey(DATA, 10)) storage.read(tag.getCompoundTag(DATA));
            else storage.read(tag.copy());
        }
        this.workingEnabled = !tag.hasKey("PatternWorking") || tag.getBoolean("PatternWorking");
        this.securityBlocked = tag.getBoolean("PatternSecurityBlocked");
        this.detached = false;
    }

    public void onSecurityBreak() {
        if (getWorld() == null || getWorld().isRemote) return;
        AENetworkProxy proxy = getProxy();
        this.securityBlocked = true;
        this.workingEnabled = false;
        if (proxy != null) proxy.invalidate();
        contentsChanged();
        if (!isRecoveryBlocked()) getWorld().destroyBlock(getPos(), true);
    }

    private void detach() {
        this.detached = true;
        for (MetaTileEntityMEPatternBufferProxy proxy : new ArrayList<>(listeners)) proxy.sourceChanged();
        listeners.clear();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.detached = false;
        storage.changed();
    }

    @Override
    public void onRemoval() {
        detach();
        super.onRemoval();
    }

    @Override
    public void onUnload() {
        detach();
        super.onUnload();
    }

    @Override
    public void invalidate() {
        detach();
        super.invalidate();
    }
}
