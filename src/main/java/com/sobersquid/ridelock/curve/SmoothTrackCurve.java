package com.sobersquid.ridelock.curve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A regularized cubic B-spline approximation of an ordered rail path.
 */
public final class SmoothTrackCurve {
    public static final double CONTROL_POINT_SPACING = 8.0;
    public static final double SMOOTHNESS_WEIGHT = 4.0;
    public static final double DEFAULT_TANGENT_SAMPLE_SPACING = 4.0;

    private static final int DEGREE = 3;
    private static final double DENSE_SAMPLE_SPACING = 0.25;
    private static final double SOLVER_EPSILON = 1.0e-9;

    private final BSpline spline;
    private final List<DenseSample> denseSamples;
    private final List<TangentSample> tangentSamples;
    private final double length;

    private SmoothTrackCurve(BSpline spline, List<DenseSample> denseSamples,
                             List<TangentSample> tangentSamples, double length) {
        this.spline = spline;
        this.denseSamples = denseSamples;
        this.tangentSamples = tangentSamples;
        this.length = length;
    }

    public static SmoothTrackCurve fit(List<Vector3> samples) {
        return fit(samples, DEFAULT_TANGENT_SAMPLE_SPACING);
    }

    public static SmoothTrackCurve fit(List<Vector3> samples, double tangentSampleSpacing) {
        if (samples == null || samples.size() < 9) {
            throw new IllegalArgumentException("At least nine samples are required");
        }
        if (!Double.isFinite(tangentSampleSpacing) || tangentSampleSpacing <= 0.0) {
            throw new IllegalArgumentException("Tangent sample spacing must be finite and positive");
        }

        List<Vector3> unique = removeConsecutiveDuplicates(samples);
        if (unique.size() < 9) {
            throw new IllegalArgumentException("At least nine unique samples are required");
        }

        double[] chordLengths = cumulativeLengths(unique);
        double rawLength = chordLengths[chordLengths.length - 1];
        if (rawLength < tangentSampleSpacing * 2.0) {
            throw new IllegalArgumentException("The sampled path is too short");
        }

        int controlCount = Math.max(DEGREE + 1,
                (int) Math.ceil(rawLength / CONTROL_POINT_SPACING) + DEGREE);
        double endParameter = controlCount - DEGREE;
        double[] knots = makeOpenUniformKnots(controlCount, endParameter);

        double[][] normal = new double[controlCount][controlCount];
        double[][] rightHandSides = new double[3][controlCount];
        for (int sampleIndex = 0; sampleIndex < unique.size(); sampleIndex++) {
            double parameter = chordLengths[sampleIndex] / rawLength * endParameter;
            double[] basis = basisValues(parameter, DEGREE, knots, controlCount);
            Vector3 point = unique.get(sampleIndex);
            for (int row = 0; row < controlCount; row++) {
                if (basis[row] == 0.0) continue;
                rightHandSides[0][row] += basis[row] * point.x;
                rightHandSides[1][row] += basis[row] * point.y;
                rightHandSides[2][row] += basis[row] * point.z;
                for (int column = 0; column < controlCount; column++) {
                    if (basis[column] != 0.0) {
                        normal[row][column] += basis[row] * basis[column];
                    }
                }
            }
        }

        addSecondDifferencePenalty(normal, SMOOTHNESS_WEIGHT);
        for (int index = 0; index < controlCount; index++) {
            normal[index][index] += SOLVER_EPSILON;
        }

        double[][] decomposition = cholesky(normal);
        double[] controlX = solveCholesky(decomposition, rightHandSides[0]);
        double[] controlY = solveCholesky(decomposition, rightHandSides[1]);
        double[] controlZ = solveCholesky(decomposition, rightHandSides[2]);
        List<Vector3> controls = new ArrayList<>(controlCount);
        for (int index = 0; index < controlCount; index++) {
            controls.add(new Vector3(controlX[index], controlY[index], controlZ[index]));
        }

        BSpline spline = new BSpline(controls, knots, endParameter);
        List<DenseSample> dense = buildDenseSamples(spline, rawLength);
        double fittedLength = dense.get(dense.size() - 1).arcLength;
        if (fittedLength < tangentSampleSpacing) {
            throw new IllegalArgumentException("The fitted path is too short");
        }
        List<TangentSample> tangents = buildTangentSamples(
                spline, dense, fittedLength, tangentSampleSpacing);
        return new SmoothTrackCurve(spline, Collections.unmodifiableList(dense),
                Collections.unmodifiableList(tangents), fittedLength);
    }

    public double length() {
        return length;
    }

    public List<TangentSample> tangentSamples() {
        return tangentSamples;
    }

    /** Returns the interpolated direction at the closest point on the curve. */
    public Vector3 directionAt(Vector3 position) {
        double arcLength = projectArcLength(position);
        int upper = firstTangentAtOrAfter(arcLength);
        if (upper <= 0) return tangentSamples.get(0).direction;
        if (upper >= tangentSamples.size()) return tangentSamples.get(tangentSamples.size() - 1).direction;

        TangentSample before = tangentSamples.get(upper - 1);
        TangentSample after = tangentSamples.get(upper);
        double span = after.arcLength - before.arcLength;
        double amount = span <= 1.0e-9 ? 0.0 : (arcLength - before.arcLength) / span;
        Vector3 interpolated = Vector3.lerp(before.direction, after.direction, amount).normalize();
        return interpolated.lengthSquared() > 0.0 ? interpolated : before.direction;
    }

    public double projectArcLength(Vector3 position) {
        double bestDistanceSquared = Double.POSITIVE_INFINITY;
        double bestArcLength = 0.0;
        for (int index = 0; index < denseSamples.size() - 1; index++) {
            DenseSample start = denseSamples.get(index);
            DenseSample end = denseSamples.get(index + 1);
            Vector3 segment = end.position.subtract(start.position);
            double segmentLengthSquared = segment.lengthSquared();
            double amount = segmentLengthSquared < 1.0e-12 ? 0.0
                    : clamp(position.subtract(start.position).dot(segment) / segmentLengthSquared, 0.0, 1.0);
            Vector3 projected = start.position.add(segment.scale(amount));
            double distanceSquared = position.subtract(projected).lengthSquared();
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                bestArcLength = start.arcLength + (end.arcLength - start.arcLength) * amount;
            }
        }
        return bestArcLength;
    }

    private int firstTangentAtOrAfter(double arcLength) {
        int low = 0;
        int high = tangentSamples.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (tangentSamples.get(middle).arcLength < arcLength) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private static List<Vector3> removeConsecutiveDuplicates(List<Vector3> samples) {
        List<Vector3> result = new ArrayList<>(samples.size());
        Vector3 previous = null;
        for (Vector3 sample : samples) {
            if (previous == null || sample.distanceTo(previous) > 1.0e-9) {
                result.add(sample);
                previous = sample;
            }
        }
        return result;
    }

    private static double[] cumulativeLengths(List<Vector3> samples) {
        double[] lengths = new double[samples.size()];
        for (int index = 1; index < samples.size(); index++) {
            lengths[index] = lengths[index - 1] + samples.get(index).distanceTo(samples.get(index - 1));
        }
        return lengths;
    }

    private static double[] makeOpenUniformKnots(int controlCount, double endParameter) {
        double[] knots = new double[controlCount + DEGREE + 1];
        for (int index = 0; index <= DEGREE; index++) knots[index] = 0.0;
        int interiorCount = controlCount - DEGREE - 1;
        for (int index = 1; index <= interiorCount; index++) knots[DEGREE + index] = index;
        for (int index = controlCount; index < knots.length; index++) knots[index] = endParameter;
        return knots;
    }

    private static double[] basisValues(double parameter, int degree, double[] knots, int controlCount) {
        double[] result = new double[controlCount];
        if (parameter >= knots[controlCount]) {
            result[controlCount - 1] = 1.0;
            return result;
        }

        double[][] levels = basisLevels(parameter, degree, knots, controlCount);
        System.arraycopy(levels[degree], 0, result, 0, controlCount);
        return result;
    }

    private static double[][] basisLevels(double parameter, int degree, double[] knots, int controlCount) {
        double[][] levels = new double[degree + 1][controlCount + degree + 1];
        for (int index = 0; index < controlCount + degree; index++) {
            if (parameter >= knots[index] && parameter < knots[index + 1]) levels[0][index] = 1.0;
        }
        for (int currentDegree = 1; currentDegree <= degree; currentDegree++) {
            for (int index = 0; index < controlCount; index++) {
                double leftDenominator = knots[index + currentDegree] - knots[index];
                double rightDenominator = knots[index + currentDegree + 1] - knots[index + 1];
                double left = leftDenominator == 0.0 ? 0.0
                        : (parameter - knots[index]) / leftDenominator * levels[currentDegree - 1][index];
                double right = rightDenominator == 0.0 ? 0.0
                        : (knots[index + currentDegree + 1] - parameter) / rightDenominator
                        * levels[currentDegree - 1][index + 1];
                levels[currentDegree][index] = left + right;
            }
        }
        return levels;
    }

    private static double[] derivativeBasisValues(double parameter, double[] knots, int controlCount) {
        double safeParameter = Math.min(parameter, knots[controlCount] - 1.0e-10);
        double[][] levels = basisLevels(safeParameter, DEGREE - 1, knots, controlCount + 1);
        double[] derivative = new double[controlCount];
        for (int index = 0; index < controlCount; index++) {
            double leftDenominator = knots[index + DEGREE] - knots[index];
            double rightDenominator = knots[index + DEGREE + 1] - knots[index + 1];
            double left = leftDenominator == 0.0 ? 0.0
                    : DEGREE / leftDenominator * levels[DEGREE - 1][index];
            double right = rightDenominator == 0.0 ? 0.0
                    : DEGREE / rightDenominator * levels[DEGREE - 1][index + 1];
            derivative[index] = left - right;
        }
        return derivative;
    }

    private static void addSecondDifferencePenalty(double[][] matrix, double weight) {
        double[] coefficients = {1.0, -2.0, 1.0};
        for (int start = 0; start < matrix.length - 2; start++) {
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    matrix[start + row][start + column] += weight * coefficients[row] * coefficients[column];
                }
            }
        }
    }

    private static double[][] cholesky(double[][] matrix) {
        int size = matrix.length;
        double[][] lower = new double[size][size];
        for (int row = 0; row < size; row++) {
            for (int column = 0; column <= row; column++) {
                double value = matrix[row][column];
                for (int inner = 0; inner < column; inner++) value -= lower[row][inner] * lower[column][inner];
                if (row == column) {
                    if (value <= 0.0 || !Double.isFinite(value)) {
                        throw new IllegalArgumentException("Could not fit a stable curve");
                    }
                    lower[row][column] = Math.sqrt(value);
                } else {
                    lower[row][column] = value / lower[column][column];
                }
            }
        }
        return lower;
    }

    private static double[] solveCholesky(double[][] lower, double[] rightHandSide) {
        int size = lower.length;
        double[] intermediate = new double[size];
        for (int row = 0; row < size; row++) {
            double value = rightHandSide[row];
            for (int column = 0; column < row; column++) value -= lower[row][column] * intermediate[column];
            intermediate[row] = value / lower[row][row];
        }
        double[] result = new double[size];
        for (int row = size - 1; row >= 0; row--) {
            double value = intermediate[row];
            for (int column = row + 1; column < size; column++) value -= lower[column][row] * result[column];
            result[row] = value / lower[row][row];
        }
        return result;
    }

    private static List<DenseSample> buildDenseSamples(BSpline spline, double approximateLength) {
        int count = Math.max(2, (int) Math.ceil(approximateLength / DENSE_SAMPLE_SPACING) + 1);
        List<DenseSample> result = new ArrayList<>(count);
        Vector3 previous = null;
        double arcLength = 0.0;
        for (int index = 0; index < count; index++) {
            double parameter = spline.endParameter * index / (count - 1.0);
            Vector3 position = spline.evaluate(parameter);
            if (previous != null) arcLength += position.distanceTo(previous);
            result.add(new DenseSample(parameter, arcLength, position));
            previous = position;
        }
        return result;
    }

    private static List<TangentSample> buildTangentSamples(BSpline spline, List<DenseSample> dense,
                                                            double fittedLength,
                                                            double tangentSampleSpacing) {
        List<TangentSample> result = new ArrayList<>();
        for (double arcLength = 0.0; arcLength < fittedLength; arcLength += tangentSampleSpacing) {
            addTangentSample(result, spline, dense, arcLength);
        }
        addTangentSample(result, spline, dense, fittedLength);
        return result;
    }

    private static void addTangentSample(List<TangentSample> target, BSpline spline,
                                         List<DenseSample> dense, double arcLength) {
        double parameter = parameterAtArcLength(dense, arcLength);
        Vector3 direction = spline.derivative(parameter).normalize();
        if (direction.lengthSquared() < 1.0e-12 && !target.isEmpty()) {
            direction = target.get(target.size() - 1).direction;
        }
        target.add(new TangentSample(arcLength, spline.evaluate(parameter), direction));
    }

    private static double parameterAtArcLength(List<DenseSample> dense, double arcLength) {
        int low = 0;
        int high = dense.size() - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (dense.get(middle).arcLength < arcLength) low = middle + 1;
            else high = middle;
        }
        if (low == 0) return dense.get(0).parameter;
        DenseSample before = dense.get(low - 1);
        DenseSample after = dense.get(low);
        double span = after.arcLength - before.arcLength;
        double amount = span <= 1.0e-12 ? 0.0 : (arcLength - before.arcLength) / span;
        return before.parameter + (after.parameter - before.parameter) * amount;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static final class TangentSample {
        public final double arcLength;
        public final Vector3 position;
        public final Vector3 direction;

        private TangentSample(double arcLength, Vector3 position, Vector3 direction) {
            this.arcLength = arcLength;
            this.position = position;
            this.direction = direction;
        }
    }

    private static final class DenseSample {
        private final double parameter;
        private final double arcLength;
        private final Vector3 position;

        private DenseSample(double parameter, double arcLength, Vector3 position) {
            this.parameter = parameter;
            this.arcLength = arcLength;
            this.position = position;
        }
    }

    private static final class BSpline {
        private final List<Vector3> controls;
        private final double[] knots;
        private final double endParameter;

        private BSpline(List<Vector3> controls, double[] knots, double endParameter) {
            this.controls = controls;
            this.knots = knots;
            this.endParameter = endParameter;
        }

        private Vector3 evaluate(double parameter) {
            double[] basis = basisValues(parameter, DEGREE, knots, controls.size());
            Vector3 result = new Vector3(0.0, 0.0, 0.0);
            for (int index = 0; index < controls.size(); index++) {
                if (basis[index] != 0.0) result = result.add(controls.get(index).scale(basis[index]));
            }
            return result;
        }

        private Vector3 derivative(double parameter) {
            double[] derivativeBasis = derivativeBasisValues(parameter, knots, controls.size());
            Vector3 result = new Vector3(0.0, 0.0, 0.0);
            for (int index = 0; index < controls.size(); index++) {
                if (derivativeBasis[index] != 0.0) {
                    result = result.add(controls.get(index).scale(derivativeBasis[index]));
                }
            }
            return result;
        }
    }
}
