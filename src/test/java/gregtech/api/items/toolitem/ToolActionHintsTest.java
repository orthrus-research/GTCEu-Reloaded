package gregtech.api.items.toolitem;

import gregtech.Bootstrap;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.SimpleCapabilityManager;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.SimpleMachineMetaTileEntity;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.covers.CoverConveyor;
import gregtech.common.covers.CoverPump;
import gregtech.common.metatileentities.electric.SimpleMachineMetaTileEntityResizable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.common.discovery.ASMDataTable;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.vec.Vector3;
import com.mojang.authlib.GameProfile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ToolActionHintsTest {

    private static RecipeMap<SimpleRecipeBuilder> recipeMap;
    private static EntityPlayer player;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        // The standalone harness does not perform Forge's capability injection phase.
        ASMDataTable capabilities = new ASMDataTable();
        capabilities.addASMData(null, CapabilityInject.class.getName(), GregtechTileCapabilities.class.getName(),
                "CAPABILITY_CONTROLLABLE", Collections.singletonMap("value", Type.getType(IControllable.class)));
        capabilities.addASMData(null, CapabilityInject.class.getName(), GregtechCapabilities.class.getName(),
                "CAPABILITY_TOOLBELT_HANDLER",
                Collections.singletonMap("value", Type.getType(ItemGTToolbelt.ToolStackHandler.class)));
        CapabilityManager.INSTANCE.injectCapabilities(capabilities);
        if (GregtechTileCapabilities.CAPABILITY_CONTROLLABLE == null) {
            SimpleCapabilityManager.registerCapabilityWithNoDefault(IControllable.class);
        }
        if (GregtechCapabilities.CAPABILITY_TOOLBELT_HANDLER == null) {
            SimpleCapabilityManager.registerCapabilityWithNoDefault(ItemGTToolbelt.ToolStackHandler.class);
        }
        assertNotNull(GregtechTileCapabilities.CAPABILITY_CONTROLLABLE);
        assertNotNull(GregtechCapabilities.CAPABILITY_TOOLBELT_HANDLER);
        recipeMap = new RecipeMapBuilder<>("q5_tool_hints", new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1).build();
        player = new EntityPlayer(DummyWorld.INSTANCE, new GameProfile(new UUID(0, 5), "tool_hint_test")) {

            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }

            @Override
            public void sendStatusMessage(ITextComponent message, boolean actionBar) {}
        };
    }

    @Test
    void admittedMachinesMatchActualWrenchDispatchOnEveryFaceAndSneakState() {
        for (boolean resizable : new boolean[] { false, true }) {
            for (boolean sneak : new boolean[] { false, true }) {
                for (EnumFacing side : EnumFacing.VALUES) {
                    SimpleMachineMetaTileEntity machine = machine(resizable);
                    player.setSneaking(sneak);
                    ToolAction hint = machine.getToolActionHint(tools(ToolClasses.WRENCH), sneak, side);
                    boolean handled = machine.onToolClick(player, tools(ToolClasses.WRENCH), EnumHand.MAIN_HAND,
                            hit(side));
                    assertEquals(handled, hint != null, resizable + ":" + sneak + ":" + side);
                    if (hint == ToolAction.ROTATE_FRONT) assertEquals(side, machine.getFrontFacing());
                    if (hint == ToolAction.ROTATE_OUTPUT) assertEquals(side, machine.getOutputFacingItems());
                }
            }
        }
    }

    @Test
    void frontlessMachinesAndSplitOutputsRetainTheirActualWrenchRules() {
        for (boolean sneak : new boolean[] { false, true }) {
            player.setSneaking(sneak);
            for (EnumFacing side : EnumFacing.VALUES) {
                SimpleMachineMetaTileEntity machine = attach(new SimpleMachineMetaTileEntity(
                        GTUtility.gregtechId("q5_frontless"), recipeMap, null, 1, false));
                ToolAction hint = machine.getToolActionHint(tools(ToolClasses.WRENCH), sneak, side);
                assertEquals(machine.onToolClick(player, tools(ToolClasses.WRENCH), EnumHand.MAIN_HAND, hit(side)),
                        hint != null);
                if (sneak) assertNull(hint);
            }
        }
        player.setSneaking(false);
        SimpleMachineMetaTileEntity split = machine(false);
        split.setOutputFacingFluids(EnumFacing.WEST);
        assertNull(split.getToolActionHint(tools(ToolClasses.WRENCH), false, EnumFacing.SOUTH));
        assertFalse(split.onToolClick(player, tools(ToolClasses.WRENCH), EnumHand.MAIN_HAND, hit(EnumFacing.SOUTH)));
        assertEquals(ToolAction.ROTATE_OUTPUT,
                split.getToolActionHint(tools(ToolClasses.WRENCH), false, EnumFacing.WEST));
        assertTrue(split.onToolClick(player, tools(ToolClasses.WRENCH), EnumHand.MAIN_HAND, hit(EnumFacing.WEST)));
        assertEquals(EnumFacing.WEST, split.getOutputFacingItems());
        assertEquals(EnumFacing.WEST, split.getOutputFacingFluids());
    }

    @Test
    void inputPermissionIsANeutralToggleOnEveryFace() {
        SimpleMachineMetaTileEntity machine = machine(false);
        for (boolean sneak : new boolean[] { false, true }) {
            player.setSneaking(sneak);
            for (EnumFacing side : EnumFacing.VALUES) {
                for (boolean enabled : new boolean[] { false, true }) {
                    machine.setAllowInputFromOutputSideItems(enabled);
                    machine.setAllowInputFromOutputSideFluids(!enabled);
                    assertEquals(ToolAction.TOGGLE_INPUT,
                            machine.getToolActionHint(tools(ToolClasses.SCREWDRIVER), sneak, side));
                    assertTrue(
                            machine.onToolClick(player, tools(ToolClasses.SCREWDRIVER), EnumHand.MAIN_HAND, hit(side)));
                    assertEquals(!enabled, machine.isAllowInputFromOutputSideItems());
                    assertEquals(!enabled, machine.isAllowInputFromOutputSideFluids());
                }
            }
        }
    }

    @Test
    void workingAndSoundHintsDescribeTheNextAction() {
        SimpleMachineMetaTileEntity machine = machine(false);
        for (EnumFacing side : EnumFacing.VALUES) {
            assertEquals(ToolAction.DISABLE_WORKING,
                    machine.getToolActionHint(tools(ToolClasses.SOFT_MALLET), false, side));
            assertTrue(machine.onToolClick(player, tools(ToolClasses.SOFT_MALLET), EnumHand.MAIN_HAND, hit(side)));
            assertFalse(machine.getRecipeLogic().isWorkingEnabled());
            assertEquals(ToolAction.ENABLE_WORKING,
                    machine.getToolActionHint(tools(ToolClasses.SOFT_MALLET), false, side));
            assertTrue(machine.onToolClick(player, tools(ToolClasses.SOFT_MALLET), EnumHand.MAIN_HAND, hit(side)));
            assertTrue(machine.getRecipeLogic().isWorkingEnabled());

            assertEquals(ToolAction.MUFFLE, machine.getToolActionHint(tools(ToolClasses.HARD_HAMMER), false, side));
            assertTrue(machine.onToolClick(player, tools(ToolClasses.HARD_HAMMER), EnumHand.MAIN_HAND, hit(side)));
            assertTrue(machine.isMuffled());
            assertEquals(ToolAction.UNMUFFLE, machine.getToolActionHint(tools(ToolClasses.HARD_HAMMER), false, side));
            assertTrue(machine.onToolClick(player, tools(ToolClasses.HARD_HAMMER), EnumHand.MAIN_HAND, hit(side)));
            assertFalse(machine.isMuffled());
        }
    }

    @Test
    void multiPurposeToolsFollowTheActualDispatchOrder() {
        SimpleMachineMetaTileEntity machine = machine(false);
        player.setSneaking(false);
        Set<String> all = tools(ToolClasses.SCREWDRIVER, ToolClasses.SOFT_MALLET, ToolClasses.WRENCH,
                ToolClasses.CROWBAR, ToolClasses.HARD_HAMMER);
        assertEquals(ToolAction.TOGGLE_INPUT, machine.getToolActionHint(all, false, EnumFacing.EAST));
        assertTrue(machine.onToolClick(player, all, EnumHand.MAIN_HAND, hit(EnumFacing.EAST)));
        assertTrue(machine.isAllowInputFromOutputSideItems());
        assertTrue(machine.getRecipeLogic().isWorkingEnabled());
        assertEquals(EnumFacing.SOUTH, machine.getOutputFacingItems());

        all.remove(ToolClasses.SCREWDRIVER);
        assertEquals(ToolAction.DISABLE_WORKING, machine.getToolActionHint(all, false, EnumFacing.EAST));
        assertTrue(machine.onToolClick(player, all, EnumHand.MAIN_HAND, hit(EnumFacing.EAST)));
        assertFalse(machine.getRecipeLogic().isWorkingEnabled());
        all.remove(ToolClasses.SOFT_MALLET);
        assertEquals(ToolAction.ROTATE_OUTPUT, machine.getToolActionHint(all, false, EnumFacing.EAST));
        assertTrue(machine.onToolClick(player, all, EnumHand.MAIN_HAND, hit(EnumFacing.EAST)));
        assertEquals(EnumFacing.EAST, machine.getOutputFacingItems());
        all.remove(ToolClasses.WRENCH);
        assertNull(machine.getToolActionHint(all, false, EnumFacing.EAST));
        assertFalse(machine.onToolClick(player, all, EnumHand.MAIN_HAND, hit(EnumFacing.EAST)));
        assertFalse(machine.isMuffled());
    }

    @Test
    void unknownSubclassChangingARealHandlerDoesNotInheritHints() {
        SimpleMachineMetaTileEntity custom = attach(new SimpleMachineMetaTileEntity(
                GTUtility.gregtechId("q5_custom"), recipeMap, null, 1, true) {

            @Override
            public boolean onWrenchClick(EntityPlayer player, EnumHand hand, EnumFacing side,
                                         CuboidRayTraceResult hit) {
                return false;
            }
        });
        for (String tool : tools(ToolClasses.WRENCH, ToolClasses.SCREWDRIVER, ToolClasses.SOFT_MALLET,
                ToolClasses.HARD_HAMMER, ToolClasses.CROWBAR)) {
            for (EnumFacing side : EnumFacing.VALUES) {
                assertNull(custom.getToolActionHint(tools(tool), false, side));
            }
        }
        assertFalse(custom.onToolClick(player, tools(ToolClasses.WRENCH), EnumHand.MAIN_HAND, hit(EnumFacing.EAST)));
    }

    @Test
    void coversTakePrecedenceAndUnknownCoverHandlersAreNeverProbed() {
        SimpleMachineMetaTileEntity machine = machine(false);
        CoverDefinition conveyorDefinition = new CoverDefinition(GTUtility.gregtechId("q5_conveyor"),
                (definition, view, side) -> new CoverConveyor(definition, view, side, 1, 8), ItemStack.EMPTY);
        machine.addCover(EnumFacing.EAST, conveyorDefinition.createCover(machine, EnumFacing.EAST));
        assertEquals(ToolAction.COVER_SETTINGS,
                machine.getToolActionHint(tools(ToolClasses.SCREWDRIVER), false, EnumFacing.EAST));
        assertEquals(ToolAction.REMOVE_COVER,
                machine.getToolActionHint(tools(ToolClasses.CROWBAR), false, EnumFacing.EAST));
        assertNull(machine.getToolActionHint(tools(ToolClasses.CROWBAR), false, EnumFacing.WEST));
        assertNull(machine.getToolActionHint(tools(ToolClasses.SOFT_MALLET), false, EnumFacing.EAST));

        CoverDefinition pumpDefinition = new CoverDefinition(GTUtility.gregtechId("q5_pump"),
                (definition, view, side) -> new CoverPump(definition, view, side, 1, 1000), ItemStack.EMPTY);
        machine.addCover(EnumFacing.WEST, pumpDefinition.createCover(machine, EnumFacing.WEST));
        assertEquals(ToolAction.COVER_SETTINGS,
                machine.getToolActionHint(tools(ToolClasses.SCREWDRIVER, ToolClasses.WRENCH), true, EnumFacing.WEST));

        machine.addCover(EnumFacing.UP, new CoverConveyor(conveyorDefinition, machine, EnumFacing.UP, 1, 8) {

            @Override
            public EnumActionResult onScrewdriverClick(EntityPlayer player, EnumHand hand, CuboidRayTraceResult hit) {
                throw new AssertionError("A hint must not probe a click handler");
            }
        });
        assertNull(machine.getToolActionHint(tools(ToolClasses.SCREWDRIVER), false, EnumFacing.UP));
        assertNull(machine.getToolActionHint(tools(ToolClasses.SOFT_MALLET), false, EnumFacing.UP));
    }

    @Test
    void queryingHintsDoesNotChangeMachineData() {
        SimpleMachineMetaTileEntity machine = machine(false);
        NBTTagCompound before = machine.writeToNBT(new NBTTagCompound());
        for (boolean sneak : new boolean[] { false, true }) {
            for (EnumFacing side : EnumFacing.VALUES) {
                for (String tool : tools(ToolClasses.WRENCH, ToolClasses.SCREWDRIVER, ToolClasses.SOFT_MALLET,
                        ToolClasses.HARD_HAMMER, ToolClasses.CROWBAR, ToolClasses.WIRE_CUTTER)) {
                    machine.getToolActionHint(tools(tool), sneak, side);
                }
            }
        }
        assertEquals(before, machine.writeToNBT(new NBTTagCompound()));
        assertNull(machine.getToolActionHint(tools(ToolClasses.WIRE_CUTTER), false, EnumFacing.UP));
        assertNull(machine.getToolActionHint(Collections.emptySet(), false, EnumFacing.UP));
        assertNull(ToolActionHints.forMainHand(machine, ItemStack.EMPTY, false, EnumFacing.UP));
    }

    @Test
    void toolbeltUsesOnlyItsSelectedToolAndNeverFallsBackToOffhand() {
        SimpleMachineMetaTileEntity machine = machine(false);
        ItemGTTool wrench = ItemGTTool.Builder.of("gregtech", "q5_wrench")
                .toolStats(builder -> builder).toolClasses(ToolClasses.WRENCH).build();
        ItemGTTool screwdriver = ItemGTTool.Builder.of("gregtech", "q5_screwdriver")
                .toolStats(builder -> builder).toolClasses(ToolClasses.SCREWDRIVER).build();
        ItemGTToolbelt.ToolStackHandler fixedHandler = new ItemGTToolbelt.ToolStackHandler(2);
        ItemGTToolbelt beltItem = new ItemGTToolbelt("gregtech", "q5_belt", () -> ItemStack.EMPTY) {

            @Override
            public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
                // Material-based slot sizing loads SRG reflection that is unavailable in the standalone harness.
                return new ToolbeltCapabilityProvider(stack) {

                    @Override
                    protected ToolStackHandler getHandler() {
                        return fixedHandler;
                    }
                };
            }
        };
        ItemStack belt = new ItemStack(beltItem);
        ItemGTToolbelt.ToolStackHandler handler = belt.getCapability(GregtechCapabilities.CAPABILITY_TOOLBELT_HANDLER,
                null);
        assertNotNull(handler);
        handler.setStackInSlot(0, new ItemStack(wrench));
        handler.setStackInSlot(1, new ItemStack(screwdriver));
        assertNull(ToolActionHints.forMainHand(machine, belt, false, EnumFacing.EAST));
        handler.setSelectedSlot(0);
        assertEquals(ToolAction.ROTATE_OUTPUT, ToolActionHints.forMainHand(machine, belt, false, EnumFacing.EAST));
        handler.setSelectedSlot(1);
        NBTTagCompound before = belt.serializeNBT();
        assertEquals(ToolAction.TOGGLE_INPUT, ToolActionHints.forMainHand(machine, belt, false, EnumFacing.EAST));
        assertEquals(before, belt.serializeNBT());
        player.setHeldItem(EnumHand.OFF_HAND, new ItemStack(wrench));
        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        assertNull(ToolActionHints.forMainHand(machine, player.getHeldItemMainhand(), false, EnumFacing.EAST));
        player.setHeldItem(EnumHand.OFF_HAND, ItemStack.EMPTY);
    }

    private static SimpleMachineMetaTileEntity machine(boolean resizable) {
        return attach(resizable ? new SimpleMachineMetaTileEntityResizable(GTUtility.gregtechId("q5_resizable"),
                recipeMap, 2, 2, null, 1) :
                new SimpleMachineMetaTileEntity(
                        GTUtility.gregtechId("q5_simple"), recipeMap, null, 1, true));
    }

    private static SimpleMachineMetaTileEntity attach(SimpleMachineMetaTileEntity machine) {
        MetaTileEntityHolder holder = new MetaTileEntityHolder() {

            {
                setRawMetaTileEntity(machine);
            }
        };
        holder.setWorld(DummyWorld.INSTANCE);
        machine.setFrontFacing(EnumFacing.NORTH);
        machine.setOutputFacing(EnumFacing.SOUTH);
        return machine;
    }

    private static Set<String> tools(String... tools) {
        return new HashSet<>(Arrays.asList(tools));
    }

    private static CuboidRayTraceResult hit(EnumFacing face) {
        return new CuboidRayTraceResult(new Vector3(ToolGrid.pointOnFace(face, 0, 0, 0.5)), BlockPos.ORIGIN, face,
                MetaTileEntity.FULL_CUBE_COLLISION, 1);
    }
}
