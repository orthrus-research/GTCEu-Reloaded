package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import static gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferCodec.require;

/** Optional FC2.6.6-r wire format only; no optional addon classes are linked. */
public final class PatternBufferTokens {

    private PatternBufferTokens() {}

    public static boolean isBridgeAvailable() {
        ModContainer mod = Loader.instance().getIndexedModList().get("ae2fc");
        return mod != null && "2.6.6-r".equals(mod.getVersion());
    }

    public static boolean isUnsupportedBridgePresent() {
        return Loader.instance().getIndexedModList().containsKey("ae2fc") && !isBridgeAvailable();
    }

    /** Returns null for an ordinary item; recognized but invalid/unsupported fake inputs throw. */
    public static FluidStack fluid(ItemStack stack, boolean bridgeAvailable) {
        ResourceLocation id = stack.getItem().getRegistryName();
        if (id == null || !"ae2fc".equals(id.getNamespace())) return null;
        String path = id.getPath();
        if ("gas_drop".equals(path) || "gas_packet".equals(path))
            throw new IllegalArgumentException("Gas crafting tokens are unsupported");
        if (!"fluid_drop".equals(path) && !"fluid_packet".equals(path)) return null;
        require(Item.REGISTRY.getObject(id) == stack.getItem(), "Unregistered fluid token identity");
        require(bridgeAvailable, "Fluid crafting bridge unavailable");
        require(!stack.isEmpty() && stack.getCount() > 0 && stack.hasTagCompound(), "Malformed fluid token");
        NBTTagCompound tag = stack.getTagCompound();
        require(!tag.hasKey("DisplayOnly") || (tag.hasKey("DisplayOnly", 1) && !tag.getBoolean("DisplayOnly")),
                "Display-only fluid token");
        if ("fluid_packet".equals(path)) {
            require(stack.getCount() == 1 && tag.hasKey("FluidStack", 10), "Invalid fluid packet count/payload");
            FluidStack fluid = PatternBufferCodec.readFluid(tag.getCompoundTag("FluidStack"));
            require(fluid != null, "Empty fluid packet");
            return fluid;
        }
        require(tag.hasKey("Fluid", 8) && (!tag.hasKey("FluidTag") || tag.hasKey("FluidTag", 10)),
                "Invalid fluid drop fields");
        require(FluidRegistry.getFluid(tag.getString("Fluid")) != null, "Unknown fluid drop");
        FluidStack fluid = new FluidStack(FluidRegistry.getFluid(tag.getString("Fluid")), stack.getCount());
        if (tag.hasKey("FluidTag")) fluid.tag = tag.getCompoundTag("FluidTag").copy();
        return fluid;
    }
}
