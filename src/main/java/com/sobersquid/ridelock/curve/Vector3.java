package com.sobersquid.ridelock.curve;

/**
 * Small immutable vector used by the curve code so its mathematics stays
 * independent from Minecraft world access.
 */
public final class Vector3 {
    public final double x;
    public final double y;
    public final double z;

    public Vector3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3 add(Vector3 other) {
        return new Vector3(x + other.x, y + other.y, z + other.z);
    }

    public Vector3 subtract(Vector3 other) {
        return new Vector3(x - other.x, y - other.y, z - other.z);
    }

    public Vector3 scale(double amount) {
        return new Vector3(x * amount, y * amount, z * amount);
    }

    public double dot(Vector3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public double lengthSquared() {
        return dot(this);
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public double distanceTo(Vector3 other) {
        return subtract(other).length();
    }

    public Vector3 normalize() {
        double length = length();
        if (length < 1.0e-12) {
            return new Vector3(0.0, 0.0, 0.0);
        }
        return scale(1.0 / length);
    }

    public static Vector3 lerp(Vector3 from, Vector3 to, double amount) {
        return from.scale(1.0 - amount).add(to.scale(amount));
    }

    @Override
    public String toString() {
        return "Vector3{" + x + ", " + y + ", " + z + '}';
    }
}
