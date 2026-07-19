package dev.sukkit.izanami.math;

/** Triplet d'entiers immuable, remplaçant minimal de {@code net.minecraft.core.Vec3i}. */
public final class Vec3i {

    private final int x;
    private final int y;
    private final int z;

    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }
}
