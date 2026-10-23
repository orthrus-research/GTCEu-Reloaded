package gregtech.api.gui.widgets;

import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.resources.TextureArea;
import gregtech.api.recipes.logic.RecipeFailure;
import gregtech.api.recipes.logic.RecipeFailureReason;
import gregtech.api.util.LocalizationUtils;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;
import java.util.function.Supplier;

/** An existing-layout status icon whose reason is synchronized only while its GUI is open. */
public class RecipeFailureWidget extends ImageWidget {

    private final Supplier<RecipeFailure> failureSupplier;
    private final TextureArea energyTexture;
    private RecipeFailure failure = RecipeFailure.NONE;

    public RecipeFailureWidget(int x, int y, TextureArea energyTexture, Supplier<RecipeFailure> failureSupplier) {
        super(x, y, 18, 18, GuiTextures.INFO_ICON);
        this.failureSupplier = failureSupplier;
        this.energyTexture = energyTexture;
        setIgnoreColor(true);
        setPredicate(() -> !failure.isEmpty());
    }

    @Override
    public void detectAndSendChanges() {
        RecipeFailure current = failureSupplier.get();
        if (!current.equals(failure)) {
            this.failure = current;
            writeUpdateInfo(2, failure::write);
        }
        super.detectAndSendChanges();
    }

    @Override
    public void readUpdateInfo(int id, PacketBuffer buffer) {
        if (id == 2) {
            this.failure = RecipeFailure.read(buffer);
            RecipeFailureReason reason = failure.getReason();
            this.area = reason == RecipeFailureReason.NOT_ENOUGH_ENERGY ||
                    reason == RecipeFailureReason.NOT_ENOUGH_STEAM ? energyTexture : GuiTextures.INFO_ICON;
        } else {
            super.readUpdateInfo(id, buffer);
        }
    }

    public RecipeFailure getDisplayedFailure() {
        return failure;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawInForeground(int mouseX, int mouseY) {
        if (!failure.isEmpty() && isMouseOverElement(mouseX, mouseY)) {
            drawHoveringText(ItemStack.EMPTY, Collections.singletonList(failure.format(LocalizationUtils::format)),
                    300, mouseX, mouseY);
        }
    }
}
