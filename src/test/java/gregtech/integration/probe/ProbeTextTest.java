package gregtech.integration.probe;

import gregtech.api.recipes.logic.RecipeFailure;
import gregtech.api.recipes.logic.RecipeFailureReason;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.util.Constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProbeTextTest {

    private static String render(ProbeText text) {
        return text.render(PlainProbeRenderer.INSTANCE).replace('§', '&');
    }

    @Test
    public void rendersRunsInOrder() {
        ProbeText text = ProbeText.builder()
                .style(ProbeStyle.INFO)
                .translation("gregtech.top.energy_consumption")
                .literal(" ")
                .format(TextFormatting.RED)
                .quantity(32, "EU/t")
                .build();

        assertEquals("<info>[gregtech.top.energy_consumption] &c32 EU/t", render(text));
    }

    @Test
    public void rendersTranslationArgumentsBeforeTheirTranslation() {
        ProbeText argument = ProbeText.builder().format(TextFormatting.WHITE).quantity(12, "EU/t").build();
        ProbeText text = ProbeText.translation("gregtech.waila.progress_sec", 3, 5L, argument, "plain");

        assertEquals("[gregtech.waila.progress_sec: 3L, 5L, &f12 EU/t, plain]", render(text));
    }

    @Test
    public void refusesArgumentsNoProbeCanSend() {
        assertThrows(IllegalArgumentException.class, () -> ProbeText.translation("key", 1.5D));
    }

    @Test
    public void rendersRecipeFailuresThroughTheRenderer() {
        ProbeText text = ProbeText.recipeFailure(RecipeFailure.of(RecipeFailureReason.TEMPERATURE, 450, 360));

        assertEquals("[gregtech.recipe.failure.temperature: 450, 360]", render(text));
    }

    @Test
    public void emptyBuilderGivesEmptyText() {
        assertSame(ProbeText.EMPTY, ProbeText.builder().build());
        assertTrue(ProbeText.EMPTY.isEmpty());
        assertEquals("", render(ProbeText.EMPTY));
    }

    @Test
    public void survivesNBT() {
        ProbeText text = ProbeText.builder()
                .style(ProbeStyle.WARNING)
                .literal("literal ")
                .translation("key.with.arguments", ProbeText.quantity(10_000, "EU"), 7)
                .recipeFailure(RecipeFailure.of(RecipeFailureReason.FUSION_START, 160_000_000, 1))
                .recipeFailure(RecipeFailure.of(RecipeFailureReason.NO_RECIPE))
                .build();

        NBTTagList written = text.writeToNBT();
        NBTTagCompound wrapper = new NBTTagCompound();
        wrapper.setTag("Text", written);
        ProbeText read = ProbeText.readFromNBT(wrapper.getTagList("Text", Constants.NBT.TAG_COMPOUND));

        assertEquals(text, read);
        assertEquals(render(text), render(read));
    }
}
