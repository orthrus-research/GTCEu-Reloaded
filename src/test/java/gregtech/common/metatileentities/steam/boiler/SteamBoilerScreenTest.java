package gregtech.common.metatileentities.steam.boiler;

import gregtech.BootstrapExtension;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.mui.GTGuiTheme;
import gregtech.api.mui.sync.GTFluidSyncHandler;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.items.MetaItems;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static gregtech.api.util.GTUtility.gregtechId;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Each steam boiler, bronze and steel, keeps the screen it had, on ModularUI 2 in the theme of its pressure: the
 * temperature bar, its water, steam and fuel tanks, which show how full they are and are not clicked into, the slot
 * that takes only filled fluid containers with the one they come out of, its fuel slot or sun, and the player's
 * inventory.
 */
@ExtendWith(BootstrapExtension.class)
class SteamBoilerScreenTest {

    static Stream<SteamBoiler> boilers() {
        return Stream.of(false, true).flatMap(steel -> Stream.of(
                place(new SteamCoalBoiler(gregtechId("screen_test_coal_" + steel), steel)),
                place(new SteamLavaBoiler(gregtechId("screen_test_lava_" + steel), steel)),
                place(new SteamSolarBoiler(gregtechId("screen_test_solar_" + steel), steel))));
    }

    @ParameterizedTest
    @MethodSource("boilers")
    void keepsItsScreen(SteamBoiler boiler) {
        assertSame(boiler.isHighPressure ? GTGuiTheme.STEEL : GTGuiTheme.BRONZE, boiler.getUITheme());
        ModularPanel panel = boiler.buildUI(null, new PanelSyncManager(new ModularSyncManager(false), true),
                new UISettings());
        List<IWidget> widgets = new ArrayList<>();
        collect(panel, widgets);

        Set<IFluidTank> tanks = new HashSet<>();
        for (int i = 0; i < boiler.getImportFluids().getTanks(); i++) {
            tanks.add(boiler.getImportFluids().getTankAt(i).getDelegate());
        }
        for (int i = 0; i < boiler.getExportFluids().getTanks(); i++) {
            tanks.add(boiler.getExportFluids().getTankAt(i).getDelegate());
        }
        assertTrue(tanks.contains(boiler.waterFluidTank) && tanks.contains(boiler.steamFluidTank));
        Set<IFluidTank> shown = new HashSet<>();
        for (GTFluidSlot widget : ofType(widgets, GTFluidSlot.class)) {
            GTFluidSyncHandler syncHandler = (GTFluidSyncHandler) widget.getSyncHandler();
            assertFalse(syncHandler.canDrainSlot() || syncHandler.canFillSlot());
            assertFalse(syncHandler.drawAlwaysFull());
            assertTrue(shown.add(tankOf(syncHandler)));
        }
        assertEquals(tanks, shown);

        int player = 0;
        List<ModularSlot> containers = new ArrayList<>();
        List<ModularSlot> fuel = new ArrayList<>();
        for (ItemSlot widget : ofType(widgets, ItemSlot.class)) {
            if (!widget.isSynced()) player++;
            else if (widget.getSlot().getItemHandler() == boiler.getImportItems() ||
                    widget.getSlot().getItemHandler() == boiler.getExportItems())
                fuel.add(widget.getSlot());
            else containers.add(widget.getSlot());
        }
        assertEquals(36, player);

        assertEquals(2, containers.size());
        ModularSlot filled = containers.get(0);
        assertEquals(0, filled.getSlotIndex());
        assertTrue(filled.isCanPut() && filled.isCanTake() && filled.getSlotGroup() != null);
        IFluidHandlerItem cell = FluidUtil.getFluidHandler(MetaItems.FLUID_CELL.getStackForm());
        assertNotNull(cell);
        assertEquals(1000, cell.fill(Materials.Water.getFluid(1000), true));
        assertTrue(filled.getFilter().test(cell.getContainer()));
        assertFalse(filled.getFilter().test(MetaItems.FLUID_CELL.getStackForm()));
        assertFalse(filled.getFilter().test(new ItemStack(Blocks.DIRT)));
        ModularSlot emptied = containers.get(1);
        assertEquals(1, emptied.getSlotIndex());
        assertTrue(!emptied.isCanPut() && emptied.isCanTake());

        int bars = ofType(widgets, ProgressWidget.class).size();
        if (boiler instanceof SteamCoalBoiler) {
            // the fuel slot, the ash slot and the fuel left
            assertEquals(2, fuel.size());
            assertTrue(fuel.get(0).isCanPut() && fuel.get(0).getSlotGroup() != null);
            assertSame(boiler.getImportItems(), fuel.get(0).getItemHandler());
            assertTrue(!fuel.get(1).isCanPut() && fuel.get(1).isCanTake());
            assertSame(boiler.getExportItems(), fuel.get(1).getItemHandler());
            assertEquals(2, bars);
        } else {
            assertTrue(fuel.isEmpty());
            assertEquals(boiler instanceof SteamSolarBoiler ? 2 : 1, bars);
        }
    }

    private static SteamBoiler place(SteamBoiler boiler) {
        MetaTileEntityHolder holder = new MetaTileEntityHolder();
        SteamBoiler placed = (SteamBoiler) holder.setMetaTileEntity(boiler);
        holder.setWorld(DummyWorld.INSTANCE);
        return placed;
    }

    private static IFluidTank tankOf(GTFluidSyncHandler syncHandler) {
        try {
            Field field = GTFluidSyncHandler.class.getDeclaredField("tank");
            field.setAccessible(true);
            return (IFluidTank) field.get(syncHandler);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void collect(IWidget widget, List<IWidget> widgets) {
        widgets.add(widget);
        for (IWidget child : widget.getChildren()) collect(child, widgets);
    }

    private static <T> List<T> ofType(List<IWidget> widgets, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (IWidget widget : widgets) {
            if (type.isInstance(widget)) found.add(type.cast(widget));
        }
        return found;
    }
}
