package gregtech.mixins.projectred;

import gregtech.api.items.toolitem.ItemGTAxe;
import gregtech.api.items.toolitem.ItemGTHoe;
import gregtech.api.items.toolitem.ItemGTSword;
import gregtech.api.items.toolitem.ItemGTTool;

import mrtjp.projectred.api.IScrewdriver;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Gives GT's tool classes, and so their subclasses, Project Red's screwdriver interface while that mod is loaded.
 * {@link gregtech.api.items.toolitem.IGTTool} implements its methods, and no published class names it.
 */
@Mixin(value = { ItemGTTool.class, ItemGTAxe.class, ItemGTSword.class, ItemGTHoe.class }, remap = false)
public abstract class ProjectRedScrewdriverMixin implements IScrewdriver {}
