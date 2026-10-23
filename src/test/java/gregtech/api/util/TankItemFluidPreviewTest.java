package gregtech.api.util;

import gregtech.Bootstrap;
import gregtech.api.GregTechAPI;
import gregtech.api.block.machines.BlockMachine;
import gregtech.api.block.machines.MachineItemBlock;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.registry.MTERegistry;
import gregtech.api.unification.material.Materials;
import gregtech.common.ConfigHolder;
import gregtech.common.metatileentities.storage.MetaTileEntityCreativeTank;
import gregtech.common.metatileentities.storage.MetaTileEntityDrum;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumTank;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;
import net.minecraftforge.fml.common.discovery.ASMDataTable;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class TankItemFluidPreviewTest {

    private static CountingMachineItemBlock item;
    private static MetaTileEntityDrum drum;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        ASMDataTable capabilities = new ASMDataTable();
        capabilities.addASMData(null, CapabilityInject.class.getName(), CapabilityFluidHandler.class.getName(),
                "FLUID_HANDLER_CAPABILITY", Collections.singletonMap("value", Type.getType(IFluidHandler.class)));
        capabilities.addASMData(null, CapabilityInject.class.getName(), CapabilityFluidHandler.class.getName(),
                "FLUID_HANDLER_ITEM_CAPABILITY",
                Collections.singletonMap("value", Type.getType(IFluidHandlerItem.class)));
        CapabilityManager.INSTANCE.injectCapabilities(capabilities);
        if (CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY == null) CapabilityFluidHandler.register();
        assertNotNull(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);

        MTERegistry registry = GregTechAPI.mteManager.createRegistry("q7_preview_test");
        BlockMachine block = new BlockMachine();
        block.setRegistryName("q7_preview_test", "machine");
        registry.setBlock(block);
        drum = new MetaTileEntityDrum(id("drum"), Materials.Steel, 32000);
        register(registry, 0, drum);
        register(registry, 1, new MetaTileEntityQuantumTank(id("tank"), 5, Integer.MAX_VALUE));
        register(registry, 2, new MetaTileEntityCreativeTank(id("creative")));
        register(registry, 3, new MetaTileEntityDrum(id("addon_drum"), Materials.Steel, 32000) {});
        register(registry, 4, new MetaTileEntityQuantumTank(id("addon_tank"), 5, 64000) {});
        item = new CountingMachineItemBlock(block);
        item.setRegistryName("q7_preview_test", "machine");
    }

    @Test
    void defaultsAndDisabledFamiliesDoNotQueryCapabilities() {
        ConfigHolder.ClientOptions defaults = new ConfigHolder.ClientOptions();
        assertFalse(defaults.drumFluidBadges);
        assertFalse(defaults.quantumTankFluidBadges);
        ItemStack drumItem = filled(0, new FluidStack(FluidRegistry.WATER, 1000));
        ItemStack tankItem = filled(1, new FluidStack(FluidRegistry.WATER, 1000));
        item.queries = 0;
        assertNull(TankItemFluidPreview.getFluid(drumItem, false, false));
        assertNull(TankItemFluidPreview.getFluid(tankItem, false, false));
        assertNull(TankItemFluidPreview.getFluid(drumItem, false, true));
        assertNull(TankItemFluidPreview.getFluid(tankItem, true, false));
        assertEquals(0, item.queries);
        assertNotNull(TankItemFluidPreview.getFluid(drumItem, true, false));
        assertNotNull(TankItemFluidPreview.getFluid(tankItem, false, true));
        assertEquals(2, item.queries);
    }

    @Test
    void onlyTheTwoExactFamiliesAreAdmitted() {
        for (int metadata : new int[] { 2, 3, 4 }) {
            ItemStack excluded = filled(metadata, new FluidStack(FluidRegistry.WATER, 1000));
            item.queries = 0;
            assertNull(TankItemFluidPreview.getFluid(excluded, true, true));
            assertEquals(0, item.queries);
        }
        assertNull(TankItemFluidPreview.getFluid(ItemStack.EMPTY, true, true));
        assertNull(TankItemFluidPreview.getFluid(new ItemStack(Items.BUCKET), true, true));
    }

    @Test
    void emptyNonpositiveAndInvalidFluidDataHaveNoBadge() {
        for (int metadata : new int[] { 0, 1 }) {
            assertNull(TankItemFluidPreview.getFluid(new ItemStack(item, 1, metadata), true, true));
            assertNull(TankItemFluidPreview.getFluid(filled(metadata, new FluidStack(FluidRegistry.WATER, 0)), true,
                    true));
            assertNull(TankItemFluidPreview.getFluid(filled(metadata, new FluidStack(FluidRegistry.WATER, -1)), true,
                    true));
            ItemStack invalid = filled(metadata, new FluidStack(FluidRegistry.WATER, 1000));
            invalid.getTagCompound().getCompoundTag(FluidHandlerItemStack.FLUID_NBT_KEY)
                    .setString("FluidName", "q7_unknown_fluid");
            assertNull(TankItemFluidPreview.getFluid(invalid, true, true));
        }
    }

    @Test
    void lockedFluidIsNotStoredFluid() {
        ItemStack locked = new ItemStack(item, 1, 1);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("LockedFluid", new FluidStack(FluidRegistry.LAVA, 1000).writeToNBT(new NBTTagCompound()));
        locked.setTagCompound(tag);
        NBTTagCompound before = tag.copy();
        assertNull(TankItemFluidPreview.getFluid(locked, true, true));
        assertEquals(before, locked.getTagCompound());
    }

    @Test
    void eachItemSuppliesItsOwnFluidAndNeitherNbtNorPrototypeChanges() {
        FluidStack water = new FluidStack(FluidRegistry.WATER, 1000);
        water.tag = new NBTTagCompound();
        water.tag.setString("sample", "unchanged");
        ItemStack first = filled(0, water);
        ItemStack second = filled(0, new FluidStack(FluidRegistry.LAVA, 32000));
        NBTTagCompound firstBefore = first.getTagCompound().copy();
        NBTTagCompound secondBefore = second.getTagCompound().copy();
        NBTTagCompound prototypeBefore = drum.writeToNBT(new NBTTagCompound());
        FluidStack firstPreview = TankItemFluidPreview.getFluid(first, true, true);
        FluidStack secondPreview = TankItemFluidPreview.getFluid(second, true, true);
        assertNotNull(firstPreview);
        assertNotNull(secondPreview);
        assertEquals(FluidRegistry.WATER, firstPreview.getFluid());
        assertEquals(1000, firstPreview.amount);
        assertEquals(FluidRegistry.LAVA, secondPreview.getFluid());
        assertEquals(32000, secondPreview.amount);
        firstPreview.amount = 1;
        firstPreview.tag.setString("sample", "changed preview only");
        assertEquals(firstBefore, first.getTagCompound());
        assertEquals(secondBefore, second.getTagCompound());
        assertEquals(prototypeBefore, drum.writeToNBT(new NBTTagCompound()));
    }

    @Test
    void largeAmountsAndFluidTintRemainAvailableWithoutCapacityScaling() {
        FluidStack tinted = Materials.Lava.getFluid(Integer.MAX_VALUE);
        ItemStack tank = filled(1, tinted);
        FluidStack preview = TankItemFluidPreview.getFluid(tank, false, true);
        assertNotNull(preview);
        assertEquals(Integer.MAX_VALUE, preview.amount);
        assertEquals(tinted.getFluid().getColor(tinted), preview.getFluid().getColor(preview));
        assertEquals(tinted.getFluid().getStill(tinted), preview.getFluid().getStill(preview));
    }

    @Test
    void badgeBoundsLeaveTheBottomAndCountOverlayClear() {
        assertEquals(1, TankItemFluidPreview.BADGE_OFFSET);
        assertEquals(6, TankItemFluidPreview.BADGE_SIZE);
        assertTrue(TankItemFluidPreview.BADGE_OFFSET + TankItemFluidPreview.BADGE_SIZE <= 8);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("q7_preview_test", path);
    }

    private static void register(MTERegistry registry, int number, MetaTileEntity machine) {
        registry.register(number, machine.metaTileEntityId, machine);
    }

    private static ItemStack filled(int metadata, FluidStack contents) {
        ItemStack stack = new ItemStack(item, 1, metadata);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag(FluidHandlerItemStack.FLUID_NBT_KEY, contents.writeToNBT(new NBTTagCompound()));
        stack.setTagCompound(tag);
        return stack;
    }

    private static final class CountingMachineItemBlock extends MachineItemBlock {

        private int queries;

        CountingMachineItemBlock(BlockMachine block) {
            super(block);
        }

        @Override
        public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
            ICapabilityProvider delegate = super.initCapabilities(stack, nbt);
            return new ICapabilityProvider() {

                @Override
                public boolean hasCapability(Capability<?> capability, EnumFacing side) {
                    return delegate != null && delegate.hasCapability(capability, side);
                }

                @Override
                public <T> T getCapability(Capability<T> capability, EnumFacing side) {
                    queries++;
                    return delegate == null ? null : delegate.getCapability(capability, side);
                }
            };
        }
    }
}
