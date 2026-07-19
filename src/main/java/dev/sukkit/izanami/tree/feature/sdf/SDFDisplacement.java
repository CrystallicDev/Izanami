package dev.sukkit.izanami.tree.feature.sdf;

import dev.sukkit.izanami.math.Vec3f;

import java.util.function.Function;

public class SDFDisplacement extends SDFUnary {
    private final Vec3f pos = new Vec3f(0, 0, 0);
    private Function<Vec3f, Float> displace;

    public SDFDisplacement setFunction(Function<Vec3f, Float> displace) {
        this.displace = displace;
        return this;
    }

    @Override
    public float getDistance(float x, float y, float z) {
        this.pos.set(x, y, z);
        return this.source.getDistance(x, y, z) + this.displace.apply(this.pos);
    }
}
