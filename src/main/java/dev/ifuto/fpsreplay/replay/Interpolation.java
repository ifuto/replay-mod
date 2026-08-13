package dev.ifuto.fpsreplay.replay;

/**
 * Time interpolation used at render time to synthesize the extra frames
 * between recorded ticks — this is what lets the output "upscale" from 20
 * samples/sec to 360fps (or any rate) with smooth motion.
 */
public final class Interpolation {
    private Interpolation() {
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Linear interpolation of an angle taking the shortest path around the circle. */
    public static float lerpAngle(float a, float b, float t) {
        return a + shortestDelta(a, b) * t;
    }

    /** Catmull-Rom spline for sub-tick smoothness (optional, higher quality). */
    public static double catmullRom(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * (2.0 * p1
                + (-p0 + p2) * t
                + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2
                + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
    }

    public static float shortestDelta(float from, float to) {
        float d = (to - from) % 360.0f;
        if (d >= 180.0f) {
            d -= 360.0f;
        } else if (d < -180.0f) {
            d += 360.0f;
        }
        return d;
    }
}
