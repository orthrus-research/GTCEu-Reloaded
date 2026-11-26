package com.example.gtaddon;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.StandardMetaItem;

import net.minecraft.item.ItemStack;

/** A meta item subclass with one sub-item. */
public final class FixtureItems extends StandardMetaItem {

    public static FixtureItems items;
    public static MetaItem<?>.MetaValueItem widget;

    private FixtureItems() {
        super((short) 0);
        setRegistryName(FixtureAddon.MODID, "meta_item");
    }

    static void register() {
        items = new FixtureItems();
    }

    @Override
    public void registerSubItems() {
        widget = addItem(0, "widget").setMaxStackSize(16);
    }

    static ItemStack widgets(int amount) {
        return widget.getStackForm(amount);
    }
}
