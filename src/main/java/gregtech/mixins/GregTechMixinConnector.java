package gregtech.mixins;

import gregtech.integration.groovy.GroovyScriptCompatibility;

import com.cleanroommc.cleanmix.ConditionalMixin;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

/** Uses discovery metadata before mod construction; never initializes the GregTech API or optional mod classes. */
public final class GregTechMixinConnector implements IMixinConnector {

    /** The ModularUI release of the qualified dependency baseline ({@code modularUiVersion}). */
    static final String MODULARUI_TESTED_RANGE = "[3.1.6]";

    @Override
    public void connect() {
        ConditionalMixin.of("mixins.gregtech.forge.json", "mixins.gregtech.minecraft.json").register();
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
        // GTCEu draws no terrain pass of its own: emissive textures reach renderers through OrthLib's render
        // interface, which a renderer provides, and fall back to CUTOUT elsewhere.
    }
}
