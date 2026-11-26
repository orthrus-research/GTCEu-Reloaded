package gregtech.api.gui.widgets;

import gregtech.Bootstrap;
import gregtech.api.fluids.GTFluid;
import gregtech.api.unification.material.Materials;

import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TankWidgetFluidNameTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void materialFluidIsNamedByItsMaterialUnderItsTranslationKey() {
        FluidStack copper = Materials.Copper.getFluid(1);
        GTFluid.GTMaterialFluid fluid = assertInstanceOf(GTFluid.GTMaterialFluid.class, copper.getFluid());
        assertNotNull(fluid.getNameTranslationKey());

        TextComponentTranslation name = TankWidget.fluidTextComponent(copper);
        assertEquals(fluid.getNameTranslationKey(), name.getKey());
        assertEquals(1, name.getFormatArgs().length);
        TextComponentTranslation materialName = assertInstanceOf(TextComponentTranslation.class,
                name.getFormatArgs()[0]);
        assertEquals(Materials.Copper.getUnlocalizedName(), materialName.getKey());
        assertEquals(0, materialName.getFormatArgs().length);
    }

    @Test
    void otherFluidsAreNamedByTheirOwnKey() {
        FluidStack water = new FluidStack(FluidRegistry.WATER, 1);
        TextComponentTranslation name = TankWidget.fluidTextComponent(water);
        assertEquals(water.getUnlocalizedName(), name.getKey());
        assertEquals(0, name.getFormatArgs().length);
    }

    @Test
    void phantomTankNamesItsPhantomFluidWhileEmpty() {
        FluidStack copper = Materials.Copper.getFluid(1);
        PhantomTankWidget locked = new PhantomTankWidget(new FluidTank(1000), 0, 0, 18, 18, () -> copper, fs -> {});
        assertEquals(TankWidget.fluidTextComponent(copper), locked.getFluidTextComponent());

        PhantomTankWidget unlocked = new PhantomTankWidget(new FluidTank(1000), 0, 0, 18, 18, () -> null, fs -> {});
        assertNull(unlocked.getFluidTextComponent());
        assertNull(new TankWidget(new FluidTank(1000), 0, 0, 18, 18).getFluidTextComponent());
    }
}
