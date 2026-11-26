package com.example.gtaddon;

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.registry.MTEManager;
import gregtech.api.unification.material.event.MaterialEvent;
import gregtech.api.unification.material.event.MaterialRegistryEvent;

import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * A small addon written the way addons use the API. The build compiles it against the published artifact and its
 * published dependencies only; it is never run.
 */
@Mod(modid = FixtureAddon.MODID, name = "GT Consumer Fixture", version = "1.0",
     dependencies = "required-after:gregtech")
@Mod.EventBusSubscriber(modid = FixtureAddon.MODID)
public final class FixtureAddon {

    public static final String MODID = "gtaddon";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        FixtureItems.register();
        FixtureTools.register();
    }

    @SubscribeEvent
    public static void registerMaterialRegistry(MaterialRegistryEvent event) {
        GregTechAPI.materialManager.createRegistry(MODID);
    }

    @SubscribeEvent
    public static void registerMaterials(MaterialEvent event) {
        FixtureMaterials.register();
    }

    @SubscribeEvent
    public static void registerMachineRegistry(MTEManager.MTERegistryEvent event) {
        GregTechAPI.mteManager.createRegistry(MODID);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(FixtureItems.items);
        event.getRegistry().register(FixtureTools.probe.get());
        ResourceLocation machine = new ResourceLocation(MODID, "fixture_machine");
        GregTechAPI.mteManager.getRegistry(MODID).register(1, machine, new FixtureMachine(machine));
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        FixtureRecipes.register();
    }
}
