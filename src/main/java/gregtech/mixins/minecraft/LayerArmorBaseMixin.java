package gregtech.mixins.minecraft;

import gregtech.api.items.armor.IArmorItem;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(LayerArmorBase.class)
public class LayerArmorBaseMixin {

    @Inject(method = "renderArmorLayer", at = @At("TAIL"))
    public void renderGTArmor(EntityLivingBase entityLivingBaseIn, float limbSwing, float limbSwingAmount,
                              float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale,
                              EntityEquipmentSlot slotIn, CallbackInfo ci) {
        ItemStack itemStack = entityLivingBaseIn.getItemStackFromSlot(slotIn);

        if ((itemStack.getItem() instanceof IArmorItem armorItem &&
                itemStack.getItem().getEquipmentSlot(itemStack) == slotIn)) {
            @SuppressWarnings("unchecked")
            LayerArmorBase<ModelBase> layer = (LayerArmorBase<ModelBase>) (Object) this;
            ModelBase armorModel = layer.getModelFromSlot(slotIn);
            if (armorModel instanceof ModelBiped) {
                armorModel = ForgeHooksClient.getArmorModel(entityLivingBaseIn, itemStack, slotIn,
                        (ModelBiped) armorModel);
            }
            armorModel.setModelAttributes(layer.renderer.getMainModel());
            armorModel.setLivingAnimations(entityLivingBaseIn, limbSwing, limbSwingAmount, partialTicks);
            layer.setModelSlotVisible(armorModel, slotIn);

            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

            int layers = armorItem.getArmorLayersAmount(itemStack);
            for (int layerIndex = 0; layerIndex < layers; layerIndex++) {
                int i = armorItem.getArmorLayerColor(itemStack, layerIndex);
                float f = (float) (i >> 16 & 255) / 255.0F;
                float f1 = (float) (i >> 8 & 255) / 255.0F;
                float f2 = (float) (i & 255) / 255.0F;
                GlStateManager.color(f, f1, f2, 1.0f);
                String type = layerIndex == 0 ? null : "layer_" + layerIndex;
                layer.renderer.bindTexture(gregTechCEu$getArmorTexture(entityLivingBaseIn, itemStack, slotIn, type));
                armorModel.render(entityLivingBaseIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch,
                        scale);
            }
            // leave the state as vanilla armour rendering found it
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableBlend();
            if (itemStack.hasEffect()) {
                LayerArmorBase.renderEnchantedGlint(layer.renderer, entityLivingBaseIn, armorModel, limbSwing,
                        limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch, scale);
            }
        }
    }

    @Unique
    private static final Map<String, ResourceLocation> gregTechCEu$TEXTURE_CACHE = new Object2ObjectOpenHashMap<>();

    @Unique
    private static ResourceLocation gregTechCEu$getArmorTexture(EntityLivingBase entity, ItemStack itemStack,
                                                                EntityEquipmentSlot slot, String type) {
        ResourceLocation registryName = itemStack.getItem().getRegistryName();
        if (registryName == null) {
            throw new IllegalArgumentException(
                    "ItemStack " + itemStack.getTranslationKey() + " has a null registry name");
        }

        String s1 = registryName.getNamespace() + ":textures/models/armor/" + registryName.getPath() + "_layer_" +
                (slot == EntityEquipmentSlot.LEGS ? 2 : 1) + (type == null ? "" : "_" + type) + ".png";
        // the item may pick another texture per wearer, so cache by the final path as vanilla does
        String path = ForgeHooksClient.getArmorTexture(entity, itemStack, s1, slot, type);
        return gregTechCEu$TEXTURE_CACHE.computeIfAbsent(path, ResourceLocation::new);
    }
}
