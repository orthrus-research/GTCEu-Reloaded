package gregtech.mixins.mui2;

import gregtech.api.util.LocalizationUtils;

import com.cleanroommc.modularui.drawable.text.BaseKey;
import com.cleanroommc.modularui.drawable.text.LangKey;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// switches newlines to the expected format: the escaped form for every key, GregTech's /n marker for its own keys
@Mixin(value = LangKey.class, remap = false)
public abstract class LangKeyMixin extends BaseKey {

    @ModifyReturnValue(method = { "get", "getFormatted" }, at = @At("RETURN"))
    private String normalizeNewLines(String original) {
        String text = original.replace("\n", "\n");
        if (text.contains("/n") && LocalizationUtils.isOwnKey(((LangKey) (Object) this).getKeySupplier().get())) {
            text = text.replace("/n", "\n");
        }
        return text;
    }
}
