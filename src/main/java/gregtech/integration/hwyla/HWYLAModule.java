package gregtech.integration.hwyla;

import gregtech.api.GTValues;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.modules.GregTechModule;
import gregtech.api.util.Mods;
import gregtech.common.blocks.BlockLamp;
import gregtech.common.blocks.BlockOre;
import gregtech.integration.IntegrationSubmodule;
import gregtech.integration.probe.ProbeInfoProducer;
import gregtech.integration.probe.producer.AEHatchProbeInfo;
import gregtech.integration.probe.producer.ActiveTransformerProbeInfo;
import gregtech.integration.probe.producer.BatteryBufferProbeInfo;
import gregtech.integration.probe.producer.BlockOreProbeInfo;
import gregtech.integration.probe.producer.ControllableProbeInfo;
import gregtech.integration.probe.producer.ConverterProbeInfo;
import gregtech.integration.probe.producer.DiodeProbeInfo;
import gregtech.integration.probe.producer.EnergyContainerProbeInfo;
import gregtech.integration.probe.producer.LampProbeInfo;
import gregtech.integration.probe.producer.LaserContainerProbeInfo;
import gregtech.integration.probe.producer.MaintenanceProbeInfo;
import gregtech.integration.probe.producer.MultiRecipeMapProbeInfo;
import gregtech.integration.probe.producer.MultiblockProbeInfo;
import gregtech.integration.probe.producer.PrimitivePumpProbeInfo;
import gregtech.integration.probe.producer.QuantumStorageProbeInfo;
import gregtech.integration.probe.producer.RecipeLogicProbeInfo;
import gregtech.integration.probe.producer.SteamBoilerProbeInfo;
import gregtech.integration.probe.producer.TransformerProbeInfo;
import gregtech.integration.probe.producer.WorkableProbeInfo;
import gregtech.modules.GregTechModules;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.IWailaRegistrar;
import mcp.mobius.waila.api.SpecialChars;
import mcp.mobius.waila.api.WailaPlugin;

@WailaPlugin
@GregTechModule(
                moduleID = GregTechModules.MODULE_HWYLA,
                containerID = GTValues.MODID,
                modDependencies = Mods.Names.HWYLA,
                name = "GregTech HWYLA Integration",
                description = "HWYLA (WAILA) Integration Module")
public class HWYLAModule extends IntegrationSubmodule implements IWailaPlugin {

    @Override
    public void register(IWailaRegistrar registrar) {
        fromServer(registrar, "gregtech.energy", TileEntity.class, new EnergyContainerProbeInfo());
        fromServer(registrar, "gregtech.laser", TileEntity.class, new LaserContainerProbeInfo());
        fromServer(registrar, "gregtech.workable", TileEntity.class, new WorkableProbeInfo());
        fromServer(registrar, "gregtech.controllable", TileEntity.class, new ControllableProbeInfo());
        fromServer(registrar, "gregtech.transformer", TileEntity.class, new TransformerProbeInfo());
        fromServer(registrar, "gregtech.diode", TileEntity.class, new DiodeProbeInfo());
        fromServer(registrar, "gregtech.multiblock", TileEntity.class, new MultiblockProbeInfo());
        fromServer(registrar, "gregtech.maintenance", TileEntity.class, new MaintenanceProbeInfo());
        // the client's copy holds the modes and the quantum lines, which HWYLA showed without it on the server
        HWYLADataAdapter.fromServerOrClient("gregtech.multi_recipemap", TileEntity.class, new MultiRecipeMapProbeInfo())
                .register(registrar);
        fromServer(registrar, "gregtech.converter", TileEntity.class, new ConverterProbeInfo());
        fromServer(registrar, "gregtech.recipe_logic", TileEntity.class, new RecipeLogicProbeInfo());
        fromServer(registrar, "gregtech.steam_boiler", IGregTechTileEntity.class, new SteamBoilerProbeInfo());
        fromServer(registrar, "gregtech.primitive_pump", IGregTechTileEntity.class, new PrimitivePumpProbeInfo());
        // one day, if cover provider is ported to waila, register it right here
        fromClient(registrar, "gregtech.block_ore", BlockOre.class, new BlockOreProbeInfo());
        fromClient(registrar, "gregtech.block_lamp", BlockLamp.class, new LampProbeInfo());
        fromServer(registrar, "gregtech.multiblock.activetransformer", TileEntity.class,
                new ActiveTransformerProbeInfo());
        fromServer(registrar, "gregtech.battery-buffer", TileEntity.class, new BatteryBufferProbeInfo());
        HWYLADataAdapter.fromServerOrClient("gregtech.quantum_storage", IGregTechTileEntity.class,
                new QuantumStorageProbeInfo()).register(registrar);
        fromServer(registrar, "gregtech.ae_multiblock_hatch_provider", IGregTechTileEntity.class,
                new AEHatchProbeInfo());
    }

    /** Registers a shared producer whose lines the server sends under a HWYLA configuration key. */
    private static void fromServer(IWailaRegistrar registrar, String configKey, Class<?> target,
                                   ProbeInfoProducer producer) {
        HWYLADataAdapter.fromServer(configKey, target, producer).register(registrar);
    }

    /** Registers a shared producer whose lines the client produces from the block state alone. */
    private static void fromClient(IWailaRegistrar registrar, String configKey, Class<?> target,
                                   ProbeInfoProducer producer) {
        HWYLADataAdapter.fromClient(configKey, target, producer).register(registrar);
    }

    /** Render an ItemStack. */
    public static String wailaStack(ItemStack stack) {
        String name = stack.getItem().getRegistryName().toString();
        String count = String.valueOf(stack.getCount());
        String damage = String.valueOf(stack.getItemDamage());
        String nbt = stack.hasTagCompound() ? stack.getTagCompound().toString() : "";
        return SpecialChars.getRenderString("waila.stack", "1", name, count, damage, nbt);
    }

    /** Render a string with an X/Y offset. */
    public static String offsetText(String s, int x, int y) {
        return SpecialChars.getRenderString("gregtech.text", s, Integer.toString(x), Integer.toString(y));
    }

    /** Render an ItemStack with its display name offset to the right. */
    public static String wailaStackWithName(ItemStack stack) {
        return wailaStackWithName(stack, stack.getDisplayName());
    }

    /** Render an ItemStack with a custom String displayed offset to the right. */
    public static String wailaStackWithName(ItemStack stack, String name) {
        return wailaStack(stack) + offsetText(name, 0, 4);
    }
}
