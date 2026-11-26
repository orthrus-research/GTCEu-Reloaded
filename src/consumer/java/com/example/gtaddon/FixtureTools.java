package com.example.gtaddon;

import gregtech.api.items.toolitem.IGTTool;
import gregtech.api.items.toolitem.ItemGTTool;
import gregtech.api.items.toolitem.ToolClasses;
import gregtech.api.items.toolitem.ToolHelper;
import gregtech.api.unification.material.Materials;

import net.minecraft.item.ItemStack;

import java.util.Set;

/** A GT tool made with the tool builder, used through both its class and {@link IGTTool}. */
public final class FixtureTools {

    public static IGTTool probe;

    private FixtureTools() {}

    static void register() {
        ItemGTTool tool = ItemGTTool.Builder.of(FixtureAddon.MODID, "fixture_probe")
                .toolStats(b -> b.crafting().damagePerCraftingAction(2))
                .toolClasses(ToolClasses.SCREWDRIVER)
                .oreDict("toolFixtureProbe")
                .build();
        tool.setMaxStackSize(1);
        probe = tool;
    }

    static boolean isScrewdriver() {
        ItemStack stack = probe.get(Materials.Iron);
        Set<String> classes = probe.get().getToolClasses(stack);
        ToolHelper.damageItem(stack, null);
        return classes.contains(ToolClasses.SCREWDRIVER) && !probe.isElectric();
    }
}
