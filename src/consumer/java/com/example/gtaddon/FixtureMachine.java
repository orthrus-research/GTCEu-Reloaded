package com.example.gtaddon;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuis;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import java.util.List;

/** A machine: a {@link MetaTileEntity} subclass with a ModularUI 2 screen. */
public class FixtureMachine extends MetaTileEntity {

    private int ticks;

    public FixtureMachine(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new FixtureMachine(metaTileEntityId);
    }

    @Override
    public void update() {
        super.update();
        World world = getWorld();
        if (world != null && !world.isRemote && getOffsetTimer() % 20 == 0) {
            ticks++;
            markDirty();
        }
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        tooltip.add("Ticks: " + ticks);
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager, UISettings settings) {
        return GTGuis.createPanel(this, 176, 166);
    }
}
