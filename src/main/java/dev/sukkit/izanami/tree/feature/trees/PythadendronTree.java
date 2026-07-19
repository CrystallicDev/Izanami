package dev.sukkit.izanami.tree.feature.trees;

import dev.sukkit.izanami.math.MHelper;
import dev.sukkit.izanami.math.OpenSimplexNoise;
import dev.sukkit.izanami.math.Vec3f;
import dev.sukkit.izanami.tree.IzanamiTree;
import dev.sukkit.izanami.tree.feature.BlocksHelper;
import dev.sukkit.izanami.tree.feature.SplineHelper;
import dev.sukkit.izanami.tree.feature.sdf.SDF;
import dev.sukkit.izanami.tree.feature.sdf.SDFDisplacement;
import dev.sukkit.izanami.tree.feature.sdf.SDFScale3D;
import dev.sukkit.izanami.tree.feature.sdf.SDFSphere;
import dev.sukkit.izanami.tree.feature.sdf.SDFSubtraction;
import dev.sukkit.izanami.tree.feature.sdf.SDFTranslate;
import dev.sukkit.izanami.tree.placer.BlockPos;
import net.minecraft.server.v1_8_R3.BlockLogAbstract;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.World;

import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * Arbre Pythadendron porté depuis Crystallite (Feature basée SDF de bclib).
 * Tronc en spline + branches récursives + boules de feuillage bruitées. Adapté
 * 1.8.9 : {@code RandomSource}→{@link Random}, tags/leaf-distance 1.18 →
 * {@code BlockLog}. Implémente {@link IzanamiTree} (origine = surface).
 */
public final class PythadendronTree implements IzanamiTree {

    private final PythadendronConfig config;

    public PythadendronTree(PythadendronConfig config) {
        this.config = config;
    }

    @Override
    public boolean place(World world, Random random, BlockPosition surface) {
        if (!world.getType(surface.down()).getBlock().getMaterial().isBuildable()) {
            return false;
        }
        BlockPos pos = new BlockPos(surface.getX(), surface.getY(), surface.getZ());

        float size = MHelper.randRange(config.minSize, config.maxSize, random);
        List<Vec3f> spline = SplineHelper.makeSpline(0, 0, 0, 0, size, 0, 4);
        SplineHelper.offsetParts(spline, random, 0.7F, 0, 0.7F);
        Vec3f last = spline.get(spline.size() - 1);

        int depth = MHelper.floor((size - config.minSize) * 3F / (config.maxSize - config.minSize) + 1F);
        depth = Math.min(depth, config.maxBranchDepth);
        float bsize = (config.minSize - (size - config.minSize)) / config.minSize + 1.5F;

        branch(last.x(), last.y(), last.z(), size * bsize,
                MHelper.randRange(0, MHelper.PI2, random), random, depth, world, pos);

        Function<IBlockData, Boolean> replaceFunction = state -> {
            if (state.getBlock() == config.leavesBlock.getBlock()) {
                return true;
            }
            return BlocksHelper.replaceableOrPlant(state);
        };
        Function<dev.sukkit.izanami.tree.feature.sdf.PosInfo, IBlockData> postProcess = info -> {
            if (isLog(info.getStateUp()) && isLog(info.getStateDown())) {
                return config.logBlock;
            }
            return info.getState();
        };

        SDF function = SplineHelper.buildSDF(spline, config.trunkRadius, config.trunkIrregularity,
                bpos -> config.logBlock);
        function.setReplaceFunction(replaceFunction);
        function.addPostProcess(postProcess);
        function.fillRecursive(world, pos);
        return true;
    }

    private void branch(float x, float y, float z, float size, float angle, Random random, int depth,
                        World world, BlockPos pos) {
        if (depth == 0) {
            return;
        }
        float dx = (float) Math.cos(angle) * size * 0.15F;
        float dz = (float) Math.sin(angle) * size * 0.15F;
        float x1 = x + dx, z1 = z + dz, x2 = x - dx, z2 = z - dz;

        Function<IBlockData, Boolean> replaceFunction = state -> {
            if (state.getBlock() == config.leavesBlock.getBlock()) {
                return true;
            }
            return BlocksHelper.replaceableOrPlant(state);
        };

        List<Vec3f> spline = SplineHelper.makeSpline(x, y, z, x1, y, z1, 5);
        SplineHelper.powerOffset(spline, size * MHelper.randRange(1.0F, 2.0F, random), 4);
        SplineHelper.offsetParts(spline, random, config.branchOffsetVariation, 0, config.branchOffsetVariation);
        Vec3f pos1 = spline.get(spline.size() - 1);
        boolean s1 = SplineHelper.fillSpline(spline, world, config.logBlock, pos, replaceFunction);

        spline = SplineHelper.makeSpline(x, y, z, x2, y, z2, 5);
        SplineHelper.powerOffset(spline, size * MHelper.randRange(1.0F, 2.0F, random), 4);
        SplineHelper.offsetParts(spline, random, config.branchOffsetVariation, 0, config.branchOffsetVariation);
        Vec3f pos2 = spline.get(spline.size() - 1);
        boolean s2 = SplineHelper.fillSpline(spline, world, config.logBlock, pos, replaceFunction);

        OpenSimplexNoise noise = new OpenSimplexNoise(random.nextInt());
        if (depth < 3) {
            if (s1) {
                leavesBall(world, pos.offset((int) pos1.x(), (int) pos1.y(), (int) pos1.z()), random, noise);
            }
            if (s2) {
                leavesBall(world, pos.offset((int) pos2.x(), (int) pos2.y(), (int) pos2.z()), random, noise);
            }
        }

        float size1 = size * MHelper.randRange(config.branchSizeMinMultiplier, config.branchSizeMaxMultiplier, random);
        float size2 = size * MHelper.randRange(config.branchSizeMinMultiplier, config.branchSizeMaxMultiplier, random);
        float angle1 = angle + (float) Math.PI * 0.5F
                + MHelper.randRange(-config.branchAngleVariation, config.branchAngleVariation, random);
        float angle2 = angle + (float) Math.PI * 0.5F
                + MHelper.randRange(-config.branchAngleVariation, config.branchAngleVariation, random);

        if (s1) {
            branch(pos1.x(), pos1.y(), pos1.z(), size1, angle1, random, depth - 1, world, pos);
        }
        if (s2) {
            branch(pos2.x(), pos2.y(), pos2.z(), size2, angle2, random, depth - 1, world, pos);
        }
    }

    private void leavesBall(World world, BlockPos pos, Random random, OpenSimplexNoise noise) {
        float radius = MHelper.randRange(config.leavesMinRadius, config.leavesMaxRadius, random);
        SDF sphere = new SDFSphere().setRadius(radius).setBlock(config.leavesBlock);
        sphere = new SDFScale3D().setScale(1, config.leavesVerticalScale, 1).setSource(sphere);
        sphere = new SDFDisplacement()
                .setFunction(vec -> (float) noise.eval(vec.x() * 0.2, vec.y() * 0.2, vec.z() * 0.2) * 3)
                .setSource(sphere);
        sphere = new SDFDisplacement().setFunction(vec -> random.nextFloat() * 3F - 1.5F).setSource(sphere);
        sphere = new SDFSubtraction().setSourceA(sphere)
                .setSourceB(new SDFTranslate().setTranslate(0, -radius, 0).setSource(sphere));
        sphere.setReplaceFunction(BlocksHelper::replaceableOrPlant);
        // traverse les rondins (branches) sans les écraser
        sphere.fillRecursiveIgnore(world, pos, PythadendronTree::isLog);
    }

    private static boolean isLog(IBlockData state) {
        return state.getBlock() instanceof BlockLogAbstract;
    }
}
