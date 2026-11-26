package gregtech.mixins;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.toolitem.IGTTool;
import gregtech.mixins.ae2.AE2WrenchMixin;
import gregtech.mixins.buildcraft.BuildCraftWrenchMixin;
import gregtech.mixins.cofh.CoFHHammerMixin;
import gregtech.mixins.endercore.EnderCoreOverlayMixin;
import gregtech.mixins.enderio.EnderIOToolMixin;
import gregtech.mixins.forestry.ForestryGrafterMixin;
import gregtech.mixins.projectred.ProjectRedScrewdriverMixin;
import gregtech.mixins.railcraft.RailcraftCrowbarMixin;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A tool interface mixin adds an interface and no methods, so the classes it targets must already implement every
 * method of that interface; a missing one would fail with {@link AbstractMethodError} when the other mod calls it.
 */
class ToolInterfaceMixinTest {

    private static final Map<Class<?>, List<Class<?>>> IMPLEMENTERS = Map.of(
            AE2WrenchMixin.class, List.of(IGTTool.class),
            BuildCraftWrenchMixin.class, List.of(IGTTool.class),
            CoFHHammerMixin.class, List.of(IGTTool.class),
            EnderIOToolMixin.class, List.of(IGTTool.class),
            EnderCoreOverlayMixin.class, List.of(IGTTool.class, MetaItem.class),
            ForestryGrafterMixin.class, List.of(IGTTool.class),
            ProjectRedScrewdriverMixin.class, List.of(IGTTool.class),
            RailcraftCrowbarMixin.class, List.of(IGTTool.class));

    @Test
    void theTargetedTypesImplementEveryMethodOfTheAddedInterfaces() throws Exception {
        for (var entry : IMPLEMENTERS.entrySet()) {
            Class<?>[] added = entry.getKey().getInterfaces();
            assertEquals(1, added.length, entry.getKey().getName());
            for (Method method : added[0].getMethods()) {
                if (!Modifier.isAbstract(method.getModifiers())) continue;
                for (Class<?> implementer : entry.getValue()) {
                    Method implementation = implementer.getMethod(method.getName(), method.getParameterTypes());
                    String name = implementer.getSimpleName() + "." + method.getName();
                    assertFalse(Modifier.isAbstract(implementation.getModifiers()), name + " is abstract");
                    assertEquals(method.getReturnType(), implementation.getReturnType(), name);
                }
            }
        }
    }

    @Test
    void thePublishedTypesNameNoOtherModsInterface() {
        for (Class<?> type : List.of(IGTTool.class, MetaItem.class)) {
            for (Class<?> supertype : type.getInterfaces()) {
                assertTrue(supertype.getName().startsWith("gregtech.") ||
                        supertype.getName().startsWith("com.cleanroommc.modularui."), supertype.getName());
            }
        }
    }
}
