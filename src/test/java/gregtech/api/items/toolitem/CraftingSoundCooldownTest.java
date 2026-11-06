package gregtech.api.items.toolitem;

import gregtech.Bootstrap;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.ConfigHolder;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;

import com.mojang.authlib.GameProfile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class CraftingSoundCooldownTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void cooldownUsesElapsedTimeAndSurvivesClockWrap() {
        var player = player(new DummyWorld());
        var sound = sound("clock");
        long start = Long.MAX_VALUE - 500_000_000L;
        assertTrue(CraftingSoundCooldown.acquire(player, sound, start));
        assertFalse(CraftingSoundCooldown.acquire(player, sound, start + 999_999_999L));
        assertTrue(CraftingSoundCooldown.acquire(player, sound, start + 1_000_000_000L));
        assertFalse(CraftingSoundCooldown.acquire(player, sound, start + 1_000_000_001L));
    }

    @Test
    void playersAndSoundsHaveIndependentCooldowns() {
        var first = player(new DummyWorld());
        var second = player(new DummyWorld());
        var hammer = sound("hammer");
        var saw = sound("saw");
        assertTrue(CraftingSoundCooldown.acquire(first, hammer, 0));
        assertTrue(CraftingSoundCooldown.acquire(second, hammer, 0));
        assertTrue(CraftingSoundCooldown.acquire(first, saw, 0));
        assertFalse(CraftingSoundCooldown.acquire(first, hammer, 1));
    }

    @Test
    void simultaneousRequestsOnlyAcquireOnce() throws Exception {
        var player = player(new DummyWorld());
        var sound = sound("parallel");
        try (var executor = Executors.newFixedThreadPool(4)) {
            var requests = IntStream.range(0, 32)
                    .mapToObj(i -> executor.submit(() -> CraftingSoundCooldown.acquire(player, sound, 0)))
                    .toList();
            int accepted = 0;
            for (var request : requests) if (request.get()) accepted++;
            assertEquals(1, accepted);
        }
    }

    @Test
    void soundBroadcastDoesNotChangeItemDataAndHonorsDisable() {
        var broadcasts = new AtomicInteger();
        var world = new DummyWorld() {

            @Override
            public void playSound(EntityPlayer excluded, double x, double y, double z, SoundEvent sound,
                                  SoundCategory category, float volume, float pitch) {
                assertNull(excluded);
                assertEquals(SoundCategory.PLAYERS, category);
                broadcasts.incrementAndGet();
            }
        };
        var player = player(world);
        var tool = ItemGTTool.Builder.of("sound_test", "hammer")
                .toolStats(builder -> builder).sound(sound("broadcast")).build();
        var stack = tool.get(Materials.Iron);
        ToolHelper.getToolTag(stack).setInteger("LastCraftingUse", 12345);
        var before = stack.serializeNBT().copy();
        boolean enabled = ConfigHolder.client.toolCraftingSounds;
        try {
            ConfigHolder.client.toolCraftingSounds = false;
            tool.playCraftingSound(player, stack);
            assertEquals(0, broadcasts.get());
            ConfigHolder.client.toolCraftingSounds = true;
            tool.playCraftingSound(null, stack);
            tool.playCraftingSound(player, stack);
            tool.playCraftingSound(player, stack.copy());
            assertEquals(1, broadcasts.get());
            assertEquals(before, stack.serializeNBT());
        } finally {
            ConfigHolder.client.toolCraftingSounds = enabled;
        }
    }

    private static SoundEvent sound(String name) {
        return new SoundEvent(new ResourceLocation("sound_test", name));
    }

    private static EntityPlayer player(DummyWorld world) {
        return new EntityPlayer(world, new GameProfile(UUID.randomUUID(), "sound_test")) {

            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
    }
}
