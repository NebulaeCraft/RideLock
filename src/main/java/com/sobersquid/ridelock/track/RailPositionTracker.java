package com.sobersquid.ridelock.track;

import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;

/**
 * Stabilizes the rail block selected from the cart's interpolated client
 * position. Near a curved-rail boundary that position can briefly cross into
 * the next block and then be corrected back. Treating that A-B-A sequence as
 * real travel reverses the sampled path for one client tick.
 */
final class RailPositionTracker {
    static final int BACKTRACK_CONFIRMATION_TICKS = 2;

    private BlockPos current;
    private BlockPos previous;
    private BlockPos pendingBacktrack;
    private int pendingBacktrackTicks;

    @Nullable
    BlockPos update(@Nullable BlockPos detected) {
        if (detected == null) return null;
        if (current == null) {
            current = detected;
            return current;
        }
        if (detected.equals(current)) {
            clearPendingBacktrack();
            return current;
        }

        if (previous != null && detected.equals(previous)) {
            if (detected.equals(pendingBacktrack)) {
                pendingBacktrackTicks++;
            } else {
                pendingBacktrack = detected;
                pendingBacktrackTicks = 1;
            }
            if (pendingBacktrackTicks < BACKTRACK_CONFIRMATION_TICKS) return current;
        }

        previous = current;
        current = detected;
        clearPendingBacktrack();
        return current;
    }

    void reset() {
        current = null;
        previous = null;
        clearPendingBacktrack();
    }

    private void clearPendingBacktrack() {
        pendingBacktrack = null;
        pendingBacktrackTicks = 0;
    }
}
