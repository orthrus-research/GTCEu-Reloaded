package gregtech.common.items.behaviors;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaItemGuiFactory;
import gregtech.common.items.MetaItems;
import gregtech.common.metatileentities.MetaTileEntityClipboard;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import codechicken.lib.raytracer.RayTracer;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

import static gregtech.common.metatileentities.MetaTileEntities.CLIPBOARD_TILE;

public class ClipboardBehavior implements IItemBehaviour, ItemUIFactory {

    public static final int MAX_PAGES = 25;
    public static final int TASKS = 8;
    public static final int BUTTON_STATES = 4;
    public static final int MAX_TITLE_LENGTH = 25;
    public static final int MAX_TASK_LENGTH = 23;
    public static final int TEXT_COLOR = 0xFF1E1E1E;
    private static final int WIDTH = 186;
    private static final int HEIGHT = 263;

    @Override
    public ModularPanel buildUI(HandGuiData guiData, PanelSyncManager syncManager, UISettings settings) {
        return createPanel(guiData::getUsedItemStack, () -> {}, syncManager);
    }

    /**
     * The clipboard editor. Every change a client sends is applied on the server through the setters below, which
     * check it, and then {@code onEdit} runs.
     *
     * @param clipboard the clipboard being edited
     * @param onEdit    run on the server after each change
     */
    public static ModularPanel createPanel(Supplier<ItemStack> clipboard, Runnable onEdit,
                                           PanelSyncManager syncManager) {
        initNBT(clipboard.get());
        IntSyncValue page = new IntSyncValue(() -> getPageNum(clipboard.get()), value -> {
            setPage(clipboard.get(), value);
            onEdit.run();
        });
        StringSyncValue title = new StringSyncValue(() -> getTitle(clipboard.get()), value -> {
            if (setTitle(clipboard.get(), value)) onEdit.run();
        });
        syncManager.syncValue("page", page);
        syncManager.syncValue("title", title);

        ModularPanel panel = GTGuis.createPanel("clipboard", WIDTH, HEIGHT)
                .background(GTGuiTextures.CLIPBOARD_BACKGROUND)
                .child(new TextFieldWidget()
                        .pos(28, 28)
                        .size(130, 12)
                        .value(title)
                        .setMaxLength(MAX_TITLE_LENGTH)
                        .setTextAlignment(Alignment.Center)
                        .setTextColor(TEXT_COLOR)
                        .background(GTGuiTextures.CLIPBOARD_TEXT_BOX)
                        .hoverBackground(GTGuiTextures.CLIPBOARD_TEXT_BOX));

        for (int i = 0; i < TASKS; i++) {
            final int line = i;
            IntSyncValue button = new IntSyncValue(() -> getButtonState(clipboard.get(), line), value -> {
                if (setButton(clipboard.get(), line, value)) onEdit.run();
            });
            StringSyncValue task = new StringSyncValue(() -> getString(clipboard.get(), line), value -> {
                if (setString(clipboard.get(), line, value)) onEdit.run();
            });
            syncManager.syncValue("button", line, button);
            syncManager.syncValue("task", line, task);

            CycleButtonWidget cycle = new CycleButtonWidget()
                    .pos(14, 55 + 22 * line)
                    .size(15)
                    .stateCount(BUTTON_STATES)
                    .value(button)
                    .disableHoverBackground();
            for (int state = 0; state < BUTTON_STATES; state++) {
                cycle.stateBackground(state, GTGuiTextures.CLIPBOARD_BUTTON[state]);
            }
            panel.child(cycle)
                    .child(new TextFieldWidget()
                            .pos(32, 58 + 22 * line)
                            .size(140, 12)
                            .value(task)
                            .setMaxLength(MAX_TASK_LENGTH)
                            .setTextColor(TEXT_COLOR)
                            .background(GTGuiTextures.CLIPBOARD_TEXT_BOX)
                            .hoverBackground(GTGuiTextures.CLIPBOARD_TEXT_BOX));
        }

        return panel
                .child(pageButton(page, -1).pos(38, 231).background(GTGuiTextures.BUTTON_LEFT))
                .child(pageButton(page, 1).pos(132, 231).background(GTGuiTextures.BUTTON_RIGHT))
                .child(IKey.dynamic(() -> (page.getIntValue() + 1) + " / " + MAX_PAGES)
                        .color(TEXT_COLOR)
                        .shadow(false)
                        .asWidget()
                        .alignment(Alignment.Center)
                        .pos(68, 236)
                        .size(50, 9));
    }

    private static ButtonWidget<?> pageButton(IntSyncValue page, int direction) {
        return new ButtonWidget<>()
                .size(16)
                .disableHoverBackground()
                .onMousePressed(mouseButton -> {
                    page.setIntValue(page.getIntValue() + direction * (Interactable.hasShiftDown() ? 10 : 1));
                    return true;
                });
    }

    /** @return the tag of the page the clipboard shows, or null if the stack is not a clipboard */
    private static @Nullable NBTTagCompound getPageCompound(ItemStack stack) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack)) return null;
        initNBT(stack);
        NBTTagCompound tagCompound = stack.getTagCompound();
        String key = "Page" + getPageNum(stack);
        if (!tagCompound.hasKey(key, Constants.NBT.TAG_COMPOUND)) {
            tagCompound.setTag(key, new NBTTagCompound());
        }
        return tagCompound.getCompoundTag(key);
    }

    public static void initNBT(ItemStack stack) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack))
            return;
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
            tagCompound.setShort("PageIndex", (short) 0);
            tagCompound.setShort("TotalPages", (short) 0);

            NBTTagCompound pageCompound = new NBTTagCompound();
            pageCompound.setShort("ButStat", (short) 0);
            pageCompound.setString("Title", "");
            for (int i = 0; i < TASKS; i++) {
                pageCompound.setString("Task" + i, "");
            }

            for (int i = 0; i < MAX_PAGES; i++) {
                tagCompound.setTag("Page" + i, pageCompound.copy());
            }

            stack.setTagCompound(tagCompound);
        }
    }

    /**
     * Sets a task's check box on the current page.
     *
     * @return whether the line and state were valid and the box changed
     */
    public static boolean setButton(ItemStack stack, int line, int newState) {
        NBTTagCompound tagCompound = getPageCompound(stack);
        if (tagCompound == null || line < 0 || line >= TASKS || newState < 0 || newState >= BUTTON_STATES) {
            return false;
        }
        short buttonState = tagCompound.getShort("ButStat");
        short clearedState = (short) (buttonState & ~(3 << (line * 2))); // clear out the line's two bits
        short newButtonState = (short) (clearedState | (newState << (line * 2))); // and set the new state
        if (newButtonState == buttonState) return false;
        tagCompound.setShort("ButStat", newButtonState);
        return true;
    }

    public static int getButtonState(ItemStack stack, int line) {
        NBTTagCompound tagCompound = getPageCompound(stack);
        if (tagCompound == null || line < 0 || line >= TASKS) return 0;
        return (tagCompound.getShort("ButStat") >> line * 2) & 3;
    }

    /**
     * Sets a task's text on the current page, cut to {@link #MAX_TASK_LENGTH} characters.
     *
     * @return whether the line was valid and the text changed
     */
    public static boolean setString(ItemStack stack, int line, @Nullable String newString) {
        NBTTagCompound tagCompound = getPageCompound(stack);
        if (tagCompound == null || newString == null || line < 0 || line >= TASKS) return false;
        String text = limit(newString, MAX_TASK_LENGTH);
        if (text.equals(tagCompound.getString("Task" + line))) return false;
        tagCompound.setString("Task" + line, text);
        return true;
    }

    public static String getString(ItemStack stack, int line) {
        NBTTagCompound tagCompound = getPageCompound(stack);
        if (tagCompound == null || line < 0 || line >= TASKS) return "";
        return tagCompound.getString("Task" + line);
    }

    /**
     * Sets the current page's title, cut to {@link #MAX_TITLE_LENGTH} characters.
     *
     * @return whether the title changed
     */
    public static boolean setTitle(ItemStack stack, @Nullable String newString) {
        NBTTagCompound tagCompound = getPageCompound(stack);
        if (tagCompound == null || newString == null) return false;
        String text = limit(newString, MAX_TITLE_LENGTH);
        if (text.equals(tagCompound.getString("Title"))) return false;
        tagCompound.setString("Title", text);
        return true;
    }

    public static String getTitle(ItemStack stack) {
        NBTTagCompound tagCompound = getPageCompound(stack);
        return tagCompound == null ? "" : tagCompound.getString("Title");
    }

    /** @return the page the clipboard shows, from 0 to {@link #MAX_PAGES} - 1 */
    public static int getPageNum(ItemStack stack) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack) || stack.getTagCompound() == null) return 0;
        int page = stack.getTagCompound().getInteger("PageIndex");
        return Math.max(0, Math.min(page, MAX_PAGES - 1));
    }

    /** Turns to a page, kept between the first and the last. */
    public static void setPage(ItemStack stack, int page) {
        if (!MetaItems.CLIPBOARD.isItemEqual(stack)) return;
        initNBT(stack);
        stack.getTagCompound().setInteger("PageIndex", Math.max(Math.min(page, MAX_PAGES - 1), 0));
    }

    private static String limit(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        // so that the player doesn't place a clipboard before suddenly getting the GUI
        if (!world.isRemote && RayTracer.retrace(player).typeOfHit != RayTraceResult.Type.BLOCK) {
            MetaItemGuiFactory.open(player, hand);
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, heldItem);
    }

    @Override
    public ActionResult<ItemStack> onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                             EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && facing.getAxis() != EnumFacing.Axis.Y) {
            ItemStack heldItem = player.getHeldItem(hand).copy();
            heldItem.setCount(1); // don't place multiple items at a time
            // Make sure it's the right block
            IBlockState testState = world.getBlockState(pos);
            Block testBlock = testState.getBlock();
            if (!testBlock.isAir(world.getBlockState(pos), world, pos) && testState.isSideSolid(world, pos, facing)) {
                // Step away from the block so that you don't replace it, and then give it our fun blockstate
                BlockPos shiftedPos = pos.offset(facing);
                Block shiftedBlock = world.getBlockState(shiftedPos).getBlock();
                if (shiftedBlock.isAir(world.getBlockState(shiftedPos), world, shiftedPos)) {
                    IBlockState state = GregTechAPI.mteManager.getRegistry(GTValues.MODID).getBlock().getDefaultState();
                    world.setBlockState(shiftedPos, state);
                    // Get new TE
                    shiftedBlock.createTileEntity(world, state);
                    // And manipulate it to our liking
                    IGregTechTileEntity holder = (IGregTechTileEntity) world.getTileEntity(shiftedPos);
                    if (holder != null) {
                        MetaTileEntityClipboard clipboard = (MetaTileEntityClipboard) holder
                                .setMetaTileEntity(CLIPBOARD_TILE);
                        if (clipboard != null) {
                            clipboard.initializeClipboard(heldItem);
                            clipboard.setFrontFacing(facing.getOpposite());
                            ItemStack returnedStack = player.getHeldItem(hand);
                            if (!player.isCreative()) {
                                returnedStack.setCount(player.getHeldItem(hand).getCount() - 1);
                            }
                            return ActionResult.newResult(EnumActionResult.SUCCESS, returnedStack);
                        }
                    }
                }
            }
        }
        return ActionResult.newResult(EnumActionResult.PASS, player.getHeldItem(hand));
    }
}
