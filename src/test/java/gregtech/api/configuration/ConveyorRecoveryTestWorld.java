package gregtech.api.configuration;

import gregtech.api.util.world.DummyWorld;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Records real inventory-drop API output without a chunk or entity tick simulation. */
final class ConveyorRecoveryTestWorld extends DummyWorld {

    final List<ItemStack> dropped = new ArrayList<>();

    @Override
    public boolean spawnEntity(Entity entity) {
        if (!(entity instanceof EntityItem item)) throw new AssertionError("Unexpected recovery entity");
        dropped.add(item.getItem().copy());
        return true;
    }
}
