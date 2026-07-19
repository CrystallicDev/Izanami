package dev.sukkit.izanami.tree.feature.sdf;

import dev.sukkit.izanami.math.Quatf;
import dev.sukkit.izanami.math.Vec3f;

public class SDFRotation extends SDFUnary {
    private final Vec3f pos = new Vec3f(0, 0, 0);
    private Quatf rotation;

    public SDFRotation setRotation(Vec3f axis, float rotationAngle) {
        this.rotation = new Quatf().setAngleAxis(rotationAngle, axis.x, axis.y, axis.z);
        return this;
    }

    @Override
    public float getDistance(float x, float y, float z) {
        this.pos.set(x, y, z);
        this.pos.rotate(this.rotation);
        return this.source.getDistance(this.pos.x(), this.pos.y(), this.pos.z());
    }
}
