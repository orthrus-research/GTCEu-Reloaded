package gregtech.mixins.endercore;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.toolitem.ItemGTAxe;
import gregtech.api.items.toolitem.ItemGTHoe;
import gregtech.api.items.toolitem.ItemGTSword;
import gregtech.api.items.toolitem.ItemGTTool;

import com.enderio.core.common.interfaces.IOverlayRenderAware;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Gives GT's tool classes and meta items, and so their subclasses, EnderCore's item overlay interface while
 * EnderCore is loaded. {@link gregtech.api.items.toolitem.IGTTool} and {@link MetaItem} implement its method,
 * and no published class names it.
 */
@Mixin(value = { ItemGTTool.class, ItemGTAxe.class, ItemGTSword.class, ItemGTHoe.class, MetaItem.class }, remap = false)
public abstract class EnderCoreOverlayMixin implements IOverlayRenderAware {}
