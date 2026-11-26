package gregtech.api.items.behavior;

import gregtech.api.GregTechAPI;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaItemGuiFactory;
import gregtech.api.util.IDirtyNotifiable;
import gregtech.common.metatileentities.multi.electric.centralmonitor.MetaTileEntityMonitorScreen;
import gregtech.core.network.packets.PacketPluginSynced;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.value.IDoubleValue;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public abstract class MonitorPluginBaseBehavior implements IItemBehaviour, ItemUIFactory, IDirtyNotifiable {

    protected MetaTileEntityMonitorScreen screen;
    private NBTTagCompound nbtTagCompound;

    public static MonitorPluginBaseBehavior getBehavior(ItemStack itemStack) {
        if (itemStack.getItem() instanceof MetaItem<?>) {
            MetaItem<?> item = (MetaItem<?>) itemStack.getItem();
            for (IItemBehaviour behaviour : item.getBehaviours(itemStack)) {
                if (behaviour instanceof MonitorPluginBaseBehavior) {
                    return (MonitorPluginBaseBehavior) behaviour;
                }
            }
        }
        return null;
    }

    public MetaTileEntityMonitorScreen getScreen() {
        return screen;
    }

    abstract public MonitorPluginBaseBehavior createPlugin();

    /**
     * The size of this plugin's settings area, {@link #buildConfigUI}'s parent; zero when the plugin has no settings.
     */
    public int getConfigWidth() {
        return 0;
    }

    public int getConfigHeight() {
        return 0;
    }

    /**
     * Adds this plugin's settings to {@code parent}, an area {@link #getConfigWidth()} by {@link #getConfigHeight()}
     * that is the monitor screen's plugin settings or the plugin item's own screen. The sync values registered here
     * reach this plugin's setters on the server with whatever a client sent, so the setters check every value.
     */
    public void buildConfigUI(ParentWidget<?> parent, PanelSyncManager syncManager) {}

    /***
     * Whether right-clicking with the plugin item opens its settings screen.
     */
    public boolean hasUI() {
        return false;
    }

    /***
     * Server / Client. Itemstack will be synced to client when init so... yeah normally you don't need to consider nbt
     * init.
     * this will be called when you markDirty.
     * 
     * @param data nbtTag
     */
    public void writeToNBT(NBTTagCompound data) {}

    /***
     * Server / Client. Initialization of Server and Client.
     * 
     * @param data nbtTag
     */
    public void readFromNBT(NBTTagCompound data) {
        this.nbtTagCompound = data;
    }

    /***
     * Server. Same as writeCustomData in MetaTileEntity.
     * 
     * @param id  PacketID
     * @param buf PacketBuffer
     */
    public final void writePluginData(int id, @NotNull Consumer<PacketBuffer> buf) {
        if (screen != null && this.screen.getWorld() != null && !this.screen.getWorld().isRemote) {
            screen.writeCustomData(GregtechDataCodes.UPDATE_PLUGIN_DATA, packetBuffer -> {
                packetBuffer.writeVarInt(id);
                buf.accept(packetBuffer);
            });
        }
    }

    /***
     * Client. Same as receiveCustomData in MetaTileEntity.
     * 
     * @param id  PacketID
     * @param buf PacketBuffer
     */
    public void readPluginData(int id, PacketBuffer buf) {}

    /***
     * Client. Send data to Server.
     * 
     * @param id         PacketID
     * @param dataWriter PacketBuffer
     */
    public final void writePluginAction(int id, @NotNull Consumer<PacketBuffer> dataWriter) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        dataWriter.accept(buffer);
        GregTechAPI.networkHandler.sendToServer(new PacketPluginSynced(
                this.getScreen().getWorld().provider.getDimension(),
                this.getScreen().getPos(),
                id, buffer));
    }

    /***
     * Server. receive data from client
     * 
     * @param player player
     * @param id     PacketID
     * @param buf    PacketBuffer
     */
    public void readPluginAction(EntityPlayerMP player, int id, PacketBuffer buf) {}

    /***
     * Server. Same as writeInitialSyncData in MetaTileEntity.
     * 
     * @param buf PacketBuffer
     */
    public void writeInitialSyncData(PacketBuffer buf) {}

    /***
     * Client. Same as receiveInitialSyncData in MetaTileEntity.
     * 
     * @param buf PacketBuffer
     */
    public void receiveInitialSyncData(PacketBuffer buf) {}

    /***
     * Server / Client (deprecated). Should be called when need to write persistence data to NBT
     */
    public void markAsDirty() {
        if (screen != null) {
            screen.pluginDirty();
        } else if (nbtTagCompound != null) {
            writeToNBT(nbtTagCompound);
        }
    }

    /***
     * Server / Client. Called when player touch the screen.
     * 
     * @param playerIn Player
     * @param hand     Hand
     * @param facing   Facing
     * @param isRight  is Right Click
     * @param x        xPos of the screen (0 ~ 1.0)
     * @param y        yPos of the screen (0 ~ 1.0)
     * @return trigger result
     */
    public boolean onClickLogic(EntityPlayer playerIn, EnumHand hand, EnumFacing facing, boolean isRight, double x,
                                double y) {
        return false;
    }

    /***
     * Server / Client. Called per tick when structure formed.
     */
    public void update() {}

    /***
     * Client. Write rendering here
     */
    @SideOnly(Side.CLIENT)
    public void renderPlugin(float partialTicks, RayTraceResult rayTraceResult) {}

    /***
     * Server / Client. Called when plugin is added or removed from the screen.
     * 
     * @param screen
     * @param valid
     */
    public void onMonitorValid(MetaTileEntityMonitorScreen screen, boolean valid) {
        if (valid) {
            this.screen = screen;
        } else {
            this.screen = null;
        }
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!world.isRemote) {
            if (hand != EnumHand.MAIN_HAND)
                return ActionResult.newResult(EnumActionResult.PASS, player.getHeldItem(hand));
            ItemStack itemStack = player.getHeldItem(hand);
            MonitorPluginBaseBehavior behavior = getBehavior(itemStack);
            if (behavior != null && behavior.hasUI()) {
                MetaItemGuiFactory.open(player, hand);
                return ActionResult.newResult(EnumActionResult.SUCCESS, itemStack);
            }
        }
        return ActionResult.newResult(EnumActionResult.PASS, player.getHeldItem(hand));
    }

    /** The plugin item's own screen edits the settings saved in the item. */
    @Override
    public ModularPanel buildUI(HandGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        ItemStack itemStack = guiData.getUsedItemStack();
        MonitorPluginBaseBehavior plugin = createPlugin();
        plugin.readFromNBT(itemStack.getOrCreateSubCompound("monitor_plugin"));
        ModularPanel panel = GTGuis.createPanel(itemStack, plugin.getConfigWidth(), plugin.getConfigHeight());
        plugin.buildConfigUI(panel, syncManager);
        return panel;
    }

    /**
     * A titled slider for a plugin setting. It sends the value it is dragged to; the setter behind {@code value}
     * still checks it.
     */
    protected static Flow slider(String title, IDoubleValue<?> value, double min, double max) {
        return Flow.column()
                .size(210, 20)
                .child(IKey.dynamic(() -> title + ": " + String.format(Locale.ROOT, "%.2f", value.getDoubleValue()))
                        .asWidget()
                        .alignment(Alignment.Center)
                        .widthRel(1f)
                        .height(10))
                .child(new SliderWidget()
                        .value(value)
                        .bounds(min, max)
                        .widthRel(1f)
                        .height(10));
    }

    /**
     * Checks a setting a client sent: not a number or infinite gives {@code NaN}, otherwise the value is kept between
     * {@code min} and {@code max} and rounded to a multiple of {@code step} from {@code min}.
     */
    public static float snap(double value, double min, double max, double step) {
        if (!Double.isFinite(value)) return Float.NaN;
        double clamped = Math.max(min, Math.min(max, value));
        if (step > 0) {
            clamped = min + Math.round((clamped - min) / step) * step;
            clamped = Math.max(min, Math.min(max, clamped));
        }
        return (float) clamped;
    }

    @Override
    public void addInformation(ItemStack itemStack, List<String> lines) {
        lines.add(I18n.format("metaitem.plugin.tooltips.1"));
    }
}
