package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IControllable;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.IPassthroughHatch;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.mui.GTGuis;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Grid;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A hatch that moves items or fluids through a multiblock's wall: it pulls from the block in front and pushes to the
 * block behind. This holds everything that does not depend on which of the two it carries.
 *
 * @param <H> the handler the hatch exposes on its front and back
 */
public abstract class MetaTileEntityPassthroughHatch<H> extends MetaTileEntityMultiblockPart
                                                    implements IPassthroughHatch,
                                                    IMultiblockAbilityPart<IPassthroughHatch>, IControllable {

    private final String contentsTag;
    private final String langKey;
    protected H importHandler;
    protected H exportHandler;
    private boolean workingEnabled;

    /**
     * @param contentsTag the key the contents are saved under
     * @param langKey     the key prefix of the working button's tooltip, completed by {@code .enabled} or
     *                    {@code .disabled}
     */
    MetaTileEntityPassthroughHatch(ResourceLocation metaTileEntityId, int tier, String contentsTag, String langKey) {
        super(metaTileEntityId, tier);
        this.contentsTag = contentsTag;
        this.langKey = langKey;
        this.workingEnabled = true;
        initializeInventory();
    }

    /** Pushes the contents out of the back, then pulls more in from the front. */
    protected abstract void transferContents();

    protected abstract Capability<H> getHandlerCapability();

    /** The overlay drawn over the pipe overlay: for input on the front, for output on the back. */
    protected abstract SimpleOverlayRenderer getHatchOverlay(boolean input);

    protected abstract NBTTagCompound serializeContents();

    protected abstract void deserializeContents(NBTTagCompound tag);

    /** The tooltip line that says how much the hatch holds. */
    protected abstract String getCapacityTooltip();

    /** The number of slots or tanks per side of the square in the screen. */
    protected abstract int getRowSize();

    /** Registers what the slot widgets sync through, before they are made. */
    protected void registerSlotGroups(PanelSyncManager syncManager, int rowSize) {}

    protected abstract IWidget createSlotWidget(int index);

    protected abstract int getWorkingButtonTop();

    protected abstract IDrawable getWorkingButtonOverlay();

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote && getOffsetTimer() % 5 == 0) {
            if (workingEnabled) {
                transferContents();
            }
        }
    }

    @Override
    public boolean isWorkingEnabled() {
        return this.workingEnabled;
    }

    @Override
    public void setWorkingEnabled(boolean workingEnabled) {
        this.workingEnabled = workingEnabled;
        World world = getWorld();
        if (world != null && !world.isRemote) {
            writeCustomData(GregtechDataCodes.WORKING_ENABLED, buf -> buf.writeBoolean(workingEnabled));
        }
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            // front side input
            Textures.PIPE_IN_OVERLAY.renderSided(getFrontFacing(), renderState, translation, pipeline);
            getHatchOverlay(true).renderSided(getFrontFacing(), renderState, translation, pipeline);

            // back side output
            Textures.PIPE_OUT_OVERLAY.renderSided(getFrontFacing().getOpposite(), renderState, translation, pipeline);
            getHatchOverlay(false).renderSided(getFrontFacing().getOpposite(), renderState, translation, pipeline);
        }
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        int rowSize = getRowSize();
        registerSlotGroups(guiSyncManager, rowSize);

        int backgroundWidth = 9 * 18 + 14;
        int backgroundHeight = 18 + 18 * rowSize + 94;

        List<List<IWidget>> widgets = new ArrayList<>();
        for (int i = 0; i < rowSize; i++) {
            widgets.add(new ArrayList<>());
            for (int j = 0; j < rowSize; j++) {
                widgets.get(i).add(createSlotWidget(i * rowSize + j));
            }
        }

        BooleanSyncValue workingStateValue = new BooleanSyncValue(() -> workingEnabled, val -> workingEnabled = val);
        return GTGuis.createPanel(this, backgroundWidth, backgroundHeight)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7))
                .child(new Grid()
                        .top(18).height(rowSize * 18)
                        .minElementMargin(0, 0)
                        .minColWidth(18).minRowHeight(18)
                        .alignX(0.5f)
                        .matrix(widgets))
                .child(new ToggleButton()
                        .top(getWorkingButtonTop()).left(18 * 8 + 7)
                        .value(workingStateValue)
                        .overlay(getWorkingButtonOverlay())
                        .tooltip(t -> t.setAutoUpdate(true))
                        .tooltipBuilder(t -> t.addLine(workingStateValue.getBoolValue() ?
                                IKey.lang(langKey + ".enabled") :
                                IKey.lang(langKey + ".disabled"))));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag(contentsTag, serializeContents());
        tag.setBoolean("WorkingEnabled", workingEnabled);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        deserializeContents(tag.getCompoundTag(contentsTag));
        // Passthrough hatches before this change won't have WorkingEnabled at all, so we need to check if it exists
        if (tag.hasKey("WorkingEnabled")) {
            this.workingEnabled = tag.getBoolean("WorkingEnabled");
        }
    }

    @Override
    protected boolean shouldSerializeInventories() {
        return false;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(getCapacityTooltip());
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }

    @Override
    public MultiblockAbility<IPassthroughHatch> getAbility() {
        return MultiblockAbility.PASSTHROUGH_HATCH;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(this);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        // enforce strict sided-ness for IO
        Capability<H> handlerCapability = getHandlerCapability();
        if (capability == handlerCapability) {
            if (side == getFrontFacing()) {
                return handlerCapability.cast(importHandler);
            } else if (side == getFrontFacing().getOpposite()) {
                return handlerCapability.cast(exportHandler);
            } else return null;
        } else if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);

        buf.writeBoolean(workingEnabled);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);

        this.workingEnabled = buf.readBoolean();
    }
}
