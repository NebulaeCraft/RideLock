package com.sobersquid.ridelock.track;

import com.sobersquid.ridelock.curve.Vector3;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.BlockRailBase.EnumRailDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Collects an ordered, cart-specific path without loading any chunks. */
public final class RailPathSampler {
    public static final int SAMPLE_LIMIT_EACH_DIRECTION = 128;
    private static final double MOTION_EPSILON_SQUARED = 1.0e-5;
    private static final int HISTORY_LIMIT = SAMPLE_LIMIT_EACH_DIRECTION * 2 + 4;

    private final Deque<BlockPos> actualHistory = new ArrayDeque<>();
    private final RailPositionTracker railPositionTracker = new RailPositionTracker();
    private UUID vehicleId;
    private double previousX;
    private double previousY;
    private double previousZ;
    private boolean hasPreviousPosition;
    private Vector3 stableTravelDirection;

    public void reset() {
        actualHistory.clear();
        railPositionTracker.reset();
        vehicleId = null;
        hasPreviousPosition = false;
        stableTravelDirection = null;
    }

    @Nullable
    public SampledRailPath sample(EntityMinecart cart, float referenceYaw) {
        if (vehicleId == null || !vehicleId.equals(cart.getUniqueID())) {
            reset();
            vehicleId = cart.getUniqueID();
        }

        BlockPos current = railPositionTracker.update(findCurrentRail(cart.world, cart));
        Vector3 travelDirection = updateTravelDirection(cart, referenceYaw);
        if (current == null) return null;

        BlockPos arrivedFrom = findArrivalBlock(current);
        recordCurrentRail(current);
        List<BlockPos> currentNeighbors = connectedNeighbors(cart.world, current, cart);
        if (currentNeighbors.size() < 2) return null;

        TraversalChoice traversal = chooseTraversal(current, currentNeighbors, arrivedFrom, travelDirection);
        BlockPos forward = traversal.forward;
        BlockPos backward = traversal.backward;
        if (forward == null || backward == null || forward.equals(backward)) return null;

        List<BlockPos> behindOutward = collectActualHistoryBehind(cart.world, current);
        Set<BlockPos> allUsed = new HashSet<>();
        allUsed.add(current);
        allUsed.addAll(behindOutward);

        if (behindOutward.isEmpty()) {
            appendWalk(cart.world, cart, current, backward, behindOutward,
                    SAMPLE_LIMIT_EACH_DIRECTION, allUsed);
        } else if (behindOutward.size() < SAMPLE_LIMIT_EACH_DIRECTION) {
            BlockPos outer = behindOutward.get(behindOutward.size() - 1);
            BlockPos towardCurrent = behindOutward.size() == 1
                    ? current : behindOutward.get(behindOutward.size() - 2);
            BlockPos next = otherConnectedNeighbor(cart.world, cart, outer, towardCurrent, allUsed);
            if (next != null) {
                appendWalk(cart.world, cart, outer, next, behindOutward,
                        SAMPLE_LIMIT_EACH_DIRECTION, allUsed);
            }
        }

        List<BlockPos> ahead = new ArrayList<>();
        appendWalk(cart.world, cart, current, forward, ahead,
                SAMPLE_LIMIT_EACH_DIRECTION, allUsed);

        List<BlockPos> ordered = new ArrayList<>(behindOutward.size() + 1 + ahead.size());
        List<BlockPos> farToNearBehind = new ArrayList<>(behindOutward);
        Collections.reverse(farToNearBehind);
        ordered.addAll(farToNearBehind);
        ordered.add(current);
        ordered.addAll(ahead);
        List<Vector3> centers = new ArrayList<>(ordered.size());
        for (BlockPos position : ordered) {
            EnumRailDirection direction = railDirection(cart.world, position, cart);
            if (direction == null) return null;
            centers.add(RailDirectionGeometry.center(position, direction));
        }
        return new SampledRailPath(ordered, centers, behindOutward.size(), ahead.size());
    }

    private Vector3 updateTravelDirection(EntityMinecart cart, float referenceYaw) {
        Vector3 movement = null;
        if (hasPreviousPosition) {
            Vector3 displacement = new Vector3(cart.posX - previousX, cart.posY - previousY, cart.posZ - previousZ);
            if (displacement.lengthSquared() >= MOTION_EPSILON_SQUARED) movement = displacement;
        }
        if (movement == null) {
            Vector3 velocity = new Vector3(cart.motionX, cart.motionY, cart.motionZ);
            if (velocity.lengthSquared() >= MOTION_EPSILON_SQUARED) movement = velocity;
        }
        if (movement != null) stableTravelDirection = movement.normalize();
        if (stableTravelDirection == null) {
            double radians = Math.toRadians(referenceYaw);
            stableTravelDirection = new Vector3(-Math.sin(radians), 0.0, Math.cos(radians));
        }

        previousX = cart.posX;
        previousY = cart.posY;
        previousZ = cart.posZ;
        hasPreviousPosition = true;
        return stableTravelDirection;
    }

    private void recordCurrentRail(BlockPos current) {
        if (actualHistory.isEmpty() || !actualHistory.peekLast().equals(current)) {
            actualHistory.addLast(current);
            while (actualHistory.size() > HISTORY_LIMIT) actualHistory.removeFirst();
        }
    }

    /**
     * Returns the rail block from which the cart most recently entered the
     * current block. This is more reliable than client-side position deltas,
     * which can briefly point backwards while minecart packets are interpolated.
     */
    @Nullable
    private BlockPos findArrivalBlock(BlockPos current) {
        Iterator<BlockPos> iterator = actualHistory.descendingIterator();
        if (!iterator.hasNext()) return null;
        BlockPos latest = iterator.next();
        if (!latest.equals(current)) return latest;
        return iterator.hasNext() ? iterator.next() : null;
    }

    private List<BlockPos> collectActualHistoryBehind(World world, BlockPos current) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        seen.add(current);
        BlockPos previous = current;
        Iterator<BlockPos> iterator = actualHistory.descendingIterator();
        if (iterator.hasNext()) iterator.next(); // current
        while (iterator.hasNext() && result.size() < SAMPLE_LIMIT_EACH_DIRECTION) {
            BlockPos candidate = iterator.next();
            if (seen.contains(candidate) || !areAdjacent(previous, candidate)
                    || !isLoadedRail(world, candidate)) break;
            result.add(candidate);
            seen.add(candidate);
            previous = candidate;
        }
        return result;
    }

    private static boolean areAdjacent(BlockPos first, BlockPos second) {
        int horizontal = Math.abs(first.getX() - second.getX()) + Math.abs(first.getZ() - second.getZ());
        return horizontal == 1 && Math.abs(first.getY() - second.getY()) <= 1;
    }

    private static void appendWalk(World world, EntityMinecart cart, BlockPos previous,
                                   BlockPos next, List<BlockPos> target, int limit,
                                   Set<BlockPos> allUsed) {
        BlockPos from = previous;
        BlockPos current = next;
        while (current != null && target.size() < limit && allUsed.add(current)) {
            target.add(current);
            BlockPos following = otherConnectedNeighbor(world, cart, current, from, allUsed);
            from = current;
            current = following;
        }
    }

    @Nullable
    private static BlockPos otherConnectedNeighbor(World world, EntityMinecart cart,
                                                   BlockPos current, BlockPos previous,
                                                   Set<BlockPos> used) {
        List<BlockPos> neighbors = connectedNeighbors(world, current, cart);
        if (!neighbors.contains(previous)) return null;
        for (BlockPos neighbor : neighbors) {
            if (!neighbor.equals(previous) && !used.contains(neighbor)) return neighbor;
        }
        return null;
    }

    @Nullable
    private static BlockPos chooseByDirection(BlockPos current, List<BlockPos> neighbors,
                                              Vector3 direction, boolean maximum) {
        BlockPos choice = null;
        double choiceDot = maximum ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        for (BlockPos neighbor : neighbors) {
            Vector3 offset = new Vector3(neighbor.getX() - current.getX(),
                    neighbor.getY() - current.getY(), neighbor.getZ() - current.getZ()).normalize();
            double dot = offset.dot(direction);
            if ((maximum && dot > choiceDot) || (!maximum && dot < choiceDot)) {
                choice = neighbor;
                choiceDot = dot;
            }
        }
        return choice;
    }

    static TraversalChoice chooseTraversal(BlockPos current, List<BlockPos> neighbors,
                                           @Nullable BlockPos arrivedFrom,
                                           Vector3 travelDirection) {
        if (arrivedFrom != null && neighbors.contains(arrivedFrom)) {
            for (BlockPos neighbor : neighbors) {
                if (!neighbor.equals(arrivedFrom)) {
                    return new TraversalChoice(neighbor, arrivedFrom, true);
                }
            }
        }

        BlockPos forward = chooseByDirection(current, neighbors, travelDirection, true);
        BlockPos backward = chooseByDirection(current, neighbors, travelDirection, false);
        return new TraversalChoice(forward, backward, false);
    }

    private static List<BlockPos> connectedNeighbors(World world, BlockPos position, EntityMinecart cart) {
        EnumRailDirection shape = railDirection(world, position, cart);
        if (shape == null) return Collections.emptyList();
        List<BlockPos> result = new ArrayList<>(2);
        for (RailDirectionGeometry.Endpoint endpoint : RailDirectionGeometry.endpoints(shape)) {
            BlockPos neighbor = findRailNear(world, endpoint.offset(position));
            if (neighbor != null && connectsBack(world, neighbor, position, cart)) {
                result.add(neighbor);
            }
        }
        return result;
    }

    private static boolean connectsBack(World world, BlockPos from, BlockPos to, EntityMinecart cart) {
        EnumRailDirection shape = railDirection(world, from, cart);
        if (shape == null) return false;
        for (RailDirectionGeometry.Endpoint endpoint : RailDirectionGeometry.endpoints(shape)) {
            if (endpoint.pointsHorizontallyFromTo(from, to)) return true;
        }
        return false;
    }

    @Nullable
    private static BlockPos findRailNear(World world, BlockPos expectedPosition) {
        BlockPos[] candidates = {expectedPosition, expectedPosition.up(), expectedPosition.down()};
        for (BlockPos candidate : candidates) {
            if (isLoadedRail(world, candidate)) return candidate;
        }
        return null;
    }

    @Nullable
    private static EnumRailDirection railDirection(World world, BlockPos position, EntityMinecart cart) {
        if (!isLoadedRail(world, position)) return null;
        IBlockState state = world.getBlockState(position);
        try {
            return ((BlockRailBase) state.getBlock()).getRailDirection(world, position, state, cart);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isLoadedRail(World world, BlockPos position) {
        return world.isBlockLoaded(position, false)
                && BlockRailBase.isRailBlock(world.getBlockState(position));
    }

    @Nullable
    private static BlockPos findCurrentRail(World world, EntityMinecart cart) {
        BlockPos base = new BlockPos(cart.posX, cart.posY, cart.posZ);
        BlockPos[] candidates = {base, base.down(), base.up()};
        for (BlockPos candidate : candidates) {
            if (isLoadedRail(world, candidate)) return candidate;
        }
        return null;
    }

    static final class TraversalChoice {
        final BlockPos forward;
        final BlockPos backward;
        final boolean usedHistory;

        private TraversalChoice(BlockPos forward, BlockPos backward, boolean usedHistory) {
            this.forward = forward;
            this.backward = backward;
            this.usedHistory = usedHistory;
        }
    }
}
