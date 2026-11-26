package gregtech.common.metatileentities.multi.electric.centralmonitor;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.cover.Cover;
import gregtech.api.cover.CoverHolder;
import gregtech.api.items.behavior.MonitorPluginBaseBehavior;
import gregtech.api.items.behavior.ProxyHolderPluginBehavior;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.items.toolitem.ToolClasses;
import gregtech.api.items.toolitem.ToolHelper;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaTileEntityGuiFactory;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.pipenet.tile.PipeCoverableImplementation;
import gregtech.api.util.FacingPos;
import gregtech.api.util.GTLog;
import gregtech.client.renderer.handler.BlockPosHighlightRenderer;
import gregtech.client.utils.RenderUtil;
import gregtech.common.covers.CoverDigitalInterface;
import gregtech.common.metatileentities.MetaTileEntities;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;
import gregtech.common.mui.widget.ARGBColorWidget;
import gregtech.common.mui.widget.monitor.MonitorScreenGridWidget;
import gregtech.common.mui.widget.monitor.MonitorScreenPreviewWidget;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.EnumSyncValue;
import com.cleanroommc.modularui.value.sync.FloatSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class MetaTileEntityMonitorScreen extends MetaTileEntityMultiblockPart {

    private static final int WIDTH = 330;
    private static final int HEIGHT = 260;
    private static final String SELECT_COVER = "select_cover";
    private static final String OPEN_SCREEN = "open_screen";
    private static final IDrawable SELECTED = new Rectangle().setColor(0x4BFFFFFF);

    // run-time data
    public CoverDigitalInterface coverTMP;
    private long lastClickTime;
    private UUID lastClickUUID;
    public MonitorPluginBaseBehavior plugin;
    // persistent data
    public FacingPos coverPos;
    public CoverDigitalInterface.MODE mode = CoverDigitalInterface.MODE.FLUID;
    public int slot = 0;
    public float scale = 1;
    public int frameColor = 0XFF00FF00;
    private ItemStackHandler inventory;

    public MetaTileEntityMonitorScreen(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, 1);
    }

    public void setMode(FacingPos cover, CoverDigitalInterface.MODE mode) {
        CoverDigitalInterface last_cover = this.getCoverFromPosSide(coverPos);
        CoverDigitalInterface now_cover = this.getCoverFromPosSide(cover);
        if (this.mode == mode) {
            if (Objects.equals(cover, coverPos) && last_cover == null && cover == null ||
                    last_cover != null && last_cover == now_cover) {
                return;
            }
        }
        if (last_cover != null && this.mode != CoverDigitalInterface.MODE.PROXY) {
            last_cover.unSubProxyMode(this.mode);
        }
        if (now_cover != null && mode != CoverDigitalInterface.MODE.PROXY) {
            now_cover.subProxyMode(mode);
        }
        this.coverPos = cover;
        this.mode = mode;
        updateProxyPlugin();
        writeCustomData(GregtechDataCodes.UPDATE_ALL, this::writeSync);
        this.markDirty();
    }

    public void setMode(FacingPos cover) {
        setMode(cover, this.mode);
    }

    public void setMode(CoverDigitalInterface.MODE mode) {
        if (mode == null) return;
        this.setMode(this.coverPos, mode);
    }

    /**
     * Sets the slot shown, the scale and the frame colour. Each is checked on its own, since a client sends them: a
     * negative slot or a scale outside 1 to 8 keeps the current one.
     */
    public void setConfig(int slot, float scale, int color) {
        boolean changed = false;
        if (slot >= 0 && slot != this.slot) {
            this.slot = slot;
            changed = true;
        }
        if (isValidScale(scale) && scale != this.scale) {
            this.scale = scale;
            changed = true;
        }
        if (color != this.frameColor) {
            this.frameColor = color;
            changed = true;
        }
        if (!changed) return;
        writeCustomData(GregtechDataCodes.UPDATE_ALL, this::writeSync);
        markDirty();
    }

    /** Whether a screen may be drawn at {@code scale} times its size. */
    public static boolean isValidScale(float scale) {
        return scale >= 1 && scale <= 8;
    }

    /**
     * Shows the digital interface cover at {@code cover}, or none when it is null. A client names the cover, so only
     * one of the covers this screen's central monitor lists is accepted.
     *
     * @return whether the cover was accepted
     */
    public boolean selectCover(@Nullable FacingPos cover) {
        if (cover == null) {
            setMode(null, this.mode);
            return true;
        }
        MultiblockControllerBase controller = getController();
        if (!(controller instanceof MetaTileEntityCentralMonitor centralMonitor) ||
                !centralMonitor.getAllCovers().contains(cover) || getCoverFromPosSide(cover) == null) {
            return false;
        }
        setMode(cover);
        return true;
    }

    public CoverDigitalInterface getCoverFromPosSide(FacingPos posFacing) {
        if (posFacing == null) return null;
        CoverHolder coverHolder = null;
        IGregTechTileEntity holder = getHolderFromPos(posFacing.getPos());
        if (holder == null) {
            TileEntity te = this.getWorld() == null ? null : this.getWorld().getTileEntity(posFacing.getPos());
            if (te instanceof IPipeTile<?, ?> pipeTile) {
                coverHolder = pipeTile.getCoverableImplementation();
            }
        } else {
            coverHolder = holder.getMetaTileEntity();
        }
        if (coverHolder != null) {
            Cover cover = coverHolder.getCoverAtSide(posFacing.getFacing());
            if (cover instanceof CoverDigitalInterface digitalInterface) {
                return digitalInterface;
            }
        }
        return null;
    }

    public IGregTechTileEntity getHolderFromPos(BlockPos pos) {
        TileEntity te = this.getWorld() == null || pos == null ? null : this.getWorld().getTileEntity(pos);
        if (te instanceof IGregTechTileEntity && ((IGregTechTileEntity) te).isValid()) {
            return (IGregTechTileEntity) te;
        }
        return null;
    }

    public void updateCoverValid(Set<FacingPos> covers) {
        if (this.coverPos != null) {
            if (!covers.contains(this.coverPos)) {
                setMode(null, CoverDigitalInterface.MODE.PROXY);
            }
        }
    }

    private void writeSync(PacketBuffer buf) {
        buf.writeBoolean(this.coverPos != null);
        if (this.coverPos != null) {
            buf.writeBlockPos(coverPos.getPos());
            buf.writeByte(coverPos.getFacing().getIndex());
        }
        buf.writeByte(this.mode.ordinal());
        buf.writeVarInt(this.slot);
        buf.writeFloat(this.scale);
        buf.writeVarInt(this.frameColor);
    }

    private void readSync(PacketBuffer buf) {
        if (buf.readBoolean()) {
            BlockPos pos = buf.readBlockPos();
            EnumFacing side = EnumFacing.byIndex(buf.readByte());
            FacingPos pair = new FacingPos(pos, side);
            if (!pair.equals(this.coverPos)) {
                this.coverTMP = null;
                this.coverPos = pair;
            }
        } else {
            this.coverPos = null;
            this.coverTMP = null;
        }
        this.mode = CoverDigitalInterface.MODE.VALUES[buf.readByte()];
        this.slot = buf.readVarInt();
        this.scale = buf.readFloat();
        this.frameColor = buf.readVarInt();
        updateProxyPlugin();
    }

    private void updateProxyPlugin() {
        if (this.plugin instanceof ProxyHolderPluginBehavior) {
            if (this.mode == CoverDigitalInterface.MODE.PROXY && coverPos != null) {
                ((ProxyHolderPluginBehavior) this.plugin).onHolderPosUpdated(coverPos.getPos());
            } else {
                ((ProxyHolderPluginBehavior) this.plugin).onHolderPosUpdated(null);
            }
        }
    }

    public int getX() {
        MultiblockControllerBase controller = this.getController();
        if (controller != null) {
            EnumFacing spin = controller.getUpwardsFacing();
            switch (controller.getFrontFacing().getAxis()) {
                case Y -> {
                    if (spin.getAxis() == EnumFacing.Axis.X)
                        return Math.abs(this.getController().getPos().getZ() - this.getPos().getZ()) - 1;
                    else
                        return Math.abs(this.getController().getPos().getX() - this.getPos().getX()) - 1;
                }
                case X -> {
                    if (spin.getAxis() == EnumFacing.Axis.Z)
                        return Math.abs(this.getController().getPos().getZ() - this.getPos().getZ()) - 1;
                    else
                        return Math.abs(this.getController().getPos().getY() - this.getPos().getY()) - 1;
                }
                default -> {
                    if (spin.getAxis() == EnumFacing.Axis.Z)
                        return Math.abs(this.getController().getPos().getX() - this.getPos().getX()) - 1;
                    else
                        return Math.abs(this.getController().getPos().getY() - this.getPos().getY()) - 1;
                }
            }
        }
        return -1;
    }

    public int getY() {
        MultiblockControllerBase controller = this.getController();
        if (controller != null) {
            EnumFacing spin = controller.getUpwardsFacing();
            EnumFacing facing = controller.getFrontFacing();
            int height = ((MetaTileEntityCentralMonitor) this.getController()).height;
            switch (facing.getAxis()) {
                case Y -> {
                    if (spin.getAxis() == EnumFacing.Axis.X)
                        return height -
                                (Math.abs(controller.getPos().getX() - spin.getXOffset() - this.getPos().getX())) - 1;
                    else
                        return height -
                                (Math.abs(controller.getPos().getZ() - spin.getZOffset() - this.getPos().getZ())) - 1;
                }
                case X -> {
                    if (spin.getAxis() == EnumFacing.Axis.Z)
                        return height -
                                (Math.abs(controller.getPos().getY() + spin.getZOffset() - this.getPos().getY())) - 1;
                    else
                        return height - (Math.abs(
                                controller.getPos().getZ() + spin.getXOffset() * facing.rotateY().getZOffset() -
                                        this.getPos().getZ())) -
                                1;
                }
                default -> {
                    if (spin.getAxis() == EnumFacing.Axis.Z)
                        return height -
                                (Math.abs(controller.getPos().getY() + spin.getZOffset() - this.getPos().getY())) - 1;
                    else
                        return height - (Math.abs(
                                controller.getPos().getX() + spin.getXOffset() * facing.rotateY().getXOffset() -
                                        this.getPos().getX())) -
                                1;
                }
            }
        }
        return -1;
    }

    public boolean isActive() {
        if (this.coverPos != null && this.mode != CoverDigitalInterface.MODE.PROXY) {
            CoverDigitalInterface cover = coverTMP != null ? coverTMP : this.getCoverFromPosSide(this.coverPos);
            if (cover != null) {
                if (cover.isProxy()) {
                    coverTMP = cover;
                    return true;
                }
            }
            this.coverPos = null;
        }
        return plugin != null;
    }

    public void pluginDirty() {
        if (plugin != null) {
            plugin.writeToNBT(this.itemInventory.getStackInSlot(0).getOrCreateSubCompound("monitor_plugin"));
        }
    }

    private void loadPlugin(MonitorPluginBaseBehavior plugin) {
        if (plugin != null && this.plugin != plugin) {
            this.plugin = plugin.createPlugin();
            this.plugin.readFromNBT(this.itemInventory.getStackInSlot(0).getOrCreateSubCompound("monitor_plugin"));
            this.plugin.onMonitorValid(this, true);
        }
        updateProxyPlugin();
    }

    private void unloadPlugin() {
        if (plugin != null) {
            this.plugin.onMonitorValid(null, false);
        }
        this.plugin = null;
    }

    @Override
    public void update() {
        super.update();
        if (plugin != null && this.getController() != null && this.isActive()) {
            plugin.update();
        }
    }

    @SideOnly(Side.CLIENT)
    public void renderScreen(float partialTicks, RayTraceResult rayTraceResult) {
        if (getController() == null) return;
        EnumFacing side = getController().getFrontFacing();
        GlStateManager.translate((scale - 1) * 0.5, (scale - 1) * 0.5, 0);
        GlStateManager.scale(this.scale, this.scale, 1);

        if (plugin != null) {
            plugin.renderPlugin(partialTicks, rayTraceResult);
        }

        if (coverTMP != null) {
            boolean flag = true;
            if (checkLookingAt(rayTraceResult) != null && plugin == null &&
                    this.mode != CoverDigitalInterface.MODE.PROXY) {
                if (coverTMP.renderSneakingLookAt(rayTraceResult.getBlockPos(), side, slot, partialTicks)) {
                    flag = false;
                }
            }
            if (this.mode == CoverDigitalInterface.MODE.PROXY) return;
            if (flag) {
                coverTMP.renderMode(this.mode, this.slot, partialTicks);

                TileEntity te = coverTMP.getCoveredTE();
                if (te != null) {
                    ItemStack itemStack;
                    if (te instanceof IGregTechTileEntity) {
                        itemStack = ((IGregTechTileEntity) te).getMetaTileEntity().getStackForm();
                    } else {
                        BlockPos pos = te.getPos();
                        itemStack = te.getBlockType().getPickBlock(te.getWorld().getBlockState(pos),
                                new RayTraceResult(new Vec3d(0.5, 0.5, 0.5), coverTMP.getCoveredFacing(), pos),
                                te.getWorld(), pos, Minecraft.getMinecraft().player);
                    }
                    String name = itemStack.getDisplayName();
                    // render machine
                    RenderUtil.renderItemOverLay(-2.6f, -2.65f, 0.003f, 1 / 100f, itemStack);
                    // render name
                    RenderUtil.renderText(0, -3.5f / 16, 0, 1.0f / 200, 0XFFFFFFFF, name, true);
                }

            }
            // render frame
            RenderUtil.renderRect(-7f / 16, -7f / 16, 14f / 16, 0.01f, 0.003f, frameColor);
            RenderUtil.renderRect(-7f / 16, -4f / 16 - 0.01f, 14f / 16, 0.01f, 0.003f, frameColor);
            RenderUtil.renderRect(-7f / 16, -7f / 16 + 0.01f, 0.01f, 3f / 16 - 0.02f, 0.003f, frameColor);
            RenderUtil.renderRect(7f / 16 - 0.01f, -7f / 16 + 0.01f, 0.01f, 3f / 16 - 0.02f, 0.003f, frameColor);

            RenderUtil.renderRect(-7f / 16, -3f / 16, 14f / 16, 0.01f, 0.003f, frameColor);
            RenderUtil.renderRect(-7f / 16, 7f / 16 - 0.01f, 14f / 16, 0.01f, 0.003f, frameColor);
            RenderUtil.renderRect(-7f / 16, -3f / 16 + 0.01f, 0.01f, 10f / 16 - 0.02f, 0.003f, frameColor);
            RenderUtil.renderRect(7f / 16 - 0.01f, -3f / 16 + 0.01f, 0.01f, 10f / 16 - 0.02f, 0.003f, frameColor);
        }
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        writeSync(buf);
        buf.writeItemStack(this.inventory.getStackInSlot(0));
        if (plugin != null) {
            plugin.writeInitialSyncData(buf);
        }
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        readSync(buf);
        try {
            ItemStack itemStack = buf.readItemStack();
            MonitorPluginBaseBehavior behavior = MonitorPluginBaseBehavior.getBehavior(itemStack);
            if (behavior == null) {
                unloadPlugin();
            } else {
                this.inventory.setStackInSlot(0, itemStack);
                loadPlugin(behavior);
                plugin.receiveInitialSyncData(buf);
            }
        } catch (IOException e) {
            GTLog.logger.error("Could not initialize Monitor Screen from InitialSyncData buffer", e);
        }
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.UPDATE_ALL) {
            readSync(buf);
        } else if (dataId == GregtechDataCodes.UPDATE_PLUGIN_DATA) { // plugin
            if (plugin != null) {
                plugin.readPluginData(buf.readVarInt(), buf);
            }
        } else if (dataId == GregtechDataCodes.UPDATE_PLUGIN_ITEM) {
            try {
                ItemStack itemStack = buf.readItemStack();
                MonitorPluginBaseBehavior behavior = MonitorPluginBaseBehavior.getBehavior(itemStack);
                if (behavior == null) {
                    unloadPlugin();
                } else {
                    this.inventory.setStackInSlot(0, itemStack);
                    loadPlugin(behavior);
                }
            } catch (IOException e) {
                GTLog.logger.error("Could not initialize Monitor Screen from CustomData buffer", e);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        if (this.coverPos != null) {
            data.setTag("coverPos", NBTUtil.createPosTag(this.coverPos.getPos()));
            data.setByte("coverSide", (byte) this.coverPos.getFacing().getIndex());
        }
        data.setByte("mode", (byte) this.mode.ordinal());
        data.setFloat("scale", this.scale);
        data.setInteger("color", this.frameColor);
        data.setInteger("slot", this.slot);
        data.setTag("Inventory", this.inventory.serializeNBT());
        return super.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.frameColor = data.hasKey("color") ? data.getInteger("color") : 0XFF00Ff00;
        this.scale = data.hasKey("scale") ? data.getFloat("scale") : 1;
        this.slot = data.hasKey("slot") ? data.getInteger("slot") : 0;
        this.mode = CoverDigitalInterface.MODE.VALUES[data.hasKey("mode") ? data.getByte("mode") : 0];
        this.inventory.deserializeNBT(data.getCompoundTag("Inventory"));
        if (data.hasKey("coverPos") && data.hasKey("coverSide")) {
            BlockPos pos = NBTUtil.getPosFromTag(data.getCompoundTag("coverPos"));
            EnumFacing side = EnumFacing.byIndex(data.getByte("coverSide"));
            this.coverPos = new FacingPos(pos, side);
        } else {
            this.coverPos = null;
        }
        MonitorPluginBaseBehavior behavior = MonitorPluginBaseBehavior.getBehavior(this.inventory.getStackInSlot(0));
        if (behavior == null) {
            unloadPlugin();
        } else {
            loadPlugin(behavior);
        }
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        this.inventory = new GTItemStackHandler(this) {

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                MonitorPluginBaseBehavior behavior = MonitorPluginBaseBehavior.getBehavior(stack);
                return behavior != null;
            }

            @NotNull
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (!getWorld().isRemote && !getStackInSlot(slot).isEmpty() && !simulate) {
                    unloadPlugin();
                    writeCustomData(GregtechDataCodes.UPDATE_PLUGIN_ITEM, packetBuffer -> {
                        packetBuffer.writeItemStack(ItemStack.EMPTY);
                    });
                }
                return super.extractItem(slot, amount, simulate);
            }
        };
        this.itemInventory = this.inventory;
    }

    @Override
    public boolean shouldRenderOverlay() {
        MultiblockControllerBase controller = this.getController();
        return controller instanceof MetaTileEntityCentralMonitor && controller.isActive();
    }

    @Override
    public boolean canPlaceCoverOnSide(@NotNull EnumFacing side) {
        return this.getController() != null && this.getController().getFrontFacing() != side;
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        if (!this.getWorld().isRemote) {
            this.setMode(null, this.mode);
        }
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY ||
                capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            Cover cover = getCoverFromPosSide(this.coverPos);
            if (cover != null) {
                return cover.getCoverableView().getCapability(capability, cover.getAttachedSide());
            }
        }
        return null;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityMonitorScreen(this.metaTileEntityId);
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    /** Whether this screen's settings can be opened: it belongs to an active central monitor. */
    private boolean canOpenUI() {
        MultiblockControllerBase controller = getController();
        return controller instanceof MetaTileEntityCentralMonitor && controller.isActive();
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        settings.canInteractWith(player -> isValid() && canOpenUI());

        EnumSyncValue<CoverDigitalInterface.MODE> modeValue = new EnumSyncValue<>(CoverDigitalInterface.MODE.class,
                () -> this.mode, this::setMode);
        FloatSyncValue scaleValue = new FloatSyncValue(() -> this.scale,
                value -> setConfig(this.slot, value, this.frameColor));
        IntSyncValue slotValue = new IntSyncValue(() -> this.slot,
                value -> setConfig(value, this.scale, this.frameColor));
        IntSyncValue colorValue = new IntSyncValue(() -> this.frameColor,
                value -> setConfig(this.slot, this.scale, value));
        syncManager.syncValue("mode", modeValue);
        syncManager.syncValue("scale", scaleValue);
        syncManager.syncValue("slot", slotValue);
        syncManager.syncValue("frame_color", colorValue);
        syncManager.registerSyncedAction(SELECT_COVER, false, true, buf -> selectCover(readFacingPos(buf)));
        syncManager.registerSyncedAction(OPEN_SCREEN, false, true,
                buf -> openScreen(syncManager.getPlayer(), buf.readVarInt(), buf.readVarInt()));
        syncManager.addCloseListener(player -> {
            if (this.plugin != null && !player.world.isRemote) {
                this.plugin.markAsDirty();
            }
        });

        IPanelHandler pluginConfig = syncManager.panel("plugin_config", (manager, handler) -> {
            MonitorPluginBaseBehavior shown = this.plugin;
            int width = shown == null ? 0 : shown.getConfigWidth();
            int height = shown == null ? 0 : shown.getConfigHeight();
            GTGuis.PopupPanel panel = GTGuis.createPopupPanel("plugin_config", Math.max(width, 60),
                    Math.max(height, 30), true);
            if (shown != null) shown.buildConfigUI(panel, manager);
            return panel;
        }, true);
        IPanelHandler screenGrid = syncManager.panel("screen_grid",
                (manager, handler) -> createScreenGrid(syncManager), true);

        ModularPanel panel = GTGuis.createPanel(this, WIDTH, HEIGHT)
                .child(IKey.lang("gregtech.machine.monitor_screen.name").asWidget().pos(15, 13))
                .child(new ButtonWidget<>()
                        .pos(15, 25)
                        .size(40, 20)
                        .overlay(IKey.lang("monitor.gui.title.back"))
                        .onMousePressed(mouseButton -> {
                            if (canOpenUI()) screenGrid.openPanel();
                            return true;
                        }))
                .child(IKey.lang("monitor.gui.title.scale").asWidget().pos(15, 55))
                .child(stepButton("-1", 50, 50, () -> scaleValue.setFloatValue(stepScale(scaleValue, -1))))
                .child(display(IKey.dynamic(() -> Float.toString(scaleValue.getFloatValue())), 70, 50))
                .child(stepButton("+1", 130, 50, () -> scaleValue.setFloatValue(stepScale(scaleValue, 1))))
                .child(IKey.lang("monitor.gui.title.argb").asWidget().pos(15, 85))
                .child(new ARGBColorWidget(colorValue, 20).pos(50, 80))
                .child(IKey.lang("monitor.gui.title.slot").asWidget().pos(15, 110))
                .child(stepButton("-1", 50, 105, () -> slotValue.setIntValue(slotValue.getIntValue() - 1)))
                .child(display(IKey.dynamic(() -> Integer.toString(slotValue.getIntValue())), 70, 105))
                .child(stepButton("+1", 130, 105, () -> slotValue.setIntValue(slotValue.getIntValue() + 1)))
                .child(IKey.lang("monitor.gui.title.plugin").asWidget().pos(15, 135))
                .child(new ItemSlot()
                        .slot(SyncHandlers.itemSlot(this.inventory, 0)
                                .singletonSlotGroup()
                                .changeListener((newItem, onlyAmountChanged, client, init) -> {
                                    if (client || init || onlyAmountChanged) return;
                                    onPluginSlotChanged();
                                    if (pluginConfig.isPanelOpen()) pluginConfig.closePanel();
                                }))
                        .pos(50, 130))
                .child(new ButtonWidget<>()
                        .pos(80, 130)
                        .size(40, 20)
                        .overlay(IKey.lang("monitor.gui.title.config"))
                        .onMousePressed(mouseButton -> {
                            if (this.plugin != null && this.plugin.getConfigWidth() > 0) pluginConfig.openPanel();
                            return true;
                        }))
                .child(new ListWidget<>()
                        .children(createCoverRows(syncManager))
                        .pos(WIDTH - 140, 50)
                        .size(120, 11 * 18))
                .child(SlotGroupWidget.playerInventory(false).pos(15, 170))
                .child(new MonitorScreenPreviewWidget(this, 150).pos(WIDTH, 0));

        int[] modeX = { WIDTH - 135, WIDTH - 115, WIDTH - 95, WIDTH - 75, WIDTH - 35 };
        for (CoverDigitalInterface.MODE mode : CoverDigitalInterface.MODE.VALUES) {
            panel.child(new ButtonWidget<>()
                    .pos(modeX[mode.ordinal()], 25)
                    .size(20)
                    .onMousePressed(mouseButton -> {
                        modeValue.setValue(mode);
                        return true;
                    })
                    .tooltipAutoUpdate(true)
                    .tooltipBuilder(tooltip -> tooltip.add(mode.getLangKey(modeValue.getValue() == mode)))
                    .disableHoverBackground()
                    .background(new DynamicDrawable(() -> mode.getOverlay(modeValue.getValue() == mode))));
        }
        return panel;
    }

    /** The scale a step button sets: a tenth more or less, or a whole one while shift is held. */
    private static float stepScale(FloatSyncValue scale, int direction) {
        float step = Interactable.hasShiftDown() ? 1.0f : 0.1f;
        return Math.round((scale.getFloatValue() + direction * step) * 10) / 10f;
    }

    private static ButtonWidget<?> stepButton(String label, int x, int y, Runnable onPress) {
        return new ButtonWidget<>()
                .pos(x, y)
                .size(20)
                .overlay(IKey.str(label))
                .onMousePressed(mouseButton -> {
                    onPress.run();
                    return true;
                });
    }

    private static IWidget display(IKey text, int x, int y) {
        return text.color(0xFFFFFFFF)
                .asWidget()
                .alignment(Alignment.Center)
                .pos(x, y)
                .size(60, 20)
                .background(GTGuiTextures.DISPLAY);
    }

    /** Server: loads the plugin now in the slot and shows it to the players watching. */
    private void onPluginSlotChanged() {
        if (getWorld() == null || getWorld().isRemote) return;
        MonitorPluginBaseBehavior behavior = MonitorPluginBaseBehavior.getBehavior(inventory.getStackInSlot(0));
        if (behavior == null) {
            unloadPlugin();
        } else {
            loadPlugin(behavior);
        }
        writeCustomData(GregtechDataCodes.UPDATE_PLUGIN_ITEM,
                packetBuffer -> packetBuffer.writeItemStack(inventory.getStackInSlot(0)));
    }

    /**
     * The digital interface covers this screen can show. The list is drawn from what the client knows; selecting one
     * sends only its position, and the server accepts it only from its own list.
     */
    private List<IWidget> createCoverRows(PanelSyncManager syncManager) {
        List<IWidget> rows = new ArrayList<>();
        if (!syncManager.isClient() || !(getController() instanceof MetaTileEntityCentralMonitor controller)) {
            return rows;
        }
        for (FacingPos coverPos : controller.getAllCovers()) {
            CoverDigitalInterface cover = getCoverFromPosSide(coverPos);
            if (cover == null) continue;
            ItemStack itemStack = cover.getPickItem();
            BlockPos pos = cover.getPos();
            if (cover.getCoverableView() instanceof PipeCoverableImplementation) {
                pos = pos.offset(cover.getAttachedSide());
                TileEntity tileEntity = cover.getWorld().getTileEntity(pos);
                if (tileEntity == null) continue;
                itemStack = tileEntity.getBlockType().getItem(cover.getWorld(), pos,
                        cover.getWorld().getBlockState(pos));
            }
            rows.add(coverRow(syncManager, new FacingPos(cover.getPos(), cover.getAttachedSide()), itemStack, pos));
        }
        return rows;
    }

    private IWidget coverRow(PanelSyncManager syncManager, FacingPos selection, ItemStack icon, BlockPos shownPos) {
        String posText = String.format("(%d, %d, %d)", shownPos.getX(), shownPos.getY(), shownPos.getZ());
        return Flow.row()
                .widthRel(1f)
                .height(18)
                .background(new DynamicDrawable(() -> selection.equals(this.coverPos) ? SELECTED : IDrawable.EMPTY))
                .child(new ButtonWidget<>()
                        .size(18)
                        .background(GTGuiTextures.SLOT, new ItemDrawable(icon).asIcon().size(16))
                        .disableHoverBackground()
                        .onMousePressed(mouseButton -> {
                            highlightCover(shownPos, icon, posText);
                            return true;
                        }))
                .child(new ButtonWidget<>()
                        .height(18)
                        .expanded()
                        .background(IDrawable.EMPTY)
                        .disableHoverBackground()
                        .overlay(IKey.str(posText).alignment(Alignment.CenterLeft))
                        .onMousePressed(mouseButton -> {
                            boolean deselect = selection.equals(this.coverPos);
                            syncManager.callSyncedAction(SELECT_COVER, buf -> {
                                buf.writeBoolean(!deselect);
                                if (!deselect) {
                                    buf.writeBlockPos(selection.getPos());
                                    buf.writeByte(selection.getFacing().getIndex());
                                }
                            });
                            return true;
                        }));
    }

    private static @Nullable FacingPos readFacingPos(PacketBuffer buf) {
        if (!buf.readBoolean()) return null;
        BlockPos pos = buf.readBlockPos();
        int side = buf.readByte();
        return side < 0 || side >= EnumFacing.VALUES.length ? null : new FacingPos(pos, EnumFacing.VALUES[side]);
    }

    @SideOnly(Side.CLIENT)
    private static void highlightCover(BlockPos pos, ItemStack icon, String posText) {
        BlockPosHighlightRenderer.renderBlockBoxHighLight(pos, 5000);
        Minecraft.getMinecraft().player.closeScreen();
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString(icon.getDisplayName() + ": " + posText));
    }

    /** The grid of this monitor's screens; a click opens that screen's settings. */
    private ModularPanel createScreenGrid(PanelSyncManager mainSyncManager) {
        MetaTileEntityCentralMonitor controller = getController() instanceof MetaTileEntityCentralMonitor monitor ?
                monitor : null;
        int width = controller == null ? 0 : controller.width;
        int height = controller == null ? 0 : controller.height;
        GTGuis.PopupPanel panel = GTGuis.createPopupPanel("screen_grid", Math.max(28 * width, 60),
                Math.max(28 * height, 40), true);
        int cell = MonitorScreenGridWidget.SIZE;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                panel.child(new MonitorScreenGridWidget(controller, i, j,
                        (column, row) -> mainSyncManager.callSyncedAction(OPEN_SCREEN, buf -> {
                            buf.writeVarInt(column);
                            buf.writeVarInt(row);
                        })).pos(4 * width + i * cell, 4 * height + j * cell));
            }
        }
        return panel;
    }

    /**
     * Server: opens the settings of the screen at a column and row of this screen's central monitor. A client names
     * the cell, so only a screen of the same active monitor opens.
     */
    private void openScreen(EntityPlayer player, int column, int row) {
        if (!(getController() instanceof MetaTileEntityCentralMonitor controller) || !controller.isActive()) return;
        MetaTileEntityMonitorScreen[][] screens = controller.screens;
        if (screens == null || column < 0 || column >= screens.length || row < 0 || row >= screens[column].length) {
            return;
        }
        MetaTileEntityMonitorScreen screen = screens[column][row];
        if (screen != null && screen.isValid() && screen.getController() == controller) {
            MetaTileEntityGuiFactory.open(player, screen);
        }
    }

    // adaptive click, supports scaling. x and y is the pos of the origin screen (scale = 1). this func must be called
    // when screen is active.
    public boolean onClickLogic(EntityPlayer playerIn, EnumHand hand, EnumFacing facing, boolean isRight, double x,
                                double y) {
        if (this.plugin != null) {
            boolean flag = this.plugin.onClickLogic(playerIn, hand, facing, isRight, x, y);
            if (flag) return true;
        }
        if (this.getWorld().isRemote) return true;
        CoverDigitalInterface cover = getCoverFromPosSide(this.coverPos);
        if (isRight) {
            if (cover != null && cover.isProxy() && this.mode != CoverDigitalInterface.MODE.PROXY) {
                if (playerIn.isSneaking() && playerIn.getHeldItemMainhand().isEmpty() && this.plugin == null) {
                    if (1f / 16 < x && x < 4f / 16 && 1f / 16 < y && y < 4f / 16) {
                        this.setConfig(this.slot - 1, this.scale, this.frameColor);
                        return true;
                    } else if (12f / 16 < x && x < 15f / 16 && 1f / 16 < y && y < 4f / 16) {
                        this.setConfig(this.slot + 1, this.scale, this.frameColor);
                        return true;
                    }
                }
                if (cover.modeRightClick(playerIn, hand, this.mode, this.slot) == EnumActionResult.PASS) {
                    if (!playerIn.isSneaking() && this.openGUIOnRightClick()) {
                        TileEntity te = cover.getCoveredTE();
                        if (te != null) {
                            BlockPos pos = te.getPos();
                            IBlockState state = te.getWorld().getBlockState(pos);
                            state.getBlock().onBlockActivated(cover.getWorld(), pos, state, playerIn, hand,
                                    cover.getCoveredFacing(), 0.5f, 0.5f, 0.5f);
                        }
                        return true;
                    } else {
                        return false;
                    }
                }
                return true;
            }
        } else {
            if (cover != null && cover.isProxy() && this.mode != CoverDigitalInterface.MODE.PROXY) {
                return cover.modeLeftClick(playerIn, this.mode, this.slot);
            }
        }
        return false;
    }

    private static double[] handleRayTraceResult(RayTraceResult rayTraceResult) {
        double dX = rayTraceResult.sideHit.getAxis() == EnumFacing.Axis.X ?
                rayTraceResult.hitVec.z - rayTraceResult.getBlockPos().getZ() :
                rayTraceResult.hitVec.x - rayTraceResult.getBlockPos().getX();
        double dY = rayTraceResult.sideHit.getAxis() == EnumFacing.Axis.Y ?
                rayTraceResult.hitVec.z - rayTraceResult.getBlockPos().getZ() :
                rayTraceResult.hitVec.y - rayTraceResult.getBlockPos().getY();
        dX = 1 - dX;
        dY = 1 - dY;
        if (rayTraceResult.sideHit.getYOffset() < 0) {
            dY = 1 - dY;
        }
        if (rayTraceResult.sideHit == EnumFacing.WEST || rayTraceResult.sideHit == EnumFacing.SOUTH) {
            dX = 1 - dX;
        } else if (rayTraceResult.sideHit == EnumFacing.UP) {
            dX = 1 - dX;
            dY = 1 - dY;
        }
        return new double[] { dX, dY };
    }

    private boolean handleHitResultWithScale(EntityPlayer playerIn, EnumHand hand, EnumFacing facing, boolean isRight,
                                             CuboidRayTraceResult rayTraceResult) {
        boolean flag = false;
        if (rayTraceResult != null && rayTraceResult.typeOfHit == RayTraceResult.Type.BLOCK &&
                this.getController() != null) {
            double[] pos = handleRayTraceResult(rayTraceResult);
            MetaTileEntityMonitorScreen[][] screens = ((MetaTileEntityCentralMonitor) this.getController()).screens;
            int mX = this.getX(), mY = this.getY();
            int max = Math.max(mX, mY);
            for (int i = 0; i <= max && mX - i >= 0; i++) {
                for (int j = 0; j <= max && mY - j >= 0; j++) {
                    MetaTileEntityMonitorScreen screen = screens[mX - i][mY - j];
                    if (screen != null && screen.isActive()) {
                        double xR = (pos[0] + i) / screen.scale;
                        double yR = (pos[1] + j) / screen.scale;
                        if (xR >= 0 && xR <= 1 && yR >= 0 && yR <= 1)
                            if (screen.onClickLogic(playerIn, hand, facing, isRight, xR, yR)) {
                                flag = true;
                            }
                    }
                }
            }
        }
        return flag;
    }

    @SideOnly(Side.CLIENT)
    public double[] checkLookingAt(RayTraceResult rayTraceResult) {
        if (this.getWorld() != null) {
            MultiblockControllerBase controller = this.getController();
            if (rayTraceResult != null && rayTraceResult.typeOfHit == RayTraceResult.Type.BLOCK && controller != null &&
                    rayTraceResult.sideHit == controller.getFrontFacing()) {
                int i, j;
                TileEntity tileEntity = this.getWorld().getTileEntity(rayTraceResult.getBlockPos());
                if (tileEntity instanceof IGregTechTileEntity &&
                        ((IGregTechTileEntity) tileEntity).getMetaTileEntity() instanceof MetaTileEntityMonitorScreen) {
                    MetaTileEntityMonitorScreen screenHit = (MetaTileEntityMonitorScreen) ((IGregTechTileEntity) tileEntity)
                            .getMetaTileEntity();
                    if (controller == screenHit.getController()) {
                        i = ((MetaTileEntityMonitorScreen) ((IGregTechTileEntity) tileEntity).getMetaTileEntity())
                                .getX() - this.getX();
                        j = ((MetaTileEntityMonitorScreen) ((IGregTechTileEntity) tileEntity).getMetaTileEntity())
                                .getY() - this.getY();
                        double[] pos = handleRayTraceResult(rayTraceResult);
                        if ((i >= 0 && j >= 0)) {
                            pos[0] = (pos[0] + i) / this.scale;
                            pos[1] = (pos[1] + j) / this.scale;
                            if (pos[0] >= 0 && pos[0] <= 1 && pos[1] >= 0 && pos[1] <= 1)
                                return new double[] { pos[0], pos[1] };
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public boolean onRightClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                CuboidRayTraceResult hitResult) {
        if (!(!playerIn.isSneaking() && ToolHelper.isTool(playerIn.getHeldItem(hand), ToolClasses.SCREWDRIVER)) &&
                !MetaTileEntities.MONITOR_SCREEN.getStackForm().isItemEqual(playerIn.getHeldItem(hand))) {
            if (playerIn.world.getTotalWorldTime() - lastClickTime < 2 &&
                    playerIn.getPersistentID().equals(lastClickUUID)) {
                return true;
            }
            lastClickTime = playerIn.world.getTotalWorldTime();
            lastClickUUID = playerIn.getPersistentID();

            MultiblockControllerBase controller = this.getController();
            if (controller instanceof MetaTileEntityCentralMonitor &&
                    controller.isActive() && controller.getFrontFacing() == facing) {
                return handleHitResultWithScale(playerIn, hand, facing, true, hitResult);
            }
        }
        return false;
    }

    @Override
    public boolean onScrewdriverClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                      CuboidRayTraceResult hitResult) {
        if (!playerIn.isSneaking() && this.getWorld() != null && !this.getWorld().isRemote) {
            if (canOpenUI()) MetaTileEntityGuiFactory.open(playerIn, this);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onLeftClick(EntityPlayer playerIn, EnumFacing facing, CuboidRayTraceResult hitResult) {
        if (playerIn.world.getTotalWorldTime() - lastClickTime < 2 &&
                playerIn.getPersistentID().equals(lastClickUUID)) {
            return;
        }
        lastClickTime = playerIn.world.getTotalWorldTime();
        lastClickUUID = playerIn.getPersistentID();
        MultiblockControllerBase controller = this.getController();
        if (controller != null && controller.getFrontFacing() == facing) {
            handleHitResultWithScale(playerIn, null, facing, false, hitResult);
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.multiblock.monitor_screen.tooltip.1"));
        tooltip.add(I18n.format("gregtech.multiblock.monitor_screen.tooltip.2"));
        tooltip.add(I18n.format("gregtech.multiblock.monitor_screen.tooltip.3"));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Pair<TextureAtlasSprite, Integer> getParticleTexture() {
        return Pair.of(null, -1);
    }

    @Override
    public boolean showToolUsages() {
        return false;
    }

    @Override
    public boolean hasFrontFacing() {
        return false;
    }
}
