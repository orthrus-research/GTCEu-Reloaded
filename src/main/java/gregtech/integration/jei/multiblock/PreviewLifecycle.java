package gregtech.integration.jei.multiblock;

import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import mezz.jei.gui.recipes.RecipesGui;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

/** Registered only by the client JEI category, so the optional dependency never enters common event handling. */
public final class PreviewLifecycle {

    private static final PreviewLifecycle INSTANCE = new PreviewLifecycle();
    private static final Map<EditableMultiblockInfoRecipeWrapper, Long> ACTIVE = new HashMap<>();
    private static boolean registered;
    private static long tick;

    static void register() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(INSTANCE);
            registered = true;
        }
    }

    static void touch(EditableMultiblockInfoRecipeWrapper wrapper) {
        ACTIVE.put(wrapper, tick);
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!(event.getGui() instanceof RecipesGui))
            expire(ACTIVE, Long.MAX_VALUE, EditableMultiblockInfoRecipeWrapper::release);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tick++;
            // A category change may keep the same RecipesGui instance. Keep only recently drawn previews.
            expire(ACTIVE, tick - 20, EditableMultiblockInfoRecipeWrapper::release);
        }
    }

    static <T> void expire(Map<T, Long> sessions, long before, Consumer<T> close) {
        Iterator<Map.Entry<T, Long>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<T, Long> entry = iterator.next();
            if (entry.getValue() < before) {
                close.accept(entry.getKey());
                iterator.remove();
            }
        }
    }
}
