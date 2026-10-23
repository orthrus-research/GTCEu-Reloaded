package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import gregtech.Bootstrap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import org.objectweb.asm.Type;

import java.util.Collections;

/** Fixtures do not install FC: only its documented wire identities exist in this test JVM. */
public final class PatternBufferTestSupport {

    private PatternBufferTestSupport() {}

    public static void bootstrap() {
        Bootstrap.perform();
        // Standalone fixtures do not run Forge's capability injection/registration phase.
        ASMDataTable capabilities = new ASMDataTable();
        capabilities.addASMData(null, CapabilityInject.class.getName(), CapabilityItemHandler.class.getName(),
                "ITEM_HANDLER_CAPABILITY", Collections.singletonMap("value", Type.getType(IItemHandler.class)));
        capabilities.addASMData(null, CapabilityInject.class.getName(), CapabilityFluidHandler.class.getName(),
                "FLUID_HANDLER_CAPABILITY", Collections.singletonMap("value", Type.getType(IFluidHandler.class)));
        capabilities.addASMData(null, CapabilityInject.class.getName(), CapabilityFluidHandler.class.getName(),
                "FLUID_HANDLER_ITEM_CAPABILITY",
                Collections.singletonMap("value", Type.getType(IFluidHandlerItem.class)));
        CapabilityManager.INSTANCE.injectCapabilities(capabilities);
        if (CapabilityItemHandler.ITEM_HANDLER_CAPABILITY == null) CapabilityItemHandler.register();
        if (CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY == null) CapabilityFluidHandler.register();
    }

    public static ItemStack pattern() {
        return new ItemStack(Items.PAPER);
    }

    public static PatternBufferStorage storage(Runnable changed) {
        PatternBufferStorage storage = new PatternBufferStorage(changed, stack -> stack.getItem() == Items.PAPER);
        storage.getPatterns().insertItem(0, pattern(), false);
        return storage;
    }

    public static InventoryCrafting table(ItemStack... stacks) {
        InventoryCrafting table = new InventoryCrafting(new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer player) {
                return true;
            }
        }, 10, 10);
        for (int i = 0; i < stacks.length; i++) table.setInventorySlotContents(i, stacks[i].copy());
        return table;
    }

    public static ICraftingPatternDetails details(ItemStack... inputs) {
        IAEItemStack[] ae = new IAEItemStack[inputs.length];
        for (int i = 0; i < inputs.length; i++) ae[i] = AEItemStack.fromItemStack(inputs[i]);
        return details(ae);
    }

    public static ICraftingPatternDetails details(IAEItemStack[] inputs) {
        return new ICraftingPatternDetails() {

            @Override
            public ItemStack getPattern() {
                return pattern();
            }

            @Override
            public boolean isValidItemForSlot(int slot, ItemStack stack, World world) {
                return false;
            }

            @Override
            public boolean isCraftable() {
                return false;
            }

            @Override
            public IAEItemStack[] getInputs() {
                return inputs;
            }

            @Override
            public IAEItemStack[] getCondensedInputs() {
                return inputs;
            }

            @Override
            public IAEItemStack[] getOutputs() {
                return new IAEItemStack[] { AEItemStack.fromItemStack(new ItemStack(Items.DIAMOND)) };
            }

            @Override
            public IAEItemStack[] getCondensedOutputs() {
                return getOutputs();
            }

            @Override
            public boolean canSubstitute() {
                return false;
            }

            @Override
            public ItemStack getOutput(InventoryCrafting table, World world) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getPriority() {
                return 0;
            }

            @Override
            public void setPriority(int priority) {}
        };
    }

    public static Item token(String name) {
        ResourceLocation id = new ResourceLocation("ae2fc", name);
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            item = new Item().setRegistryName(id);
            ForgeRegistries.ITEMS.register(item);
        }
        return item;
    }

    public static ItemStack drop(int amount, String marker) {
        ItemStack stack = new ItemStack(token("fluid_drop"), amount);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Fluid", FluidRegistry.WATER.getName());
        NBTTagCompound fluidTag = new NBTTagCompound();
        fluidTag.setString("batch", marker);
        tag.setTag("FluidTag", fluidTag);
        stack.setTagCompound(tag);
        return stack;
    }
}
