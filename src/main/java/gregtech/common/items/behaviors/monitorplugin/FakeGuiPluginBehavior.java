package gregtech.common.items.behaviors.monitorplugin;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.items.behavior.MonitorPluginBaseBehavior;
import gregtech.api.items.behavior.ProxyHolderPluginBehavior;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.client.utils.RenderUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The GUI proxy plugin, which showed the screen of the proxied machine, or of one of its multiblock parts, on the
 * monitor. Machine screens are ModularUI 2 screens now, which it cannot show, so it shows a notice in their place. It
 * keeps the part it is set to, which the advanced monitor plugin draws its connections to.
 */
public class FakeGuiPluginBehavior extends ProxyHolderPluginBehavior {

    /** The lang key of the notice the plugin shows on the monitor. */
    public static final String NOTICE = "metaitem.plugin.fake_gui.notice";
    /** The notice's widest line and its line height, in the monitor's 128 text pixels across. */
    private static final int NOTICE_WIDTH = 120;
    private static final int NOTICE_LINE_HEIGHT = 10;

    private int partIndex;

    // run-time
    private BlockPos partPos;

    /** Shows another part of the proxied multiblock; a client sends {@code partIndex}, so a negative one is ignored. */
    public void setConfig(int partIndex) {
        if (this.partIndex == partIndex || partIndex < 0) return;
        this.partIndex = partIndex;
        this.partPos = null;
        writePluginData(GregtechDataCodes.UPDATE_PLUGIN_CONFIG, buffer -> buffer.writeVarInt(this.partIndex));
        markAsDirty();
    }

    /** The proxied machine, or the part of it the plugin is set to; {@code null} when that part is not found. */
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
    public void onHolderChanged(IGregTechTileEntity lastHolder) {}

    @Override
    public MonitorPluginBaseBehavior createPlugin() {
        return new FakeGuiPluginBehavior();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderPlugin(float partialTicks, RayTraceResult rayTraceResult) {
        List<String> lines = Minecraft.getMinecraft().fontRenderer.listFormattedStringToWidth(I18n.format(NOTICE),
                NOTICE_WIDTH);
        float lineHeight = NOTICE_LINE_HEIGHT / 128f;
        float top = -(lines.size() - 1) * lineHeight / 2;
        for (int i = 0; i < lines.size(); i++) {
            RenderUtil.renderText(0, top + i * lineHeight, 0.002f, 1 / 128f, 0xFFFFFFFF, lines.get(i), true);
        }
    }

    @Override
    public void readPluginData(int id, PacketBuffer buf) {
        if (id == GregtechDataCodes.UPDATE_PLUGIN_CONFIG) {
            this.partIndex = buf.readVarInt();
            this.partPos = null;
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
