package gregtech.api.gui.widgets;

import gregtech.Bootstrap;
import gregtech.api.gui.GuiTextures;
import gregtech.api.recipes.logic.RecipeFailure;
import gregtech.api.recipes.logic.RecipeFailureReason;

import net.minecraft.network.PacketBuffer;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class RecipeFailureWidgetTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void everyReasonHasAUniqueStableCodeAndBoundedRoundTrip() {
        Set<Integer> identifiers = new HashSet<>();
        for (RecipeFailureReason reason : RecipeFailureReason.values()) {
            assertTrue(identifiers.add(reason.getId()));
            RecipeFailure original = RecipeFailure.of(reason, Long.MAX_VALUE, 42);
            PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
            try {
                original.write(buffer);
                assertEquals(reason.hasConditions() ? 19 : 17, buffer.readableBytes());
                assertEquals(original, RecipeFailure.read(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void unknownCodesUseGenericRequirementsAndConsumeTheCompletePayload() {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            buffer.writeVarInt(999);
            buffer.writeLong(100);
            buffer.writeLong(1);
            assertEquals(RecipeFailureReason.REQUIREMENTS, RecipeFailure.read(buffer).getReason());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void widgetSynchronizesInitialFailureChangesAndRecoveryOnlyWhenNeeded() {
        AtomicReference<RecipeFailure> serverFailure = new AtomicReference<>(RecipeFailure.NONE);
        RecipeFailureWidget client = new RecipeFailureWidget(0, 0, GuiTextures.INDICATOR_NO_ENERGY,
                () -> { throw new AssertionError("Client rendering must not query the server recipe logic"); });
        int[] packets = { 0 };
        boolean[] visible = { false };
        RecipeFailureWidget server = new RecipeFailureWidget(0, 0, GuiTextures.INDICATOR_NO_ENERGY,
                serverFailure::get) {

            @Override
            protected void writeUpdateInfo(int id, Consumer<PacketBuffer> writer) {
                packets[0]++;
                PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
                try {
                    writer.accept(buffer);
                    if (id == 1) visible[0] = buffer.getBoolean(buffer.readerIndex());
                    client.readUpdateInfo(id, buffer);
                    assertEquals(0, buffer.readableBytes());
                } finally {
                    buffer.release();
                }
            }
        };
        server.detectAndSendChanges();
        assertEquals(0, packets[0]);
        assertTrue(client.getDisplayedFailure().isEmpty());
        assertFalse(visible[0]);
        serverFailure.set(RecipeFailure.of(RecipeFailureReason.TEMPERATURE, 1800, 1500));
        server.detectAndSendChanges();
        assertEquals(serverFailure.get(), client.getDisplayedFailure());
        assertEquals(2, packets[0]);
        assertTrue(visible[0]);
        for (int i = 0; i < 100; i++) server.detectAndSendChanges();
        assertEquals(2, packets[0]);
        serverFailure.set(RecipeFailure.of(RecipeFailureReason.NOT_ENOUGH_ENERGY));
        server.detectAndSendChanges();
        assertEquals(3, packets[0]);
        assertEquals(serverFailure.get(), client.getDisplayedFailure());
        serverFailure.set(RecipeFailure.NONE);
        server.detectAndSendChanges();
        assertEquals(5, packets[0]);
        assertTrue(client.getDisplayedFailure().isEmpty());
        assertFalse(visible[0]);
    }
}
