package gregtech.api.util.random;

import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * A {@link Random} that hands each thread its own {@link XoShiRo256PlusPlusRandom}. One shared unsynchronised
 * generator used from the server thread, the client thread and world generation can corrupt its state when
 * threads call it at once. Seeding is not supported: each thread's generator seeds itself.
 */
public final class PerThreadRandom extends Random {

    private final ThreadLocal<XoShiRo256PlusPlusRandom> delegate = ThreadLocal
            .withInitial(XoShiRo256PlusPlusRandom::new);

    @Override
    public void setSeed(long seed) {
        // Random's constructor calls this before the delegate exists; afterwards a seed would apply to one
        // thread only, which no caller could rely on, so it is ignored
    }

    @Override
    protected int next(int bits) {
        return bits == 0 ? 0 : delegate.get().nextInt() >>> (32 - bits);
    }

    @Override
    public int nextInt() {
        return delegate.get().nextInt();
    }

    @Override
    public int nextInt(int bound) {
        return delegate.get().nextInt(bound);
    }

    @Override
    public long nextLong() {
        return delegate.get().nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return delegate.get().nextBoolean();
    }

    @Override
    public float nextFloat() {
        return delegate.get().nextFloat();
    }

    @Override
    public double nextDouble() {
        return delegate.get().nextDouble();
    }

    @Override
    public double nextGaussian() {
        return delegate.get().nextGaussian();
    }

    @Override
    public void nextBytes(byte @NotNull [] bytes) {
        delegate.get().nextBytes(bytes);
    }
}
