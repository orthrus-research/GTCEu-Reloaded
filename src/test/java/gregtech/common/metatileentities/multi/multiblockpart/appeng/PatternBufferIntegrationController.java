package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static gregtech.api.util.GTUtility.gregtechId;

/** In-memory formed ownership fixture; A7 selection and A6 live providers remain production implementations. */
final class PatternBufferIntegrationController extends RecipeMapMultiblockController {

    private final Map<MultiblockAbility<?>, AbilityInstances> abilities = new HashMap<>();
    private final Map<Object, IMultiblockPart> owners = new IdentityHashMap<>();
    private final List<IMultiblockPart> parts = new ArrayList<>();
    Runnable beforeStart;

    PatternBufferIntegrationController(RecipeMap<?> map, PatternBufferTestWorld world,
                                       IMultiblockAbilityPart<?>... attached) {
        super(gregtechId("pattern_integration_controller"), map);
        world.put(new BlockPos(0, 0, 4), this);
        this.recipeMapWorkable = new PatternBufferIntegrationLogic(this);
        for (IMultiblockAbilityPart<?> part : attached) {
            parts.add(part);
            if (part instanceof PatternBufferTestPart) ((PatternBufferTestPart) part).controller = this;
            if (part instanceof PatternBufferTestProxy) ((PatternBufferTestProxy) part).controller = this;
            part.addToMultiBlock(this);
            for (MultiblockAbility<?> ability : part.getAbilities()) {
                AbilityInstances claimed = new AbilityInstances(ability);
                part.registerAbilities(claimed);
                AbilityInstances all = abilities.computeIfAbsent(ability, AbilityInstances::new);
                for (Object handler : claimed) {
                    all.add(handler);
                    owners.put(handler, part);
                }
            }
        }
        initializeAbilities();
        this.energyContainer = new EnergyContainerHandler(this, 100_000, 32, 1, 0, 0);
        this.energyContainer.addEnergy(100_000);
        recipeMapWorkable.setAllowOverclocking(false);
    }

    PatternBufferIntegrationLogic logic() {
        return (PatternBufferIntegrationLogic) recipeMapWorkable;
    }

    @Override
    public boolean canUseRecipeInputViews() {
        return true;
    }

    @Override
    public boolean isStructureFormed() {
        return true;
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return false;
    }

    @Override
    public boolean hasMufflerMechanics() {
        return false;
    }

    @Override
    public boolean canBeDistinct() {
        return true;
    }

    @Override
    public List<IMultiblockPart> getMultiblockParts() {
        return Collections.unmodifiableList(parts);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity holder) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected BlockPattern createStructurePattern() {
        return null;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart part) {
        return null;
    }

    @Override
    public <T> List<T> getAbilities(MultiblockAbility<T> ability) {
        return abilities == null || !abilities.containsKey(ability) ? Collections.emptyList() :
                abilities.get(ability).cast();
    }

    @Override
    public <T> IMultiblockPart getAbilityOwner(MultiblockAbility<T> ability, T handler) {
        return owners.get(handler);
    }

    @Override
    public boolean checkRecipe(Recipe recipe, boolean consume) {
        if (!consume && beforeStart != null) {
            Runnable action = beforeStart;
            beforeStart = null;
            action.run();
        }
        return true;
    }
}
