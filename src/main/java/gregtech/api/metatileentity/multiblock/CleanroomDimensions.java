package gregtech.api.metatileentity.multiblock;

/** Distances from a cleanroom controller to its walls and floor, in controller-relative directions. */
public record CleanroomDimensions(int left, int right, int back, int front, int depth) {

    public CleanroomDimensions {
        if (left < 0 || right < 0 || back < 0 || front < 0 || depth < 0) {
            throw new IllegalArgumentException("Cleanroom distances cannot be negative");
        }
    }
}
