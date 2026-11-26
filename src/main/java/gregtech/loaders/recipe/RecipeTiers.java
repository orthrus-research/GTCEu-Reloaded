package gregtech.loaders.recipe;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.unification.material.MarkerMaterials.Tier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.stack.UnificationEntry;

import java.util.Locale;

import static gregtech.api.GTValues.LV;
import static gregtech.api.GTValues.VN;
import static gregtech.common.items.MetaItems.*;

/**
 * Tier lookups several recipe loaders share, by the voltage tiers of {@link gregtech.api.GTValues}. A table only one
 * loader reads stays in that loader.
 * <p>
 * The electric components are the items themselves, LV to UV, not {@link CraftingComponent}'s entries, which addons
 * may change; they are read when a loader runs, after the meta items exist.
 */
final class RecipeTiers {

    /** The circuit marker material of each tier, ULV to MAX. */
    private static final Material[] CIRCUITS = { Tier.ULV, Tier.LV, Tier.MV, Tier.HV, Tier.EV, Tier.IV, Tier.LuV,
            Tier.ZPM, Tier.UV, Tier.UHV, Tier.UEV, Tier.UIV, Tier.UXV, Tier.OpV, Tier.MAX };

    private RecipeTiers() {}

    /** @return the marker material of a tier's circuits, as {@code circuitLv} names it for LV */
    static Material circuitTier(int tier) {
        return CIRCUITS[tier];
    }

    /** @return the material of a crafting component's ore dictionary entry at a tier, such as the tier's plate */
    static Material material(CraftingComponent.Component component, int tier) {
        return ((UnificationEntry) component.getIngredient(tier)).material;
    }

    /** @return the tier's name in lower case, as recipe names hold it: {@code lv}, {@code luv} */
    static String tierName(int tier) {
        return VN[tier].toLowerCase(Locale.ROOT);
    }

    static MetaItem<?>.MetaValueItem motor(int tier) {
        return fromLV(tier, ELECTRIC_MOTOR_LV, ELECTRIC_MOTOR_MV, ELECTRIC_MOTOR_HV, ELECTRIC_MOTOR_EV,
                ELECTRIC_MOTOR_IV, ELECTRIC_MOTOR_LuV, ELECTRIC_MOTOR_ZPM, ELECTRIC_MOTOR_UV);
    }

    static MetaItem<?>.MetaValueItem conveyor(int tier) {
        return fromLV(tier, CONVEYOR_MODULE_LV, CONVEYOR_MODULE_MV, CONVEYOR_MODULE_HV, CONVEYOR_MODULE_EV,
                CONVEYOR_MODULE_IV, CONVEYOR_MODULE_LuV, CONVEYOR_MODULE_ZPM, CONVEYOR_MODULE_UV);
    }

    static MetaItem<?>.MetaValueItem pump(int tier) {
        return fromLV(tier, ELECTRIC_PUMP_LV, ELECTRIC_PUMP_MV, ELECTRIC_PUMP_HV, ELECTRIC_PUMP_EV, ELECTRIC_PUMP_IV,
                ELECTRIC_PUMP_LuV, ELECTRIC_PUMP_ZPM, ELECTRIC_PUMP_UV);
    }

    static MetaItem<?>.MetaValueItem piston(int tier) {
        return fromLV(tier, ELECTRIC_PISTON_LV, ELECTRIC_PISTON_MV, ELECTRIC_PISTON_HV, ELECTRIC_PISTON_EV,
                ELECTRIC_PISTON_IV, ELECTRIC_PISTON_LUV, ELECTRIC_PISTON_ZPM, ELECTRIC_PISTON_UV);
    }

    static MetaItem<?>.MetaValueItem robotArm(int tier) {
        return fromLV(tier, ROBOT_ARM_LV, ROBOT_ARM_MV, ROBOT_ARM_HV, ROBOT_ARM_EV, ROBOT_ARM_IV, ROBOT_ARM_LuV,
                ROBOT_ARM_ZPM, ROBOT_ARM_UV);
    }

    static MetaItem<?>.MetaValueItem fieldGenerator(int tier) {
        return fromLV(tier, FIELD_GENERATOR_LV, FIELD_GENERATOR_MV, FIELD_GENERATOR_HV, FIELD_GENERATOR_EV,
                FIELD_GENERATOR_IV, FIELD_GENERATOR_LuV, FIELD_GENERATOR_ZPM, FIELD_GENERATOR_UV);
    }

    static MetaItem<?>.MetaValueItem emitter(int tier) {
        return fromLV(tier, EMITTER_LV, EMITTER_MV, EMITTER_HV, EMITTER_EV, EMITTER_IV, EMITTER_LuV, EMITTER_ZPM,
                EMITTER_UV);
    }

    static MetaItem<?>.MetaValueItem sensor(int tier) {
        return fromLV(tier, SENSOR_LV, SENSOR_MV, SENSOR_HV, SENSOR_EV, SENSOR_IV, SENSOR_LuV, SENSOR_ZPM,
                SENSOR_UV);
    }

    private static MetaItem<?>.MetaValueItem fromLV(int tier, MetaItem<?>.MetaValueItem... items) {
        return items[tier - LV];
    }
}
