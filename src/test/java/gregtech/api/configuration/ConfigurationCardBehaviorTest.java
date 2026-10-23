package gregtech.api.configuration;

import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.SimpleMachineMetaTileEntity;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.common.items.MetaItems;
import gregtech.common.items.behaviors.ConfigurationCardBehavior;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;

import com.mojang.authlib.GameProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigurationCardBehaviorTest {

    private static final BlockPos POSITION = new BlockPos(0, 64, 0);
    private static RecipeMap<SimpleRecipeBuilder> recipeMap;
    private static ConfigurationTestItems testItems;

    @BeforeAll
    public static void bootstrap() {
        testItems = ConfigurationTestItems.setup();
        recipeMap = new RecipeMapBuilder<>("configuration_behavior_test", new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1).fluidInputs(1).fluidOutputs(1).build();
    }

    @AfterAll
    public static void restoreItems() {
        testItems.restore();
    }

    @Test
    public void itemCopiesReplacesAndPastesWithoutChangingUnrelatedCardNbtOrDataSticks() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        SimpleMachineMetaTileEntity machine = machine(world);
        EntityPlayer player = player(world);
        ItemStack card = MetaItems.CONFIGURATION_CARD.getStackForm();
        assertEquals(1, card.getMaxStackSize());
        NBTTagCompound cardData = new NBTTagCompound();
        cardData.setString("OtherItemData", "retained");
        NBTTagCompound staleClipboard = new NBTTagCompound();
        staleClipboard.setString("Stale", "must disappear");
        cardData.setTag(ConfigurationClipboard.ITEM_KEY, staleClipboard);
        card.setTagCompound(cardData);
        ItemStack dataStick = MetaItems.TOOL_DATA_STICK.getStackForm();
        NBTTagCompound legacyData = new NBTTagCompound();
        legacyData.setString("AssemblyLineResearch", "untouched");
        dataStick.setTagCompound(legacyData);
        NBTTagCompound beforeDataStick = dataStick.writeToNBT(new NBTTagCompound());
        assertTrue(MetaItems.TOOL_DATA_STICK.getBehaviours().stream()
                .noneMatch(behavior -> behavior instanceof ConfigurationCardBehavior));

        ConfigurationCardBehavior behavior = new ConfigurationCardBehavior();
        player.setHeldItem(EnumHand.MAIN_HAND, card);
        player.setSneaking(true);
        machine.setGhostCircuitConfig(14);
        assertEquals(EnumActionResult.SUCCESS, use(behavior, player, world, EnumHand.MAIN_HAND));
        assertEquals("retained", card.getTagCompound().getString("OtherItemData"));
        assertFalse(card.getTagCompound().getCompoundTag(ConfigurationClipboard.ITEM_KEY).hasKey("Stale"));
        machine.setGhostCircuitConfig(-1);
        assertEquals(EnumActionResult.SUCCESS, use(behavior, player, world, EnumHand.MAIN_HAND));
        machine.setGhostCircuitConfig(32);
        player.setSneaking(false);
        assertEquals(EnumActionResult.SUCCESS, use(behavior, player, world, EnumHand.MAIN_HAND));
        assertEquals(-1, machine.getConfigurationAdapter().capture().getInteger("Circuit"));
        assertEquals(beforeDataStick, dataStick.writeToNBT(new NBTTagCompound()));
    }

    @Test
    public void emptyMalformedOffhandAndDeniedOperationsLeaveTargetAndCardUntouched() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        SimpleMachineMetaTileEntity machine = machine(world);
        EntityPlayer player = player(world);
        ItemStack card = MetaItems.CONFIGURATION_CARD.getStackForm();
        player.setHeldItem(EnumHand.MAIN_HAND, card);
        ConfigurationCardBehavior behavior = new ConfigurationCardBehavior();
        NBTTagCompound before = machine.getConfigurationAdapter().capture();
        assertEquals(EnumActionResult.FAIL, use(behavior, player, world, EnumHand.MAIN_HAND));
        assertEquals(EnumActionResult.PASS, use(behavior, player, world, EnumHand.OFF_HAND));
        NBTTagCompound malformed = new NBTTagCompound();
        malformed.setTag(ConfigurationClipboard.ITEM_KEY, new NBTTagCompound());
        card.setTagCompound(malformed);
        assertEquals(EnumActionResult.FAIL, use(behavior, player, world, EnumHand.MAIN_HAND));
        player.capabilities.allowEdit = false;
        player.setSneaking(true);
        assertEquals(EnumActionResult.FAIL, use(behavior, player, world, EnumHand.MAIN_HAND));
        assertEquals(malformed, card.getTagCompound());
        assertEquals(before, machine.getConfigurationAdapter().capture());
    }

    private static SimpleMachineMetaTileEntity machine(ConfigurationTestWorld world) {
        SimpleMachineMetaTileEntity machine = new SimpleMachineMetaTileEntity(
                GTUtility.gregtechId("configuration_behavior_machine"), recipeMap, null, 1, true);
        MetaTileEntityHolder holder = new MetaTileEntityHolder() {

            {
                setRawMetaTileEntity(machine);
            }
        };
        world.put(POSITION, holder, Blocks.AIR.getDefaultState());
        return machine;
    }

    private static EntityPlayer player(ConfigurationTestWorld world) {
        return new EntityPlayer(world, new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "configuration_test")) {

            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }

            @Override
            public void sendStatusMessage(ITextComponent text, boolean actionBar) {}
        };
    }

    private static EnumActionResult use(ConfigurationCardBehavior behavior, EntityPlayer player,
                                        ConfigurationTestWorld world, EnumHand hand) {
        return behavior.onItemUseFirst(player, world, POSITION, EnumFacing.NORTH, 0.5f, 0.5f, 0.5f, hand);
    }
}
