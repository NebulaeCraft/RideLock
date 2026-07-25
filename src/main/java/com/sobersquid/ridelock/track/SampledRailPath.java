package com.sobersquid.ridelock.track;

import com.sobersquid.ridelock.curve.Vector3;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SampledRailPath {
    private final List<BlockPos> blocks;
    private final List<Vector3> centers;
    private final int behindCount;
    private final int aheadCount;

    SampledRailPath(List<BlockPos> blocks, List<Vector3> centers, int behindCount, int aheadCount) {
        if (blocks.size() != centers.size()) {
            throw new IllegalArgumentException("Each rail block must have one center point");
        }
        this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
        this.centers = Collections.unmodifiableList(new ArrayList<>(centers));
        this.behindCount = behindCount;
        this.aheadCount = aheadCount;
    }

    public List<BlockPos> blocks() {
        return blocks;
    }

    public List<Vector3> centers() {
        return centers;
    }

    public int behindCount() {
        return behindCount;
    }

    public int aheadCount() {
        return aheadCount;
    }

    public boolean hasEnoughContext() {
        return behindCount >= 4 && aheadCount >= 4;
    }
}
