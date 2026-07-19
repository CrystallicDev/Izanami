package dev.sukkit.izanami.math;

/**
 * Quaternion, remplaçant minimal de {@code org.joml.Quaternionf} pour la
 * rotation des vecteurs dans le framework SDF (SDFRotation).
 */
public final class Quatf {

    public float x;
    public float y;
    public float z;
    public float w = 1f;

    public Quatf() {
    }

    /** Rotation d'angle {@code angle} (radians) autour de l'axe (ax, ay, az). */
    public Quatf setAngleAxis(float angle, float ax, float ay, float az) {
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len > 1.0e-6f) {
            ax /= len;
            ay /= len;
            az /= len;
        }
        float half = angle * 0.5f;
        float s = (float) Math.sin(half);
        this.x = ax * s;
        this.y = ay * s;
        this.z = az * s;
        this.w = (float) Math.cos(half);
        return this;
    }
}
