package gregtech.common.items.behaviors;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IElectricItem;
import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaItemGuiFactory;
import gregtech.api.util.GTUtility;
import gregtech.common.mui.widget.prospector.ProspectingMapWidget;
import gregtech.common.mui.widget.prospector.ProspectingOreListWidget;
import gregtech.common.mui.widget.prospector.ProspectingSyncHandler;
import gregtech.common.mui.widget.prospector.ProspectorMode;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.TextWidget;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class ProspectorScannerBehavior implements IItemBehaviour, ItemUIFactory {

    private static final long VOLTAGE_FACTOR = 16L;
    private static final int FLUID_PROSPECTION_THRESHOLD = GTValues.HV;
    private static final int WIDTH = 332;
    private static final int HEIGHT = 200;

    private final int radius;
    private final int tier;

    public ProspectorScannerBehavior(int radius, int tier) {
        this.radius = radius + 1;
        this.tier = tier;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(@NotNull World world, @NotNull EntityPlayer player, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (!world.isRemote) {
            if (player.isSneaking()) {
                ItemStack stack = player.getHeldItem(hand);
                ProspectorMode mode = getMode(stack);
                ProspectorMode nextMode = mode.next();
                if (nextMode == ProspectorMode.FLUID) {
                    if (tier >= FLUID_PROSPECTION_THRESHOLD) {
                        setMode(stack, nextMode);
                        player.sendStatusMessage(new TextComponentTranslation("metaitem.prospector.mode.fluid"), true);
                    }
                } else {
                    setMode(stack, nextMode);
                    player.sendStatusMessage(new TextComponentTranslation("metaitem.prospector.mode.ores"), true);
                }
            } else if (checkCanUseScanner(heldItem, player, true)) {
                MetaItemGuiFactory.open(player, hand);
            } else {
                player.sendMessage(new TextComponentTranslation("behavior.prospector.not_enough_energy"));
            }
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, heldItem);
    }

    @NotNull
    private static ProspectorMode getMode(ItemStack stack) {
        if (stack.isEmpty()) {
            return ProspectorMode.ORE;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return ProspectorMode.ORE;
        }
        if (tag.hasKey("Mode", Constants.NBT.TAG_INT)) {
            return GTUtility.enumByOrdinal(ProspectorMode.VALUES, tag.getInteger("Mode"), ProspectorMode.ORE);
        }
        return ProspectorMode.ORE;
    }

    private static void setMode(ItemStack stack, @NotNull ProspectorMode mode) {
        NBTTagCompound tagCompound = GTUtility.getOrCreateNbtCompound(stack);
        tagCompound.setInteger("Mode", mode.ordinal());
    }

    private boolean checkCanUseScanner(ItemStack stack, @NotNull EntityPlayer player, boolean simulate) {
        return player.isCreative() || drainEnergy(stack, GTValues.V[tier] / VOLTAGE_FACTOR, simulate);
    }

    private static boolean drainEnergy(@NotNull ItemStack stack, long amount, boolean simulate) {
        IElectricItem electricItem = stack.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (electricItem == null) return false;

        return electricItem.discharge(amount, Integer.MAX_VALUE, true, false, simulate) >= amount;
    }

    @Override
    public ModularPanel buildUI(HandGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        ProspectorMode mode = getMode(guiData.getUsedItemStack());
        int mapSize = 16 * (radius * 2 - 1);
        ProspectingSyncHandler scanner = new ProspectingSyncHandler(radius, mode);
        syncManager.syncValue("prospecting", scanner);

        ProspectingOreListWidget oreList = new ProspectingOreListWidget()
                .pos(32 * radius - 6, 18)
                .size(WIDTH - 32 * radius, 176);
        ProspectingMapWidget map = new ProspectingMapWidget(radius, mode, oreList).pos(6, 18);
        scanner.setReceiver(map::addPacket);

        return GTGuis.createPanel(guiData.getUsedItemStack(), WIDTH, HEIGHT)
                .child(IKey.lang(getTranslationKey()).asWidget().pos(6, 6))
                .child(map)
                .child(oreList)
                // cardinal directions
                .child(direction("N").pos(3 + mapSize / 2, 14))
                .child(direction("S").pos(3 + mapSize / 2, 14 + mapSize))
                .child(direction("W").pos(3, 15 + mapSize / 2))
                .child(direction("E").pos(3 + mapSize, 15 + mapSize / 2));
    }

    private static TextWidget<?> direction(String name) {
        return IKey.str(name).color(0xFFAAAAAA).shadow(true).asWidget();
    }

    private String getTranslationKey() {
        return String.format("metaitem.prospector.%s.name", GTValues.VN[tier].toLowerCase(Locale.ROOT));
    }

    @Override
    public void addInformation(ItemStack itemStack, List<String> lines) {
        IItemBehaviour.super.addInformation(itemStack, lines);

        if (tier >= FLUID_PROSPECTION_THRESHOLD) {
            lines.add(I18n.format("metaitem.prospector.tooltip.fluids", radius));
            lines.add(I18n.format(getMode(itemStack).unlocalizedName));
        } else {
            lines.add(I18n.format("metaitem.prospector.tooltip.ores", radius));
        }
    }

    @Override
    public void onUpdate(ItemStack itemStack, Entity entity) {
        // the open screen scans every tick, and stops when the scanner runs out of energy
        if (entity instanceof EntityPlayer player && !player.world.isRemote && !player.isCreative() &&
                player.openContainer instanceof ModularContainer container &&
                container.getGuiData() instanceof HandGuiData data && data.getUsedItemStack() == itemStack) {
            if (checkCanUseScanner(itemStack, player, true)) {
                drainEnergy(itemStack, GTValues.V[tier] / VOLTAGE_FACTOR, false);
            } else {
                player.closeScreen();
            }
        }
    }
}
