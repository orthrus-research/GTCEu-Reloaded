package gregtech.common.metatileentities.storage;

import gregtech.Bootstrap;
import gregtech.api.capability.IQuantumStorage;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class QuantumStorageControllerCacheTest {

    private static final BlockPos ORIGIN = new BlockPos(-32, 64, -32);
    private static final BlockPos CHEST = ORIGIN.east();
    private static final BlockPos TANK = ORIGIN.west();
    private QuantumStorageTestWorld world;
    private MetaTileEntityQuantumStorageController controller;
    private MetaTileEntityQuantumChest chest;
    private MetaTileEntityQuantumTank tank;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        ASMDataTable table = new ASMDataTable();
        inject(table, CapabilityItemHandler.class, "ITEM_HANDLER_CAPABILITY", IItemHandler.class);
        inject(table, CapabilityFluidHandler.class, "FLUID_HANDLER_CAPABILITY", IFluidHandler.class);
        CapabilityManager.INSTANCE.injectCapabilities(table);
        if (CapabilityItemHandler.ITEM_HANDLER_CAPABILITY == null) CapabilityItemHandler.register();
        if (CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY == null) CapabilityFluidHandler.register();
        assertNotNull(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
        assertNotNull(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY);
    }

    private static void inject(ASMDataTable table, Class<?> owner, String field, Class<?> type) {
        table.addASMData(null, CapabilityInject.class.getName(), owner.getName(), field,
                Collections.singletonMap("value", Type.getType(type)));
    }

    @BeforeEach
    void setUp() {
        world = new QuantumStorageTestWorld();
        controller = new MetaTileEntityQuantumStorageController(id("controller"));
        chest = new MetaTileEntityQuantumChest(id("chest"), 6, 100000);
        tank = new MetaTileEntityQuantumTank(id("tank"), 6, 100000);
        world.put(ORIGIN, controller);
        world.put(CHEST, chest);
        world.put(TANK, tank);
        controller.rebuildNetwork();
        assertEquals(1, controller.getCount(IQuantumStorage.Type.ITEM));
        assertEquals(1, controller.getCount(IQuantumStorage.Type.FLUID));
    }

    @Test
    void liveWeakReferenceReturnsTheCachedStorageWithoutReadingTheWorld() {
        world.tileReads = 0;

        assertSame(chest, controller.getStorage(CHEST));
        assertSame(tank, controller.getStorage(TANK));
        assertEquals(0, world.tileReads);
    }

    @Test
    void clearedWeakReferenceRepopulatesFromTheCurrentWorldTile() throws ReflectiveOperationException {
        WeakReference<IQuantumStorage<?>> previous = cache(controller).get(CHEST);
        previous.clear();
        world.tileReads = 0;

        assertSame(chest, controller.getStorage(CHEST));
        assertEquals(1, world.tileReads);
        assertNotSame(previous, cache(controller).get(CHEST));
        assertSame(chest, cache(controller).get(CHEST).get());
        assertSame(chest, controller.getStorage(CHEST));
        assertEquals(1, world.tileReads);
    }

    @Test
    void absentCacheEntryUsesTheSameWorldFallback() throws ReflectiveOperationException {
        cache(controller).remove(CHEST);

        assertSame(chest, controller.getStorage(CHEST));
        assertSame(chest, cache(controller).get(CHEST).get());
    }

    @Test
    void missingTileAfterWeakReferenceClearDoesNotRemoveOtherStorage() throws ReflectiveOperationException {
        world.remove(CHEST);
        cache(controller).get(CHEST).clear();

        assertNull(controller.getStorage(CHEST));
        assertSame(tank, controller.getStorage(TANK));
        controller.rebuildNetwork();
        assertEquals(0, controller.getCount(IQuantumStorage.Type.ITEM));
        assertEquals(1, controller.getCount(IQuantumStorage.Type.FLUID));
    }

    @Test
    void rebuildingAfterTileReplacementSelectsTheNewInventory() throws ReflectiveOperationException {
        MetaTileEntityQuantumChest replacement = new MetaTileEntityQuantumChest(id("replacement"), 6, 100000);
        world.put(CHEST, replacement);
        replacement.getExportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));

        controller.rebuildNetwork();
        powered(controller, true);

        assertSame(replacement, controller.getStorage(CHEST));
        assertSame(replacement, cache(controller).get(CHEST).get());
        assertEquals(8, countItems(controller.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
                EnumFacing.NORTH)));
    }

    @Test
    void mixedStorageCapabilitiesFollowPowerAdmissionAndSurviveCacheRefresh() throws ReflectiveOperationException {
        chest.getExportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
        assertEquals(1000, tank.getTypeValue().fill(new FluidStack(FluidRegistry.WATER, 1000), true));
        cache(controller).values().forEach(WeakReference::clear);
        powered(controller, true);

        IItemHandler items = controller.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.NORTH);
        IFluidHandler fluids = controller.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
                EnumFacing.NORTH);
        assertEquals(8, countItems(items));
        assertNotNull(fluids);
        assertEquals(1000, fluids.drain(1000, false).amount);
        assertEquals(1, controller.getCount(IQuantumStorage.Type.ITEM));
        assertEquals(1, controller.getCount(IQuantumStorage.Type.FLUID));

        powered(controller, false);
        assertNull(controller.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.NORTH));
        assertNull(controller.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, EnumFacing.NORTH));
        powered(controller, true);
        assertSame(items, controller.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.NORTH));
    }

    @Test
    void savedControllerResolvesFreshStorageReferencesOnLoad() throws ReflectiveOperationException {
        powered(controller, true);
        NBTTagCompound saved = controller.writeToNBT(new NBTTagCompound());
        MetaTileEntityQuantumStorageController loaded = new MetaTileEntityQuantumStorageController(id("loaded"));
        world.put(ORIGIN, loaded);
        loaded.readFromNBT(saved);
        assertTrue(cache(loaded).isEmpty());

        loaded.onLoad();

        assertSame(chest, loaded.getStorage(CHEST));
        assertSame(tank, loaded.getStorage(TANK));
        assertEquals(controller.getEnergyUsage(), loaded.getEnergyUsage());
        assertEquals(1, loaded.getCount(IQuantumStorage.Type.ITEM));
        assertEquals(1, loaded.getCount(IQuantumStorage.Type.FLUID));
    }

    @Test
    void controllerRemovalDisconnectsStorageAndClearsBothHandlerDelegates() throws ReflectiveOperationException {
        powered(controller, true);
        assertNotNull(controller.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.NORTH));
        assertNotNull(controller.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, EnumFacing.NORTH));

        controller.onRemoval();

        assertFalse(chest.isConnected());
        assertFalse(tank.isConnected());
        assertTrue(cache(controller).isEmpty());
        assertNull(controller.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.NORTH));
        assertNull(controller.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, EnumFacing.NORTH));
        assertFalse(controller.canConnect(chest));
    }

    @Test
    void cacheKeepsFullPositionsAcrossPackedHeightAliases() {
        BlockPos lower = new BlockPos(-17, -5000, -17);
        BlockPos upper = lower.up(4096);
        MetaTileEntityQuantumChest first = new MetaTileEntityQuantumChest(id("lower"), 6, 100000);
        MetaTileEntityQuantumChest second = new MetaTileEntityQuantumChest(id("upper"), 6, 100000);
        world.put(lower, first);
        world.put(upper, second);
        assertEquals(lower.toLong(), upper.toLong());

        assertSame(first, controller.getStorage(lower));
        assertSame(second, controller.getStorage(upper));
        assertSame(first, controller.getStorage(lower));
    }

    private static int countItems(IItemHandler handler) {
        assertNotNull(handler);
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) count += handler.getStackInSlot(slot).getCount();
        return count;
    }

    @SuppressWarnings("unchecked")
    private static Map<BlockPos, WeakReference<IQuantumStorage<?>>> cache(MetaTileEntityQuantumStorageController owner)
                                                                                                                        throws ReflectiveOperationException {
        Field field = MetaTileEntityQuantumStorageController.class.getDeclaredField("storageInstances");
        field.setAccessible(true);
        return (Map<BlockPos, WeakReference<IQuantumStorage<?>>>) field.get(owner);
    }

    private static void powered(MetaTileEntityQuantumStorageController owner, boolean value)
                                                                                             throws ReflectiveOperationException {
        Field field = MetaTileEntityQuantumStorageController.class.getDeclaredField("isPowered");
        field.setAccessible(true);
        field.setBoolean(owner, value);
    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation("gregtech", "test_quantum_cache_" + name);
    }
}
