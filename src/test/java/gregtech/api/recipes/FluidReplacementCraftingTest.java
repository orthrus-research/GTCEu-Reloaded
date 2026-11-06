package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.util.DummyContainer;
import gregtech.common.crafting.FluidReplaceRecipe;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;
import net.minecraftforge.fml.common.discovery.ASMDataTable;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.Type;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FluidReplacementCraftingTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        var capabilities = new ASMDataTable();
        capabilities.addASMData(null, CapabilityInject.class.getName(), CapabilityFluidHandler.class.getName(),
                "FLUID_HANDLER_CAPABILITY", Map.of("value", Type.getType(IFluidHandler.class)));
        capabilities.addASMData(null, CapabilityInject.class.getName(), CapabilityFluidHandler.class.getName(),
                "FLUID_HANDLER_ITEM_CAPABILITY", Map.of("value", Type.getType(IFluidHandlerItem.class)));
        CapabilityManager.INSTANCE.injectCapabilities(capabilities);
        if (CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY == null) CapabilityFluidHandler.register();
        assertNotNull(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void vanillaBucketsUseNativeFilledBucketResolution(boolean reverse) {
        ItemStack input = new ItemStack(reverse ? Items.LAVA_BUCKET : Items.WATER_BUCKET);
        Item output = reverse ? Items.WATER_BUCKET : Items.LAVA_BUCKET;
        var recipe = new FluidReplaceRecipe(false, null, new ItemStack(output), "A", 'A', input);
        var inventory = grid(input);
        var before = input.serializeNBT();
        assertTrue(recipe.matches(inventory, null));
        ItemStack result = recipe.getCraftingResult(inventory);
        assertSame(output, result.getItem());
        assertEquals(1, result.getCount());
        assertTrue(recipe.getRemainingItems(inventory).getFirst().isEmpty());
        assertEquals(before, input.serializeNBT());
    }

    @Test
    void nonBucketContainerPreservesItsItemAndUnrelatedNbt() {
        ItemStack input = container(FluidRegistry.WATER);
        input.setStackDisplayName("Retained container");
        var before = input.serializeNBT();
        var recipe = new FluidReplaceRecipe(false, null, new ItemStack(Items.LAVA_BUCKET), "A", 'A', input);
        var inventory = grid(input);
        assertTrue(recipe.matches(inventory, null));
        ItemStack result = recipe.getCraftingResult(inventory);
        assertSame(input.getItem(), result.getItem());
        assertEquals("Retained container", result.getDisplayName());
        var resultFluid = result.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)
                .drain(Integer.MAX_VALUE, false);
        assertSame(FluidRegistry.LAVA, resultFluid.getFluid());
        assertEquals(1000, resultFluid.amount);
        assertEquals(before, input.serializeNBT());
        assertTrue(recipe.getRemainingItems(inventory).getFirst().isEmpty());
    }

    @Test
    void fluidWithoutABucketDoesNotProduceAnInvalidBucket() {
        var fluid = new Fluid("native_crafting_no_bucket", new ResourceLocation("test", "still"),
                new ResourceLocation("test", "flow"));
        assertTrue(FluidRegistry.registerFluid(fluid));
        assertFalse(FluidRegistry.hasBucket(fluid));
        var recipe = new FluidReplaceRecipe(false, null, container(fluid), "A", 'A',
                new ItemStack(Items.WATER_BUCKET));
        assertTrue(recipe.getCraftingResult(grid(new ItemStack(Items.WATER_BUCKET))).isEmpty());
    }

    private static ItemStack container(Fluid fluid) {
        var item = new Item() {

            @Override
            public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
                return new FluidHandlerItemStack(stack, 1000);
            }

            @Override
            public boolean hasContainerItem(ItemStack stack) {
                return true;
            }
        };
        var stack = new ItemStack(item);
        assertEquals(1000, stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)
                .fill(new FluidStack(fluid, 1000), true));
        return stack;
    }

    private static InventoryCrafting grid(ItemStack input) {
        var inventory = new InventoryCrafting(new DummyContainer(), 3, 3);
        inventory.setInventorySlotContents(0, input);
        return inventory;
    }
}
