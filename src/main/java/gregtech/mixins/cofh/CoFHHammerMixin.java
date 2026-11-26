package gregtech.mixins.cofh;

import gregtech.api.items.toolitem.ItemGTAxe;
import gregtech.api.items.toolitem.ItemGTHoe;
import gregtech.api.items.toolitem.ItemGTSword;
import gregtech.api.items.toolitem.ItemGTTool;

import cofh.api.item.IToolHammer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Gives GT's tool classes, and so their subclasses, CoFH Core's hammer interface while that mod is loaded.
 * {@link gregtech.api.items.toolitem.IGTTool} implements its methods, and no published class names it.
 */
@Mixin(value = { ItemGTTool.class, ItemGTAxe.class, ItemGTSword.class, ItemGTHoe.class }, remap = false)
public abstract class CoFHHammerMixin implements IToolHammer {}
