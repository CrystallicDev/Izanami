package dev.sukkit.izanami.tree.feature.sdf;

public class SDFTranslate extends SDFUnary {
    private float x, y, z;

    public SDFTranslate setTranslate(float x, float y, float z) {
        this.x = x; this.y = y; this.z = z; return this;
    }

    @Override
    public float getDistance(float x, float y, float z) {
        return this.source.getDistance(x - this.x, y - this.y, z - this.z);
    }
}
