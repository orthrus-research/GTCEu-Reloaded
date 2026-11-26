package gregtech.api.fluids;

import gregtech.api.fluids.attribute.AttributedFluid;
import gregtech.api.fluids.attribute.FluidAttribute;
import gregtech.api.unification.material.Material;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

public class GTFluid extends Fluid implements AttributedFluid {

    private final Collection<FluidAttribute> attributes = new ObjectLinkedOpenHashSet<>();
    private final FluidState state;

    public GTFluid(@NotNull String fluidName, ResourceLocation still, ResourceLocation flowing,
                   @NotNull FluidState state) {
        super(fluidName, still, flowing);
        setGaseous(state != FluidState.LIQUID);
        this.state = state;
    }

    @Override
    public @NotNull FluidState getState() {
        return state;
    }

    @Override
    public @NotNull @Unmodifiable Collection<FluidAttribute> getAttributes() {
        return attributes;
    }

    @Override
    public void addAttribute(@NotNull FluidAttribute attribute) {
        attributes.add(attribute);
    }

    public static class GTMaterialFluid extends GTFluid {

        private final Material material;
        private final String translationKey;

        public GTMaterialFluid(@NotNull String fluidName, ResourceLocation still, ResourceLocation flowing,
                               @NotNull FluidState state, @Nullable String translationKey, @NotNull Material material) {
            super(fluidName, still, flowing, state);
            this.material = material;
            this.translationKey = translationKey;
        }

        public @NotNull Material getMaterial() {
            return this.material;
        }

        /**
         * @return the translation key this fluid's name formats its material's name into, or {@code null} when the
         *         fluid is named by its material's name alone
         */
        public @Nullable String getNameTranslationKey() {
            return this.translationKey;
        }

        public @NotNull IKey getLocalizedKey() {
            IKey localizedName;
            String customMaterialTranslation = "fluid." + material.getUnlocalizedName();

            if (net.minecraft.util.text.translation.I18n.canTranslate(customMaterialTranslation)) {
                localizedName = IKey.lang(customMaterialTranslation);
            } else {
                localizedName = IKey.lang(material.getUnlocalizedName());
            }

            if (translationKey != null) {
                return IKey.lang(translationKey, localizedName);
            }

            return localizedName;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public String getLocalizedName(FluidStack stack) {
            return getLocalizedKey().get();
        }
    }
}
