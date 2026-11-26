package com.example.gtaddon;

import gregtech.api.GregTechAPI;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.info.MaterialFlags;
import gregtech.api.unification.material.info.MaterialIconSet;
import gregtech.api.unification.material.properties.BlastProperty;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.ore.OrePrefix;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/** The material API: a builder, flags, properties and ore-dictionary unification. */
public final class FixtureMaterials {

    public static Material fixtureAlloy;

    private FixtureMaterials() {}

    static void register() {
        fixtureAlloy = Material.builder(1, new ResourceLocation(FixtureAddon.MODID, "fixture_alloy"))
                .ingot().fluid()
                .color(0x8899AA).iconSet(MaterialIconSet.METALLIC)
                .flags(MaterialFlags.GENERATE_PLATE)
                .components(Materials.Iron, 1, Materials.Copper, 1)
                .blast(1200)
                .build();
    }

    static int blastTemperature() {
        BlastProperty blast = fixtureAlloy.getProperty(PropertyKey.BLAST);
        return blast == null ? 0 : blast.getBlastTemperature();
    }

    static ItemStack plate() {
        Material iron = GregTechAPI.materialManager.getMaterial("gregtech:iron");
        Material material = fixtureAlloy.hasFlag(MaterialFlags.GENERATE_PLATE) ? fixtureAlloy : iron;
        return OreDictUnifier.get(OrePrefix.plate, material);
    }
}
