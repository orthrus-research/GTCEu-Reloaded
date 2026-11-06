package gregtech.api.items.toolitem;

import gregtech.Bootstrap;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolHelperMethodHandleTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void silkTouchHandleCallsNativeAndOverriddenProtectedMethods() {
        ItemStack glass = ToolHelper.getSilkTouchDrop(Blocks.GLASS.getDefaultState());
        assertEquals(Item.getItemFromBlock(Blocks.GLASS), glass.getItem());
        assertEquals(1, glass.getCount());
        Block custom = new Block(Material.ROCK) {

            @Override
            protected ItemStack getSilkTouchDrop(IBlockState state) {
                return new ItemStack(Items.APPLE, 7);
            }
        };
        ItemStack overridden = ToolHelper.getSilkTouchDrop(custom.getDefaultState());
        assertEquals(Items.APPLE, overridden.getItem());
        assertEquals(7, overridden.getCount());
    }
}
