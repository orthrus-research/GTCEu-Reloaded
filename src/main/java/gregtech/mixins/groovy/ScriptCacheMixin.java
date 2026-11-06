package gregtech.mixins.groovy;

import gregtech.integration.groovy.cache.GroovyCacheEnvironment;
import gregtech.integration.groovy.cache.GroovyCacheIntegrity;
import gregtech.integration.groovy.cache.ScriptCacheIdentity;

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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Map;
import java.util.UUID;

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
    private ScriptCacheIdentity.Inputs gregtech$inputs;

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

    @Inject(method = "readIndex", at = @At("HEAD"))
    private void gregtech$validatePersistedCache(CallbackInfo ci) throws IOException {
        String environment;
        try {
            environment = GroovyCacheEnvironment.capture(config);
        } catch (IOException | RuntimeException unavailable) {
            // Unknown dependencies must never authorize old bytecode. A session identity still permits
            // normal compilation and in-session reuse, without making optional persistence fatal.
            environment = "unverified-session:" + UUID.randomUUID();
            GroovyScript.LOGGER.warn("GTCEu script cache: persistent reuse unavailable; recompiling for this session",
                    unavailable);
        }
        gregtech$inputs = new ScriptCacheIdentity.Inputs(ScriptCacheIdentity.sources(scriptRoot.toPath()), environment);
        if (!ScriptCacheIdentity.reusable(cacheRoot.toPath(), gregtech$inputs)) {
            gregtech$discardCache();
            GroovyScript.LOGGER.info("GTCEu script cache: content/environment check missed; recompilation required");
        } else GroovyScript.LOGGER.info("GTCEu script cache: content/environment and compiled bytes verified");
    }

    @Override
    public void gregtech$verifyScriptInputs() {
        try {
            String sources = ScriptCacheIdentity.sources(scriptRoot.toPath());
            if (!sources.equals(gregtech$inputs.sources())) {
                gregtech$discardCache();
                gregtech$inputs = new ScriptCacheIdentity.Inputs(sources, gregtech$inputs.environment());
                GroovyScript.LOGGER.info("GTCEu script cache: authoring inputs changed; invalidated dependent classes");
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot validate script inputs", failure);
        }
    }

    @Inject(method = "writeIndex", at = @At("RETURN"))
    private void gregtech$sealCompiledCache(CallbackInfo ci) throws IOException {
        if (gregtech$inputs.sources().equals(ScriptCacheIdentity.sources(scriptRoot.toPath()))) {
            ScriptCacheIdentity.seal(cacheRoot.toPath(), gregtech$inputs);
        } else ScriptCacheIdentity.invalidate(cacheRoot.toPath());
    }

    @Unique
    private void gregtech$discardCache() {
        if (!deleteScriptCache()) throw new IllegalStateException("Cannot discard stale Groovy script cache");
    }
}
