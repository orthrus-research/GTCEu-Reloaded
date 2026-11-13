package gregtech.mixins;

import gregtech.integration.groovy.GroovyScriptCompatibility;

import com.cleanroommc.cleanmix.ConditionalMixin;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

/** Uses discovery metadata before mod construction; never initializes the GregTech API or optional mod classes. */
public final class GregTechMixinConnector implements IMixinConnector {

    /** The ModularUI release of the qualified dependency baseline ({@code modularUiVersion}). */
    static final String MODULARUI_TESTED_RANGE = "[3.1.6]";
    /** The Nothirium release the bloom pass was last checked against in a client session. */
    static final String NOTHIRIUM_TESTED_RANGE = "[0.4.9-beta]";
    /** The release of the vintagium mod id (Relictium) the bloom pass was last checked against in a client session. */
    static final String VINTAGIUM_TESTED_RANGE = "[1.2.0]";

    @Override
    public void connect() {
        ConditionalMixin.of("mixins.gregtech.forge.json", "mixins.gregtech.minecraft.json").register();
        // The bloom pass wraps the translucent renderBlockLayer call in renderWorldPass. Celeritas-based renderers
        // (Demonica bundles Celeritas) redirect that call and draw the bloom layer in their own terrain passes; with
        // the wrapper in place their terrain never builds. BloomEffectUtil falls back to plain layers for them.
        ConditionalMixin.of("mixins.gregtech.bloom.json").conflicts("celeritas").conflicts("demonica")
                .because("Celeritas draws the bloom layer itself and owns renderWorldPass's terrain layer calls")
                .register();
        ConditionalMixin.of("mixins.gregtech.ccl.json").requires("codechickenlib").register();
        // These mixins name an anonymous class by index and overwrite TextWidget.checkString; a ModularUI update
        // can move either silently, so an untested version keeps stock ModularUI behaviour instead.
        ConditionalMixin.of("mixins.gregtech.mui2.json").requires("modularui")
                .version(MODULARUI_TESTED_RANGE)
                .because("GTCEu's ModularUI number, text and newline mixins are verified only for these versions")
                .orWarn()
                .register();
        // These mixins target GroovyScript internals; an untested version keeps stock caching instead of crashing.
        ConditionalMixin.of("mixins.gregtech.groovy.json").requires("groovyscript")
                .version(GroovyScriptCompatibility.TESTED_RANGE)
                .because("GTCEu script caching and typed definitions are verified only for these versions")
                .orWarn()
                .register();
        ConditionalMixin.of("mixins.gregtech.theoneprobe.json").requires("theoneprobe").register();
        ConditionalMixin.of("mixins.gregtech.jei.json").requires("jei").register();
        ConditionalMixin.of("mixins.gregtech.ctm.json").requires("ctm").register();
        ConditionalMixin.of("mixins.gregtech.littletiles.json").requires("littletiles").register();
        // Adds a BLOOM value to the BlockRenderPass enum by rewriting $VALUES and the cached VALUES and COUNT copies.
        // Relictium, the maintained Vintagium fork, keeps the vintagium mod id and packages.
        ConditionalMixin.of("mixins.gregtech.vintagium.json").requires("vintagium")
                .version(VINTAGIUM_TESTED_RANGE)
                .because("GTCEu's bloom render pass for Vintagium-based renderers is verified only for these versions")
                .orWarn()
                .register();
        // Adds a BLOOM value to Nothirium's ChunkRenderPass enum by rewriting $VALUES and its cached ALL copy
        ConditionalMixin.of("mixins.gregtech.nothirium.json").requires("nothirium")
                .version(NOTHIRIUM_TESTED_RANGE)
                .because("GTCEu's bloom render pass for Nothirium is verified only for these versions")
                .orWarn()
                .register();
    }
}
