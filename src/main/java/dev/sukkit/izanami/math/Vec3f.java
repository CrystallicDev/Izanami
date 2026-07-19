package dev.sukkit.izanami.math;

/**
 * Vecteur 3D mutable, remplaçant minimal de {@code org.joml.Vector3f} avec la
 * même sémantique (les opérations modifient l'instance et la renvoient) — pour
 * porter le framework SDF de SALM/bclib sans dépendance externe.
 */
public final class Vec3f {

    public float x;
    public float y;
    public float z;

    public Vec3f(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3f(Vec3f other) {
        this(other.x, other.y, other.z);
    }

    public float x() {
        return this.x;
    }

    public float y() {
        return this.y;
    }

    public float z() {
        return this.z;
    }

    public Vec3f set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public Vec3f set(Vec3f o) {
        return set(o.x, o.y, o.z);
    }

    public Vec3f add(Vec3f o) {
        this.x += o.x;
        this.y += o.y;
        this.z += o.z;
        return this;
    }

    public Vec3f add(float dx, float dy, float dz) {
        this.x += dx;
        this.y += dy;
        this.z += dz;
        return this;
    }

    public Vec3f sub(Vec3f o) {
        this.x -= o.x;
        this.y -= o.y;
        this.z -= o.z;
        return this;
    }

    public Vec3f mul(float s) {
        this.x *= s;
        this.y *= s;
        this.z *= s;
        return this;
    }

    public float dot(Vec3f o) {
        return this.x * o.x + this.y * o.y + this.z * o.z;
    }

    public float length() {
        return (float) Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    /** Interpolation linéaire vers {@code o} (this = lerp(this, o, t)), façon JOML. */
    public Vec3f lerp(Vec3f o, float t) {
        this.x += (o.x - this.x) * t;
        this.y += (o.y - this.y) * t;
        this.z += (o.z - this.z) * t;
        return this;
    }

    /** Rotation par un quaternion (v' = q·v·q⁻¹), même résultat que JOML. */
    public Vec3f rotate(Quatf q) {
        float tx = 2f * (q.y * this.z - q.z * this.y);
        float ty = 2f * (q.z * this.x - q.x * this.z);
        float tz = 2f * (q.x * this.y - q.y * this.x);
        float nx = this.x + q.w * tx + (q.y * tz - q.z * ty);
        float ny = this.y + q.w * ty + (q.z * tx - q.x * tz);
        float nz = this.z + q.w * tz + (q.x * ty - q.y * tx);
        return set(nx, ny, nz);
    }
}
