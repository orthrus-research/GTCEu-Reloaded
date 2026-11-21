package gregtech.client.renderer;

import gregtech.api.util.GTLog;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import dev.orthlib.api.render.Admission;
import dev.orthlib.api.render.BloomKind;
import dev.orthlib.api.render.Contribution;
import dev.orthlib.api.render.ContributionShape;
import dev.orthlib.client.render.EmissiveSource;
import dev.orthlib.client.render.RenderInterface;
import org.jetbrains.annotations.Nullable;

/**
 * Emissive geometry submitted through {@link GTBloomKind#contribute}: its owner's lease on an OrthLib contribution,
 * kept across changes of its kind's settings. OrthLib also retires the contribution when its source's
 * {@link EmissiveSource#valid()} answers false at a client tick, when its world unloads, and when the source throws.
 * Client thread.
 */
@SideOnly(Side.CLIENT)
public final class EmissiveContribution {

    private static boolean reportedExhausted;

    private final GTBloomKind kind;
    private final ContributionShape shape;
    private final EmissiveSource source;
    @Nullable
    private Contribution<EmissiveSource> lease;

    EmissiveContribution(GTBloomKind kind, ContributionShape shape, EmissiveSource source) {
        this.kind = kind;
        this.shape = shape;
        this.source = source;
    }

    /** @return whether a renderer may still draw the geometry: false once closed or retired */
    public boolean isLive() {
        Contribution<EmissiveSource> current = this.lease;
        return current != null && current.isLive();
    }

    /** Withdraws the geometry. Closing again does nothing. */
    public void close() {
        detach();
        this.kind.forget(this);
    }

    void attach(@Nullable BloomKind bloomKind) {
        if (bloomKind == null) return;
        switch (RenderInterface.contribute(this.shape, bloomKind, this.source)) {
            case Admission.Admitted<Contribution<EmissiveSource>>(var handle) -> this.lease = handle;
            case Admission.Exhausted<Contribution<EmissiveSource>>(var limit) -> {
                if (!reportedExhausted) {
                    reportedExhausted = true;
                    GTLog.logger.warn("OrthLib's render interface holds its limit of {} emissive contributions; " +
                            "further {} geometry is not drawn", limit, this.kind.key().key());
                }
            }
        }
    }

    void detach() {
        if (this.lease != null) {
            this.lease.close();
            this.lease = null;
        }
    }
}
