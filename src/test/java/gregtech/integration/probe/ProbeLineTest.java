package gregtech.integration.probe;

import gregtech.Bootstrap;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.util.Constants;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProbeLineTest {

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    public void sideMasksFollowTheFilter() {
        int sides = ProbeLine.sides(facing -> facing == EnumFacing.NORTH || facing == EnumFacing.UP);
        ProbeLine line = ProbeLine.text(ProbeText.literal("x")).onSides(sides);

        assertTrue(line.isVisibleFrom(EnumFacing.NORTH));
        assertTrue(line.isVisibleFrom(EnumFacing.UP));
        assertFalse(line.isVisibleFrom(EnumFacing.SOUTH));
        assertFalse(line.isVisibleFrom(null));
    }

    @Test
    public void linesForEverySideShowWithoutASide() {
        ProbeLine line = ProbeLine.text(ProbeText.literal("x"));

        assertEquals(ProbeLine.ALL_SIDES, line.getSides());
        assertTrue(line.isVisibleFrom(null));
        assertTrue(line.isVisibleFrom(EnumFacing.DOWN));
    }

    @Test
    public void linesSurviveNBT() {
        ProbeText fallback = ProbeText.translation("gregtech.waila.energy_stored", "1", "2");
        ProbeText wrench = ProbeText.translation("gregtech.top.maintenance.wrench");
        List<ProbeLine> lines = List.of(
                ProbeLine.text(ProbeText.builder().style(ProbeStyle.OK).literal("formed").build()),
                ProbeLine.item(new ItemStack(Items.STICK, 2), wrench),
                ProbeLine.namedItem(new ItemStack(Items.IRON_INGOT)),
                ProbeLine.progress(new ProbeProgress(1, 2, "EU", true, true, 0xFFEEE600, fallback)),
                ProbeLine.text(ProbeText.literal("west")).onSides(1 << EnumFacing.WEST.getIndex()));

        NBTTagCompound wrapper = new NBTTagCompound();
        wrapper.setTag("Lines", ProbeLine.writeList(lines));
        List<ProbeLine> read = ProbeLine.readList(wrapper.getTagList("Lines", Constants.NBT.TAG_COMPOUND));

        List<String> everySide = List.of("<ok>formed", "{minecraft:stick} [gregtech.top.maintenance.wrench]",
                "{minecraft:iron_ingot} (name)", "bar 1/2 EU | [gregtech.waila.energy_stored: 1, 2]");
        assertEquals(lines, read);
        assertEquals(everySide, PlainProbeRenderer.render(read));
        assertEquals("west", PlainProbeRenderer.render(read, EnumFacing.WEST).get(4));
    }
}
