package dev.sukkit.izanami.tree.placer.trees;

import dev.sukkit.izanami.tree.placer.BlockPos;
import dev.sukkit.izanami.tree.placer.Direction;
import dev.sukkit.izanami.tree.placer.FoliagePlacer;
import dev.sukkit.izanami.tree.placer.IntProvider;
import dev.sukkit.izanami.tree.placer.TreeConfig;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.World;

import java.util.Random;
import java.util.function.BiConsumer;

/**
 * Porté depuis Crystallite (RedwoodFoliagePlacer) : cime conique (rayon
 * croissant vers le bas) avec branches radiales à intervalle, et petits
 * buissons sur les accroches latérales.
 */
public class RedwoodFoliagePlacer extends FoliagePlacer {

    private final int trunkWidth;
    private final int foliageHeight;
    private final int maxRadius;
    private final int branchInterval;

    public RedwoodFoliagePlacer(IntProvider radius, IntProvider offset, int trunkWidth, int foliageHeight,
                                int maxRadius, int branchInterval) {
        super(radius, offset);
        this.trunkWidth = trunkWidth;
        this.foliageHeight = foliageHeight;
        this.maxRadius = maxRadius;
        this.branchInterval = branchInterval;
    }

    @Override
    public void createFoliage(World level, BiConsumer<BlockPos, IBlockData> setter, Random random,
            TreeConfig config, int maxFreeTreeHeight, FoliageAttachment attachment, int foliageHeight,
            int foliageRadius, int offset) {

        BlockPos pos = attachment.pos();

        if (attachment.doubleTrunk()) {
            tryPlaceLeaf(level, setter, random, config, pos);

            for (int i = 0; i < this.foliageHeight; i++) {
                BlockPos layerPos = pos.below(i);
                float t = (float) i / this.foliageHeight;
                int radius = Math.round(maxRadius * t);
                radius = Math.max(0, Math.min(radius, maxRadius));

                if (radius == 0) {
                    tryPlaceLeaf(level, setter, random, config, layerPos);
                } else {
                    for (int x = -radius; x <= radius; x++) {
                        for (int z = -radius; z <= radius; z++) {
                            if ((x * x + z * z > (radius + 0.5f) * (radius + 0.5f))
                                    || (Math.abs(x) == radius && Math.abs(z) == radius && random.nextInt(2) == 0)) {
                                continue;
                            }

                            tryPlaceLeaf(level, setter, random, config, layerPos.offset(x, 0, z));
                        }
                    }
                    int interval = Math.max(1, branchInterval - Math.round(t * (branchInterval - 1)));
                    if (i % interval == 0 && radius >= 2) {
                        int branchLen = Math.max(2, radius - 1 + random.nextInt(2));
                        generateBranch(level, setter, random, config, layerPos, Direction.NORTH, branchLen);
                        generateBranch(level, setter, random, config, layerPos, Direction.EAST, branchLen);
                        generateBranch(level, setter, random, config, layerPos, Direction.SOUTH, branchLen);
                        generateBranch(level, setter, random, config, layerPos, Direction.WEST, branchLen);
                    }
                }
            }

        } else {
            generateBush(level, setter, random, config, pos);
        }
    }

    private void generateBranch(World level, BiConsumer<BlockPos, IBlockData> setter, Random random,
            TreeConfig config, BlockPos pos, Direction direction, int length) {

        Direction sideways = direction.getClockWise();

        for (int i = 1; i <= length; i++) {
            BlockPos pos1 = pos.relative(direction, i);
            int sideWidth = (i == 1 || i == length) ? 1 : Math.min(3, 1 + length / 3);
            for (int j = -sideWidth; j <= sideWidth; j++) {
                if (i < length || random.nextInt(2) == 0) {
                    tryPlaceLeaf(level, setter, random, config, pos1.relative(sideways, j));
                }
            }

            if (length - i > 1) {
                tryPlaceLeaf(level, setter, random, config, pos1.above());
                tryPlaceLeaf(level, setter, random, config, pos1.above().relative(sideways, -1));
                tryPlaceLeaf(level, setter, random, config, pos1.above().relative(sideways, 1));
                if (length >= 4) {
                    tryPlaceLeaf(level, setter, random, config, pos1.below());
                }
            }
        }
    }

    private void generateBush(World level, BiConsumer<BlockPos, IBlockData> setter, Random random,
            TreeConfig config, BlockPos pos) {
        int bushHeight = 3;
        for (int y = 0; y < bushHeight; y++) {
            int leavesRadius = bushHeight - y >= 2 ? 2 : 1;

            for (int x = -leavesRadius; x <= leavesRadius; x++) {
                for (int z = -leavesRadius; z <= leavesRadius; z++) {
                    if (Math.abs(x) < leavesRadius || Math.abs(z) < leavesRadius) {
                        tryPlaceLeaf(level, setter, random, config, pos.offset(x, y, z));
                    }
                }
            }
        }
    }

    @Override
    public int foliageHeight(Random random, int height, TreeConfig config) {
        return this.foliageHeight;
    }

    @Override
    protected boolean shouldSkipLocation(Random random, int dx, int dy, int dz, int range, boolean large) {
        return false;
    }
}
