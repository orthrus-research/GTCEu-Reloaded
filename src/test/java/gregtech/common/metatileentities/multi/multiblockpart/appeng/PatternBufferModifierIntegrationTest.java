package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.GTValues;
import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
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
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.modifiers.IRecipeModifierProvider;
import gregtech.api.recipes.modifiers.RecipeModification;
import gregtech.api.recipes.modifiers.RecipeModifier;
import gregtech.api.recipes.modifiers.RecipeModifierList;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityItemBus;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferLink;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferStorage;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static gregtech.api.util.GTUtility.gregtechId;
import static gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises pattern-buffer ledger and proxy inputs through grouped modifier startup with in-memory formation. */
class PatternBufferModifierIntegrationTest {

    @BeforeAll
    static void setup() {
        bootstrap();
    }

    @Test
    void scaledPrivateProxyAttemptsConsumeOnlyTheAdmittedBatchAndPruneAfterSetup() {
        PatternBufferTestWorld world = new PatternBufferTestWorld();
        PatternBufferStorage seed = new PatternBufferStorage(() -> {}, stack -> stack.getItem() == Items.PAPER);
        for (int index = 0; index < 2; index++) {
            ItemStack pattern = new ItemStack(Items.PAPER);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("seam_pattern", index);
            pattern.setTagCompound(tag);
            assertTrue(seed.getPatterns().insertItem(index, pattern, false).isEmpty());
            ItemStack input = new ItemStack(Items.IRON_INGOT, index + 2);
            InventoryCrafting table = table(input);
            assertTrue(seed.accept(index, patternDetails(pattern, input), table, false));
            assertTrue(table.isEmpty());
        }
        PatternBufferTestPart buffer = new PatternBufferTestPart();
        world.put(BlockPos.ORIGIN, buffer);
        buffer.getPatternStorage().read(seed.write());
        PatternBufferStorage storage = buffer.getPatternStorage();
        assertFalse(storage.isQuarantined());
        assertTrue(storage.getSharedItems().insertItem(0, new ItemStack(Items.STICK), false).isEmpty());
        long firstId = storage.getBatches().get(0).getId();
        long selectedId = storage.getBatches().get(1).getId();
        NBTTagCompound firstBefore = storage.write().getTagList("Batches", 10).getCompoundTagAt(0).copy();

        PatternBufferTestProxy proxy = new PatternBufferTestProxy(buffer.owner);
        world.put(BlockPos.ORIGIN.east(), proxy);
        NBTTagCompound link = new NBTTagCompound();
        link.setTag("PatternBufferLink", PatternBufferLink.capture(buffer).write());
        proxy.initFromItemStackData(link);
        MetaTileEntityItemBus output = new MetaTileEntityItemBus(gregtechId("modifier_seam_output"), GTValues.IV, true);
        world.put(BlockPos.ORIGIN.north(), output);

        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("pattern_modifier_seam",
                new SimpleRecipeBuilder()).itemInputs(2).itemOutputs(1).build();
        Recipe registered = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .notConsumable(new ItemStack(Items.STICK)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(8).EUt(1).build().getResult();
        assertTrue(map.compileRecipe(registered));
        PatternBufferModifierController controller = new PatternBufferModifierController(map, world, buffer,
                selectedId, proxy, output);
        controller.setPaintGroupingEnabled(false);
        controller.setDistinct(false);
        AtomicInteger callbacks = new AtomicInteger();
        controller.modifiers = RecipeModifierList
                .of(RecipeModifier.named("test:actual_batch_scaling", (context, view) -> {
                    callbacks.incrementAndGet();
                    assertEquals(0, controller.logic().getProgress());
                    assertEquals(2, storage.getBatches().size());
                    assertTrue(storage.ownsPattern(0));
                    assertTrue(storage.ownsPattern(1));
                    return RecipeModification.builder().itemInputs(3, 0).build();
                }));
        long revisionBefore = storage.getRevision();
        controller.logic().search();

        assertEquals(2, callbacks.get(), "The same source is adjusted once for each matching private batch");
        assertEquals(1, controller.logic().getProgress());
        assertEquals(1, controller.logic().setups);
        assertFalse(controller.logic().attemptActive());
        assertTrue(storage.getRevision() > revisionBefore, "Actual consumption and prune must publish changes");
        assertEquals(1, storage.getBatches().size());
        assertEquals(firstId, storage.getBatches().get(0).getId());
        assertEquals(firstBefore, storage.write().getTagList("Batches", 10).getCompoundTagAt(0));
        assertEquals(2, storage.getBatches().get(0).getItems().getStackInSlot(0).getCount());
        assertTrue(storage.ownsPattern(0));
        assertFalse(storage.ownsPattern(1));
        assertTrue(storage.getPatterns().extractItem(0, 1, true).isEmpty());
        assertEquals(1, storage.getPatterns().extractItem(1, 1, true).getCount());
        assertEquals(Items.STICK, storage.getSharedItems().getStackInSlot(0).getItem());
        assertEquals(1, storage.getSharedItems().getStackInSlot(0).getCount());
        assertEquals(1, registered.getInputs().stream().filter(input -> !input.isNonConsumable())
                .findFirst().get().getAmount(), "Registered input must remain unchanged");

        controller.logic().finish();
        assertEquals(0, controller.logic().getProgress());
        assertEquals(Items.GOLD_INGOT, output.getExportItems().getStackInSlot(0).getItem());
        assertEquals(1, output.getExportItems().getStackInSlot(0).getCount());
        assertEquals(2, callbacks.get(), "Completion must not create another startup attempt");
        assertEquals(firstBefore, storage.write().getTagList("Batches", 10).getCompoundTagAt(0));
    }

    private static ICraftingPatternDetails patternDetails(ItemStack pattern, ItemStack input) {
        IAEItemStack[] inputs = { AEItemStack.fromItemStack(input.copy()) };
        return new ICraftingPatternDetails() {

            @Override
            public ItemStack getPattern() {
                return pattern.copy();
            }

            @Override
            public boolean isValidItemForSlot(int slot, ItemStack stack, World world) {
                return false;
            }

            @Override
            public boolean isCraftable() {
                return false;
            }

            @Override
            public IAEItemStack[] getInputs() {
                return inputs;
            }

            @Override
            public IAEItemStack[] getCondensedInputs() {
                return inputs;
            }

            @Override
            public IAEItemStack[] getOutputs() {
                return new IAEItemStack[] { AEItemStack.fromItemStack(new ItemStack(Items.GOLD_INGOT)) };
            }

            @Override
            public IAEItemStack[] getCondensedOutputs() {
                return getOutputs();
            }

            @Override
            public boolean canSubstitute() {
                return false;
            }

            @Override
            public ItemStack getOutput(InventoryCrafting table, World world) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getPriority() {
                return 0;
            }

            @Override
            public void setPriority(int priority) {}
        };
    }
}

final class PatternBufferModifierController extends RecipeMapMultiblockController implements IRecipeModifierProvider {

    private final Map<MultiblockAbility<?>, AbilityInstances> abilities = new HashMap<>();
    private final Map<Object, IMultiblockPart> owners = new IdentityHashMap<>();
    private final List<IMultiblockPart> parts = new ArrayList<>();
    final PatternBufferTestPart source;
    final long selectedBatch;
    RecipeModifierList modifiers = RecipeModifierList.EMPTY;

    PatternBufferModifierController(RecipeMap<?> map, PatternBufferTestWorld world, PatternBufferTestPart source,
                                    long selectedBatch, IMultiblockAbilityPart<?>... attached) {
        super(gregtechId("pattern_modifier_seam_controller"), map);
        this.source = source;
        this.selectedBatch = selectedBatch;
        world.put(new BlockPos(0, 0, 4), this);
        this.recipeMapWorkable = new PatternBufferModifierLogic(this);
        for (IMultiblockAbilityPart<?> part : attached) {
            parts.add(part);
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

    PatternBufferModifierLogic logic() {
        return (PatternBufferModifierLogic) recipeMapWorkable;
    }

    @Override
    public RecipeModifierList getRecipeModifiers() {
        return modifiers;
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
}

final class PatternBufferModifierLogic extends MultiblockRecipeLogic {

    private final PatternBufferModifierController controller;
    int setups;

    PatternBufferModifierLogic(PatternBufferModifierController controller) {
        super(controller);
        this.controller = controller;
    }

    void search() {
        trySearchNewRecipe();
    }

    void finish() {
        tryCompleteRecipe();
    }

    boolean attemptActive() {
        return hasRecipeModifierAttempt();
    }

    @Override
    protected void setupRecipe(Recipe recipe) {
        PatternBufferStorage storage = controller.source.getPatternStorage();
        assertTrue(hasRecipeModifierAttempt());
        assertEquals(0, getProgress());
        assertEquals(2, storage.getBatches().size(), "No batch may be pruned halfway through consumption");
        assertTrue(storage.getBatches().stream().filter(batch -> batch.getId() == controller.selectedBatch)
                .findFirst().get().isEmpty(), "The selected batch must have been consumed before setup");
        assertTrue(storage.ownsPattern(1));
        super.setupRecipe(recipe); // Production A7 setup invokes the actual A6 proxy start observer.
        this.setups++;
        assertEquals(1, getProgress());
        assertEquals(1, storage.getBatches().size());
        assertFalse(storage.ownsPattern(1));
    }
}
