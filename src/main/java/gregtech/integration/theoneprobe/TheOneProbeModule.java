package gregtech.integration.theoneprobe;

import gregtech.api.GTValues;
import gregtech.api.modules.GregTechModule;
import gregtech.api.util.Mods;
import gregtech.integration.IntegrationSubmodule;
import gregtech.integration.probe.ProbeInfoProducer;
import gregtech.integration.probe.producer.BatteryBufferProbeInfo;
import gregtech.integration.probe.producer.ControllableProbeInfo;
import gregtech.integration.probe.producer.ConverterProbeInfo;
import gregtech.integration.probe.producer.DiodeProbeInfo;
import gregtech.integration.probe.producer.MaintenanceProbeInfo;
import gregtech.integration.probe.producer.MultiRecipeMapProbeInfo;
import gregtech.integration.probe.producer.MultiblockProbeInfo;
import gregtech.integration.probe.producer.RecipeLogicProbeInfo;
import gregtech.integration.probe.producer.TransformerProbeInfo;
import gregtech.integration.probe.producer.WorkableProbeInfo;
import gregtech.integration.theoneprobe.provider.*;
import gregtech.integration.theoneprobe.provider.debug.DebugPipeNetInfoProvider;
import gregtech.integration.theoneprobe.provider.debug.DebugTickTimeProvider;
import gregtech.modules.GregTechModules;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;

import mcjty.theoneprobe.TheOneProbe;
import mcjty.theoneprobe.api.ITheOneProbe;

@GregTechModule(
                moduleID = GregTechModules.MODULE_TOP,
                containerID = GTValues.MODID,
                modDependencies = Mods.Names.THE_ONE_PROBE,
                name = "GregTech TheOneProbe Integration",
                description = "TheOneProbe Integration Module")
public class TheOneProbeModule extends IntegrationSubmodule {

    @Override
    public void init(FMLInitializationEvent event) {
        getLogger().info("TheOneProbe found. Enabling integration...");
        ITheOneProbe oneProbe = TheOneProbe.theOneProbeImp;
        oneProbe.registerProvider(new ElectricContainerInfoProvider());
        register(oneProbe, GTValues.MODID + ":workable_provider", new WorkableProbeInfo());
        register(oneProbe, GTValues.MODID + ":controllable_provider", new ControllableProbeInfo());
        register(oneProbe, GTValues.MODID + ":transformer_info_provider", new TransformerProbeInfo());
        register(oneProbe, "gregtech:diode_info_provider", new DiodeProbeInfo());
        register(oneProbe, GTValues.MODID + ":multiblock_controller_provider", new MultiblockProbeInfo());
        register(oneProbe, GTValues.MODID + ":multiblock_maintenance_provider", new MaintenanceProbeInfo());
        register(oneProbe, GTValues.MODID + ":multi_recipemap_provider", new MultiRecipeMapProbeInfo());
        register(oneProbe, GTValues.MODID + ":converter_info_provider", new ConverterProbeInfo());
        register(oneProbe, GTValues.MODID + ":recipe_logic_provider", new RecipeLogicProbeInfo());
        oneProbe.registerProvider(new SteamBoilerInfoProvider());
        oneProbe.registerProvider(new PrimitivePumpInfoProvider());
        oneProbe.registerProvider(new CoverInfoProvider());
        oneProbe.registerProvider(new BlockOreInfoProvider());
        oneProbe.registerProvider(new LampInfoProvider());
        oneProbe.registerProvider(new LDPipeProvider());
        oneProbe.registerProvider(new LaserContainerInfoProvider());
        oneProbe.registerProvider(new QuantumStorageProvider());
        oneProbe.registerProvider(new ActiveTransformerInfoProvider());
        register(oneProbe, GTValues.MODID + ":battery_buffer_provider", new BatteryBufferProbeInfo());
        oneProbe.registerProvider(new AEMultiblockHatchProvider());

        // Dev environment debug providers
        oneProbe.registerProvider(new DebugPipeNetInfoProvider());
        oneProbe.registerProvider(new DebugTickTimeProvider());
    }

    /** Registers a shared producer's lines under the provider ID The One Probe's configuration knows. */
    private static void register(ITheOneProbe oneProbe, String id, ProbeInfoProducer producer) {
        oneProbe.registerProvider(new TOPInfoAdapter(id, producer));
    }
}
