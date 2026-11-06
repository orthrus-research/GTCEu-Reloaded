package gregtech.mixins.mui2;

import com.cleanroommc.modularui.drawable.text.BaseKey;
import com.cleanroommc.modularui.drawable.text.LangKey;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// all this mixin does is switch newlines to the expected format
@Mixin(value = LangKey.class, remap = false)
public abstract class LangKeyMixin extends BaseKey {

    @ModifyReturnValue(method = { "get", "getFormatted" }, at = @At("RETURN"))
    private String normalizeNewLines(String original) {
        return original
                .replace("\\n", "\n")
                .replace("/n", "\n");
    }
}
