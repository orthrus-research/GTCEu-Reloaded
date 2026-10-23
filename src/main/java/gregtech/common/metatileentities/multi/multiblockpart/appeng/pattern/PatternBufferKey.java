package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import java.util.Objects;
import java.util.UUID;

/** Material identity is independent of which painted consuming part exposes it. */
public final class PatternBufferKey {

    private final UUID buffer;
    private final UUID incarnation;
    private final long batch;
    private final long observedRevision;

    public PatternBufferKey(PatternBufferStorage storage, long batch) {
        this.buffer = storage.getIdentity();
        this.incarnation = storage.getIncarnation();
        this.batch = batch;
        this.observedRevision = storage.getRevision();
    }

    public boolean matches(PatternBufferStorage storage, PatternBufferBatch batch) {
        return this.batch == batch.getId() && buffer.equals(storage.getIdentity()) &&
                incarnation.equals(storage.getIncarnation()) && observedRevision == storage.getRevision();
    }

    @Override
    public boolean equals(Object other) {
        // Content observations invalidate old descriptors, not the authoritative batch's selection identity.
        if (!(other instanceof PatternBufferKey)) return false;
        PatternBufferKey key = (PatternBufferKey) other;
        return batch == key.batch && buffer.equals(key.buffer) && incarnation.equals(key.incarnation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(buffer, incarnation, batch);
    }
}
