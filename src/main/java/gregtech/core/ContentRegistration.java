package gregtech.core;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.block.coil.CoilManager;
import gregtech.api.capability.SimpleCapabilityManager;
import gregtech.api.fluids.GTFluidRegistration;
import gregtech.api.metatileentity.registry.MTEManager;
import gregtech.api.metatileentity.registry.MTERegistry;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.event.MaterialEvent;
import gregtech.api.unification.material.event.MaterialRegistryEvent;
import gregtech.api.unification.material.event.PostMaterialEvent;
import gregtech.api.unification.material.registry.MarkerMaterialRegistry;
import gregtech.common.blocks.BlockBatteryPart;
import gregtech.common.blocks.BlockCleanroomCasing;
import gregtech.common.blocks.BlockWireCoil;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.items.MetaItems;
import gregtech.common.items.ToolItems;
import gregtech.common.metatileentities.MetaTileEntities;
import gregtech.core.unification.material.internal.MaterialRegistryManager;

import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus;

/**
 * The content-registration part of the core module's pre-initialization: capabilities, materials, the MTE and coil
 * registries, blocks, items, tools, fluids, machines and the block stats multiblocks read.
 * <p>
 * The game runs it from {@link CoreModule#preInit}; the unit-test bootstrap runs the same routine, so tests see the
 * game's order and content. It needs no world and no client.
 */
@ApiStatus.Internal
public final class ContentRegistration {

    private ContentRegistration() {}

    public static void register() {
        Logger logger = CoreModule.logger;
        SimpleCapabilityManager.init();

        registerMaterials(logger);

        // need to do this before MetaBlocks runs, to make sure all addons get their own BlockMachine
        /* Start MTE Registry Addition */
        GregTechAPI.mteManager = MTEManager.getInstance();
        GregTechAPI.coilManager = CoilManager.getInstance();
        MinecraftForge.EVENT_BUS.post(new MTEManager.MTERegistryEvent());
        /* End MTE Registry Addition */

        OreDictUnifier.init();

        MetaBlocks.init();
        logger.info("Registering Coils");
        MinecraftForge.EVENT_BUS.post(new CoilManager.CoilRegistryEvent());

        MetaItems.init();
        ToolItems.init();
        GTFluidRegistration.INSTANCE.register();

        /* Start CEu MetaTileEntity Registration */
        for (MTERegistry registry : GregTechAPI.mteManager.getRegistries()) {
            registry.unfreeze();
        }
        logger.info("Registering GTCEu Meta Tile Entities");
        MetaTileEntities.init();
        /* End CEu MetaTileEntity Registration */
        /* Addons not done via an Event due to how much must be initialized for MTEs to register */

        registerBlockStats();
    }

    private static void registerMaterials(Logger logger) {
        GregTechAPI.markerMaterialRegistry = MarkerMaterialRegistry.getInstance();

        // First, register other mods' Registries
        MaterialRegistryManager managerInternal = (MaterialRegistryManager) GregTechAPI.materialManager;

        logger.info("Registering material registries");
        MinecraftForge.EVENT_BUS.post(new MaterialRegistryEvent());

        // First, register CEu Materials
        managerInternal.unfreezeRegistries();
        MaterialEvent materialEvent = new MaterialEvent();
        logger.info("Registering GTCEu Materials");
        Materials.register();
        MaterialRegistryManager.getInstance()
                .getRegistry(GTValues.MODID)
                .setFallbackMaterial(Materials.Aluminium);

        // Then, register addon Materials
        logger.info("Registering addon Materials");
        MinecraftForge.EVENT_BUS.post(materialEvent);

        // Fire Post-Material event, intended for when Materials need to be iterated over in-full before freezing
        // Block entirely new Materials from being added in the Post event
        managerInternal.closeRegistries();
        MinecraftForge.EVENT_BUS.post(new PostMaterialEvent());

        // Freeze Material Registry before processing Items, Blocks, and Fluids
        managerInternal.freezeRegistries();
    }

    /** The coil, battery and cleanroom filter states multiblocks look up. */
    private static void registerBlockStats() {
        for (BlockWireCoil.CoilType type : BlockWireCoil.getCoilTypes()) {
            GregTechAPI.HEATING_COILS.put(MetaBlocks.WIRE_COIL.getState(type), type);
        }
        for (BlockBatteryPart.BatteryPartType type : BlockBatteryPart.BatteryPartType.values()) {
            GregTechAPI.PSS_BATTERIES.put(MetaBlocks.BATTERY_BLOCK.getState(type), type);
        }
        for (BlockCleanroomCasing.CasingType type : BlockCleanroomCasing.CasingType.values()) {
            GregTechAPI.CLEANROOM_FILTERS.put(MetaBlocks.CLEANROOM_CASING.getState(type), type);
        }
    }
}
