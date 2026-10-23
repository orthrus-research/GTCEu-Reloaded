package gregtech.common.items.behaviors;

import gregtech.api.configuration.ConfigurationAdapter;
import gregtech.api.configuration.ConfigurationClipboard;
import gregtech.api.configuration.ConfigurationContext;
import gregtech.api.configuration.ConfigurationTransaction;
import gregtech.api.cover.CoverableView;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.util.GTLog;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;

import java.util.List;

public final class ConfigurationCardBehavior implements IItemBehaviour {

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        if (hand != EnumHand.MAIN_HAND) return EnumActionResult.PASS;
        if (world.isRemote) return EnumActionResult.SUCCESS;
        ItemStack stack = player.getHeldItem(hand);
        ConfigurationContext context = new ConfigurationContext(world,
                (target, face) -> !player.isSpectator() && world.isBlockModifiable(player, target) &&
                        player.canPlayerEdit(target, face, stack));
        if (!context.canEdit(pos, side)) return message(player, "denied", false);
        TileEntity tile = context.loadedTile(pos);
        ConfigurationAdapter adapter;
        CoverableView holder;
        String kind;
        if (tile instanceof IGregTechTileEntity machineHolder) {
            MetaTileEntity machine = machineHolder.getMetaTileEntity();
            if (machine == null) return message(player, "unsupported", false);
            adapter = machine.getConfigurationAdapter();
            holder = machine;
            kind = "machine";
        } else if (tile instanceof IPipeTile<?, ?>pipe) {
            adapter = pipe.getConfigurationAdapter();
            holder = pipe.getCoverableImplementation();
            kind = "pipe";
        } else {
            return message(player, "unsupported", false);
        }
        if (adapter == null) return message(player, "unsupported", false);
        try {
            ConfigurationTransaction transaction = new ConfigurationTransaction();
            context.guardTile(pos, tile, transaction);
            if (tile instanceof IGregTechTileEntity machineHolder) {
                transaction.guard(() -> machineHolder.getMetaTileEntity() == holder);
            }
            if (player.isSneaking()) {
                NBTTagCompound clipboard = ConfigurationClipboard.capture(kind, adapter, holder);
                ConfigurationClipboard.prepare(clipboard, kind, adapter, holder, context, transaction);
                // Copy replaces the complete card payload only after validating the new source.
                NBTTagCompound itemData = stack.hasTagCompound() ? stack.getTagCompound().copy() : new NBTTagCompound();
                itemData.setTag(ConfigurationClipboard.ITEM_KEY, clipboard);
                stack.setTagCompound(itemData);
                return message(player, "copied", true);
            }
            NBTTagCompound itemData = stack.getTagCompound();
            if (itemData == null || !itemData.hasKey(ConfigurationClipboard.ITEM_KEY, NBT.TAG_COMPOUND)) {
                return message(player, "empty", false);
            }
            ConfigurationClipboard.prepare(itemData.getCompoundTag(ConfigurationClipboard.ITEM_KEY), kind, adapter,
                    holder, context, transaction);
            boolean applied = transaction.execute();
            return message(player, applied ? "pasted" : "failed", applied);
        } catch (IllegalArgumentException invalid) {
            return message(player, "incompatible", false);
        } catch (RuntimeException failure) {
            GTLog.logger.error("Unable to prepare configuration card operation", failure);
            return message(player, "failed", false);
        }
    }

    private static EnumActionResult message(EntityPlayer player, String key, boolean success) {
        player.sendStatusMessage(new TextComponentTranslation("gregtech.configuration_card." + key), true);
        return success ? EnumActionResult.SUCCESS : EnumActionResult.FAIL;
    }

    @Override
    public void addInformation(ItemStack stack, List<String> lines) {
        lines.add(I18n.format("gregtech.configuration_card.use"));
        lines.add(I18n.format("gregtech.configuration_card.settings"));
        lines.add(I18n.format("gregtech.configuration_card.covers"));
        lines.add(I18n.format("gregtech.configuration_card.filters"));
        if (stack.hasTagCompound() &&
                stack.getTagCompound().hasKey(ConfigurationClipboard.ITEM_KEY, NBT.TAG_COMPOUND)) {
            lines.add(I18n.format("gregtech.configuration_card.stored"));
        }
    }
}
