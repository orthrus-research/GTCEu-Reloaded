package gregtech.client.renderer;

import dev.orthlib.api.render.BloomKindDefinition;
import dev.orthlib.api.render.RenderKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The bloom kinds keep GTCEu's composite rule: a kind's glow over a background of brightness Y is weighed by
 * strength * (base + low + (1 - Y) * (high - low)).
 */
class GTBloomKindTest {

    private static final RenderKey KEY = new RenderKey("gregtech:test");
    private static final float EPSILON = 1e-6f;

    @Test
    void emissiveTextureDefaultsPassThrough() {
        BloomKindDefinition definition = GTBloomKind.definition(KEY, 1.5, 0.1, 0.2, 0.5, 1);
        assertEquals(1.5f, definition.strength());
        assertEquals(0.1f, definition.base());
        assertEquals(0.2f, definition.lowLimit());
        assertEquals(0.5f, definition.highLimit());
        assertEquals(1f, definition.radius());
    }

    @Test
    void fusionReactorLimitsAboveOneKeepTheirWeights() {
        assertSameWeights(1.5, 0, 0.3, 1.3, GTBloomKind.definition(KEY, 1.5, 0, 0.3, 1.3, 1));
    }

    @Test
    void heatEffectLimitsAboveOneKeepTheirWeights() {
        assertSameWeights(1.1, 0, 0.6, 1.4, GTBloomKind.definition(KEY, 1.1, 0, 0.6, 1.4, 1));
    }

    @Test
    void theSettingsDefaultsDefineTheKinds() {
        assertSameWeights(1.5, 0.1, 0.2, 0.5, GTBloomKind.EMISSIVE_TEXTURES.settings());
        assertSameWeights(1.5, 0, 0.3, 1.3, GTBloomKind.FUSION_REACTOR.settings());
        assertSameWeights(1.1, 0, 0.6, 1.4, GTBloomKind.HEAT_EFFECT.settings());
        assertEquals(1f, GTBloomKind.EMISSIVE_TEXTURES.settings().radius());
        assertEquals(GTBloomKind.FUSION_REACTOR.key(), GTBloomKind.FUSION_REACTOR.settings().key());
    }

    @Test
    void settingsOutsideTheirRangesAreBroughtIntoThem() {
        BloomKindDefinition undefined = GTBloomKind.definition(KEY, Double.NaN, -1, Double.NEGATIVE_INFINITY, -2, 0);
        assertEquals(0f, undefined.strength());
        assertEquals(0f, undefined.base());
        assertEquals(0f, undefined.lowLimit());
        assertEquals(0f, undefined.highLimit());
        assertEquals(GTBloomKind.MIN_RADIUS, undefined.radius());

        BloomKindDefinition crossed = GTBloomKind.definition(KEY, 1, 0, 0.7, 0.4, 10);
        assertEquals(0.7f, crossed.lowLimit(), "a high limit below the low one weighs every brightness alike");
        assertEquals(0.7f, crossed.highLimit());
        assertEquals(GTBloomKind.MAX_RADIUS, crossed.radius());

        BloomKindDefinition huge = GTBloomKind.definition(KEY, 1e12, 0, 0, 1, Double.NaN);
        assertEquals(GTBloomKind.MAX_SETTING, huge.strength());
        assertEquals(1f, huge.radius());
    }

    private static void assertSameWeights(double strength, double base, double low, double high,
                                          BloomKindDefinition definition) {
        assertTrue(definition.highLimit() <= 1);
        for (double brightness = 0; brightness <= 1; brightness += 0.125) {
            double expected = strength * (base + low + (1 - brightness) * (high - low));
            double actual = definition.strength() * (definition.base() + definition.lowLimit() +
                    (1 - brightness) * (definition.highLimit() - definition.lowLimit()));
            assertEquals(expected, actual, EPSILON, "weight over brightness " + brightness);
        }
    }
}
