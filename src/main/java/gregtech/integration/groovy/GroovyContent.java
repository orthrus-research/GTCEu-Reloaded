package gregtech.integration.groovy;

import gregtech.api.definition.ContentDefinitions;
import gregtech.api.definition.DefinitionResult;
import gregtech.api.definition.HeatingCoilDefinition;
import gregtech.api.definition.OreItemDefinition;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.FurnaceRecipePolicy;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;

import com.cleanroommc.groovyscript.GroovyScript;
import com.cleanroommc.groovyscript.api.GroovyLog;
import com.cleanroommc.groovyscript.registry.NamedRegistry;
import com.cleanroommc.groovyscript.sandbox.LoadStage;

import java.util.List;

/** Startup content uses native descriptors; it is deliberately not advertised as a reloadable Forge registry. */
public final class GroovyContent extends NamedRegistry {

    public GroovyContent() {
        super(List.of("content"));
    }

    public DefinitionResult<List<OreItemDefinition>> items(List<OreItemDefinition> definitions) {
        if (GroovyScript.getSandbox().getCurrentLoader() != LoadStage.PRE_INIT) {
            throw new IllegalStateException("Custom items must be declared in preInit before Forge item registration");
        }
        var result = ContentDefinitions.ORE_ITEMS.register(definitions);
        result.diagnostics().forEach(d -> GroovyLog.get().error(d.format()));
        return result;
    }

    public ItemStack item(int metadata, int amount) {
        return ContentDefinitions.ORE_ITEMS.stack(metadata, amount);
    }

    public DefinitionResult<HeatingCoilDefinition> coil(IBlockState state, HeatingCoilDefinition definition) {
        var result = ContentDefinitions.coil(state, definition);
        result.diagnostics().forEach(d -> GroovyLog.get().error(d.format()));
        return result;
    }

    public void removeCoil(IBlockState state) {
        ContentDefinitions.removeCoil(state);
    }

    public void protectSmelting(ItemStack input, ItemStack output, SourceOrigin origin) {
        FurnaceRecipePolicy.protect(input, output, origin);
    }
}
