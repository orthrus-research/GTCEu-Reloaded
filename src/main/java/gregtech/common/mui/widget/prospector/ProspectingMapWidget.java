package gregtech.common.mui.widget.prospector;

import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.util.FileUtility;
import gregtech.api.util.Mods;
import gregtech.api.worldgen.config.OreDepositDefinition;
import gregtech.api.worldgen.config.WorldGenRegistry;
import gregtech.api.worldgen.filler.FillerEntry;
import gregtech.core.network.packets.PacketProspecting;
import gregtech.integration.xaero.ColorUtility;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

/** The prospector's map: one 16 pixel square per scanned chunk, with the player's position marked in red. */
public class ProspectingMapWidget extends Widget<ProspectingMapWidget> implements Interactable {

    private static final int PACKETS_PER_TICK = 10;
    private static final double[][] XAEROS_COLORS = {
            ColorUtility.getLab(new Color(0, 0, 0)),
            ColorUtility.getLab(new Color(0, 0, 128)),
            ColorUtility.getLab(new Color(0, 128, 0)),
            ColorUtility.getLab(new Color(0, 128, 128)),
            ColorUtility.getLab(new Color(128, 0, 0)),
            ColorUtility.getLab(new Color(128, 0, 128)),
            ColorUtility.getLab(new Color(128, 128, 0)),
            ColorUtility.getLab(new Color(192, 192, 192)),
            ColorUtility.getLab(new Color(128, 128, 128)),
            ColorUtility.getLab(new Color(0, 0, 255)),
            ColorUtility.getLab(new Color(0, 255, 0)),
            ColorUtility.getLab(new Color(0, 255, 255)),
            ColorUtility.getLab(new Color(255, 0, 0)),
            ColorUtility.getLab(new Color(255, 0, 255)),
            ColorUtility.getLab(new Color(255, 255, 0)),
            ColorUtility.getLab(new Color(255, 255, 255)),
    };

    private final int chunkRadius;
    private final ProspectorMode mode;
    private final Queue<PacketProspecting> packets = new ArrayDeque<>();
    private final ProspectingOreListWidget oreList;

    private ProspectingTexture texture;

    private long lastClicked;
    private final List<String> hoveredNames = new ArrayList<>();
    private int hoveredOreHeight;
    private int color;

    public ProspectingMapWidget(int chunkRadius, @NotNull ProspectorMode mode, ProspectingOreListWidget oreList) {
        this.chunkRadius = chunkRadius;
        this.mode = mode;
        this.oreList = oreList;
        size(16 * (chunkRadius * 2 - 1));
        tooltipAutoUpdate(true);
        tooltipDynamic(this::buildTooltip);
        oreList.onSelected(this::select);
    }

    public void addPacket(PacketProspecting packet) {
        packets.add(packet);
    }

    private void select(String name) {
        if (texture != null) {
            texture.loadTexture(null, name);
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (packets.isEmpty()) return;
        for (int i = 0; i < PACKETS_PER_TICK && !packets.isEmpty(); i++) {
            PacketProspecting packet = packets.poll();
            if (texture == null) {
                texture = new ProspectingTexture(packet.mode, chunkRadius, false);
            }
            texture.updateTexture(packet);
            oreList.addOres(packet.ores, packet.mode);
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        if (texture != null) {
            texture.deleteGlTexture();
            texture = null;
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (texture == null) return;
        GlStateManager.color(1, 1, 1, 1);
        texture.draw(0, 0);
        int[] chunk = hoveredChunk();
        if (chunk != null) {
            GuiDraw.drawRect(chunk[0] * 16, chunk[1] * 16, 16, 16, 0x4B6C6C6C);
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    /** The column and row of the chunk under the mouse, or null when it is outside the map. */
    private int[] hoveredChunk() {
        if (!isHovering()) return null;
        int mouseX = getContext().getMouseX();
        int mouseY = getContext().getMouseY();
        int side = chunkRadius * 2 - 1;
        // integer division rounds toward zero, so check the left and top edges first
        if (mouseX < 0 || mouseY < 0) return null;
        int cX = mouseX / 16;
        int cZ = mouseY / 16;
        if (cX >= side || cZ >= side) return null;
        return new int[] { cX, cZ };
    }

    private void buildTooltip(RichTooltip tooltip) {
        hoveredNames.clear();
        hoveredOreHeight = 0;
        int[] chunk = hoveredChunk();
        if (texture == null || chunk == null) return;
        int cX = chunk[0];
        int cZ = chunk[1];
        // the colour of the most common resource becomes the waypoint colour
        int maxAmount = 0;

        if (this.mode == ProspectorMode.ORE) {
            tooltip.addLine(I18n.format("terminal.prospector.ore"));
            Map<String, Integer> oreInfo = new HashMap<>();
            Map<String, Integer> oreHeight = new HashMap<>();
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    Map<Byte, String> data = texture.map[cX * 16 + i][cZ * 16 + j];
                    if (data == null) continue;
                    for (Map.Entry<Byte, String> entry : data.entrySet()) {
                        String dict = entry.getValue();
                        if (!ProspectingTexture.SELECTED_ALL.equals(texture.getSelected()) &&
                                !texture.getSelected().equals(dict)) {
                            continue;
                        }
                        String name = OreDictUnifier.get(dict).getDisplayName();
                        oreInfo.merge(name, 1, Integer::sum);
                        oreHeight.merge(name, Byte.toUnsignedInt(entry.getKey()), Integer::sum);
                        if (oreInfo.get(name) > maxAmount) {
                            maxAmount = oreInfo.get(name);
                            MaterialStack m = OreDictUnifier.getMaterial(OreDictUnifier.get(dict));
                            if (m != null) {
                                color = m.material.getMaterialRGB();
                            }
                        }
                    }
                }
            }
            int totalCount = 0;
            for (Map.Entry<String, Integer> entry : oreHeight.entrySet()) {
                hoveredOreHeight += entry.getValue();
                int count = oreInfo.getOrDefault(entry.getKey(), 0);
                totalCount += count;
                entry.setValue(count != 0 ? entry.getValue() / count : 0);
            }
            if (totalCount != 0) {
                hoveredOreHeight /= totalCount;
            }
            oreInfo.forEach((name, count) -> {
                tooltip.addLine(name + " --- §e" + count + "§r, §cy" + oreHeight.getOrDefault(name, 0) + "§r");
                hoveredNames.add(name);
            });
        } else if (this.mode == ProspectorMode.FLUID) {
            tooltip.addLine(I18n.format("terminal.prospector.fluid"));
            Map<Byte, String> data = texture.map[cX][cZ];
            if (data != null && !data.isEmpty() && (ProspectingTexture.SELECTED_ALL.equals(texture.getSelected()) ||
                    texture.getSelected().equals(data.get((byte) 1)))) {
                FluidStack fluidStack = FluidRegistry.getFluidStack(data.get((byte) 1), 1);
                if (fluidStack != null) {
                    tooltip.addLine(I18n.format("terminal.prospector.fluid.info", fluidStack.getLocalizedName(),
                            data.get((byte) 2), data.get((byte) 3)));
                    hoveredNames.add(fluidStack.getLocalizedName());
                    color = fluidStack.getFluid().getColor(fluidStack);
                }
            }
        }

        if (Mods.JourneyMap.isModLoaded() || Mods.VoxelMap.isModLoaded() || Mods.XaerosMinimap.isModLoaded()) {
            tooltip.addLine(I18n.format("terminal.prospector.waypoint.add"));
        }
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        int[] chunk = hoveredChunk();
        if (chunk == null) return Result.IGNORE;
        if (System.currentTimeMillis() - lastClicked < 400 && !hoveredNames.isEmpty()) {
            addWaypoint(chunk[0] - (chunkRadius - 1), chunk[1] - (chunkRadius - 1));
        }
        this.lastClicked = System.currentTimeMillis();
        return Result.SUCCESS;
    }

    @SideOnly(Side.CLIENT)
    private void addWaypoint(int xDiff, int zDiff) {
        Minecraft mc = Minecraft.getMinecraft();
        int xPos = ((mc.player.chunkCoordX + xDiff) << 4) + 8;
        int zPos = ((mc.player.chunkCoordZ + zDiff) << 4) + 8;
        int yPos = hoveredOreHeight != 0 ? hoveredOreHeight : mc.world.getHeight(xPos, zPos);
        BlockPos b = new BlockPos(xPos, yPos, zPos);
        trimHoveredNames();

        boolean added = false;
        if (Mods.JourneyMap.isModLoaded()) {
            added = addJourneymapWaypoint(b);
        } else if (Mods.VoxelMap.isModLoaded()) {
            added = addVoxelMapWaypoint(b);
        } else if (Mods.XaerosMinimap.isModLoaded()) {
            added = addXaeroMapWaypoint(b);
        }
        if (added) {
            mc.player.sendStatusMessage(new TextComponentTranslation("behavior.prospector.added_waypoint"), true);
        }
    }

    /** Names a vein after its deposit when most of the deposit's ores are under the mouse. */
    private void trimHoveredNames() {
        for (OreDepositDefinition odd : WorldGenRegistry.getOreDeposits()) {
            for (FillerEntry fillerEntry : odd.getBlockFiller().getAllPossibleStates()) {
                Collection<String> matches = new ArrayList<>();
                Collection<IBlockState> pr = fillerEntry.getPossibleResults();
                for (IBlockState bs : pr) {
                    Set<String> ores = OreDictUnifier.getOreDictionaryNames(new ItemStack(bs.getBlock()));
                    for (String dict : ores) {
                        String name = OreDictUnifier.get(dict).getDisplayName();
                        if (hoveredNames.contains(name)) {
                            matches.add(name);
                        }
                    }
                }
                if (matches.size() > pr.size() / 2) {
                    this.hoveredNames.removeAll(matches);
                    this.hoveredNames.add(FileUtility.trimFileName(odd.getDepositName()));
                }
            }
        }
    }

    @NotNull
    private String createVeinName() {
        // remove the [] surrounding the array
        String s = hoveredNames.toString();
        return s.substring(1, s.length() - 1);
    }

    @Optional.Method(modid = Mods.Names.JOURNEY_MAP)
    private boolean addJourneymapWaypoint(BlockPos b) {
        journeymap.client.model.Waypoint journeyMapWaypoint = new journeymap.client.model.Waypoint(createVeinName(),
                b,
                new Color(color),
                journeymap.client.model.Waypoint.Type.Normal,
                Minecraft.getMinecraft().world.provider.getDimension());
        if (!journeymap.client.waypoint.WaypointStore.INSTANCE.getAll().contains(journeyMapWaypoint)) {
            journeymap.client.waypoint.WaypointStore.INSTANCE.save(journeyMapWaypoint);
            return true;
        }
        return false;
    }

    @Optional.Method(modid = Mods.Names.VOXEL_MAP)
    private boolean addVoxelMapWaypoint(@NotNull BlockPos b) {
        Color c = new Color(color);
        TreeSet<Integer> world = new TreeSet<>();
        world.add(Minecraft.getMinecraft().world.provider.getDimension());

        com.mamiyaotaru.voxelmap.interfaces.IWaypointManager waypointManager = com.mamiyaotaru.voxelmap.interfaces.AbstractVoxelMap
                .getInstance().getWaypointManager();
        com.mamiyaotaru.voxelmap.util.Waypoint voxelMapWaypoint = new com.mamiyaotaru.voxelmap.util.Waypoint(
                createVeinName(),
                b.getX(),
                b.getZ(),
                b.getY(),
                true,
                c.getRed() / 255F,
                c.getGreen() / 255F,
                c.getBlue() / 255F,
                Minecraft.getMinecraft().world.provider.getDimensionType().getSuffix(),
                Minecraft.getMinecraft().world.provider.getDimensionType().getName(),
                world);

        if (!waypointManager.getWaypoints().contains(voxelMapWaypoint)) {
            waypointManager.addWaypoint(voxelMapWaypoint);
            waypointManager.saveWaypoints();
            return true;
        }
        return false;
    }

    @Optional.Method(modid = Mods.Names.XAEROS_MINIMAP)
    private boolean addXaeroMapWaypoint(@NotNull BlockPos b) {
        int red = clampColor(color >> 16 & 0xFF);
        int green = clampColor(color >> 8 & 0xFF);
        int blue = clampColor(color & 0xFF);

        Color wpc = new Color(red, green, blue);
        double[] labWPC = ColorUtility.getLab(wpc);
        int bestColorIndex = 0;
        double closestDistance = Double.MAX_VALUE;

        for (int i = 0; i < XAEROS_COLORS.length; i++) {
            double[] c = XAEROS_COLORS[i];
            double diffLInner = Math.abs(c[0] - labWPC[0]);
            double diffAInner = Math.abs(c[1] - labWPC[1]);
            double diffBInner = Math.abs(c[2] - labWPC[2]);
            double distance = diffLInner * diffLInner + diffAInner * diffAInner + diffBInner * diffBInner;
            if (distance < closestDistance) {
                closestDistance = distance;
                bestColorIndex = i;
            }
        }

        xaero.common.XaeroMinimapSession minimapSession = xaero.common.XaeroMinimapSession.getCurrentSession();
        xaero.common.minimap.waypoints.WaypointSet wps = minimapSession.getWaypointsManager().getWaypoints();
        xaero.common.minimap.waypoints.WaypointWorld ww = minimapSession.getWaypointsManager().getCurrentWorld();
        xaero.common.minimap.waypoints.Waypoint xaeroWaypoint = new xaero.common.minimap.waypoints.Waypoint(
                b.getX(),
                b.getY(),
                b.getZ(),
                createVeinName(), hoveredNames.get(0).substring(0, 1), bestColorIndex);

        for (xaero.common.minimap.waypoints.Waypoint xwp : wps.getList()) {
            if (xwp.getX() == xaeroWaypoint.getX() &&
                    xwp.getY() == xaeroWaypoint.getY() &&
                    xwp.getZ() == xaeroWaypoint.getZ()) {
                return false;
            }
        }
        wps.getList().add(xaeroWaypoint);
        try {
            minimapSession.getModMain().getSettings().saveWaypoints(ww);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    private static int clampColor(int color) {
        if (color < 32) {
            return 0;
        } else if (color < 128) {
            return 128;
        } else if (color < 192) {
            return 192;
        } else {
            return 255;
        }
    }
}
