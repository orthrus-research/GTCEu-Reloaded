package gregtech.integration.jei.multiblock;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.block.IHeatingCoilBlockStats;
import gregtech.api.block.machines.BlockMachine;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.registry.MTERegistry;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.*;
import gregtech.common.metatileentities.MetaTileEntities;
import gregtech.common.metatileentities.multi.electric.*;
import gregtech.common.metatileentities.multi.multiblockpart.*;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registers only the production components needed by the four real core pattern definitions. */
final class PreviewTestRegistry implements AutoCloseable {

    private final Map<Field, Object> savedFields = new LinkedHashMap<>();
    private final Map<MetaTileEntity[], MetaTileEntity[]> savedArrays = new LinkedHashMap<>();
    private final Map<MultiblockAbility<?>, List<MetaTileEntity>> abilities = new HashMap<>();
    private final Map<IBlockState, IHeatingCoilBlockStats> coils = new HashMap<>();
    private final Map<Block, Item> itemMappings;
    private final Map<Block, Item> previousItems = new HashMap<>();
    private final boolean research;
    private final boolean maintenance;
    private final boolean orderedFluids;
    private final MTERegistry registry;
    private int nextId;
    final MetaTileEntityElectricBlastFurnace blastFurnace;
    final MetaTileEntityLargeChemicalReactor chemicalReactor;
    final MetaTileEntityDistillationTower tower;
    final MetaTileEntityAssemblyLine assembly;

    @SuppressWarnings("unchecked")
    PreviewTestRegistry() throws ReflectiveOperationException {
        Bootstrap.perform();
        Field itemMap = Item.class.getDeclaredField("BLOCK_TO_ITEM");
        itemMap.setAccessible(true);
        this.itemMappings = (Map<Block, Item>) itemMap.get(null);
        this.research = ConfigHolder.machines.enableResearch;
        this.maintenance = ConfigHolder.machines.enableMaintenance;
        this.orderedFluids = ConfigHolder.machines.orderedFluidAssembly;
        ConfigHolder.machines.enableResearch = false;
        ConfigHolder.machines.enableMaintenance = true;
        ConfigHolder.machines.orderedFluidAssembly = false;
        abilities.putAll(MultiblockAbility.REGISTRY);
        coils.putAll(GregTechAPI.HEATING_COILS);
        for (MultiblockAbility<?> ability : new MultiblockAbility<?>[] {
                MultiblockAbility.IMPORT_ITEMS, MultiblockAbility.EXPORT_ITEMS,
                MultiblockAbility.IMPORT_FLUIDS, MultiblockAbility.EXPORT_FLUIDS,
                MultiblockAbility.INPUT_ENERGY, MultiblockAbility.MAINTENANCE_HATCH,
                MultiblockAbility.MUFFLER_HATCH }) {
            MultiblockAbility.REGISTRY.put(ability, new ArrayList<>());
        }
        this.registry = GregTechAPI.mteManager.createRegistry("q4_preview_test");
        BlockMachine machines = new BlockMachine();
        machines.setRegistryName(id("machines"));
        registry.setBlock(machines);
        mapItem(machines);
        replace(MetaBlocks.class, "METAL_CASING", block(new BlockMetalCasing(), "metal"));
        replace(MetaBlocks.class, "BOILER_CASING", block(new BlockBoilerCasing(), "boiler"));
        replace(MetaBlocks.class, "MULTIBLOCK_CASING", block(new BlockMultiblockCasing(), "multi"));
        replace(MetaBlocks.class, "TRANSPARENT_CASING", block(new BlockGlassCasing(), "glass"));
        replace(MetaBlocks.class, "WIRE_COIL", block(new BlockWireCoil(), "coil"));
        GregTechAPI.HEATING_COILS.clear();
        GregTechAPI.HEATING_COILS.put(MetaBlocks.WIRE_COIL.getState(BlockWireCoil.CoilType.CUPRONICKEL),
                BlockWireCoil.CoilType.CUPRONICKEL);
        GregTechAPI.HEATING_COILS.put(MetaBlocks.WIRE_COIL.getState(BlockWireCoil.CoilType.KANTHAL),
                BlockWireCoil.CoilType.KANTHAL);
        saveArray(MetaTileEntities.ITEM_IMPORT_BUS);
        saveArray(MetaTileEntities.ITEM_EXPORT_BUS);
        saveArray(MetaTileEntities.FLUID_IMPORT_HATCH);
        saveArray(MetaTileEntities.FLUID_EXPORT_HATCH);
        saveArray(MetaTileEntities.ENERGY_INPUT_HATCH);
        saveArray(MetaTileEntities.MUFFLER_HATCH);
        for (int tier : new int[] { GTValues.ULV, GTValues.LV, GTValues.HV }) {
            MetaTileEntities.ITEM_IMPORT_BUS[tier] = register(
                    new MetaTileEntityItemBus(id("item_in_" + tier), tier, false));
            MetaTileEntities.ITEM_EXPORT_BUS[tier] = register(
                    new MetaTileEntityItemBus(id("item_out_" + tier), tier, true));
            MetaTileEntities.FLUID_IMPORT_HATCH[tier] = register(
                    new MetaTileEntityFluidHatch(id("fluid_in_" + tier), tier, false));
            MetaTileEntities.FLUID_EXPORT_HATCH[tier] = register(
                    new MetaTileEntityFluidHatch(id("fluid_out_" + tier), tier, true));
            MetaTileEntities.ENERGY_INPUT_HATCH[tier] = register(
                    new MetaTileEntityEnergyHatch(id("energy_" + tier), tier, 2, false));
            MetaTileEntities.MUFFLER_HATCH[tier] = register(
                    new MetaTileEntityMufflerHatch(id("muffler_" + tier), tier));
        }
        replace(MetaTileEntities.class, "MAINTENANCE_HATCH",
                register(new MetaTileEntityMaintenanceHatch(id("maintenance"), false)));
        this.blastFurnace = register(new MetaTileEntityElectricBlastFurnace(id("blast")));
        this.chemicalReactor = register(new MetaTileEntityLargeChemicalReactor(id("chemical")));
        this.tower = register(new MetaTileEntityDistillationTower(id("tower")));
        this.assembly = register(new MetaTileEntityAssemblyLine(id("assembly")));
        replace(MetaTileEntities.class, "ELECTRIC_BLAST_FURNACE", blastFurnace);
        replace(MetaTileEntities.class, "LARGE_CHEMICAL_REACTOR", chemicalReactor);
    }

    <T extends MetaTileEntity> T register(T machine) {
        return MetaTileEntities.registerMetaTileEntity(nextId++, machine);
    }

    static ResourceLocation id(String path) {
        return new ResourceLocation("q4_preview_test", path);
    }

    private <T extends Block> T block(T block, String name) {
        block.setRegistryName(id(name));
        mapItem(block);
        return block;
    }

    private void mapItem(Block block) {
        previousItems.put(block, itemMappings.get(block));
        itemMappings.put(block, new ItemBlock(block).setHasSubtypes(true).setRegistryName(block.getRegistryName()));
    }

    private void replace(Class<?> owner, String name, Object value) throws ReflectiveOperationException {
        Field field = owner.getField(name);
        savedFields.put(field, field.get(null));
        field.set(null, value);
    }

    private void saveArray(MetaTileEntity[] array) {
        savedArrays.put(array, array.clone());
    }

    @Override
    public void close() throws IllegalAccessException {
        for (Map.Entry<Field, Object> entry : savedFields.entrySet()) entry.getKey().set(null, entry.getValue());
        savedArrays.forEach((array, copy) -> System.arraycopy(copy, 0, array, 0, array.length));
        MultiblockAbility.REGISTRY.clear();
        MultiblockAbility.REGISTRY.putAll(abilities);
        GregTechAPI.HEATING_COILS.clear();
        GregTechAPI.HEATING_COILS.putAll(coils);
        previousItems.forEach((block, item) -> {
            if (item == null) itemMappings.remove(block);
            else itemMappings.put(block, item);
        });
        ConfigHolder.machines.enableResearch = research;
        ConfigHolder.machines.enableMaintenance = maintenance;
        ConfigHolder.machines.orderedFluidAssembly = orderedFluids;
    }
}
