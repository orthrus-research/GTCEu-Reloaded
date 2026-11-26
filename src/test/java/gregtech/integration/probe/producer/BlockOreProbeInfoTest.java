package gregtech.integration.probe.producer;

import gregtech.Bootstrap;
import gregtech.integration.probe.PlainProbeRenderer;
import gregtech.integration.probe.ProbeLine;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BlockOreProbeInfoTest {

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    public void showsTheDropWithItsName() {
        List<ProbeLine> lines = new ArrayList<>();
        BlockOreProbeInfo.addLines(new ItemStack(Items.COAL), lines);

        assertEquals(List.of("<info>[gregtech.top.block_drops]:", "{minecraft:coal} (name)"),
                PlainProbeRenderer.render(lines));
    }
}
