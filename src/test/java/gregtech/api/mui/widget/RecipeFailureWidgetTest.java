package gregtech.api.mui.widget;

import gregtech.BootstrapExtension;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.logic.RecipeFailure;
import gregtech.api.recipes.logic.RecipeFailureReason;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The recipe failure indicator receives the server's reason when the screen opens and whenever it changes, and shows
 * the machine's own icon for missing energy or steam.
 */
@ExtendWith(BootstrapExtension.class)
class RecipeFailureWidgetTest {

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
    void reasonsReachTheScreenWhenOpenedAndWhenTheyChange() throws Exception {
        AtomicReference<RecipeFailure> serverFailure = new AtomicReference<>(RecipeFailure.NONE);
        GenericSyncValue<RecipeFailure> server = RecipeFailureWidget.createSyncValue(serverFailure::get);
        GenericSyncValue<RecipeFailure> client = RecipeFailureWidget.createSyncValue(() -> RecipeFailure.NONE);

        // the screen opens: the current reason is sent
        assertTrue(server.updateCacheFromSource(true));
        send(server, client);
        assertSame(RecipeFailure.NONE, client.getValue());

        // nothing changed: nothing is sent
        assertFalse(server.updateCacheFromSource(false));

        RecipeFailure energy = RecipeFailure.of(RecipeFailureReason.NOT_ENOUGH_ENERGY, 512, 32);
        serverFailure.set(energy);
        assertTrue(server.updateCacheFromSource(false));
        send(server, client);
        assertEquals(energy, client.getValue());

        // an equal reason made again is not sent again
        serverFailure.set(RecipeFailure.of(RecipeFailureReason.NOT_ENOUGH_ENERGY, 512, 32));
        assertFalse(server.updateCacheFromSource(false));

        // the machine recovers
        serverFailure.set(RecipeFailure.NONE);
        assertTrue(server.updateCacheFromSource(false));
        send(server, client);
        assertTrue(client.getValue().isEmpty());
    }

    @Test
    void missingEnergyAndSteamShowTheMachinesIconAndOtherReasonsTheInformationIcon() {
        AtomicReference<RecipeFailure> failure = new AtomicReference<>(RecipeFailure.NONE);
        RecipeFailureWidget widget = new RecipeFailureWidget(failure::get, GTGuiTextures.INDICATOR_NO_STEAM_BRONZE);
        for (RecipeFailureReason reason : RecipeFailureReason.values()) {
            failure.set(RecipeFailure.of(reason));
            boolean energy = reason == RecipeFailureReason.NOT_ENOUGH_ENERGY ||
                    reason == RecipeFailureReason.NOT_ENOUGH_STEAM;
            assertSame(energy ? GTGuiTextures.INDICATOR_NO_STEAM_BRONZE : GTGuiTextures.INFO_ICON, widget.getIcon(),
                    reason.toString());
        }
    }

    private static void send(GenericSyncValue<RecipeFailure> from,
                             GenericSyncValue<RecipeFailure> to) throws Exception {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            from.write(buffer);
            to.read(buffer);
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
