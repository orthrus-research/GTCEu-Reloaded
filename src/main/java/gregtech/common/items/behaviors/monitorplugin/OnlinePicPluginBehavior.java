package gregtech.common.items.behaviors.monitorplugin;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.items.behavior.MonitorPluginBaseBehavior;
import gregtech.client.renderer.texture.picture.PictureTexture;
import gregtech.client.utils.RenderUtil;
import gregtech.client.utils.image.DownloadThread;
import gregtech.common.ConfigHolder;

import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.FloatSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public class OnlinePicPluginBehavior extends MonitorPluginBaseBehavior {

    /** The longest address the server keeps; clients read addresses no longer than this. */
    public static final int MAX_URL_LENGTH = 200;

    public String url;
    public float scaleX;
    public float scaleY;
    public float rotation;
    public boolean flippedX;
    public boolean flippedY;

    @SideOnly(Side.CLIENT)
    private DownloadThread downloader;
    @SideOnly(Side.CLIENT)
    public PictureTexture texture;
    @SideOnly(Side.CLIENT)
    public boolean failed;
    @SideOnly(Side.CLIENT)
    public String error;

    /**
     * Sets the picture and how it is drawn. A client sends every value, so the call is ignored unless a new address is
     * {@linkplain #isAllowedUrl allowed}; the angle is kept between -180 and 180 and the scales between 0 and 1.
     */
    public void setConfig(String url, float rotation, float scaleX, float scaleY, boolean flippedX, boolean flippedY) {
        if (url == null || (!url.equals(this.url) && !isAllowedUrl(url))) return;
        rotation = snap(rotation, -180, 180, 1);
        scaleX = snap(scaleX, 0, 1, 0.05);
        scaleY = snap(scaleY, 0, 1, 0.05);
        if (Float.isNaN(rotation) || Float.isNaN(scaleX) || Float.isNaN(scaleY)) return;
        if (this.url.equals(url) && this.rotation == rotation && this.scaleX == scaleX && this.scaleY == scaleY &&
                this.flippedX == flippedX && this.flippedY == flippedY)
            return;
        this.url = url;
        this.rotation = rotation;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.flippedX = flippedX;
        this.flippedY = flippedY;
        String newUrl = url;
        float newRotation = rotation, newScaleX = scaleX, newScaleY = scaleY;
        writePluginData(GregtechDataCodes.UPDATE_PLUGIN_CONFIG, packetBuffer -> {
            packetBuffer.writeString(newUrl);
            packetBuffer.writeFloat(newRotation);
            packetBuffer.writeFloat(newScaleX);
            packetBuffer.writeFloat(newScaleY);
            packetBuffer.writeBoolean(flippedX);
            packetBuffer.writeBoolean(flippedY);
        });
        markAsDirty();
    }

    /**
     * Whether a monitor may show {@code url}: none at all, or an absolute http or https address with a host, at most
     * {@link #MAX_URL_LENGTH} characters long. Whether a player's game then downloads it is that player's choice
     * ({@code monitorOnlinePictures} in the client options), and the download refuses addresses inside the player's
     * own network.
     */
    public static boolean isAllowedUrl(@Nullable String url) {
        if (url == null || url.length() > MAX_URL_LENGTH) return false;
        if (url.isEmpty()) return true;
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c <= ' ' || c == 0x7F) return false;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return scheme != null && uri.getHost() != null && !uri.getHost().isEmpty() &&
                    ("http".equals(scheme.toLowerCase(Locale.ROOT)) ||
                            "https".equals(scheme.toLowerCase(Locale.ROOT)));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    @Override
    public void readPluginData(int id, PacketBuffer buf) {
        if (id == GregtechDataCodes.UPDATE_PLUGIN_CONFIG) {
            String newUrl = buf.readString(MAX_URL_LENGTH);
            if (!this.url.equals(newUrl)) {
                this.url = newUrl;
                this.texture = null;
                this.failed = false;
                this.error = null;
            }
            this.rotation = buf.readFloat();
            this.scaleX = buf.readFloat();
            this.scaleY = buf.readFloat();
            this.flippedX = buf.readBoolean();
            this.flippedY = buf.readBoolean();
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setString("url", url);
        data.setFloat("rotation", rotation);
        data.setFloat("scaleX", scaleX);
        data.setFloat("scaleY", scaleY);
        data.setBoolean("flippedX", flippedX);
        data.setBoolean("flippedY", flippedY);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.url = data.hasKey("url") ? data.getString("url") : "";
        // a longer address would not reach the clients
        if (this.url.length() > MAX_URL_LENGTH) this.url = "";
        this.rotation = data.hasKey("rotation") ? data.getFloat("rotation") : 0;
        this.scaleX = data.hasKey("scaleX") ? data.getFloat("scaleX") : 1;
        this.scaleY = data.hasKey("scaleY") ? data.getFloat("scaleY") : 1;
        this.flippedX = data.hasKey("flippedX") && data.getBoolean("flippedX");
        this.flippedY = data.hasKey("flippedY") && data.getBoolean("flippedY");
    }

    @Override
    public MonitorPluginBaseBehavior createPlugin() {
        return new OnlinePicPluginBehavior();
    }

    @Override
    public int getConfigWidth() {
        return 260;
    }

    @Override
    public int getConfigHeight() {
        return 150;
    }

    @Override
    public void buildConfigUI(ParentWidget<?> parent, PanelSyncManager syncManager) {
        StringSyncValue urlValue = new StringSyncValue(() -> this.url,
                value -> setConfig(value, this.rotation, this.scaleX, this.scaleY, this.flippedX, this.flippedY));
        FloatSyncValue rotationValue = new FloatSyncValue(() -> this.rotation,
                value -> setConfig(this.url, value, this.scaleX, this.scaleY, this.flippedX, this.flippedY));
        FloatSyncValue scaleXValue = new FloatSyncValue(() -> this.scaleX,
                value -> setConfig(this.url, this.rotation, value, this.scaleY, this.flippedX, this.flippedY));
        FloatSyncValue scaleYValue = new FloatSyncValue(() -> this.scaleY,
                value -> setConfig(this.url, this.rotation, this.scaleX, value, this.flippedX, this.flippedY));
        BooleanSyncValue flippedXValue = new BooleanSyncValue(() -> this.flippedX,
                value -> setConfig(this.url, this.rotation, this.scaleX, this.scaleY, value, this.flippedY));
        BooleanSyncValue flippedYValue = new BooleanSyncValue(() -> this.flippedY,
                value -> setConfig(this.url, this.rotation, this.scaleX, this.scaleY, this.flippedX, value));
        syncManager.syncValue("url", urlValue);
        syncManager.syncValue("rotation", rotationValue);
        syncManager.syncValue("scale_x", scaleXValue);
        syncManager.syncValue("scale_y", scaleYValue);
        syncManager.syncValue("flipped_x", flippedXValue);
        syncManager.syncValue("flipped_y", flippedYValue);

        // the address is typed here and sent only when confirmed
        StringValue typedUrl = new StringValue(this.url);
        parent.child(IKey.dynamic(() -> {
            String shown = urlValue.getStringValue();
            return shown.length() > 40 ? shown.substring(0, 39) + "..." : shown;
        }).asWidget().pos(20, 20).size(220, 10))
                .child(new TextFieldWidget()
                        .pos(20, 30)
                        .size(175, 10)
                        .setMaxLength(MAX_URL_LENGTH)
                        .value(typedUrl))
                .child(new ButtonWidget<>()
                        .pos(200, 30)
                        .size(45, 10)
                        .overlay(IKey.str("confirm"))
                        .onMousePressed(mouseButton -> {
                            urlValue.setStringValue(typedUrl.getStringValue());
                            return true;
                        }))
                .child(slider("rotation", rotationValue, -180, 180).pos(25, 40))
                .child(slider("scaleX", scaleXValue, 0, 1).pos(25, 60))
                .child(slider("scaleY", scaleYValue, 0, 1).pos(25, 80))
                .child(IKey.str("flippedX:").asWidget().pos(40, 115))
                .child(new ToggleButton().pos(90, 110).size(20).value(flippedXValue))
                .child(IKey.str("flippedY:").asWidget().pos(140, 115))
                .child(new ToggleButton().pos(190, 110).size(20).value(flippedYValue));
    }

    @Override
    public void update() {
        if (this.screen != null && this.screen.getWorld().isRemote) {
            if (this.texture != null) {
                texture.tick(); // gif update
            }
        }
    }

    @Override
    public void renderPlugin(float partialTicks, RayTraceResult rayTraceResult) {
        if (this.url.isEmpty()) return;
        if (!ConfigHolder.client.monitorOnlinePictures) {
            RenderUtil.renderText(0, 0, 0.002f, 1 / 128f, 0xFFFFFFFF,
                    I18n.format("metaitem.plugin.online_pic.disabled"), true);
        } else if (texture != null && texture.hasTexture()) {
            texture.render(-0.5f, -0.5f, 1, 1, this.rotation, this.scaleX, this.scaleY, this.flippedX,
                    this.flippedY);
        } else {
            this.loadTexture();
        }
    }

    @SideOnly(Side.CLIENT)
    public void loadTexture() {
        if (texture == null && !failed) {
            if (downloader == null && DownloadThread.activeDownloads < DownloadThread.MAXIMUM_ACTIVE_DOWNLOADS) {
                PictureTexture loadedTexture = DownloadThread.loadedImages.get(url);

                if (loadedTexture == null) {
                    synchronized (DownloadThread.LOCK) {
                        if (!DownloadThread.loadingImages.contains(url)) {
                            downloader = new DownloadThread(url);
                            return;
                        }
                    }
                } else {
                    texture = loadedTexture;
                }
            }
            if (downloader != null && downloader.hasFinished()) {
                if (downloader.hasFailed()) {
                    failed = true;
                    error = downloader.getError();
                    DownloadThread.LOGGER.error("Could not load image of " +
                            (this.screen != null ? this.screen.getPos().toString() : "") + " " + error);
                } else {
                    texture = DownloadThread.loadImage(downloader);
                }
                downloader = null;
            }
        }
    }
}
