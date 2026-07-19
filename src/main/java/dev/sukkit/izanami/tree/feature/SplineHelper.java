package dev.sukkit.izanami.tree.feature;

import dev.sukkit.izanami.math.MHelper;
import dev.sukkit.izanami.math.Vec3f;
import dev.sukkit.izanami.tree.feature.sdf.SDF;
import dev.sukkit.izanami.tree.feature.sdf.SDFLine;
import dev.sukkit.izanami.tree.feature.sdf.SDFUnion;
import dev.sukkit.izanami.tree.placer.BlockPos;
import dev.sukkit.izanami.tree.placer.Mth;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/** Splines + construction de SDF le long d'une courbe (porté de SALM/bclib, adapté 1.8). */
public final class SplineHelper {

    private SplineHelper() {}

    public static List<Vec3f> makeSpline(float x1, float y1, float z1, float x2, float y2, float z2, int points) {
        List<Vec3f> spline = new ArrayList<>();
        spline.add(new Vec3f(x1, y1, z1));
        int count = points - 1;
        for (int i = 1; i < count; i++) {
            float delta = (float) i / (float) count;
            spline.add(new Vec3f(Mth.lerp(delta, x1, x2), Mth.lerp(delta, y1, y2), Mth.lerp(delta, z1, z2)));
        }
        spline.add(new Vec3f(x2, y2, z2));
        return spline;
    }

    public static List<Vec3f> smoothSpline(List<Vec3f> spline, int segmentPoints) {
        List<Vec3f> result = new ArrayList<>();
        Vec3f start = spline.get(0);
        for (int i = 1; i < spline.size(); i++) {
            Vec3f end = spline.get(i);
            for (int j = 0; j < segmentPoints; j++) {
                float delta = (float) j / segmentPoints;
                delta = 0.5F - 0.5F * Mth.cos(delta * 3.14159F);
                result.add(lerp(start, end, delta));
            }
            start = end;
        }
        result.add(start);
        return result;
    }

    private static Vec3f lerp(Vec3f start, Vec3f end, float delta) {
        return new Vec3f(Mth.lerp(delta, start.x(), end.x()),
                Mth.lerp(delta, start.y(), end.y()),
                Mth.lerp(delta, start.z(), end.z()));
    }

    public static void offsetParts(List<Vec3f> spline, Random random, float dx, float dy, float dz) {
        int count = spline.size();
        for (int i = 1; i < count; i++) {
            Vec3f pos = spline.get(i);
            pos.set(pos.x() + (float) random.nextGaussian() * dx,
                    pos.y() + (float) random.nextGaussian() * dy,
                    pos.z() + (float) random.nextGaussian() * dz);
        }
    }

    public static void powerOffset(List<Vec3f> spline, float distance, float power) {
        int count = spline.size();
        float max = count + 1;
        for (int i = 1; i < count; i++) {
            Vec3f pos = spline.get(i);
            float x = (float) i / max;
            pos.set(pos.x(), pos.y() + (float) Math.pow(x, power) * distance, pos.z());
        }
    }

    public static SDF buildSDF(List<Vec3f> spline, float radius1, float radius2,
                               Function<BlockPos, IBlockData> placerFunction) {
        int count = spline.size();
        float max = count - 2;
        SDF result = null;
        Vec3f start = spline.get(0);
        for (int i = 1; i < count; i++) {
            Vec3f pos = spline.get(i);
            float delta = (float) (i - 1) / max;
            SDF line = new SDFLine().setRadius(Mth.lerp(delta, radius1, radius2))
                    .setStart(start.x(), start.y(), start.z())
                    .setEnd(pos.x(), pos.y(), pos.z())
                    .setBlock(placerFunction);
            result = result == null ? line : new SDFUnion().setSourceA(result).setSourceB(line);
            start = pos;
        }
        return result;
    }

    public static boolean fillSpline(List<Vec3f> spline, World world, IBlockData state, BlockPos pos,
                                     Function<IBlockData, Boolean> replace) {
        Vec3f startPos = spline.get(0);
        for (int i = 1; i < spline.size(); i++) {
            Vec3f endPos = spline.get(i);
            if (!fillLine(startPos, endPos, world, state, pos, replace)) {
                return false;
            }
            startPos = endPos;
        }
        return true;
    }

    public static boolean fillLine(Vec3f start, Vec3f end, World world, IBlockData state, BlockPos pos,
                                   Function<IBlockData, Boolean> replace) {
        float dx = end.x() - start.x();
        float dy = end.y() - start.y();
        float dz = end.z() - start.z();
        float max = MHelper.max(Math.abs(dx), Math.abs(dy), Math.abs(dz));
        if (max <= 0) {
            return true;
        }
        int count = MHelper.floor(max + 1);
        dx /= max;
        dy /= max;
        dz /= max;
        float x = start.x();
        float y = start.y();
        float z = start.z();
        boolean down = Math.abs(dy) > 0.2;

        for (int i = 0; i <= count; i++) {
            float fx = (i == count) ? end.x() : x;
            float fy = (i == count) ? end.y() : y;
            float fz = (i == count) ? end.z() : z;
            BlockPos bp = new BlockPos(MHelper.floor(fx + pos.getX()),
                    MHelper.floor(fy + pos.getY()), MHelper.floor(fz + pos.getZ()));
            if (!world.isLoaded(bp.toNms())) {
                x += dx; y += dy; z += dz;
                continue;
            }
            IBlockData bState = world.getType(bp.toNms());
            if (bState.equals(state) || replace.apply(bState)) {
                BlocksHelper.setWithoutUpdate(world, bp, state);
                BlockPos below = bp.below();
                if (world.isLoaded(below.toNms())) {
                    IBlockData bs2 = world.getType(below.toNms());
                    if ((down && bs2.equals(state)) || replace.apply(bs2)) {
                        BlocksHelper.setWithoutUpdate(world, below, state);
                    }
                }
            } else if (i < count) {
                return false;
            }
            x += dx; y += dy; z += dz;
        }
        return true;
    }

    public static void rotateSpline(List<Vec3f> spline, float angle) {
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        for (Vec3f v : spline) {
            v.set(v.x() * cos + v.z() * sin, v.y(), v.x() * sin + v.z() * cos);
        }
    }

    public static List<Vec3f> copySpline(List<Vec3f> spline) {
        List<Vec3f> result = new ArrayList<>(spline.size());
        for (Vec3f v : spline) {
            result.add(new Vec3f(v.x(), v.y(), v.z()));
        }
        return result;
    }

    public static void scale(List<Vec3f> spline, float x, float y, float z) {
        for (Vec3f v : spline) {
            v.set(v.x() * x, v.y() * y, v.z() * z);
        }
    }

    public static void offset(List<Vec3f> spline, Vec3f offset) {
        for (Vec3f v : spline) {
            v.set(offset.x() + v.x(), offset.y() + v.y(), offset.z() + v.z());
        }
    }
}
