package gregtech.client.shader.postprocessing;

import gregtech.common.ConfigHolder;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The parameters of GTCEu's former bloom post-processing. A renderer that provides OrthLib's render interface now
 * computes bloom with a technique of its own, from GTCEu's bloom kinds ({@link gregtech.client.renderer.GTBloomKind}),
 * which carry the shader settings.
 *
 * @deprecated kept for addons that set these fields; nothing reads them
 */
@Deprecated
@SideOnly(Side.CLIENT)
public class BloomEffect {

    public static float strength = (float) ConfigHolder.client.shader.strength;
    public static float baseBrightness = (float) ConfigHolder.client.shader.baseBrightness;
    public static float highBrightnessThreshold = (float) ConfigHolder.client.shader.highBrightnessThreshold;
    public static float lowBrightnessThreshold = (float) ConfigHolder.client.shader.lowBrightnessThreshold;
    public static float step = (float) ConfigHolder.client.shader.step;
}
