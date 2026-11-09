package gregtech.mixins.groovy;

import gregtech.integration.groovy.cache.GroovyCacheEnvironment;
import gregtech.integration.groovy.cache.GroovyCacheIntegrity;
import gregtech.integration.groovy.cache.ScriptCacheGuard;

import com.cleanroommc.groovyscript.GroovyScript;
import com.cleanroommc.groovyscript.sandbox.CustomGroovyScriptEngine;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.net.URL;
import java.util.Map;

@Mixin(value = CustomGroovyScriptEngine.class, remap = false)
public abstract class ScriptCacheMixin implements GroovyCacheIntegrity {

    @Shadow
    @Final
    private File cacheRoot;
    @Shadow
    @Final
    private File scriptRoot;
    @Shadow
    @Final
    private CompilerConfiguration config;
    @Shadow
    @Final
    private Map<String, ?> loadedClasses;

    @Shadow
    public abstract boolean deleteScriptCache();

    @Unique
    private ScriptCacheGuard gregtech$guard;

    // Runtime class loading passes binary names; the compiler's resolver passes slash-separated names.
    @ModifyVariable(method = "findScriptFileOfClass", at = @At("HEAD"), argsOnly = true)
    private String gregtech$normalizeClassName(String name) {
        return name.replace('.', '/');
    }

    @Inject(method = "findScriptFileOfClass", at = @At("HEAD"), cancellable = true)
    private void gregtech$findCachedSource(String name, CallbackInfoReturnable<File> cir) {
        Object cached = loadedClasses.get(name.replace('/', '.'));
        if (cached instanceof CompiledClassAccessor entry) {
            File source = new File(scriptRoot, entry.gregtech$sourcePath());
            if (source.isFile()) cir.setReturnValue(source);
        }
    }

    // A source can contain several classes. Restore the requested class, not always its primary script class.
    @Inject(method = "onRecompileClass", at = @At("RETURN"), cancellable = true)
    private void gregtech$returnRequestedClass(URL source, String name, CallbackInfoReturnable<Class<?>> cir) {
        Object cached = loadedClasses.get(name);
        if (cached instanceof CompiledClassAccessor entry && entry.gregtech$loadedClass() != null) {
            cir.setReturnValue(entry.gregtech$loadedClass());
        }
    }

    // Persistence failures degrade to a session identity inside the guard; nothing here throws into GroovyScript.
    @Inject(method = "readIndex", at = @At("HEAD"), cancellable = true)
    private void gregtech$validatePersistedCache(CallbackInfo ci) {
        this.gregtech$guard = new ScriptCacheGuard(cacheRoot.toPath(), scriptRoot.toPath(),
                this::deleteScriptCache, GroovyScript.LOGGER);
        // deleteScriptCache clears memory even when files stay locked. Never index unverified bytecode from disk.
        if (!gregtech$guard.open(() -> GroovyCacheEnvironment.capture(config))) ci.cancel();
    }

    @Override
    public void gregtech$verifyScriptInputs() {
        if (gregtech$guard != null) gregtech$guard.beforeRun();
    }

    @Inject(method = "writeIndex", at = @At("RETURN"))
    private void gregtech$sealCompiledCache(CallbackInfo ci) {
        if (gregtech$guard != null) gregtech$guard.afterWrite();
    }
}
