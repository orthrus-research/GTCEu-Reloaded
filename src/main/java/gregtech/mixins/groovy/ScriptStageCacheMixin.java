package gregtech.mixins.groovy;

import gregtech.integration.groovy.cache.GroovyCacheIntegrity;

import com.cleanroommc.groovyscript.sandbox.CustomGroovyScriptEngine;
import com.cleanroommc.groovyscript.sandbox.GroovyScriptSandbox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GroovyScriptSandbox.class, remap = false)
public abstract class ScriptStageCacheMixin {

    @Shadow
    @Final
    private CustomGroovyScriptEngine engine;

    @Inject(method = "preRun", at = @At("HEAD"))
    private void gregtech$validateInputs(CallbackInfo ci) {
        ((GroovyCacheIntegrity) engine).gregtech$verifyScriptInputs();
    }
}
