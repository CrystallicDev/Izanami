package dev.sukkit.izanami.tree.feature.sdf;

import dev.sukkit.izanami.math.MHelper;
import dev.sukkit.izanami.tree.placer.Mth;

public class SDFLine extends SDFPrimitive {
    private float radius, x1, y1, z1, x2, y2, z2;

    public SDFLine setRadius(float radius) { this.radius = radius; return this; }
    public SDFLine setStart(float x, float y, float z) { this.x1 = x; this.y1 = y; this.z1 = z; return this; }
    public SDFLine setEnd(float x, float y, float z) { this.x2 = x; this.y2 = y; this.z2 = z; return this; }

    @Override
    public float getDistance(float x, float y, float z) {
        float pax = x - x1, pay = y - y1, paz = z - z1;
        float bax = x2 - x1, bay = y2 - y1, baz = z2 - z1;
        float dpb = MHelper.dot(pax, pay, paz, bax, bay, baz);
        float dbb = MHelper.dot(bax, bay, baz, bax, bay, baz);
        float h = Mth.clamp(dpb / dbb, 0F, 1F);
        return MHelper.length(pax - bax * h, pay - bay * h, paz - baz * h) - radius;
    }
}
