package com.sobersquid.ridelock.track;

import com.sobersquid.ridelock.curve.Vector3;
import net.minecraft.block.BlockRailBase.EnumRailDirection;
import net.minecraft.util.math.BlockPos;

/** Maps each vanilla rail shape to the two block positions it connects. */
public final class RailDirectionGeometry {
    private RailDirectionGeometry() {
    }

    public static Endpoint[] endpoints(EnumRailDirection direction) {
        switch (direction) {
            case NORTH_SOUTH:
                return pair(0, 0, -1, 0, 0, 1);
            case EAST_WEST:
                return pair(-1, 0, 0, 1, 0, 0);
            case ASCENDING_EAST:
                return pair(-1, 0, 0, 1, 1, 0);
            case ASCENDING_WEST:
                return pair(-1, 1, 0, 1, 0, 0);
            case ASCENDING_NORTH:
                return pair(0, 1, -1, 0, 0, 1);
            case ASCENDING_SOUTH:
                return pair(0, 0, -1, 0, 1, 1);
            case SOUTH_EAST:
                return pair(0, 0, 1, 1, 0, 0);
            case SOUTH_WEST:
                return pair(0, 0, 1, -1, 0, 0);
            case NORTH_WEST:
                return pair(0, 0, -1, -1, 0, 0);
            case NORTH_EAST:
                return pair(0, 0, -1, 1, 0, 0);
            default:
                throw new IllegalArgumentException("Unsupported rail direction: " + direction);
        }
    }

    private static Endpoint[] pair(int firstX, int firstY, int firstZ,
                                   int secondX, int secondY, int secondZ) {
        return new Endpoint[]{
                new Endpoint(firstX, firstY, firstZ),
                new Endpoint(secondX, secondY, secondZ)
        };
    }

    /** Returns the physical rail centerline at the middle of this block. */
    public static Vector3 center(BlockPos position, EnumRailDirection direction) {
        Endpoint[] endpoints = endpoints(direction);
        double midpointHeight = (endpoints[0].y + endpoints[1].y) * 0.5;
        return new Vector3(position.getX() + 0.5,
                position.getY() + 0.0625 + midpointHeight,
                position.getZ() + 0.5);
    }

    public static final class Endpoint {
        public final int x;
        public final int y;
        public final int z;

        private Endpoint(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public BlockPos offset(BlockPos position) {
            return position.add(x, y, z);
        }

        public boolean pointsFromTo(BlockPos from, BlockPos to) {
            return from.getX() + x == to.getX()
                    && from.getY() + y == to.getY()
                    && from.getZ() + z == to.getZ();
        }

        /** Vanilla rail connection checks deliberately ignore the Y coordinate. */
        public boolean pointsHorizontallyFromTo(BlockPos from, BlockPos to) {
            return from.getX() + x == to.getX()
                    && from.getZ() + z == to.getZ();
        }
    }
}
