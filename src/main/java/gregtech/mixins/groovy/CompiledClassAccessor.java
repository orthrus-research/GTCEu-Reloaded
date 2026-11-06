package gregtech.mixins.groovy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.cleanroommc.groovyscript.sandbox.CompiledClass", remap = false)
public interface CompiledClassAccessor {

    @Accessor("path")
    String gregtech$sourcePath();

    @Accessor("clazz")
    Class<?> gregtech$loadedClass();
}
