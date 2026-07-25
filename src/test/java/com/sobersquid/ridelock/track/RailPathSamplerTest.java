package com.sobersquid.ridelock.track;

import com.sobersquid.ridelock.curve.Vector3;
import net.minecraft.util.math.BlockPos;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RailPathSamplerTest {
    private final BlockPos current = new BlockPos(0, 64, 0);
    private final BlockPos west = current.west();
    private final BlockPos south = current.south();
    private final List<BlockPos> southWestCurve = Arrays.asList(south, west);

    @Test
    public void historyPreventsSmallBackwardCorrectionFromReversingSecondCurve() {
        Vector3 backwardsCorrection = new Vector3(-0.004, 0.0, 0.0);

        RailPathSampler.TraversalChoice choice = RailPathSampler.chooseTraversal(
                current, southWestCurve, west, backwardsCorrection);

        assertTrue(choice.usedHistory);
        assertEquals(west, choice.backward);
        assertEquals(south, choice.forward);
    }

    @Test
    public void historyAlsoDeterminesIntentionalReverseTravel() {
        Vector3 staleForwardMotion = new Vector3(0.004, 0.0, 0.0);

        RailPathSampler.TraversalChoice choice = RailPathSampler.chooseTraversal(
                current, southWestCurve, south, staleForwardMotion);

        assertTrue(choice.usedHistory);
        assertEquals(south, choice.backward);
        assertEquals(west, choice.forward);
    }

    @Test
    public void motionRemainsTheFallbackWhenThereIsNoUsableHistory() {
        Vector3 movementFromWest = new Vector3(1.0, 0.0, 0.0);

        RailPathSampler.TraversalChoice choice = RailPathSampler.chooseTraversal(
                current, southWestCurve, null, movementFromWest);

        assertFalse(choice.usedHistory);
        assertEquals(west, choice.backward);
        assertEquals(south, choice.forward);
    }

    @Test
    public void disconnectedHistoryIsIgnoredAfterTeleportOrRouteChange() {
        Vector3 movementFromWest = new Vector3(1.0, 0.0, 0.0);
        BlockPos unrelatedHistory = current.east();

        RailPathSampler.TraversalChoice choice = RailPathSampler.chooseTraversal(
                current, southWestCurve, unrelatedHistory, movementFromWest);

        assertFalse(choice.usedHistory);
        assertEquals(west, choice.backward);
        assertEquals(south, choice.forward);
    }
}
