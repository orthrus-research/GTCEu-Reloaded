package gregtech.mixins.groovy;

import gregtech.integration.groovy.GroovyScriptModule;

import com.cleanroommc.groovyscript.registry.ReloadableRegistryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ReloadableRegistryManager.class, remap = false)
public abstract class ReloadableRegistryManagerMixin {

    @Inject(method = "onReload", at = @At("HEAD"), require = 1)
    private static void gregtech$restoreTypedGroupsFirst(CallbackInfo ci) {
        GroovyScriptModule.restoreDefinitionsBeforeReload();
    }
}
