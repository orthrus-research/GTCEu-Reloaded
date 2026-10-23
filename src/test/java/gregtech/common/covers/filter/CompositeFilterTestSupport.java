package gregtech.common.covers.filter;

import gregtech.Bootstrap;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.util.oreglob.OreGlob;
import gregtech.api.util.oreglob.OreGlobCompiler;
import gregtech.common.covers.filter.oreglob.impl.OreGlobParser;
import gregtech.common.items.MetaItem1;
import gregtech.common.items.MetaItems;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Scoped subitems: the shared test bootstrap does not run the production registry event. */
public final class CompositeFilterTestSupport implements AutoCloseable {

    private final MetaItem<?>.MetaValueItem[] previous;
    private final OreGlobCompiler previousCompiler;

    public CompositeFilterTestSupport() throws ReflectiveOperationException {
        Bootstrap.perform();
        this.previous = new MetaItem<?>.MetaValueItem[] { MetaItems.ITEM_FILTER, MetaItems.FLUID_FILTER,
                MetaItems.ORE_DICTIONARY_FILTER, MetaItems.SMART_FILTER,
                MetaItems.COMPOSITE_ITEM_FILTER, MetaItems.COMPOSITE_FLUID_FILTER };
        MetaItem<?> item = MetaItems.ITEMS.get(0);
        if (ForgeRegistries.ITEMS.getValue(item.getRegistryName()) != item) ForgeRegistries.ITEMS.register(item);
        MetaItems.ITEM_FILTER = leaf(item, 291, "item_filter", SimpleItemFilter::new);
        MetaItems.FLUID_FILTER = leaf(item, 290, "fluid_filter", SimpleFluidFilter::new);
        MetaItems.ORE_DICTIONARY_FILTER = leaf(item, 292, "ore_dictionary_filter", OreDictionaryItemFilter::new);
        MetaItems.SMART_FILTER = leaf(item, 293, "smart_item_filter", SmartItemFilter::new);
        if (item.getItem("composite_item_filter") == null && item.getItem("composite_fluid_filter") == null) {
            Method registration = MetaItem1.class.getDeclaredMethod("registerCompositeFilters");
            registration.setAccessible(true);
            registration.invoke(item);
        } else {
            MetaItems.COMPOSITE_ITEM_FILTER = item.getItem("composite_item_filter");
            MetaItems.COMPOSITE_FLUID_FILTER = item.getItem("composite_fluid_filter");
        }
        Field compiler = OreGlob.class.getDeclaredField("compiler");
        compiler.setAccessible(true);
        this.previousCompiler = (OreGlobCompiler) compiler.get(null);
        OreGlob.setCompiler((expression, ignoreCase) -> new OreGlobParser(expression, ignoreCase).compile());
    }

    private static MetaItem<?>.MetaValueItem leaf(MetaItem<?> item, int id, String name, IFilter.Factory factory) {
        MetaItem<?>.MetaValueItem value = item.getItem(name);
        return value == null ? item.addItem(id, name).addComponents(factory) : value;
    }

    @Override
    public void close() throws ReflectiveOperationException {
        MetaItems.ITEM_FILTER = previous[0];
        MetaItems.FLUID_FILTER = previous[1];
        MetaItems.ORE_DICTIONARY_FILTER = previous[2];
        MetaItems.SMART_FILTER = previous[3];
        MetaItems.COMPOSITE_ITEM_FILTER = previous[4];
        MetaItems.COMPOSITE_FLUID_FILTER = previous[5];
        Field compiler = OreGlob.class.getDeclaredField("compiler");
        compiler.setAccessible(true);
        compiler.set(null, previousCompiler);
    }

    public static ItemStack itemLeaf(ItemStack criterion) {
        ItemStack stack = MetaItems.ITEM_FILTER.getStackForm();
        SimpleItemFilter filter = new SimpleItemFilter(stack);
        filter.setMaxTransferSize(Integer.MAX_VALUE);
        filter.getFilterReader().setStackInSlot(0, criterion.copy());
        return stack;
    }

    public static ItemStack fluidLeaf(FluidStack criterion) {
        ItemStack stack = MetaItems.FLUID_FILTER.getStackForm();
        SimpleFluidFilter filter = new SimpleFluidFilter(stack);
        filter.getFilterReader().getFluidTank(0).setFluid(criterion.copy());
        return stack;
    }

    public static CompositeFilter editable(IFilter.FilterType type, ItemStack... children) {
        ItemStack parent = (type == IFilter.FilterType.ITEM ? MetaItems.COMPOSITE_ITEM_FILTER :
                MetaItems.COMPOSITE_FLUID_FILTER).getStackForm();
        CompositeFilter filter = new CompositeFilter(parent, type);
        filter.getInventory().setOwner(() -> true);
        for (int i = 0; i < children.length; i++) filter.getInventory().insertItem(i, children[i], false);
        return filter;
    }

    public static ItemStack rawParent(IFilter.FilterType type, ItemStack... children) {
        ItemStack parent = (type == IFilter.FilterType.ITEM ? MetaItems.COMPOSITE_ITEM_FILTER :
                MetaItems.COMPOSITE_FLUID_FILTER).getStackForm();
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < CompositeFilterInventory.SIZE; i++) {
            list.appendTag(i < children.length ? children[i].writeToNBT(new NBTTagCompound()) : new NBTTagCompound());
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag(CompositeFilterInventory.ITEMS_KEY, list);
        parent.setTagCompound(tag);
        return parent;
    }
}
