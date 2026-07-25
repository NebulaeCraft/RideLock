package com.sobersquid.ridelock.track;

import net.minecraft.block.BlockRailBase.EnumRailDirection;
import net.minecraft.util.math.BlockPos;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RailDirectionGeometryTest {
    @Test
    public void definesExactlyTwoDifferentEndpointsForEveryShape() {
        for (EnumRailDirection direction : EnumRailDirection.values()) {
            RailDirectionGeometry.Endpoint[] endpoints = RailDirectionGeometry.endpoints(direction);
            assertEquals(direction.name(), 2, endpoints.length);
            assertTrue(direction.name(), endpoints[0].x != endpoints[1].x
                    || endpoints[0].y != endpoints[1].y
                    || endpoints[0].z != endpoints[1].z);
        }
    }

    @Test
    public void mapsFlatAndCurvedShapesToTheirCardinalConnections() {
        assertEndpoints(EnumRailDirection.NORTH_SOUTH, "0,0,-1", "0,0,1");
        assertEndpoints(EnumRailDirection.EAST_WEST, "-1,0,0", "1,0,0");
        assertEndpoints(EnumRailDirection.SOUTH_EAST, "0,0,1", "1,0,0");
        assertEndpoints(EnumRailDirection.SOUTH_WEST, "0,0,1", "-1,0,0");
        assertEndpoints(EnumRailDirection.NORTH_WEST, "0,0,-1", "-1,0,0");
        assertEndpoints(EnumRailDirection.NORTH_EAST, "0,0,-1", "1,0,0");
    }

    @Test
    public void putsTheHighEndpointOnTheAscendingSide() {
        assertEndpoints(EnumRailDirection.ASCENDING_EAST, "-1,0,0", "1,1,0");
        assertEndpoints(EnumRailDirection.ASCENDING_WEST, "-1,1,0", "1,0,0");
        assertEndpoints(EnumRailDirection.ASCENDING_NORTH, "0,1,-1", "0,0,1");
        assertEndpoints(EnumRailDirection.ASCENDING_SOUTH, "0,0,-1", "0,1,1");
    }

    @Test
    public void usesThePhysicalThreeDimensionalRailCenterline() {
        BlockPos position = new BlockPos(10, 64, 20);
        assertEquals(64.0625,
                RailDirectionGeometry.center(position, EnumRailDirection.EAST_WEST).y, 1.0e-9);
        assertEquals(64.5625,
                RailDirectionGeometry.center(position, EnumRailDirection.ASCENDING_EAST).y, 1.0e-9);
    }

    @Test
    public void flatRailAtUpperLevelConnectsBackToSlopeBelow() {
        BlockPos slope = new BlockPos(0, 64, 0);
        BlockPos upperFlat = new BlockPos(1, 65, 0);
        RailDirectionGeometry.Endpoint west = RailDirectionGeometry.endpoints(
                EnumRailDirection.EAST_WEST)[0];
        assertTrue(west.pointsHorizontallyFromTo(upperFlat, slope));
    }

    private static void assertEndpoints(EnumRailDirection direction, String first, String second) {
        Set<String> actual = new HashSet<>();
        for (RailDirectionGeometry.Endpoint endpoint : RailDirectionGeometry.endpoints(direction)) {
            actual.add(endpoint.x + "," + endpoint.y + "," + endpoint.z);
        }
        Set<String> expected = new HashSet<>();
        expected.add(first);
        expected.add(second);
        assertEquals(direction.name(), expected, actual);
    }
}
