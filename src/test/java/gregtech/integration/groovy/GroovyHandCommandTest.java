package gregtech.integration.groovy;

import gregtech.Bootstrap;
import gregtech.api.items.metaitem.StandardMetaItem;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.pipelike.fluidpipe.BlockFluidPipe;
import gregtech.common.pipelike.fluidpipe.FluidPipeType;
import gregtech.common.pipelike.fluidpipe.ItemBlockFluidPipe;
import gregtech.core.unification.material.internal.MaterialRegistryImpl;
import gregtech.integration.RecipeCompatUtil;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;

import com.cleanroommc.groovyscript.event.GsHandEvent;
import com.mojang.authlib.GameProfile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GroovyHandCommandTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void nonServerPlayerCanInspectMetaItemsWithoutAServerPlayerCast() {
        EntityPlayer player = new EntityPlayer(new DummyWorld(), new GameProfile(UUID.randomUUID(), "hand_test")) {

            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        for (String namespace : List.of("gregtech", "hand_test_addon")) {
            var item = new StandardMetaItem();
            item.setRegistryName(namespace, "hand_test");
            var stack = item.addItem(0, "test_component").getStackForm();
            var messages = inspect(player, stack);
            assertEquals(1, messages.size());
            String id = namespace.equals("gregtech") ? "test_component" : namespace + ":test_component";
            assertTrue(messages.getFirst().getUnformattedText().contains(id));
            assertTrue(messages.getFirst().getStyle().getClickEvent().getValue().contains("metaitem('" + id + "')"));
        }
    }

    @Test
    void emptyHandDoesNotProduceAMetaItemExpression() {
        assertTrue(inspect(null, ItemStack.EMPTY).isEmpty());
    }

    @Test
    void pipeExpressionUsesItsOwningRegistryRatherThanTheMaterialsNamespace() {
        for (String namespace : List.of("gregtech", "hand_test_carrier")) {
            var registry = new MaterialRegistryImpl(namespace) {};
            registry.register(Materials.Iron);
            var pipe = new BlockFluidPipe(FluidPipeType.TINY, registry);
            var stack = new ItemStack(new ItemBlockFluidPipe(pipe), 1, Materials.Iron.getId());
            String prefix = namespace.equals("gregtech") ? "" : namespace + ":";
            assertEquals(prefix + "pipeTinyFluidIron", RecipeCompatUtil.getMetaItemId(stack));
        }
    }

    private static List<ITextComponent> inspect(EntityPlayer player, ItemStack stack) {
        var messages = new ArrayList<ITextComponent>();
        var event = new GsHandEvent(null, player, new String[0], messages, stack, null, null, null, null, null, false);
        GroovyHandCommand.onHandCommand(event);
        return messages;
    }
}
