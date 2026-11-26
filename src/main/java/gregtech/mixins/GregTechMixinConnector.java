package gregtech.mixins;

import gregtech.integration.groovy.GroovyScriptCompatibility;

import com.cleanroommc.cleanmix.ConditionalMixin;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

/** Uses discovery metadata before mod construction; never initializes the GregTech API or optional mod classes. */
public final class GregTechMixinConnector implements IMixinConnector {

    /** The ModularUI release of the qualified dependency baseline ({@code modularUiVersion}). */
    static final String MODULARUI_TESTED_RANGE = "[3.1.6]";

    /** The HEI release of the qualified dependency baseline ({@code mezz:jei} in {@code build.gradle}). */
    static final String HEI_TESTED_RANGE = "[4.36.1]";

    /** The One Probe release GTCEu builds against ({@code curse.maven:top-245211} in {@code build.gradle}). */
    static final String THEONEPROBE_TESTED_RANGE = "[1.4.28]";

    @Override
    public void connect() {
        ConditionalMixin.of("mixins.gregtech.forge.json", "mixins.gregtech.minecraft.json").register();
        ConditionalMixin.of("mixins.gregtech.ccl.json").requires("codechickenlib").register();
        // This mixin hooks LangKey.get and getFormatted; a ModularUI update can change either silently,
        // so an untested version keeps stock ModularUI behaviour instead.
        ConditionalMixin.of("mixins.gregtech.mui2.json").requires("modularui")
                .version(MODULARUI_TESTED_RANGE)
                .because("GTCEu's ModularUI newline mixin is verified only for these versions")
                .orWarn()
                .register();
        // These mixins target GroovyScript internals; an untested version keeps stock caching instead of crashing.
        ConditionalMixin.of("mixins.gregtech.groovy.json").requires("groovyscript")
                .version(GroovyScriptCompatibility.TESTED_RANGE)
                .because("GTCEu script caching and typed definitions are verified only for these versions")
                .orWarn()
                .register();
        // This mixin hooks a private probe packet method; an untested version shows stock probe information instead.
        ConditionalMixin.of("mixins.gregtech.theoneprobe.json").requires("theoneprobe")
                .version(THEONEPROBE_TESTED_RANGE)
                .because("GTCEu's machine harvest tool probe mixin is verified only for these versions")
                .orWarn()
                .register();
        // These mixins hook HEI internals by method, call order and local; another HEI release, or the original JEI
        // that shares its mod ID, keeps its stock behaviour instead.
        ConditionalMixin.of("mixins.gregtech.jei.json").requires("jei")
                .version(HEI_TESTED_RANGE)
                .because("GTCEu's HEI transfer, tooltip and category mixins are verified only for these versions")
                .orWarn()
                .register();
        // GT's tool classes and meta items implement these mods' tool and overlay interfaces only while the mod is
        // loaded, so the published classes name none of them. Each mixin only adds an interface whose methods the
        // classes already have, so every version of the mod qualifies.
        ConditionalMixin.of("mixins.gregtech.ae2.json").requires("appliedenergistics2").register();
        ConditionalMixin.of("mixins.gregtech.buildcraft.json").requires("buildcraftcore").register();
        ConditionalMixin.of("mixins.gregtech.cofh.json").requires("cofhcore").register();
        ConditionalMixin.of("mixins.gregtech.endercore.json").requires("endercore").register();
        ConditionalMixin.of("mixins.gregtech.enderio.json").requires("enderio").register();
        ConditionalMixin.of("mixins.gregtech.forestry.json").requires("forestry").register();
        ConditionalMixin.of("mixins.gregtech.projectred.json").requires("projred-core").register();
        ConditionalMixin.of("mixins.gregtech.railcraft.json").requires("railcraft").register();
        // GTCEu draws no terrain pass of its own: emissive textures reach renderers through OrthLib's render
        // interface, which a renderer provides, and fall back to CUTOUT elsewhere.
    }
}
