package gregtech.common.items.behaviors.monitorplugin;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.Widget;
import gregtech.api.gui.impl.FakeModularGui;
import gregtech.api.gui.widgets.SlotWidget;
import gregtech.api.items.behavior.MonitorPluginBaseBehavior;
import gregtech.api.items.behavior.ProxyHolderPluginBehavior;
import gregtech.api.items.toolitem.ToolClasses;
import gregtech.api.items.toolitem.ToolHelper;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.util.GTLog;
import gregtech.api.util.GregFakePlayer;
import gregtech.common.gui.impl.FakeModularUIPluginContainer;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import java.lang.reflect.Method;
import java.util.*;

public class FakeGuiPluginBehavior extends ProxyHolderPluginBehavior {

    private int partIndex;

    // run-time
    @SideOnly(Side.CLIENT)
    private FakeModularGui fakeModularGui;
    private BlockPos partPos;
    private FakeModularUIPluginContainer fakeModularUIContainer;
    private GregFakePlayer fakePlayer;
    /** The player whose click the clients replay, and when; only that player's widget actions are taken. */
    private UUID clickingPlayer;
    private long clickTime;
    private static final int CLICK_ACTION_TICKS = 20;
    private static final Method methodCreateUI = ObfuscationReflectionHelper.findMethod(MetaTileEntity.class,
            "createUI", ModularUI.class, EntityPlayer.class);
    static {
        methodCreateUI.setAccessible(true);
    }

    /** Shows another part of the proxied multiblock; a client sends {@code partIndex}, so a negative one is ignored. */
    public void setConfig(int partIndex) {
        if (this.partIndex == partIndex || partIndex < 0) return;
        this.partIndex = partIndex;
        this.partPos = null;
        writePluginData(GregtechDataCodes.UPDATE_PLUGIN_CONFIG, buffer -> buffer.writeVarInt(this.partIndex));
        markAsDirty();
    }

    public MetaTileEntity getRealMTE() {
        MetaTileEntity target = this.holder.getMetaTileEntity();
        if (target instanceof MultiblockControllerBase multi && partIndex > 0) {
            if (partPos != null) {
                TileEntity entity = this.screen.getWorld().getTileEntity(partPos);
                if (entity instanceof IGregTechTileEntity) {
                    return ((IGregTechTileEntity) entity).getMetaTileEntity();
                } else {
                    partPos = null;
                    return null;
                }
            }
            if (multi.structurePattern == null) return null;
            PatternMatchContext context = multi.structurePattern.checkPatternFastAt(
                    target.getWorld(), target.getPos(), target.getFrontFacing().getOpposite(), multi.getUpwardsFacing(),
                    multi.allowsFlip());
            if (context == null) {
                return null;
            }
            Set<IMultiblockPart> rawPartsSet = context.getOrCreate("MultiblockParts", HashSet::new);
            List<IMultiblockPart> parts = new ArrayList<>(rawPartsSet);
            parts.sort(Comparator.comparing((it) -> ((MetaTileEntity) it).getPos().hashCode()));
            if (parts.size() > partIndex - 1 && parts.get(partIndex - 1) instanceof MetaTileEntity) {
                target = (MetaTileEntity) parts.get(partIndex - 1);
                partPos = target.getPos();
            } else {
                return null;
            }
        }
        return target;
    }

    public void createFakeGui() {
        if (this.holder == null || this.screen == null || !this.screen.isValid()) return;
        try {
            fakePlayer = new GregFakePlayer(this.screen.getWorld());
            MetaTileEntity mte = getRealMTE();
            if (mte == null || (this.partIndex > 0 && this.holder.getMetaTileEntity() == mte)) {
                fakeModularUIContainer = null;
                if (this.screen.getWorld().isRemote) {
                    fakeModularGui = null;
                }
                return;
            }
            ModularUI ui = (ModularUI) methodCreateUI.invoke(mte, fakePlayer);
            if (ui == null) {
                fakeModularUIContainer = null;
                if (this.screen.getWorld().isRemote) {
                    fakeModularGui = null;
                }
                return;
            }
            List<Widget> widgets = new ArrayList<>();
            boolean hasPlayerInventory = false;
            for (Widget widget : ui.guiWidgets.values()) {
                if (widget instanceof SlotWidget) {
                    IInventory handler = ((SlotWidget) widget).getHandle().inventory;
                    if (handler instanceof PlayerMainInvWrapper || handler instanceof InventoryPlayer) {
                        hasPlayerInventory = true;
                        continue;
                    }
                }
                widgets.add(widget);
            }
            ModularUI.Builder builder = new ModularUI.Builder(ui.backgroundPath, ui.getWidth(),
                    ui.getHeight() - (hasPlayerInventory ? 80 : 0));
            for (Widget widget : widgets) {
                builder.widget(widget);
            }
            ui = builder.build(ui.holder, ui.entityPlayer);
            fakeModularUIContainer = new FakeModularUIPluginContainer(ui, this);
            if (this.screen.getWorld().isRemote) {
                fakeModularGui = new FakeModularGui(ui, fakeModularUIContainer);
                writePluginAction(GregtechDataCodes.ACTION_PLUGIN_CONFIG, buffer -> {});
            }
        } catch (Exception e) {
            GTLog.logger.error(e);
        }
    }

    @Override
    public void readPluginAction(EntityPlayerMP player, int id, PacketBuffer buf) {
        if (id == GregtechDataCodes.ACTION_PLUGIN_CONFIG) {
            createFakeGui();
        }
        if (id == GregtechDataCodes.ACTION_FAKE_GUI) {
            // every client watching replays a click; act once, for the player who clicked
            if (this.fakeModularUIContainer != null && player.getUniqueID().equals(clickingPlayer) &&
                    this.screen.getWorld().getTotalWorldTime() - clickTime <= CLICK_ACTION_TICKS) {
                fakeModularUIContainer.handleClientAction(buf);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("part", partIndex);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        partIndex = data.hasKey("part") ? data.getInteger("part") : 0;
    }

    @Override
    public void onHolderChanged(IGregTechTileEntity lastHolder) {
        if (holder == null) {
            if (this.screen.getWorld() != null && this.screen.getWorld().isRemote) {
                fakeModularGui = null;
            }
            fakeModularUIContainer = null;
            fakePlayer = null;
        } else {
            if (this.screen.getWorld().isRemote) {
                createFakeGui();
            }
        }
    }

    @Override
    public void update() {
        super.update();
        if (this.screen.getWorld().isRemote) {
            if (partIndex > 0 && fakeModularUIContainer == null && this.screen.getOffsetTimer() % 20 == 0) {
                createFakeGui();
            }
            if (fakeModularGui != null)
                fakeModularGui.updateScreen();
        } else {
            if (partIndex > 0 && this.screen.getOffsetTimer() % 20 == 0) {
                if (fakeModularUIContainer != null && getRealMTE() == null) {
                    this.writePluginData(GregtechDataCodes.UPDATE_PLUGIN_CONFIG,
                            buf -> buf.writeVarInt(this.partIndex));
                    fakeModularUIContainer = null;
                }
            }
            if (fakeModularUIContainer != null)
                fakeModularUIContainer.detectAndSendChanges();
        }
    }

    @Override
    public MonitorPluginBaseBehavior createPlugin() {
        return new FakeGuiPluginBehavior();
    }

    @Override
    public void renderPlugin(float partialTicks, RayTraceResult rayTraceResult) {
        if (fakeModularGui != null) {
            double[] result = this.screen.checkLookingAt(rayTraceResult);
            GlStateManager.translate(0, 0, 0.01);
            if (result == null)
                fakeModularGui.drawScreen(0, 0, partialTicks);
            else
                fakeModularGui.drawScreen(result[0], result[1], partialTicks);
        }
    }

    @Override
    public boolean onClickLogic(EntityPlayer playerIn, EnumHand hand, EnumFacing facing, boolean isRight, double x,
                                double y) {
        if (this.screen.getWorld().isRemote) return true;
        if (fakeModularUIContainer != null && fakeModularUIContainer.modularUI != null &&
                !ToolHelper.isTool(playerIn.getHeldItemMainhand(), ToolClasses.SCREWDRIVER)) {
            int width = fakeModularUIContainer.modularUI.getWidth();
            int height = fakeModularUIContainer.modularUI.getHeight();
            float halfW = width / 2f;
            float halfH = height / 2f;
            float scale = 0.5f / Math.max(halfW, halfH);
            int mouseX = (int) ((x / scale) + (halfW > halfH ? 0 : (halfW - halfH)));
            int mouseY = (int) ((y / scale) + (halfH > halfW ? 0 : (halfH - halfW)));
            MetaTileEntity mte = getRealMTE();
            if (mte != null && 0 <= mouseX && mouseX <= width && 0 <= mouseY && mouseY <= height) {
                if (playerIn.isSneaking()) {
                    clickingPlayer = playerIn.getUniqueID();
                    clickTime = this.screen.getWorld().getTotalWorldTime();
                    writePluginData(GregtechDataCodes.UPDATE_PLUGIN_CLICK, buf -> {
                        buf.writeVarInt(mouseX);
                        buf.writeVarInt(mouseY);
                        buf.writeVarInt(isRight ? 1 : 0);
                        buf.writeVarInt(fakeModularUIContainer.syncId);
                    });
                } else {
                    return isRight && mte.onRightClick(playerIn, hand, facing, null);
                }
            }
        }
        return false;
    }

    @Override
    public void readPluginData(int id, PacketBuffer buf) {
        if (id == GregtechDataCodes.UPDATE_PLUGIN_CONFIG) {
            this.partIndex = buf.readVarInt();
            this.partPos = null;
            createFakeGui();
        } else if (id == GregtechDataCodes.UPDATE_FAKE_GUI) {
            int windowID = buf.readVarInt();
            int widgetID = buf.readVarInt();
            if (fakeModularGui != null)
                fakeModularGui.handleWidgetUpdate(windowID, widgetID, buf);
        } else if (id == GregtechDataCodes.UPDATE_FAKE_GUI_DETECT) {
            if (fakeModularUIContainer != null)
                fakeModularUIContainer.handleSlotUpdate(buf);
        } else if (id == GregtechDataCodes.UPDATE_PLUGIN_CLICK) {
            int mouseX = buf.readVarInt();
            int mouseY = buf.readVarInt();
            int button = buf.readVarInt();
            int syncID = buf.readVarInt();
            if (fakeModularGui != null && fakeModularUIContainer != null) {
                fakeModularUIContainer.syncId = syncID;
                fakeModularGui.mouseClicked(mouseX, mouseY, button);
            }
        }
    }

    @Override
    public int getConfigWidth() {
        return 170;
    }

    @Override
    public int getConfigHeight() {
        return 50;
    }

    @Override
    public void buildConfigUI(ParentWidget<?> parent, PanelSyncManager syncManager) {
        IntSyncValue part = new IntSyncValue(() -> this.partIndex, this::setConfig);
        syncManager.syncValue("part", part);
        parent.child(IKey.str("Part:").asWidget().pos(20, 20))
                .child(new ButtonWidget<>()
                        .pos(55, 15)
                        .size(20)
                        .overlay(IKey.str("-1"))
                        .onMousePressed(mouseButton -> {
                            part.setIntValue(part.getIntValue() - 1);
                            return true;
                        }))
                .child(IKey.dynamic(() -> Integer.toString(part.getIntValue()))
                        .color(0xFFFFFFFF)
                        .asWidget()
                        .alignment(Alignment.Center)
                        .pos(75, 15)
                        .size(60, 20)
                        .background(GTGuiTextures.DISPLAY))
                .child(new ButtonWidget<>()
                        .pos(135, 15)
                        .size(20)
                        .overlay(IKey.str("+1"))
                        .onMousePressed(mouseButton -> {
                            part.setIntValue(part.getIntValue() + 1);
                            return true;
                        }));
    }
}
