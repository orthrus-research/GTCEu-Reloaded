package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.GTValues;
import gregtech.api.capability.RecipeInputView;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.RecipeFailureReason;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityItemBus;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferLink;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferStorage;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static gregtech.api.util.GTUtility.gregtechId;
import static gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PatternBufferA7IntegrationTest {

    private static int nextMap;

    @BeforeAll
    static void setup() {
        bootstrap();
    }

    @Test
    void realA6ProviderFeedsA7WithPaintOffAndOnInBothDistinctModes() {
        for (boolean paint : new boolean[] { false, true }) for (boolean distinct : new boolean[] { false, true }) {
            PatternBufferTestWorld world = new PatternBufferTestWorld();
            PatternBufferTestPart buffer = buffer(world);
            buffer.setPaintingColor(0xFF0000);
            MetaTileEntityItemBus output = output(world);
            output.setPaintingColor(0xFF0000);
            RecipeMap<SimpleRecipeBuilder> map = map();
            addRecipe(map);
            PatternBufferIntegrationController controller = new PatternBufferIntegrationController(map, world, buffer,
                    output);
            controller.setDistinct(distinct);
            controller.setPaintGroupingEnabled(paint);
            controller.logic().search();
            assertEquals(1, controller.logic().getProgress());
            assertEquals(paint ? 0xFF0000 : -3, controller.logic().getActiveOutputColor());
            assertEquals(2, buffer.getPatternStorage().getBatches().get(0).getItems().getStackInSlot(0).getCount());
            controller.logic().finish();
            assertEquals(1, output.getExportItems().getStackInSlot(0).getCount());
        }
    }

    @Test
    void sourceMutationBeforeListenerRegistrationInvalidatesSelectedProxyBeforeAnyConsumption() {
        PatternBufferTestWorld world = new PatternBufferTestWorld();
        PatternBufferTestPart buffer = buffer(world);
        PatternBufferTestProxy proxy = proxy(world, buffer);
        RecipeMap<SimpleRecipeBuilder> map = map();
        addRecipe(map);
        PatternBufferIntegrationController controller = new PatternBufferIntegrationController(map, world, proxy,
                output(world));
        RecipeInputView oldView = proxy.getRecipeInputViews().get(0);
        controller.beforeStart = () -> buffer.getPatternStorage().getSharedItems().insertItem(0,
                new ItemStack(Items.STICK), false);
        controller.logic().search();
        assertEquals(0, controller.logic().getProgress());
        assertEquals(RecipeFailureReason.INPUT_CHANGED, controller.logic().getRecipeFailure().getReason());
        assertEquals(3, buffer.getPatternStorage().getBatches().get(0).getItems().getStackInSlot(0).getCount());
        assertFalse(proxy.isRecipeInputViewValid(oldView));
        // A forced retry enumerates a fresh descriptor without waiting for the automatic wake poll.
        RecipeInputView fresh = proxy.getRecipeInputViews().get(0);
        assertTrue(proxy.isRecipeInputViewValid(fresh));
        controller.logic().search();
        assertEquals(1, controller.logic().getProgress());
        assertEquals(2, buffer.getPatternStorage().getBatches().get(0).getItems().getStackInSlot(0).getCount());
    }

    @Test
    void sourceReloadWithEqualSourceRevisionBeforeFirstPollRejectsOldHandlersAndWakesWithinTwentyTicks() {
        PatternBufferTestWorld world = new PatternBufferTestWorld();
        PatternBufferTestPart original = buffer(world);
        PatternBufferTestProxy proxy = proxy(world, original);
        RecipeMap<SimpleRecipeBuilder> map = map();
        PatternBufferIntegrationController controller = new PatternBufferIntegrationController(map, world, proxy,
                output(world));
        assertTrue(controller.logic().maySearch());
        controller.logic().search();
        controller.getNotifiedItemInputList().clear();
        controller.getNotifiedFluidInputList().clear();
        assertFalse(controller.logic().maySearch());
        RecipeInputView oldView = proxy.getRecipeInputViews().get(0);
        NBTTagCompound saved = original.getPatternStorage().write();
        long oldRevision = original.getPatternStorage().getRevision();
        world.loaded = false;
        int lookups = world.lookups;
        assertFalse(proxy.isRecipeInputViewValid(oldView));
        assertEquals(lookups, world.lookups);
        original.onUnload();
        PatternBufferTestPart loaded = new PatternBufferTestPart();
        world.put(BlockPos.ORIGIN, loaded);
        loaded.getPatternStorage().read(saved);
        assertEquals(oldRevision, loaded.getPatternStorage().getRevision());
        world.loaded = true;
        RecipeInputView fresh = proxy.getRecipeInputViews().get(0);
        assertEquals(oldView.getSelectionKey(), fresh.getSelectionKey());
        assertFalse(proxy.isRecipeInputViewValid(oldView));
        assertTrue(proxy.isRecipeInputViewValid(fresh));
        addRecipe(map); // A real recipe-map change while the source was absent; no input notification is injected.
        long proxyRevision = proxy.getRecipeInputRevision();
        for (proxy.tick = 1; proxy.tick < 20; proxy.tick++) {
            proxy.update();
            assertFalse(controller.logic().maySearch());
            assertEquals(proxyRevision, proxy.getRecipeInputRevision());
        }
        proxy.update();
        assertTrue(proxy.getRecipeInputRevision() > proxyRevision);
        assertTrue(controller.logic().maySearch());
        controller.logic().search();
        assertEquals(1, controller.logic().getProgress());
        assertEquals(2, loaded.getPatternStorage().getBatches().get(0).getItems().getStackInSlot(0).getCount());
        assertEquals(3, original.getPatternStorage().getBatches().get(0).getItems().getStackInSlot(0).getCount());
    }

    private static PatternBufferTestPart buffer(PatternBufferTestWorld world) {
        PatternBufferStorage storage = storage(() -> {});
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 3);
        assertTrue(storage.accept(0, details(iron), table(iron), false));
        PatternBufferTestPart buffer = new PatternBufferTestPart();
        world.put(BlockPos.ORIGIN, buffer);
        buffer.getPatternStorage().read(storage.write());
        return buffer;
    }

    private static PatternBufferTestProxy proxy(PatternBufferTestWorld world, PatternBufferTestPart source) {
        PatternBufferTestProxy proxy = new PatternBufferTestProxy(source.owner);
        world.put(BlockPos.ORIGIN.east(), proxy);
        NBTTagCompound link = new NBTTagCompound();
        link.setTag("PatternBufferLink", PatternBufferLink.capture(source).write());
        proxy.initFromItemStackData(link);
        return proxy;
    }

    private static MetaTileEntityItemBus output(PatternBufferTestWorld world) {
        MetaTileEntityItemBus output = new MetaTileEntityItemBus(gregtechId("pattern_integration_output"), GTValues.IV,
                true);
        world.put(BlockPos.ORIGIN.north(), output);
        return output;
    }

    private static RecipeMap<SimpleRecipeBuilder> map() {
        return new RecipeMapBuilder<>("pattern_integration_" + nextMap++, new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1).build();
    }

    private static void addRecipe(RecipeMap<SimpleRecipeBuilder> map) {
        assertTrue(map.compileRecipe(map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(2).EUt(1).build().getResult()));
    }
}
