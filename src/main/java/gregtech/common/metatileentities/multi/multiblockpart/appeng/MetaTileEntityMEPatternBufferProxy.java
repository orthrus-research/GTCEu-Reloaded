package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.GTValues;
import gregtech.api.capability.IDataStickIntractable;
import gregtech.api.capability.IRecipeInputViewProvider;
import gregtech.api.capability.RecipeInputView;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.mui.GTGuis;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferLink;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferUI;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.ItemStackHandler;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** A reference to loaded authoritative storage, never another copy of its materials. */
public class MetaTileEntityMEPatternBufferProxy extends MetaTileEntityMultiblockPart
                                                implements IMultiblockAbilityPart<Object>, IRecipeInputViewProvider,
                                                IDataStickIntractable {

    private final ItemStackHandler placeholderItems = new ItemStackHandler(0);
    private final FluidTank placeholderFluid = new FluidTank(0);
    private PatternBufferLink link;
    private NBTTagCompound invalidLink;
    private MetaTileEntityMEPatternBuffer observed;
    private long observedRevision;
    private long revision;
    private boolean detached;

    public MetaTileEntityMEPatternBufferProxy(ResourceLocation id) {
        super(id, GTValues.IV);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity holder) {
        return new MetaTileEntityMEPatternBufferProxy(metaTileEntityId);
    }

    private MetaTileEntityMEPatternBuffer resolve() {
        return detached || !isValid() || link == null ? null : link.resolve(getWorld(), getOwner());
    }

    @Override
    public void update() {
        super.update();
        if (getWorld().isRemote || getOffsetTimer() % 20 != 0) return;
        MetaTileEntityMEPatternBuffer current = resolve();
        long currentRevision = current == null ? 0 : current.getPatternStorage().getRevision();
        if (current != observed) {
            if (observed != null) observed.removeConsumer(this);
            this.observed = current;
            if (observed != null) observed.addConsumer(this);
            sourceChanged();
        } else if (currentRevision != observedRevision) sourceChanged();
        this.observedRevision = currentRevision;
    }

    public void sourceChanged() {
        this.revision++;
        MultiblockControllerBase controller = getController();
        if (controller != null) controller.addNotifiedInput(placeholderItems);
    }

    @Override
    public List<RecipeInputView> getRecipeInputViews() {
        MetaTileEntityMEPatternBuffer source = resolve();
        return source == null || getController() == null ? Collections.emptyList() :
                source.createViews(this, getPaintingColor());
    }

    @Override
    public long getRecipeInputRevision() {
        return revision;
    }

    @Override
    public boolean isRecipeInputViewValid(RecipeInputView view) {
        MetaTileEntityMEPatternBuffer source = resolve();
        return view.getProvider() == this && getController() != null && source != null && source.containsView(view);
    }

    @Override
    public void onRecipeInputViewStarted(RecipeInputView view) {
        MetaTileEntityMEPatternBuffer source = resolve();
        if (source != null) source.getPatternStorage().pruneEmpty();
    }

    public boolean canAccess(EntityPlayer player) {
        return player != null && !detached && isValid() && getWorld() != null && player.world == getWorld() &&
                getWorld().isBlockLoaded(getPos()) && getWorld().getTileEntity(getPos()) == getHolder() &&
                getHolder().getMetaTileEntity() == this && getWorld().isBlockModifiable(player, getPos()) &&
                (getOwner() == null || getOwner().equals(player.getUniqueID()) || player.canUseCommand(2, ""));
    }

    @Override
    public void onDataStickLeftClick(EntityPlayer player, ItemStack stick) {}

    @Override
    public boolean onDataStickRightClick(EntityPlayer player, ItemStack stick) {
        if (!stick.hasTagCompound() || !stick.getTagCompound().hasKey("PatternBufferLink", 10)) return false;
        if (getWorld().isRemote) return true;
        if (!canAccess(player)) return true;
        PatternBufferLink candidate = PatternBufferLink
                .read(stick.getTagCompound().getCompoundTag("PatternBufferLink"));
        MetaTileEntityMEPatternBuffer source = candidate == null ? null : candidate.resolve(getWorld(), getOwner());
        if (source == null || !source.canAccess(player)) {
            player.sendStatusMessage(new TextComponentTranslation("gregtech.pattern_buffer.link_unavailable"), true);
            return true;
        }
        detachSource();
        this.link = candidate;
        this.invalidLink = null;
        markDirty();
        sourceChanged();
        player.sendStatusMessage(new TextComponentTranslation("gregtech.pattern_buffer.linked"), true);
        return true;
    }

    public void unlink(EntityPlayer player) {
        if (!canAccess(player)) return;
        detachSource();
        this.link = null;
        this.invalidLink = null;
        markDirty();
        sourceChanged();
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        StringSyncValue linked = new StringSyncValue(() -> PatternBufferUI.bounded(
                link == null ? "gregtech.pattern_buffer.status.no_link" : link.describe()));
        StringSyncValue source = new StringSyncValue(() -> resolve() == null ?
                "gregtech.pattern_buffer.status.source_unavailable" : "gregtech.pattern_buffer.status.source_loaded");
        InteractionSyncHandler unlink = new InteractionSyncHandler().setOnMousePressed(mouse -> {
            if (!mouse.isClient()) unlink(guiSyncManager.getPlayer());
        });
        guiSyncManager.syncValue("pattern_buffer_link", linked);
        guiSyncManager.syncValue("pattern_buffer_source", source);
        guiSyncManager.syncValue("pattern_buffer_unlink", unlink);
        return GTGuis.createPanel(this, 176, 164)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(7, 6))
                .child(PatternBufferUI.text(linked::getStringValue).pos(7, 24))
                .child(PatternBufferUI.text(source::getStringValue).pos(7, 38))
                .child(new ButtonWidget<>()
                        .pos(7, 54).size(80, 18)
                        .overlay(IKey.lang("gregtech.pattern_buffer.unlink"))
                        .syncHandler(unlink))
                .bindPlayerInventory();
    }

    @Override
    public void renderMetaTileEntity(CCRenderState state, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(state, translation, pipeline);
        if (shouldRenderOverlay())
            Textures.DATA_ACCESS_HATCH.renderSided(getFrontFacing(), state, translation, pipeline);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.pattern_buffer.tooltip.proxy"));
        tooltip.add(I18n.format("gregtech.pattern_buffer.tooltip.link"));
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
        sourceChanged();
    }

    @Override
    public void removeFromMultiBlock(MultiblockControllerBase controller) {
        super.removeFromMultiBlock(controller);
        sourceChanged();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        writeLink(tag);
        return tag;
    }

    private void writeLink(NBTTagCompound tag) {
        if (invalidLink != null) tag.setTag("PatternBufferLink", invalidLink.copy());
        else if (link != null) tag.setTag("PatternBufferLink", link.write());
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readLink(tag);
        this.detached = false;
    }

    private void readLink(NBTTagCompound tag) {
        detachSource();
        this.link = tag.hasKey("PatternBufferLink", 10) ?
                PatternBufferLink.read(tag.getCompoundTag("PatternBufferLink")) : null;
        this.invalidLink = link == null && tag.hasKey("PatternBufferLink", 10) ?
                tag.getCompoundTag("PatternBufferLink").copy() : null;
        sourceChanged();
    }

    @Override
    public void writeItemStackData(NBTTagCompound tag) {
        super.writeItemStackData(tag);
        writeLink(tag);
    }

    @Override
    public void initFromItemStackData(NBTTagCompound tag) {
        super.initFromItemStackData(tag);
        readLink(tag);
    }

    private void detachSource() {
        if (observed != null) observed.removeConsumer(this);
        this.observed = null;
    }

    private void detach() {
        this.detached = true;
        detachSource();
        sourceChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.detached = false;
        sourceChanged();
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
