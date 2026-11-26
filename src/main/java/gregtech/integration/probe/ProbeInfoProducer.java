package gregtech.integration.probe;

import java.util.List;

/**
 * Produces the probe lines one kind of information adds, for The One Probe and HWYLA alike. Producers read the game
 * but keep no state of their own.
 */
@FunctionalInterface
public interface ProbeInfoProducer {

    /** Adds this producer's lines for the block in the context, or nothing when it does not apply there. */
    void addInfo(ProbeContext context, List<ProbeLine> lines);
}
