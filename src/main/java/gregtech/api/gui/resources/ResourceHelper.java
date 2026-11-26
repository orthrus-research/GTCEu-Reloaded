package gregtech.api.gui.resources;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;

/**
 * @deprecated moved to {@link gregtech.api.util.ResourceHelper}, which has the same members; this class forwards to
 *             it for addons built against the old name.
 */
@Deprecated
public final class ResourceHelper {

    public static final String RESOURCE_PREFIX = gregtech.api.util.ResourceHelper.RESOURCE_PREFIX;

    private ResourceHelper() {}

    public static void bindTexture(ResourceLocation texture) {
        gregtech.api.util.ResourceHelper.bindTexture(texture);
    }

    public static ResourceLocation getResource(String rs) {
        return gregtech.api.util.ResourceHelper.getResource(rs);
    }

    public static ResourceLocation getResourceRAW(String rs) {
        return gregtech.api.util.ResourceHelper.getResourceRAW(rs);
    }

    public static void bindTexture(String rs) {
        gregtech.api.util.ResourceHelper.bindTexture(rs);
    }

    public static boolean isResourceExist(String rs) {
        return gregtech.api.util.ResourceHelper.isResourceExist(rs);
    }

    @SideOnly(Side.CLIENT)
    public static boolean doResourcepacksHaveTexture(@NotNull String modid, @NotNull String textureResource,
                                                     boolean format) {
        return gregtech.api.util.ResourceHelper.doResourcepacksHaveTexture(modid, textureResource, format);
    }

    @SideOnly(Side.CLIENT)
    public static boolean doResourcepacksHaveResource(@NotNull String modid, @NotNull String resource) {
        return gregtech.api.util.ResourceHelper.doResourcepacksHaveResource(modid, resource);
    }

    @SideOnly(Side.CLIENT)
    public static boolean doResourcepacksHaveResource(@NotNull ResourceLocation resource) {
        return gregtech.api.util.ResourceHelper.doResourcepacksHaveResource(resource);
    }

    public static boolean isTextureExist(@NotNull String modid, @NotNull String textureResource) {
        return gregtech.api.util.ResourceHelper.isTextureExist(modid, textureResource);
    }

    public static boolean isTextureExist(@NotNull ResourceLocation textureResource) {
        return gregtech.api.util.ResourceHelper.isTextureExist(textureResource);
    }
}
