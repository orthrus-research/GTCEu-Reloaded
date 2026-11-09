package gregtech.mixins;

import gregtech.integration.groovy.GroovyScriptCompatibility;

import com.cleanroommc.cleanmix.ConditionalMixin;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

/** Uses discovery metadata before mod construction; never initializes the GregTech API or optional mod classes. */
public final class GregTechMixinConnector implements IMixinConnector {

    @Override
    public void connect() {
        ConditionalMixin.of("mixins.gregtech.forge.json", "mixins.gregtech.minecraft.json").register();
        ConditionalMixin.of("mixins.gregtech.ccl.json").requires("codechickenlib").register();
        ConditionalMixin.of("mixins.gregtech.mui2.json").requires("modularui").register();
        // These mixins target GroovyScript internals; an untested version keeps stock caching instead of crashing.
        ConditionalMixin.of("mixins.gregtech.groovy.json").requires("groovyscript")
                .version(GroovyScriptCompatibility.TESTED_RANGE)
                .because("GTCEu script caching and typed definitions are verified only for these versions")
                .register();
        ConditionalMixin.of("mixins.gregtech.theoneprobe.json").requires("theoneprobe").register();
        ConditionalMixin.of("mixins.gregtech.jei.json").requires("jei").register();
        ConditionalMixin.of("mixins.gregtech.ctm.json").requires("ctm").register();
        ConditionalMixin.of("mixins.gregtech.littletiles.json").requires("littletiles").register();
        ConditionalMixin.of("mixins.gregtech.vintagium.json").requires("vintagium").register();
        ConditionalMixin.of("mixins.gregtech.nothirium.json").requires("nothirium").register();
    }
}
