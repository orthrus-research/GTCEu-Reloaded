package gregtech.mixins.minecraft;

import gregtech.api.items.toolitem.ItemGTToolbelt;
import gregtech.common.ConfigHolder;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.lwjgl.input.Keyboard;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "processKeyF3", at = @At("HEAD"))
    public void addGregTechDebugMessage(int auxKey, CallbackInfoReturnable<Boolean> cir) {
        if (auxKey == Keyboard.KEY_H && !Minecraft.getMinecraft().gameSettings.advancedItemTooltips) {
            Minecraft.getMinecraft().ingameGUI.getChatGUI()
                    .printChatMessage(new TextComponentTranslation("gregtech.debug.f3_h.enabled"));
        }
    }

    // a wrap rather than a redirect, so another mod can wrap the same write
    @WrapOperation(method = "processKeyBinds",
                   at = @At(value = "FIELD",
                            target = "Lnet/minecraft/entity/player/InventoryPlayer;currentItem:I",
                            opcode = Opcodes.PUTFIELD))
    public void interceptHotbarKeybindsForToolbelt(InventoryPlayer inventoryPlayer, int i, Operation<Void> original) {
        if (ConfigHolder.client.toolbeltConfig.enableToolbeltKeypressCapture && inventoryPlayer.player.isSneaking()) {
            ItemStack stack = inventoryPlayer.player.getHeldItemMainhand();
            if (stack.getItem() instanceof ItemGTToolbelt toolbelt) {
                toolbelt.changeSelectedToolHotkey(i, stack);
                return;
            }
        }
        original.call(inventoryPlayer, i);
    }
}
