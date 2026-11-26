package gregtech.common.metatileentities;

import gregtech.api.items.itemhandlers.InaccessibleItemStackHandler;
import gregtech.api.items.toolitem.ToolClasses;
import gregtech.api.metatileentity.IFastRenderMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.factory.MetaTileEntityGuiFactory;
import gregtech.api.util.GTLog;
import gregtech.client.renderer.texture.custom.ClipboardRenderer;
import gregtech.common.items.behaviors.ClipboardBehavior;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.raytracer.IndexedCuboid6;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import codechicken.lib.vec.Vector3;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static codechicken.lib.raytracer.RayTracer.*;
import static gregtech.api.capability.GregtechDataCodes.*;
import static gregtech.client.renderer.texture.Textures.CLIPBOARD_RENDERER;
import static gregtech.common.items.MetaItems.CLIPBOARD;

public class MetaTileEntityClipboard extends MetaTileEntity implements IFastRenderMetaTileEntity {

    private static final AxisAlignedBB CLIPBOARD_AABB_NORTH = new AxisAlignedBB(2.75 / 16.0, 0, 0, 13.25 / 16.0,
            16 / 16.0, 0.4 / 16.0);
    private static final AxisAlignedBB CLIPBOARD_AABB_SOUTH = new AxisAlignedBB(13.25 / 16.0, 0, 16 / 16.0, 2.75 / 16.0,
            16 / 16.0, 15.6 / 16.0);
    private static final AxisAlignedBB CLIPBOARD_AABB_WEST = new AxisAlignedBB(0, 0, 13.25 / 16.0, 0.4 / 16.0,
            16 / 16.0, 2.75 / 16.0);
    private static final AxisAlignedBB CLIPBOARD_AABB_EAST = new AxisAlignedBB(16 / 16.0, 0, 2.75 / 16.0, 15.6 / 16.0,
            16 / 16.0, 13.25 / 16.0);

    private static final AxisAlignedBB PAGE_AABB_NORTH = new AxisAlignedBB(3 / 16.0, 0.25 / 16.0, 0.25 / 16.0,
            13 / 16.0, 14.25 / 16.0, 0.3 / 16.0);
    private static final AxisAlignedBB PAGE_AABB_SOUTH = new AxisAlignedBB(13 / 16.0, 0.25 / 16.0, 15.75 / 16.0,
            3 / 16.0, 14.25 / 16.0, 15.7 / 16.0);
    private static final AxisAlignedBB PAGE_AABB_WEST = new AxisAlignedBB(0.25 / 16.0, 0.25 / 16.0, 13 / 16.0,
            0.3 / 16.0, 14.25 / 16.0, 3 / 16.0);
    private static final AxisAlignedBB PAGE_AABB_EAST = new AxisAlignedBB(15.75 / 16.0, 0.25 / 16.0, 3 / 16.0,
            15.7 / 16.0, 14.25 / 16.0, 13 / 16.0);

    public static final float scale = 1;
    /** The page shown on a placed clipboard, in its own pixels; see {@link #clickPage}. */
    public static final int PAGE_WIDTH = 170;
    public static final int PAGE_HEIGHT = 238;
    public static final int PAGE_TASK_Y = 37;
    public static final int PAGE_TASK_SPACING = 20;
    public static final int PAGE_BUTTON_X = 6;
    public static final int PAGE_BUTTON_SIZE = 15;
    public static final int PAGE_ARROW_Y = 200;
    public static final int PAGE_LEFT_X = 30;
    public static final int PAGE_RIGHT_X = 124;
    public static final int PAGE_ARROW_SIZE = 16;
    private static final NBTBase NO_CLIPBOARD_SIG = new NBTTagInt(0);
    private boolean didSetFacing = false;

    public MetaTileEntityClipboard(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
    }

    @Override
    public int getLightOpacity() {
        return 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderMetaTileEntityFast(CCRenderState renderState, Matrix4 translation, float partialTicks) {
        ClipboardRenderer.renderBoard(renderState, translation.copy(), new IVertexOperation[] {}, getFrontFacing(),
                this, partialTicks);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderMetaTileEntity(double x, double y, double z, float partialTicks) {
        if (this.getClipboard() != null)
            ClipboardRenderer.renderGUI(x, y, z, this.getFrontFacing(), this, partialTicks);
    }

    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(getPos().add(-1, 0, -1), getPos().add(2, 2, 2));
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        return null;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityClipboard(metaTileEntityId);
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager, UISettings settings) {
        settings.canInteractWith(player -> isValid() && CLIPBOARD.isItemEqual(getClipboard()));
        return ClipboardBehavior.createPanel(this::getClipboard, this::onClipboardEdited, panelSyncManager);
    }

    /** Server: saves the clipboard and shows the change on the placed clipboard. */
    private void onClipboardEdited() {
        if (getWorld() == null || getWorld().isRemote) return;
        markDirty();
        NBTTagCompound tag = getClipboard().getTagCompound();
        writeCustomData(INIT_CLIPBOARD_NBT, buf -> buf.writeCompoundTag(tag));
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        this.itemInventory = new InaccessibleItemStackHandler(this);
    }

    public ItemStack getClipboard() {
        if (this.itemInventory.getStackInSlot(0).isEmpty()) {
            ((InaccessibleItemStackHandler) this.itemInventory).setStackInSlot(0, CLIPBOARD.getStackForm());
        }
        return this.itemInventory.getStackInSlot(0);
    }

    public void initializeClipboard(ItemStack stack) {
        ((InaccessibleItemStackHandler) this.itemInventory).setStackInSlot(0, stack.copy());
        writeCustomData(INIT_CLIPBOARD_NBT, buf -> {
            buf.writeCompoundTag(stack.getTagCompound());
        });
    }

    public void setClipboard(ItemStack stack) {
        ((InaccessibleItemStackHandler) this.itemInventory).setStackInSlot(0, stack.copy());
    }

    @Override
    public void getDrops(@NotNull List<@NotNull ItemStack> dropsList, @Nullable EntityPlayer harvester) {
        dropsList.clear();
        dropsList.add(this.getClipboard());
    }

    @Override
    public float getBlockHardness() {
        return 100;
    }

    @Override
    public int getHarvestLevel() {
        return 4;
    }

    @Override
    public boolean onRightClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                CuboidRayTraceResult hitResult) {
        if (!playerIn.isSneaking()) {
            if (getWorld() != null && !getWorld().isRemote) {
                MetaTileEntityGuiFactory.open(playerIn, this);
            }
        } else {
            breakClipboard(playerIn);
        }
        return true;
    }

    @Override
    public boolean onWrenchClick(EntityPlayer playerIn, EnumHand hand, EnumFacing wrenchSide,
                                 CuboidRayTraceResult hitResult) {
        return false;
    }

    private void breakClipboard(@Nullable EntityPlayer player) {
        if (!getWorld().isRemote) {
            BlockPos pos = this.getPos(); // Saving this for later so it doesn't get mangled
            World world = this.getWorld(); // Same here

            List<ItemStack> drops = new ArrayList<>();
            getDrops(drops, player);

            Block.spawnAsEntity(getWorld(), pos, drops.get(0));
            this.dropAllCovers();
            this.onRemoval();

            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
        }
    }

    @Override
    public void onNeighborChanged() {
        if (!getWorld().isRemote && didSetFacing) {
            BlockPos pos = getPos().offset(getFrontFacing());
            IBlockState state = getWorld().getBlockState(pos);
            if (state.getBlock().isAir(state, getWorld(), pos) ||
                    !state.isSideSolid(getWorld(), pos, getFrontFacing())) {
                breakClipboard(null);
            }
        }
    }

    @Override
    public void setFrontFacing(EnumFacing frontFacing) {
        super.setFrontFacing(frontFacing);
        this.didSetFacing = true;
    }

    @Override
    public String getHarvestTool() {
        return ToolClasses.AXE;
    }

    @Override
    public void addCollisionBoundingBox(List<IndexedCuboid6> collisionList) {
        AxisAlignedBB aabb;
        switch (this.getFrontFacing()) {
            case SOUTH:
                aabb = CLIPBOARD_AABB_SOUTH;
                break;
            case WEST:
                aabb = CLIPBOARD_AABB_WEST;
                break;
            case EAST:
                aabb = CLIPBOARD_AABB_EAST;
                break;
            default:
                aabb = CLIPBOARD_AABB_NORTH;
        }
        collisionList.add(new IndexedCuboid6(null, aabb));
    }

    public IndexedCuboid6 getPageCuboid() {
        AxisAlignedBB aabb;
        switch (this.getFrontFacing()) {
            case SOUTH:
                aabb = PAGE_AABB_SOUTH;
                break;
            case WEST:
                aabb = PAGE_AABB_WEST;
                break;
            case EAST:
                aabb = PAGE_AABB_EAST;
                break;
            default:
                aabb = PAGE_AABB_NORTH;
        }
        return new IndexedCuboid6(null, aabb);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Pair<TextureAtlasSprite, Integer> getParticleTexture() {
        return Pair.of(CLIPBOARD_RENDERER.getParticleTexture(), 0xFFFFFF);
    }

    public Pair<Double, Double> checkLookingAt(EntityPlayer player) {
        if (this.getWorld() != null && player != null) {
            Vec3d startVec = getStartVec(player);
            Vec3d endVec = getEndVec(player);
            CuboidRayTraceResult rayTraceResult = rayTrace(this.getPos(), new Vector3(startVec), new Vector3(endVec),
                    getPageCuboid());
            if (rayTraceResult != null && rayTraceResult.sideHit == this.getFrontFacing().getOpposite()) {
                TileEntity tileEntity = this.getWorld().getTileEntity(rayTraceResult.getBlockPos());
                if (tileEntity instanceof IGregTechTileEntity &&
                        ((IGregTechTileEntity) tileEntity).getMetaTileEntity() instanceof MetaTileEntityClipboard) {
                    double[] pos = handleRayTraceResult(rayTraceResult, this.getFrontFacing().getOpposite());
                    if (pos[0] >= 0 && pos[0] <= 1 && pos[1] >= 0 && pos[1] <= 1)
                        return Pair.of(pos[0], pos[1]);
                }
            }
        }
        return null;
    }

    private static double[] handleRayTraceResult(CuboidRayTraceResult rayTraceResult, EnumFacing spin) {
        double x, y;
        double dX = rayTraceResult.sideHit.getAxis() == EnumFacing.Axis.X ?
                rayTraceResult.hitVec.z - rayTraceResult.getBlockPos().getZ() :
                rayTraceResult.hitVec.x - rayTraceResult.getBlockPos().getX();
        double dY = rayTraceResult.sideHit.getAxis() == EnumFacing.Axis.Y ?
                rayTraceResult.hitVec.z - rayTraceResult.getBlockPos().getZ() :
                rayTraceResult.hitVec.y - rayTraceResult.getBlockPos().getY();
        if (spin == EnumFacing.NORTH) {
            x = 1 - dX;
        } else if (spin == EnumFacing.SOUTH) {
            x = dX;
        } else if (spin == EnumFacing.EAST) {
            x = 1 - dX;
            if (rayTraceResult.sideHit.getXOffset() < 0 || rayTraceResult.sideHit.getZOffset() > 0) {
                x = 1 - x;
            }
        } else {
            x = 1 - dX;
            if (rayTraceResult.sideHit.getXOffset() < 0 || rayTraceResult.sideHit.getZOffset() > 0) {
                x = 1 - x;
            }
        }

        y = 1 - dY; // Since y values are quite weird here

        // Scale these to be 0 - 1
        x -= 3.0 / 16;
        y -= 1.75 / 16;
        x /= 14.0 / 16;
        y /= 14.0 / 16;

        return new double[] { x, y };
    }

    @Override
    public boolean isValidFrontFacing(EnumFacing facing) {
        return false;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        if (this.getClipboard() != null && this.getClipboard().getTagCompound() != null)
            data.setTag("clipboardNBT", this.getClipboard().getTagCompound());
        else
            data.setTag("clipboardNBT", NO_CLIPBOARD_SIG);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        NBTBase clipboardNBT = data.getTag("clipboardNBT");
        if (clipboardNBT instanceof NBTTagCompound) {
            ItemStack clipboard = this.getClipboard();
            clipboard.setTagCompound((NBTTagCompound) clipboardNBT);
            this.setClipboard(clipboard);
        }
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        if (this.getClipboard() != null && this.getClipboard().getTagCompound() != null)
            buf.writeCompoundTag(this.getClipboard().getTagCompound());
        else {
            buf.writeCompoundTag(new NBTTagCompound());
        }
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        try {
            NBTTagCompound clipboardNBT = buf.readCompoundTag();
            if (clipboardNBT != null && !clipboardNBT.equals(new NBTTagCompound())) {
                ItemStack clipboard = this.getClipboard();
                clipboard.setTagCompound(clipboardNBT);
                this.setClipboard(clipboard);
            }
        } catch (Exception e) {
            GTLog.logger.error("Could not initialize Clipboard from InitialSyncData buffer", e);
        }
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == INIT_CLIPBOARD_NBT) {
            try {
                NBTTagCompound clipboardNBT = buf.readCompoundTag();
                ItemStack clipboard = this.getClipboard();
                clipboard.setTagCompound(clipboardNBT);
                this.setClipboard(clipboard);
                this.scheduleRenderUpdate();
            } catch (Exception e) {
                GTLog.logger.error("Could not read Clipboard Init NBT from CustomData buffer", e);
            }
        }
    }

    @Override
    public void getSubItems(CreativeTabs creativeTab, NonNullList<ItemStack> subItems) { // JEI shouldn't show this
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {}

    @Override
    public void onLeftClick(EntityPlayer player, EnumFacing facing, CuboidRayTraceResult hitResult) {
        if (this.getWorld().isRemote) return;
        Pair<Double, Double> clickCoords = this.checkLookingAt(player);
        if (clickCoords == null) return; // the click missed the page
        // the page is drawn in a square as wide as it is tall
        double pageX = clickCoords.getLeft() * PAGE_HEIGHT;
        double pageY = clickCoords.getRight() * PAGE_HEIGHT;
        if (clickPage(getClipboard(), pageX, pageY, player.isSneaking())) {
            onClipboardEdited();
        }
    }

    /**
     * A left click on the placed clipboard's page: a task's check box moves to its next state, and the arrows turn
     * one page, or ten while sneaking.
     *
     * @param pageX where the click hit, in the page's pixels from its left edge
     * @param pageY where the click hit, in the page's pixels from its top edge
     * @return whether the clipboard changed
     */
    public static boolean clickPage(ItemStack clipboard, double pageX, double pageY, boolean sneaking) {
        if (inside(pageX, pageY, PAGE_LEFT_X, PAGE_ARROW_Y, PAGE_ARROW_SIZE)) {
            return turnPage(clipboard, sneaking ? -10 : -1);
        } else if (inside(pageX, pageY, PAGE_RIGHT_X, PAGE_ARROW_Y, PAGE_ARROW_SIZE)) {
            return turnPage(clipboard, sneaking ? 10 : 1);
        }
        for (int line = 0; line < ClipboardBehavior.TASKS; line++) {
            if (inside(pageX, pageY, PAGE_BUTTON_X, PAGE_TASK_Y + PAGE_TASK_SPACING * line, PAGE_BUTTON_SIZE)) {
                int next = (ClipboardBehavior.getButtonState(clipboard, line) + 1) % ClipboardBehavior.BUTTON_STATES;
                return ClipboardBehavior.setButton(clipboard, line, next);
            }
        }
        return false;
    }

    private static boolean turnPage(ItemStack clipboard, int pages) {
        int page = ClipboardBehavior.getPageNum(clipboard);
        ClipboardBehavior.setPage(clipboard, page + pages);
        return ClipboardBehavior.getPageNum(clipboard) != page;
    }

    private static boolean inside(double x, double y, int left, int top, int size) {
        return left <= x && x < left + size && top <= y && y < top + size;
    }

    @Override
    public boolean canPlaceCoverOnSide(@NotNull EnumFacing side) {
        return false;
    }

    @Override
    public boolean acceptsCovers() {
        return false;
    }

    @Override
    public boolean canRenderMachineGrid(@NotNull ItemStack mainHandStack, @NotNull ItemStack offHandStack) {
        return false;
    }

    @Override
    public boolean showToolUsages() {
        return false;
    }

    @Override
    public ItemStack getPickItem(EntityPlayer player) {
        return this.getClipboard();
    }

    @NotNull
    @Override
    public SoundType getSoundType() {
        return SoundType.WOOD;
    }
}
