package gregtech.client.particle;

import gregtech.client.renderer.IRenderSetup;
import gregtech.client.shader.postprocessing.BloomType;
import gregtech.client.utils.IBloomEffect;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A particle whose bloom effect GTCEu drew in its own bloom pass. A renderer that provides OrthLib's render interface
 * now owns bloom, so the bloom effect is not drawn: submit emissive geometry through that interface, as
 * {@link gregtech.client.renderer.GTBloomKind#contribute} does, and draw the particle itself in
 * {@link #renderParticle}.
 *
 * @deprecated kept for addons built against it; its bloom effect is never drawn
 */
@Deprecated
public abstract class GTBloomParticle extends GTParticle implements IBloomEffect {

    public GTBloomParticle(double posX, double posY, double posZ) {
        super(posX, posY, posZ);
    }

    @Nullable
    protected abstract IRenderSetup getBloomRenderSetup();

    @NotNull
    protected abstract BloomType getBloomType();
}
