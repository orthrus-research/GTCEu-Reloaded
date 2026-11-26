package gregtech.common.mui.widget.prospector;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.stack.MaterialStack;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.FluidDrawable;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/** The resources the prospector found so far; selecting one shows only it on the map. */
public class ProspectingOreListWidget extends ListWidget<IWidget, ProspectingOreListWidget> {

    private static final IDrawable SELECTED = new Rectangle().setColor(0x4BFFFFFF);
    private static final int ROW_HEIGHT = 18;

    /** Resource name to its display name. */
    public final Map<String, String> ores = new HashMap<>();
    private final TreeSet<String> sorted = new TreeSet<>();
    private final Map<String, ItemDrawable> icons = new HashMap<>();
    private String selected = ProspectingTexture.SELECTED_ALL;
    private Consumer<String> onSelected;
    private int tickCounter;

    public ProspectingOreListWidget() {
        child(row(ProspectingTexture.SELECTED_ALL, GTGuiTextures.RECIPE_LOCK,
                IKey.lang("terminal.prospector.list").color(0xFFFFFFFF)));
    }

    public ProspectingOreListWidget onSelected(Consumer<String> onSelected) {
        this.onSelected = onSelected;
        return this;
    }

    public void setSelected(String name) {
        if (!name.equals(ProspectingTexture.SELECTED_ALL) && !ores.containsKey(name)) return;
        this.selected = name;
        if (onSelected != null) {
            onSelected.accept(name);
        }
    }

    public void addOres(Set<String> names, ProspectorMode mode) {
        for (String name : new TreeSet<>(names)) {
            if (ores.containsKey(name)) continue;
            switch (mode) {
                case ORE -> addOre(name);
                case FLUID -> addFluid(name);
            }
        }
    }

    private void addOre(String name) {
        ItemStack itemStack = OreDictUnifier.get(name);
        if (itemStack == null || itemStack.isEmpty()) return;
        MaterialStack materialStack = OreDictUnifier.getMaterial(itemStack);
        int color = (materialStack == null ? name.hashCode() : materialStack.material.getMaterialRGB()) | 0xFF000000;
        ItemDrawable icon = new ItemDrawable(itemStack);
        icons.put(name, icon);
        insert(name, itemStack.getDisplayName(), icon, color);
    }

    private void addFluid(String name) {
        FluidStack fluidStack = FluidRegistry.getFluidStack(name, 1);
        if (fluidStack == null) return;
        insert(name, fluidStack.getLocalizedName(), new FluidDrawable(fluidStack),
                getFluidColor(fluidStack.getFluid()) | 0xFF000000);
    }

    private void insert(String name, String displayName, IDrawable icon, int color) {
        ores.put(name, displayName);
        sorted.add(name);
        // the first row lists everything; the others follow in name order
        addChild(row(name, icon, IKey.str(displayName).color(color)), 1 + sorted.headSet(name).size());
    }

    private IWidget row(String name, IDrawable icon, IKey label) {
        return new ButtonWidget<>()
                .widthRel(1f)
                .height(ROW_HEIGHT)
                .background(new DynamicDrawable(() -> name.equals(selected) ? SELECTED : IDrawable.EMPTY))
                .disableHoverBackground()
                .onMousePressed(mouseButton -> {
                    if (!name.equals(selected)) {
                        setSelected(name);
                    }
                    return true;
                })
                .child(Flow.row()
                        .coverChildren()
                        .left(0)
                        .child(icon.asWidget().size(16).margin(1))
                        .child(label.asWidget().shadow(false).marginLeft(2)));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        // show each name's items in turn
        if (++tickCounter % 20 != 0) return;
        icons.forEach((name, icon) -> {
            List<ItemStack> list = OreDictUnifier.getAllWithOreDictionaryName(name);
            if (!list.isEmpty()) {
                icon.setItem(list.get(Math.floorMod(tickCounter / 20, list.size())));
            }
        });
    }

    public static int getFluidColor(Fluid fluid) {
        if (fluid == FluidRegistry.WATER) {
            return 3183823;
        } else {
            return fluid == FluidRegistry.LAVA ? 16766720 : fluid.getColor();
        }
    }
}
