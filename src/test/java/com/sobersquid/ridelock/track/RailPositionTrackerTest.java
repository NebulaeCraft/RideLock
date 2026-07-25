package com.sobersquid.ridelock.track;

import net.minecraft.util.math.BlockPos;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RailPositionTrackerTest {
    private final BlockPos a = new BlockPos(0, 64, 0);
    private final BlockPos b = a.east();
    private final BlockPos c = b.east();

    @Test
    public void ignoresOneTickCorrectionBackAcrossCurveBoundary() {
        RailPositionTracker tracker = new RailPositionTracker();

        assertEquals(a, tracker.update(a));
        assertEquals(b, tracker.update(b));
        assertEquals(b, tracker.update(a));
        assertEquals(b, tracker.update(b));
        assertEquals(c, tracker.update(c));
    }

    @Test
    public void acceptsARealDirectionReversalAfterConfirmation() {
        RailPositionTracker tracker = new RailPositionTracker();

        tracker.update(a);
        tracker.update(b);
        assertEquals(b, tracker.update(a));
        assertEquals(a, tracker.update(a));
    }

    @Test
    public void resetForgetsThePreviousRail() {
        RailPositionTracker tracker = new RailPositionTracker();

        tracker.update(a);
        tracker.update(b);
        tracker.reset();
        assertEquals(a, tracker.update(a));
    }
}
