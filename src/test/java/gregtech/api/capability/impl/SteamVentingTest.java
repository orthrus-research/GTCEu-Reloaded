package gregtech.api.capability.impl;

import gregtech.Bootstrap;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SteamVentingTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void steamLeavesJustOutsideTheMiddleOfTheSide() {
        BlockPos machine = new BlockPos(10, 64, -3);
        Vec3d east = SteamVenting.getVentPosition(machine, EnumFacing.EAST);
        assertEquals(10 + 0.5 + 0.6, east.x, 1e-9);
        assertEquals(64.5, east.y, 1e-9);
        assertEquals(-2.5, east.z, 1e-9);
        Vec3d down = SteamVenting.getVentPosition(machine, EnumFacing.DOWN);
        assertEquals(10.5, down.x, 1e-9);
        assertEquals(64 + 0.5 - 0.6, down.y, 1e-9);
        assertEquals(-2.5, down.z, 1e-9);
    }

    @Test
    void aCloudHasSevenToNineParticles() {
        Set<Integer> sizes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            int size = SteamVenting.getCloudSize();
            assertTrue(size >= 7 && size <= 9, () -> "Cloud of " + size);
            sizes.add(size);
        }
        assertEquals(Set.of(7, 8, 9), sizes);
    }
}
