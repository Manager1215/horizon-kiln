package net.minecraft.util;

/**
 * Kiln's stand-in for {@code Mth}, the game's maths: the common functions, written from what they compute. Kiln's
 * {@code sin} and {@code cos} are Java's exact ones, not the game's table, so they can differ in the last digits.
 */
public class Mth {
    public static final float PI = (float) Math.PI;
    public static final float HALF_PI = (float) (Math.PI / 2);
    public static final float TWO_PI = (float) (Math.PI * 2);
    public static final float DEG_TO_RAD = (float) (Math.PI / 180);
    public static final float RAD_TO_DEG = (float) (180 / Math.PI);
    public static final float EPSILON = 1.0E-5F;
    public static final float SQRT_OF_TWO = (float) Math.sqrt(2);

    public static float sin(double value) {
        return (float) Math.sin(value);
    }

    public static float cos(double value) {
        return (float) Math.cos(value);
    }

    public static float sqrt(float value) {
        return (float) Math.sqrt(value);
    }

    public static int floor(float value) {
        return (int) Math.floor(value);
    }

    public static int floor(double value) {
        return (int) Math.floor(value);
    }

    public static long lfloor(double value) {
        return (long) Math.floor(value);
    }

    public static float abs(float value) {
        return Math.abs(value);
    }

    public static int abs(int value) {
        return Math.abs(value);
    }

    public static int ceil(float value) {
        return (int) Math.ceil(value);
    }

    public static int ceil(double value) {
        return (int) Math.ceil(value);
    }

    public static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    public static long clamp(long value, long min, long max) {
        return Math.min(Math.max(value, min), max);
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double clampedLerp(double factor, double min, double max) {
        return factor < 0.0 ? min : factor > 1.0 ? max : lerp(factor, min, max);
    }

    public static float clampedLerp(float factor, float min, float max) {
        return factor < 0.0F ? min : factor > 1.0F ? max : lerp(factor, min, max);
    }

    public static int absMax(int a, int b) {
        return Math.max(Math.abs(a), Math.abs(b));
    }

    public static float absMax(float a, float b) {
        return Math.max(Math.abs(a), Math.abs(b));
    }

    public static double absMax(double a, double b) {
        return Math.max(Math.abs(a), Math.abs(b));
    }

    public static int floorDiv(int a, int b) {
        return Math.floorDiv(a, b);
    }

    public static int nextInt(RandomSource random, int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }

    public static boolean equal(float a, float b) {
        return Math.abs(b - a) < EPSILON;
    }

    public static boolean equal(double a, double b) {
        return Math.abs(b - a) < EPSILON;
    }

    public static int positiveModulo(int value, int modulo) {
        return Math.floorMod(value, modulo);
    }

    public static float positiveModulo(float value, float modulo) {
        return (value % modulo + modulo) % modulo;
    }

    public static double positiveModulo(double value, double modulo) {
        return (value % modulo + modulo) % modulo;
    }

    /** An angle in degrees as -180 to 180. */
    public static int wrapDegrees(int degrees) {
        int d = degrees % 360;
        if (d >= 180) {
            d -= 360;
        }
        if (d < -180) {
            d += 360;
        }
        return d;
    }

    public static float wrapDegrees(long degrees) {
        float d = degrees % 360L;
        if (d >= 180.0F) {
            d -= 360.0F;
        }
        if (d < -180.0F) {
            d += 360.0F;
        }
        return d;
    }

    public static float wrapDegrees(float degrees) {
        float d = degrees % 360.0F;
        if (d >= 180.0F) {
            d -= 360.0F;
        }
        if (d < -180.0F) {
            d += 360.0F;
        }
        return d;
    }

    public static double wrapDegrees(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) {
            d -= 360.0;
        }
        if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }

    public static float degreesDifference(float from, float to) {
        return wrapDegrees(to - from);
    }

    public static float frac(float value) {
        return value - floor(value);
    }

    public static double frac(double value) {
        return value - lfloor(value);
    }

    public static double inverseLerp(double value, double min, double max) {
        return (value - min) / (max - min);
    }

    public static float inverseLerp(float value, float min, float max) {
        return (value - min) / (max - min);
    }

    public static double atan2(double y, double x) {
        return Math.atan2(y, x);
    }

    /** A colour (RGB) from hue, saturation and value, each 0 to 1. */
    public static int hsvToRgb(float hue, float saturation, float value) {
        int sector = (int) (hue * 6.0F) % 6;
        float f = hue * 6.0F - (int) (hue * 6.0F);
        float p = value * (1.0F - saturation);
        float q = value * (1.0F - f * saturation);
        float t = value * (1.0F - (1.0F - f) * saturation);
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        return ARGB.color(clamp((int) (r * 255.0F), 0, 255), clamp((int) (g * 255.0F), 0, 255),
                clamp((int) (b * 255.0F), 0, 255));
    }

    public static int lerpInt(float factor, int min, int max) {
        return min + floor(factor * (max - min));
    }

    public static int lerpDiscrete(float factor, int min, int max) {
        int range = max - min;
        return min + floor(factor * (range - 1)) + (factor > 0.0F ? 1 : 0);
    }

    public static float lerp(float factor, float min, float max) {
        return min + factor * (max - min);
    }

    public static double lerp(double factor, double min, double max) {
        return min + factor * (max - min);
    }

    public static int sign(double value) {
        return value == 0.0 ? 0 : value > 0.0 ? 1 : -1;
    }

    public static float square(float value) {
        return value * value;
    }

    public static double square(double value) {
        return value * value;
    }

    public static int square(int value) {
        return value * value;
    }

    public static long square(long value) {
        return value * value;
    }

    public static double map(double value, double fromMin, double fromMax, double toMin, double toMax) {
        return lerp(inverseLerp(value, fromMin, fromMax), toMin, toMax);
    }

    public static float map(float value, float fromMin, float fromMax, float toMin, float toMax) {
        return lerp(inverseLerp(value, fromMin, fromMax), toMin, toMax);
    }

    public static double clampedMap(double value, double fromMin, double fromMax, double toMin, double toMax) {
        return clampedLerp(inverseLerp(value, fromMin, fromMax), toMin, toMax);
    }

    public static float clampedMap(float value, float fromMin, float fromMax, float toMin, float toMax) {
        return clampedLerp(inverseLerp(value, fromMin, fromMax), toMin, toMax);
    }
    // Generated by tools/kiln-stubs.py: what the mods use that isn't written by hand yet; names and signatures only.
    public static final org.joml.Vector3fc X_AXIS = null;
    public static final float[] SIN = null;
    public static float nextFloat(net.minecraft.util.RandomSource p0, float p1, float p2) { kiln.Missing.called("net.minecraft.util.Mth.nextFloat"); return 0.0F; }
    public static double nextDouble(net.minecraft.util.RandomSource p0, double p1, double p2) { kiln.Missing.called("net.minecraft.util.Mth.nextDouble"); return 0.0; }
    public static boolean isMultipleOf(int p0, int p1) { kiln.Missing.called("net.minecraft.util.Mth.isMultipleOf"); return false; }
    public static float degreesDifferenceAbs(float p0, float p1) { kiln.Missing.called("net.minecraft.util.Mth.degreesDifferenceAbs"); return 0.0F; }
    public static float approach(float p0, float p1, float p2) { kiln.Missing.called("net.minecraft.util.Mth.approach"); return 0.0F; }
    public static float approachDegrees(float p0, float p1, float p2) { kiln.Missing.called("net.minecraft.util.Mth.approachDegrees"); return 0.0F; }
    public static int smallestEncompassingPowerOfTwo(int p0) { kiln.Missing.called("net.minecraft.util.Mth.smallestEncompassingPowerOfTwo"); return 0; }
    public static int smallestSquareSide(int p0) { kiln.Missing.called("net.minecraft.util.Mth.smallestSquareSide"); return 0; }
    public static boolean isPowerOfTwo(int p0) { kiln.Missing.called("net.minecraft.util.Mth.isPowerOfTwo"); return false; }
    public static int ceillog2(int p0) { kiln.Missing.called("net.minecraft.util.Mth.ceillog2"); return 0; }
    public static int log2(int p0) { kiln.Missing.called("net.minecraft.util.Mth.log2"); return 0; }
    public static long getSeed(net.minecraft.core.Vec3i p0) { kiln.Missing.called("net.minecraft.util.Mth.getSeed"); return 0L; }
    public static long getSeed(int p0, int p1, int p2) { kiln.Missing.called("net.minecraft.util.Mth.getSeed"); return 0L; }
    public static float invSqrt(float p0) { kiln.Missing.called("net.minecraft.util.Mth.invSqrt"); return 0.0F; }
    public static double invSqrt(double p0) { kiln.Missing.called("net.minecraft.util.Mth.invSqrt"); return 0.0; }
    public static float fastInvCubeRoot(float p0) { kiln.Missing.called("net.minecraft.util.Mth.fastInvCubeRoot"); return 0.0F; }
    public static int hsvToArgb(float p0, float p1, float p2, int p3) { kiln.Missing.called("net.minecraft.util.Mth.hsvToArgb"); return 0; }
    public static float catmullrom(float p0, float p1, float p2, float p3, float p4) { kiln.Missing.called("net.minecraft.util.Mth.catmullrom"); return 0.0F; }
    public static float smoothstep(float p0) { kiln.Missing.called("net.minecraft.util.Mth.smoothstep"); return 0.0F; }
    public static float rotLerp(float p0, float p1, float p2) { kiln.Missing.called("net.minecraft.util.Mth.rotLerp"); return 0.0F; }
    public static double rotLerp(double p0, double p1, double p2) { kiln.Missing.called("net.minecraft.util.Mth.rotLerp"); return 0.0; }
    public static float rotLerpRad(float p0, float p1, float p2) { kiln.Missing.called("net.minecraft.util.Mth.rotLerpRad"); return 0.0F; }
    public static int roundToward(int p0, int p1) { kiln.Missing.called("net.minecraft.util.Mth.roundToward"); return 0; }
    public static int positiveCeilDiv(int p0, int p1) { kiln.Missing.called("net.minecraft.util.Mth.positiveCeilDiv"); return 0; }
    public static int randomBetweenInclusive(net.minecraft.util.RandomSource p0, int p1, int p2) { kiln.Missing.called("net.minecraft.util.Mth.randomBetweenInclusive"); return 0; }
    public static float randomBetween(net.minecraft.util.RandomSource p0, float p1, float p2) { kiln.Missing.called("net.minecraft.util.Mth.randomBetween"); return 0.0F; }
    public static double length(double p0, double p1) { kiln.Missing.called("net.minecraft.util.Mth.length"); return 0.0; }
    public static float length(float p0, float p1) { kiln.Missing.called("net.minecraft.util.Mth.length"); return 0.0F; }
    public static double lengthSquared(double p0, double p1, double p2) { kiln.Missing.called("net.minecraft.util.Mth.lengthSquared"); return 0.0; }
    public static double length(double p0, double p1, double p2) { kiln.Missing.called("net.minecraft.util.Mth.length"); return 0.0; }
    public static float length(float p0, float p1, float p2) { kiln.Missing.called("net.minecraft.util.Mth.length"); return 0.0F; }
    public static float lengthSquared(float p0, float p1, float p2) { kiln.Missing.called("net.minecraft.util.Mth.lengthSquared"); return 0.0F; }
    public static java.util.stream.IntStream outFromOrigin(int p0, int p1, int p2, int p3) { kiln.Missing.called("net.minecraft.util.Mth.outFromOrigin"); return null; }
    // End of what tools/kiln-stubs.py generated.
}
