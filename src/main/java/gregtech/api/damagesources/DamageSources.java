package gregtech.api.damagesources;

import gregtech.api.items.toolitem.IGTTool;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;

import org.jetbrains.annotations.Nullable;

public class DamageSources {

    private static final DamageSource EXPLOSION = new DamageSource("explosion").setExplosion();
    private static final DamageSource HEAT = new DamageSource("heat").setFireDamage();
    private static final DamageSource FROST = new DamageSource("frost");
    // held hot and cryogenic items burn through armour; separate sources, so the shared ones keep armour
    private static final DamageSource HEAT_BYPASSING_ARMOR = new DamageSource("heat").setFireDamage()
            .setDamageBypassesArmor();
    private static final DamageSource FROST_BYPASSING_ARMOR = new DamageSource("frost").setDamageBypassesArmor();
    private static final DamageSource CHEMICAL = new DamageSource("chemical").setDamageBypassesArmor();
    private static final DamageSource ELECTRIC = new DamageSource("electric");
    private static final DamageSource RADIATION = new DamageSource("radiation").setDamageBypassesArmor();
    private static final DamageSource TURBINE = new DamageSource("turbine");

    public static DamageSource getExplodingDamage() {
        return EXPLOSION;
    }

    public static DamageSource getHeatDamage() {
        return HEAT;
    }

    public static DamageSource getFrostDamage() {
        return FROST;
    }

    /** Heat damage that ignores armour, for hot items held in the inventory. */
    public static DamageSource getHeatDamageBypassingArmor() {
        return HEAT_BYPASSING_ARMOR;
    }

    /** Frost damage that ignores armour, for cryogenic items held in the inventory. */
    public static DamageSource getFrostDamageBypassingArmor() {
        return FROST_BYPASSING_ARMOR;
    }

    public static DamageSource getChemicalDamage() {
        return CHEMICAL;
    }

    public static DamageSource getElectricDamage() {
        return ELECTRIC;
    }

    public static DamageSource getRadioactiveDamage() {
        return RADIATION;
    }

    public static DamageSource getTurbineDamage() {
        return TURBINE;
    }

    public static DamageSource getPlayerDamage(@Nullable EntityPlayer source) {
        ItemStack stack = source != null ? source.getHeldItemMainhand() : ItemStack.EMPTY;
        if (!stack.isEmpty() && stack.getItem() instanceof IGTTool tool) {
            return new DamageSourceTool("player", source, String.format("death.attack.%s", tool.getToolId()));
        }
        return new EntityDamageSource("player", source);
    }

    public static DamageSource getMobDamage(@Nullable EntityLivingBase source) {
        ItemStack stack = source != null ? source.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND) : ItemStack.EMPTY;
        if (!stack.isEmpty() && stack.getItem() instanceof IGTTool tool) {
            return new DamageSourceTool("mob", source, String.format("death.attack.%s", tool.getToolId()));
        }
        return new EntityDamageSource("mob", source);
    }
}
