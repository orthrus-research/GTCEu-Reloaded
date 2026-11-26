package gregtech.mixins.railcraft;

import gregtech.api.items.toolitem.ItemGTAxe;
import gregtech.api.items.toolitem.ItemGTHoe;
import gregtech.api.items.toolitem.ItemGTSword;
import gregtech.api.items.toolitem.ItemGTTool;

import mods.railcraft.api.items.IToolCrowbar;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Gives GT's tool classes, and so their subclasses, Railcraft's crowbar interface while that mod is loaded.
 * {@link gregtech.api.items.toolitem.IGTTool} implements its methods, and no published class names it.
 */
@Mixin(value = { ItemGTTool.class, ItemGTAxe.class, ItemGTSword.class, ItemGTHoe.class }, remap = false)
public abstract class RailcraftCrowbarMixin implements IToolCrowbar {}
