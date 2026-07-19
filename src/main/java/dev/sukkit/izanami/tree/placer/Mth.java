package dev.sukkit.izanami.tree.placer;

/** Équivalent minimal de {@code net.minecraft.util.Mth} pour porter les placers. */
public final class Mth {

    private Mth() {}

    public static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    public static int floor(float value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    public static int ceil(double value) {
        int i = (int) value;
        return value > i ? i + 1 : i;
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    /** Interpolation linéaire façon {@code net.minecraft.util.Mth.lerp(delta, a, b)}. */
    public static float lerp(float delta, float a, float b) {
        return a + delta * (b - a);
    }

    public static float sin(float value) {
        return (float) Math.sin(value);
    }

    public static float cos(float value) {
        return (float) Math.cos(value);
    }
}
