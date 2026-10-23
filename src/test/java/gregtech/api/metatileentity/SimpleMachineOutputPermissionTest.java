package gregtech.api.metatileentity;

import gregtech.Bootstrap;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;
import gregtech.integration.opencomputers.drivers.DriverSimpleMachine.EnvironmentSimpleMachine;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import li.cil.oc.api.API;
import li.cil.oc.api.detail.Builder;
import li.cil.oc.api.detail.NetworkAPI;
import li.cil.oc.api.machine.Arguments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.objectweb.asm.Type;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleMachineOutputPermissionTest {

    private static RecipeMap<SimpleRecipeBuilder> recipeMap;

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.perform();

        // Standalone tests do not run Forge's capability injection phase.
        ASMDataTable capabilities = new ASMDataTable();
        capabilities.addASMData(null, CapabilityInject.class.getName(), CapabilityItemHandler.class.getName(),
                "ITEM_HANDLER_CAPABILITY", Collections.singletonMap("value", Type.getType(IItemHandler.class)));
        capabilities.addASMData(null, CapabilityInject.class.getName(), CapabilityFluidHandler.class.getName(),
                "FLUID_HANDLER_CAPABILITY", Collections.singletonMap("value", Type.getType(IFluidHandler.class)));
        // Fluid registration also creates the item capability; inject both before either test fixture registers it.
        capabilities.addASMData(null, CapabilityInject.class.getName(), CapabilityFluidHandler.class.getName(),
                "FLUID_HANDLER_ITEM_CAPABILITY",
                Collections.singletonMap("value", Type.getType(IFluidHandlerItem.class)));
        CapabilityManager.INSTANCE.injectCapabilities(capabilities);
        if (CapabilityItemHandler.ITEM_HANDLER_CAPABILITY == null) CapabilityItemHandler.register();
        if (CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY == null) CapabilityFluidHandler.register();
        assertNotNull(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
        assertNotNull(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY);

        recipeMap = new RecipeMapBuilder<>("output_permission_test", new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1).fluidInputs(1).fluidOutputs(1).build();
    }

    @ParameterizedTest
    @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
    public void capabilitiesRespectIndependentPermissions(boolean allowItems, boolean allowFluids) {
        EnumFacing[] sides = Arrays.copyOf(EnumFacing.VALUES, EnumFacing.VALUES.length + 1);
        for (EnumFacing side : sides) {
            SimpleMachineMetaTileEntity machine = createMachine();
            machine.setAllowInputFromOutputSideItems(allowItems);
            machine.setAllowInputFromOutputSideFluids(allowFluids);
            boolean acceptsItems = side != machine.getOutputFacingItems() || allowItems;
            boolean acceptsFluids = side != machine.getOutputFacingFluids() || allowFluids;
            String face = "side=" + side + ", items=" + allowItems + ", fluids=" + allowFluids;

            IItemHandler items = machine.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
            assertNotNull(items, face);
            ItemStack input = new ItemStack(Items.IRON_INGOT, 3);
            assertEquals(acceptsItems ? 0 : 3, items.insertItem(0, input.copy(), true).getCount(), face);
            assertTrue(machine.getImportItems().getStackInSlot(0).isEmpty(), face);
            assertEquals(acceptsItems ? 0 : 3, items.insertItem(0, input.copy(), false).getCount(), face);
            assertEquals(acceptsItems ? 3 : 0, machine.getImportItems().getStackInSlot(0).getCount(), face);

            machine.getExportItems().setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 4));
            int outputSlot = items.getSlots() - 1;
            ItemStack simulatedExtraction = items.extractItem(outputSlot, 2, true);
            assertEquals(Items.GOLD_INGOT, simulatedExtraction.getItem(), face);
            assertEquals(2, simulatedExtraction.getCount(), face);
            assertEquals(4, machine.getExportItems().getStackInSlot(0).getCount(), face);
            assertEquals(2, items.extractItem(outputSlot, 2, false).getCount(), face);
            assertEquals(2, machine.getExportItems().getStackInSlot(0).getCount(), face);

            IFluidHandler fluids = machine.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
            assertNotNull(fluids, face);
            FluidStack water = new FluidStack(FluidRegistry.WATER, 1000);
            assertEquals(acceptsFluids ? 1000 : 0, fluids.fill(water.copy(), false), face);
            assertEquals(0, machine.getImportFluids().getTankAt(0).getFluidAmount(), face);
            assertEquals(acceptsFluids ? 1000 : 0, fluids.fill(water.copy(), true), face);
            assertEquals(acceptsFluids ? 1000 : 0, machine.getImportFluids().getTankAt(0).getFluidAmount(), face);

            machine.getExportFluids().fill(new FluidStack(FluidRegistry.LAVA, 500), true);
            FluidStack simulatedDrain = fluids.drain(100, false);
            assertNotNull(simulatedDrain, face);
            assertEquals(FluidRegistry.LAVA, simulatedDrain.getFluid(), face);
            assertEquals(100, simulatedDrain.amount, face);
            assertEquals(500, machine.getExportFluids().getTankAt(0).getFluidAmount(), face);
            FluidStack drained = fluids.drain(new FluidStack(FluidRegistry.LAVA, 100), true);
            assertNotNull(drained, face);
            assertEquals(100, drained.amount, face);
            assertEquals(400, machine.getExportFluids().getTankAt(0).getFluidAmount(), face);
        }
    }

    @ParameterizedTest
    @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
    public void permissionsAndOutputFacesSurviveNbtRoundTrip(boolean allowItems, boolean allowFluids) {
        SimpleMachineMetaTileEntity machine = createMachine();
        machine.setAllowInputFromOutputSideItems(allowItems);
        machine.setAllowInputFromOutputSideFluids(allowFluids);
        NBTTagCompound saved = machine.writeToNBT(new NBTTagCompound());
        assertEquals(allowItems, saved.getBoolean("AllowInputFromOutputSide"));
        assertEquals(allowFluids, saved.getBoolean("AllowInputFromOutputSideF"));

        SimpleMachineMetaTileEntity restored = createMachine();
        restored.setAllowInputFromOutputSideItems(!allowItems);
        restored.setAllowInputFromOutputSideFluids(!allowFluids);
        restored.setOutputFacingItems(EnumFacing.UP);
        restored.setOutputFacingFluids(EnumFacing.DOWN);
        restored.readFromNBT(saved);
        assertEquals(allowItems, restored.isAllowInputFromOutputSideItems());
        assertEquals(allowFluids, restored.isAllowInputFromOutputSideFluids());
        assertEquals(EnumFacing.EAST, restored.getOutputFacingItems());
        assertEquals(EnumFacing.WEST, restored.getOutputFacingFluids());
        IItemHandler items = restored.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.EAST);
        IFluidHandler fluids = restored.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, EnumFacing.WEST);
        assertNotNull(items);
        assertNotNull(fluids);
        assertEquals(allowItems, items.insertItem(0, new ItemStack(Items.IRON_INGOT), true).isEmpty());
        assertEquals(allowFluids ? 1000 : 0, fluids.fill(new FluidStack(FluidRegistry.WATER, 1000), false));
    }

    @ParameterizedTest
    @CsvSource({ "false, false", "false, true", "true, false", "true, true" })
    public void computerCallbacksReportAndChangeOnlyTheirResource(boolean allowItems, boolean allowFluids) {
        SimpleMachineMetaTileEntity machine = createMachine();
        machine.setAllowInputFromOutputSideItems(allowItems);
        machine.setAllowInputFromOutputSideFluids(allowFluids);
        EnvironmentSimpleMachine environment = createEnvironment(machine);
        assertComputerPermissions(environment, allowItems, allowFluids);

        environment.setAllowInputFromOutputSideItems(null, booleanArgument(!allowItems));
        assertComputerPermissions(environment, !allowItems, allowFluids);
        assertEquals(!allowItems, machine.isAllowInputFromOutputSideItems());
        assertEquals(allowFluids, machine.isAllowInputFromOutputSideFluids());

        environment.setAllowInputFromOutputSideFluids(null, booleanArgument(!allowFluids));
        assertComputerPermissions(environment, !allowItems, !allowFluids);
        assertEquals(!allowItems, machine.isAllowInputFromOutputSideItems());
        assertEquals(!allowFluids, machine.isAllowInputFromOutputSideFluids());

        environment.setAllowInputFromOutputSide(null, booleanArgument(false));
        assertComputerPermissions(environment, false, false);
        environment.setAllowInputFromOutputSide(null, booleanArgument(true));
        assertComputerPermissions(environment, true, true);
    }

    private static SimpleMachineMetaTileEntity createMachine() {
        SimpleMachineMetaTileEntity machine = new SimpleMachineMetaTileEntity(
                GTUtility.gregtechId("output_permission_test"), recipeMap, null, 1, true);
        MetaTileEntityHolder holder = new MetaTileEntityHolder();
        holder.setRawMetaTileEntity(machine);
        holder.setWorld(DummyWorld.INSTANCE);
        machine.setFrontFacing(EnumFacing.NORTH);
        machine.setOutputFacingItems(EnumFacing.EAST);
        machine.setOutputFacingFluids(EnumFacing.WEST);
        return machine;
    }

    private static void assertComputerPermissions(EnvironmentSimpleMachine environment, boolean items, boolean fluids) {
        assertArrayEquals(new Object[] { items }, environment.isAllowInputFromOutputSideItems(null, null));
        assertArrayEquals(new Object[] { fluids }, environment.isAllowInputFromOutputSideFluids(null, null));
        assertArrayEquals(new Object[] { items && fluids }, environment.isAllowInputFromOutputSide(null, null));
    }

    private static EnvironmentSimpleMachine createEnvironment(SimpleMachineMetaTileEntity machine) {
        // Only OC's node builder is stubbed; callbacks execute against the real machine.
        ClassLoader loader = NetworkAPI.class.getClassLoader();
        Object componentBuilder = Proxy.newProxyInstance(loader, new Class<?>[] { Builder.ComponentBuilder.class },
                (proxy, method, args) -> null);
        Object nodeBuilder = Proxy.newProxyInstance(loader, new Class<?>[] { Builder.NodeBuilder.class },
                (proxy, method, args) -> componentBuilder);
        NetworkAPI previousNetwork = API.network;
        try {
            API.network = (NetworkAPI) Proxy.newProxyInstance(loader, new Class<?>[] { NetworkAPI.class },
                    (proxy, method, args) -> nodeBuilder);
            return new EnvironmentSimpleMachine(machine.getHolder(), machine);
        } finally {
            API.network = previousNetwork;
        }
    }

    private static Arguments booleanArgument(boolean value) {
        return (Arguments) Proxy.newProxyInstance(Arguments.class.getClassLoader(), new Class<?>[] { Arguments.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("checkBoolean") && Integer.valueOf(0).equals(args[0])) return value;
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
