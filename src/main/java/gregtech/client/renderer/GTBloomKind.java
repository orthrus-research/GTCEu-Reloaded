package gregtech.client.renderer;

import gregtech.api.util.GTLog;
import gregtech.common.ConfigHolder;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import dev.orthlib.api.render.Admission;
import dev.orthlib.api.render.BloomKind;
import dev.orthlib.api.render.BloomKindDefinition;
import dev.orthlib.api.render.ContributionShape;
import dev.orthlib.api.render.RenderKey;
import dev.orthlib.client.render.EmissiveSource;
import dev.orthlib.client.render.RenderInterface;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * GTCEu's bloom kinds, defined in OrthLib's render interface from the shader settings. A renderer that provides the
 * interface computes the glow of each kind's geometry together, with the kind's strength, base brightness, brightness
 * limits and radius; it owns the technique. Without such a renderer nothing glows.
 * <p>
 * Kinds, and the contributions made through {@link #contribute}, are changed on the client thread only.
 */
@SideOnly(Side.CLIENT)
public enum GTBloomKind {

    /** The glow of the emissive layer, from the shader settings' strength, brightness and step. */
    EMISSIVE_TEXTURES("gregtech:emissive_textures"),
    /** The glow of a working fusion reactor's ring, from the fusion reactor settings. */
    FUSION_REACTOR("gregtech:fusion_reactor"),
    /** The glow of overheating cables, from the heat effect settings. */
    HEAT_EFFECT("gregtech:heat_effect");

    /** The range of a kind's radius multiplier, within OrthLib's bounds: above 0, at most 4. */
    static final float MIN_RADIUS = 1 / 64f;
    static final float MAX_RADIUS = 4;
    /** Strengths and brightnesses above this count as this: far past anything a glow shows. */
    static final float MAX_SETTING = 1024;

    private final RenderKey key;
    // The live definition and its handle; the contributions made through it, to make them again with a new one.
    @Nullable
    private BloomKind kind;
    @Nullable
    private BloomKindDefinition definition;
    private final Set<EmissiveContribution> contributions = new LinkedHashSet<>();

    GTBloomKind(String key) {
        this.key = new RenderKey(key);
    }

    public RenderKey key() {
        return this.key;
    }

    /**
     * Submits emissive geometry of this kind: the renderer draws what the source writes into the scene and into this
     * kind's glow, and without one OrthLib draws it plainly, without glow. Client thread.
     *
     * @param shape  the source's vertex layout, primitive and blend
     * @param source the geometry
     * @return the contribution, which its owner closes when the geometry goes away
     */
    public EmissiveContribution contribute(ContributionShape shape, EmissiveSource source) {
        this.contributions.removeIf(contribution -> !contribution.isLive());
        EmissiveContribution contribution = new EmissiveContribution(this, shape, source);
        contribution.attach(this.kind);
        if (contribution.isLive()) {
            this.contributions.add(contribution);
        }
        return contribution;
    }

    void forget(EmissiveContribution contribution) {
        this.contributions.remove(contribution);
    }

    boolean isDefined() {
        return this.kind != null && this.kind.isLive();
    }

    /** @return whether the settings now ask for another definition than the live one */
    boolean needsDefinition() {
        return !isDefined() || !settings().equals(this.definition);
    }

    /**
     * Defines the kind from the settings, or defines it again when they changed; the contributions made through the
     * old definition are made again through the new one. A live layer naming this kind is closed first.
     */
    void define() {
        if (!needsDefinition()) return;
        List<EmissiveContribution> live = detachAll();
        this.definition = settings();
        switch (RenderInterface.defineBloomKind(this.definition)) {
            case Admission.Admitted<BloomKind>(var handle) -> this.kind = handle;
            case Admission.Exhausted<BloomKind>(var limit) -> GTLog.logger.warn(
                    "OrthLib's render interface holds its limit of {} bloom kinds; {} draws without glow", limit,
                    this.key.key());
        }
        for (EmissiveContribution contribution : live) {
            contribution.attach(this.kind);
            if (contribution.isLive()) {
                this.contributions.add(contribution);
            }
        }
    }

    /** Retires the kind and closes the contributions made through it. A live layer naming it is closed first. */
    void retire() {
        detachAll();
        this.definition = null;
    }

    // Closes the live contributions and then the kind, and returns the contributions to make again.
    private List<EmissiveContribution> detachAll() {
        List<EmissiveContribution> live = new ArrayList<>();
        for (EmissiveContribution contribution : this.contributions) {
            if (contribution.isLive()) {
                contribution.detach();
                live.add(contribution);
            }
        }
        this.contributions.clear();
        if (this.kind != null) {
            this.kind.close();
            this.kind = null;
        }
        return live;
    }

    /**
     * The definition the settings ask for. With emissive texture bloom off, the fusion reactor and heat effect kinds
     * have no strength either: their geometry is drawn without glow, as the emissive layer's is.
     */
    BloomKindDefinition settings() {
        ConfigHolder.ClientOptions.ShaderOptions shader = ConfigHolder.client.shader;
        return switch (this) {
            case EMISSIVE_TEXTURES -> definition(this.key, shader.strength, shader.baseBrightness,
                    shader.lowBrightnessThreshold, shader.highBrightnessThreshold, shader.step);
            case FUSION_REACTOR -> definition(this.key, shader.emissiveTexturesBloom ? shader.fusionBloom.strength : 0,
                    shader.fusionBloom.baseBrightness, shader.fusionBloom.lowBrightnessThreshold,
                    shader.fusionBloom.highBrightnessThreshold, 1);
            case HEAT_EFFECT -> definition(this.key,
                    shader.emissiveTexturesBloom ? shader.heatEffectBloom.strength : 0,
                    shader.heatEffectBloom.baseBrightness, shader.heatEffectBloom.lowBrightnessThreshold,
                    shader.heatEffectBloom.highBrightnessThreshold, 1);
        };
    }

    /**
     * A kind's definition from GTCEu's settings. GTCEu weighs a kind's glow over a background of brightness {@code Y}
     * by {@code strength * (base + low + (1 - Y) * (high - low))}. OrthLib bounds the limits to [0, 1], so settings
     * above 1 are divided by the larger limit and the strength multiplied by it, which leaves that weight unchanged.
     * Settings outside their ranges are brought into them: negative or undefined values count as 0 and values above
     * {@value #MAX_SETTING} as {@value #MAX_SETTING}, a high limit below the low one counts as the low one, and the
     * radius lies in [{@value #MIN_RADIUS}, {@value #MAX_RADIUS}].
     */
    static BloomKindDefinition definition(RenderKey key, double strength, double base, double low, double high,
                                          double radius) {
        float strengthSetting = atLeastZero(strength);
        float baseSetting = atLeastZero(base);
        float lowSetting = atLeastZero(low);
        float highSetting = Math.max(lowSetting, atLeastZero(high));
        float scale = Math.max(1, highSetting);
        float radiusSetting = Double.isFinite(radius) ? (float) Math.clamp(radius, MIN_RADIUS, MAX_RADIUS) : 1;
        return new BloomKindDefinition(key, strengthSetting * scale, baseSetting / scale, lowSetting / scale,
                highSetting / scale, radiusSetting);
    }

    private static float atLeastZero(double value) {
        return Double.isFinite(value) && value > 0 ? (float) Math.min(value, MAX_SETTING) : 0;
    }
}
