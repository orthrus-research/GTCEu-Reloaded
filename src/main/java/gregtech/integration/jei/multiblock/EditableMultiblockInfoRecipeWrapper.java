package gregtech.integration.jei.multiblock;

import gregtech.api.gui.GuiTextures;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.client.renderer.scene.ImmediateWorldSceneRenderer;
import gregtech.client.renderer.scene.WorldSceneRenderer;
import gregtech.client.utils.TrackedDummyWorld;
import gregtech.common.ConfigHolder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag.TooltipFlags;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;

import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.gui.recipes.RecipeLayout;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.vecmath.Vector3f;

final class EditableMultiblockInfoRecipeWrapper extends MultiblockInfoRecipeWrapper {

    private static final int SCENE_HEIGHT = 130;
    private static final int PARTS_PER_PAGE = 18;
    private static final int CANDIDATES_PER_PAGE = 24;
    private final MultiblockControllerBase prototype;
    private final MultiblockPreviewEditor editor;
    private final Map<GuiButton, Runnable> controls = new LinkedHashMap<>();
    private final Map<GuiButton, String> controlTips = new LinkedHashMap<>();
    private final GuiButton previousExample;
    private final GuiButton nextExample;
    private final GuiButton layerButton;
    private final GuiButton modeButton;
    private final GuiButton resetButton;
    private final GuiButton previousParts;
    private final GuiButton nextParts;
    private final GuiButton previousCandidates;
    private final GuiButton nextCandidates;
    private final GuiButton repeatAisle;
    private final GuiButton repeatLess;
    private final GuiButton repeatMore;
    private final GuiButton statusButton;
    private RecipeLayout layout;
    private IDrawable slot;
    private WorldSceneRenderer renderer;
    private PreviewWorld renderedWorld;
    private BlockPos selected;
    private List<PreviewCandidate> candidates = Collections.emptyList();
    private int candidatePage;
    private int partsPage;
    private int aisleIndex;
    private int layer = -1;
    private float pitch = 50;
    private float yaw = 20;
    private float zoom;
    private int lastMouseX;
    private int lastMouseY;
    private boolean dragging;
    private boolean zooming;
    private ItemStack hovered = ItemStack.EMPTY;

    EditableMultiblockInfoRecipeWrapper(MultiblockControllerBase controller) {
        super(controller, false);
        this.prototype = controller;
        this.editor = new MultiblockPreviewEditor(controller.getMatchingShapes(), TrackedDummyWorld::new);
        // Discover unsupported clone factories before registering the wrapper, then release the scratch world.
        editor.activate();
        editor.close();
        previousExample = button(130, 90, 20, "<", "example", () -> changeExample(-1));
        nextExample = button(151, 90, 20, ">", "example", () -> changeExample(1));
        layerButton = button(151, 70, 20, "L:A", "layer", () -> {
            int height = (int) ((TrackedDummyWorld) getCurrentRenderer().world).getSize().y;
            this.layer = layer + 1 >= height ? -1 : layer + 1;
            applyLayer();
        });
        modeButton = button(5, 167, 63, "", "mode", () -> {
            if (editor.setGenerated(!editor.isGenerated())) changedLayout(true);
        });
        resetButton = button(70, 167, 37, "", "reset", () -> {
            if (editor.reset()) changedLayout(false);
        });
        previousParts = button(111, 167, 19, "<", "parts", () -> {
            partsPage--;
            updateParts();
        });
        nextParts = button(132, 167, 19, ">", "parts", () -> {
            partsPage++;
            updateParts();
        });
        previousCandidates = button(82, 110, 19, "<", "candidates", () -> candidatePage--);
        nextCandidates = button(103, 110, 19, ">", "candidates", () -> candidatePage++);
        repeatAisle = button(109, 10, 62, "", "repeat", () -> aisleIndex++);
        repeatLess = button(130, 30, 20, "-", "repeat", () -> changeRepetition(-1));
        repeatMore = button(151, 30, 20, "+", "repeat", () -> changeRepetition(1));
        statusButton = button(130, 50, 41, "", "help", () -> {});
    }

    private GuiButton button(int x, int y, int width, String text, String tip, Runnable click) {
        GuiButton button = new GuiButton(0, x, y, width, 18, text);
        controls.put(button, click);
        controlTips.put(button, "gregtech.multiblock.editor." + tip);
        return button;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        ingredients.setInputs(VanillaTypes.ITEM, editor.ingredients());
        ingredients.setOutput(VanillaTypes.ITEM, prototype.getStackForm());
    }

    @Override
    public void setRecipeLayout(RecipeLayout layout, IGuiHelper helper) {
        boolean refresh = this.layout != null;
        this.layout = layout;
        this.slot = helper.drawableBuilder(GuiTextures.SLOT.imageLocation, 0, 0, 18, 18).setTextureSize(18, 18).build();
        for (int i = 0; i < PARTS_PER_PAGE; i++) layout.getItemStacks().init(i, true, 7 + (i % 9) * 18,
                SCENE_HEIGHT + (i / 9) * 18);
        ensureRenderer();
        if (refresh && Mouse.getEventDWheel() != 0) {
            this.zoom = MathHelper.clamp(zoom + (Mouse.getEventDWheel() < 0 ? 0.5f : -0.5f), 3, 999);
        }
        updateParts();
    }

    private void ensureRenderer() {
        PreviewWorld scene = editor.activate();
        if (renderedWorld == scene && renderer != null) return;
        clearRenderer();
        this.renderedWorld = scene;
        this.renderer = new ImmediateWorldSceneRenderer(scene.world);
        renderer.setClearColor(ConfigHolder.client.multiblockPreviewColor);
        renderer.setAfterWorldRender(value -> {
            RayTraceResult hit = value.getLastTraceResult();
            renderBlockOverLay(hit == null ? null : hit.getBlockPos(), 150, 150, 150);
            renderBlockOverLay(selected, 255, 0, 0);
        });
        TrackedDummyWorld world = (TrackedDummyWorld) scene.world;
        editor.snapshot().blocks.forEach((pos, block) -> {
            if (block.state.getBlock() != Blocks.AIR) world.renderedBlocks.add(pos);
        });
        if (zoom == 0) {
            Vector3f size = world.getSize();
            this.zoom = (float) (3.5 * Math.sqrt(Math.max(1, Math.max(size.x, Math.max(size.y, size.z)))));
        }
        if (layer >= world.getSize().y) this.layer = -1;
        applyLayer();
        world.setRenderFilter(renderer.renderedBlocks::contains);
    }

    private void applyLayer() {
        if (renderer == null) return;
        renderer.renderedBlocks.clear();
        TrackedDummyWorld world = (TrackedDummyWorld) renderer.world;
        int minY = (int) world.getMinPos().y;
        for (BlockPos pos : world.renderedBlocks)
            if (layer == -1 || pos.getY() - minY == layer) renderer.renderedBlocks.add(pos);
    }

    private void clearRenderer() {
        if (renderer != null) {
            renderer.setAfterWorldRender(null);
            renderer.renderedBlocks.clear();
            ((TrackedDummyWorld) renderer.world).setRenderFilter(null);
            ((TrackedDummyWorld) renderer.world).renderedBlocks.clear();
        }
        this.renderer = null;
        this.renderedWorld = null;
    }

    void release() {
        clearRenderer();
        editor.close();
        this.candidates = Collections.emptyList();
        this.hovered = ItemStack.EMPTY;
        setHoveredItemStack(null);
    }

    @Override
    public WorldSceneRenderer getCurrentRenderer() {
        ensureRenderer();
        return renderer;
    }

    @Override
    public int getLayerIndex() {
        return layer;
    }

    private void changeExample(int step) {
        clearRenderer();
        editor.selectExample(editor.exampleIndex() + step);
        changedLayout(true);
    }

    private void changeRepetition(int delta) {
        List<Integer> aisles = editor.variableAisles();
        if (aisles.isEmpty()) return;
        int aisle = aisles.get(aisleIndex % aisles.size());
        if (editor.setRepetition(aisle, editor.repetition(aisle) + delta)) changedLayout(true);
    }

    private void changedLayout(boolean clearSelection) {
        if (clearSelection) {
            this.selected = null;
            this.layer = -1;
        }
        ensureRenderer();
        this.partsPage = 0;
        updateParts();
        select(selected);
    }

    private void select(BlockPos pos) {
        this.selected = pos != null && editor.snapshot().blocks.containsKey(pos) ? pos : null;
        this.candidatePage = 0;
        this.candidates = selected == null ? Collections.emptyList() : editor.candidates(selected);
    }

    private void updateParts() {
        List<ItemStack> parts = editor.snapshot().parts();
        this.partsPage = MathHelper.clamp(partsPage, 0, Math.max(0, (parts.size() - 1) / PARTS_PER_PAGE));
        List<ItemStack> visible = PreviewParts.page(parts, partsPage, PARTS_PER_PAGE);
        for (int i = 0; i < PARTS_PER_PAGE; i++)
            layout.getItemStacks().set(i, i < visible.size() ? visible.get(i) : ItemStack.EMPTY);
    }

    private void updateControls() {
        boolean valid = editor.activate().valid;
        previousExample.enabled = !editor.isGenerated() && editor.exampleIndex() > 0;
        nextExample.enabled = !editor.isGenerated() && editor.exampleIndex() + 1 < editor.exampleCount();
        previousExample.visible = nextExample.visible = editor.exampleCount() > 1;
        layerButton.displayString = "L:" + (layer == -1 ? "A" : layer + 1);
        modeButton.displayString = I18n
                .format("gregtech.multiblock.editor." + (editor.isGenerated() ? "generated" : "examples"));
        modeButton.enabled = valid && !editor.variableAisles().isEmpty();
        resetButton.displayString = I18n.format("gregtech.multiblock.editor.reset_label");
        resetButton.enabled = valid;
        int partCount = editor.snapshot().parts().size();
        previousParts.enabled = partsPage > 0;
        nextParts.enabled = (partsPage + 1) * PARTS_PER_PAGE < partCount;
        previousCandidates.visible = nextCandidates.visible = candidates.size() > CANDIDATES_PER_PAGE;
        previousCandidates.enabled = candidatePage > 0;
        nextCandidates.enabled = (candidatePage + 1) * CANDIDATES_PER_PAGE < candidates.size();
        List<Integer> aisles = editor.variableAisles();
        repeatAisle.visible = repeatLess.visible = repeatMore.visible = editor.isGenerated() && !aisles.isEmpty();
        if (!aisles.isEmpty() && editor.isGenerated()) {
            int aisle = aisles.get(aisleIndex % aisles.size());
            int count = editor.repetition(aisle);
            int[] bounds = editor.activate().controller.structurePattern.aisleRepetitions[aisle];
            repeatAisle.displayString = "S" + (aisle + 1) + ": " + count;
            repeatLess.enabled = count > bounds[0];
            repeatMore.enabled = count < bounds[1];
        }
        statusButton.displayString = valid && editor.message().isEmpty() ? "OK" : "!";
    }

    @Override
    public void drawInfo(@NotNull Minecraft minecraft, int width, int height, int mouseX, int mouseY) {
        PreviewLifecycle.touch(this);
        ensureRenderer();
        if (selected != null && candidates.isEmpty()) select(selected);
        updateControls();
        TrackedDummyWorld world = (TrackedDummyWorld) renderer.world;
        Vector3f size = world.getSize();
        Vector3f min = world.getMinPos();
        renderer.setCameraLookAt(new Vector3f(min.x + size.x / 2, min.y + size.y / 2, min.z + size.z / 2), zoom,
                Math.toRadians(pitch), Math.toRadians(yaw));
        renderer.render(layout.getPosX(), layout.getPosY(), width, SCENE_HEIGHT,
                mouseX + layout.getPosX(), mouseY + layout.getPosY());
        GlStateManager.color(1, 1, 1, 1);
        String name = minecraft.fontRenderer.trimStringToWidth(I18n.format(prototype.getMetaFullName()), width - 10);
        minecraft.fontRenderer.drawString(name, (width - minecraft.fontRenderer.getStringWidth(name)) / 2, 0,
                ConfigHolder.client.multiblockPreviewFontColor);
        GlStateManager.color(1, 1, 1, 1);
        for (int i = 0; i < PARTS_PER_PAGE; i++) slot.draw(minecraft, 7 + (i % 9) * 18, SCENE_HEIGHT + (i / 9) * 18);
        this.hovered = ItemStack.EMPTY;
        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < CANDIDATES_PER_PAGE; i++) {
            int index = candidatePage * CANDIDATES_PER_PAGE + i;
            if (index >= candidates.size()) break;
            int x = 5 + (i % 4) * 18;
            int y = 15 + (i / 4) * 18;
            slot.draw(minecraft, x, y);
            ItemStack stack = candidates.get(index).stack();
            minecraft.getRenderItem().renderItemAndEffectIntoGUI(stack, x + 1, y + 1);
            if (inside(mouseX, mouseY, x, y, 18, 18)) this.hovered = stack;
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1, 1, 1, 1);
        for (GuiButton button : controls.keySet()) button.drawButton(minecraft, mouseX, mouseY, 0);
        minecraft.fontRenderer.drawString(Integer.toString(partsPage + 1), 156, 172, 0x404040);
        if (!editor.isGenerated()) minecraft.fontRenderer
                .drawString((editor.exampleIndex() + 1) + "/" + editor.exampleCount(), 107, 94, 0x404040);
        boolean view = inside(mouseX, mouseY, 0, 10, width, SCENE_HEIGHT - 10) && !overControl(mouseX, mouseY);
        if (!Mouse.isButtonDown(0)) dragging = false;
        if (!Mouse.isButtonDown(1)) zooming = false;
        if (view && dragging) {
            pitch = (pitch + mouseX - lastMouseX + 360) % 360;
            yaw = (float) MathHelper.clamp(yaw + mouseY - lastMouseY, -89.9, 89.9);
        } else if (view && zooming) {
            zoom = MathHelper.clamp(zoom + (mouseY - lastMouseY) * 0.05f, 3, 999);
        }
        if (view && hovered.isEmpty()) {
            RayTraceResult hit = renderer.getLastTraceResult();
            PreviewBlock block = hit == null ? null : editor.snapshot().blocks.get(hit.getBlockPos());
            if (block != null) hovered = block.stack();
        }
        setHoveredItemStack(hovered.isEmpty() ? null : hovered);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        GlStateManager.color(1, 1, 1, 1);
    }

    @Override
    public boolean handleClick(@NotNull Minecraft minecraft, int mouseX, int mouseY, int mouseButton) {
        updateControls();
        for (Map.Entry<GuiButton, Runnable> entry : controls.entrySet()) {
            if (entry.getKey().mousePressed(minecraft, mouseX, mouseY)) {
                if (mouseButton == 0) entry.getValue().run();
                return true;
            }
        }
        if (inside(mouseX, mouseY, 5, 15, 72, 108) && !candidates.isEmpty()) {
            int index = candidatePage * CANDIDATES_PER_PAGE + (mouseY - 15) / 18 * 4 + (mouseX - 5) / 18;
            if (mouseButton == 0 && index < candidates.size() &&
                    editor.replace(selected, candidates.get(index), GuiScreen.isShiftKeyDown())) {
                changedLayout(false);
            }
            return true;
        }
        if (!inside(mouseX, mouseY, 0, 10, 176, SCENE_HEIGHT - 10) || overControl(mouseX, mouseY)) return false;
        if (mouseButton == 0) dragging = true;
        if (mouseButton == 1) {
            zooming = true;
            RayTraceResult hit = getCurrentRenderer().getLastTraceResult();
            select(hit == null ? null : hit.getBlockPos());
        }
        return mouseButton == 0 || mouseButton == 1;
    }

    private boolean overControl(int x, int y) {
        if (!candidates.isEmpty() && inside(x, y, 5, 15, 72, 108)) return true;
        for (GuiButton button : controls.keySet())
            if (button.visible && inside(x, y, button.x, button.y, button.width, button.height)) return true;
        return false;
    }

    private static boolean inside(int x, int y, int left, int top, int width, int height) {
        return x >= left && y >= top && x < left + width && y < top + height;
    }

    @Override
    public @NotNull List<String> getTooltipStrings(int mouseX, int mouseY) {
        for (Map.Entry<GuiButton, String> entry : controlTips.entrySet()) {
            GuiButton button = entry.getKey();
            if (button.visible && inside(mouseX, mouseY, button.x, button.y, button.width, button.height)) {
                List<String> result = new ArrayList<>();
                result.add(I18n.format(entry.getValue()));
                if (button == statusButton) {
                    result.add(I18n.format("gregtech.multiblock.editor.pattern_only"));
                    if (!editor.message().isEmpty()) result.add(editor.message());
                }
                return result;
            }
        }
        if (!hovered.isEmpty()) {
            Minecraft minecraft = Minecraft.getMinecraft();
            List<String> result = new ArrayList<>(hovered.getTooltip(minecraft.player,
                    minecraft.gameSettings.advancedItemTooltips ? TooltipFlags.ADVANCED : TooltipFlags.NORMAL));
            if (inside(mouseX, mouseY, 5, 15, 72, 108)) {
                int index = candidatePage * CANDIDATES_PER_PAGE + (mouseY - 15) / 18 * 4 + (mouseX - 5) / 18;
                if (index < candidates.size()) result.addAll(
                        candidates.get(index).predicate.getToolTips(editor.activate().predicates.get(selected)));
                result.add(I18n.format("gregtech.multiblock.editor.apply"));
            }
            return result;
        }
        return Arrays.asList(I18n.format("gregtech.multiblock.editor.help"));
    }
}
