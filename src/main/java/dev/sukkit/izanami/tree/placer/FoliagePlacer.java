package dev.sukkit.izanami.tree.placer;

import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockLeaves;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.World;

import java.util.Random;
import java.util.function.BiConsumer;

/**
 * Base des placeurs de feuillage, calquée sur {@code FoliagePlacer} des versions
 * 1.16+. Voir {@link TrunkPlacer} pour le mode de portage.
 */
public abstract class FoliagePlacer {

    protected final IntProvider radius;
    protected final IntProvider offset;

    protected FoliagePlacer(IntProvider radius, IntProvider offset) {
        this.radius = radius;
        this.offset = offset;
    }

    /** Génère le feuillage autour d'un point d'accroche. */
    public abstract void createFoliage(
            World level, BiConsumer<BlockPos, IBlockData> blockSetter, Random random, TreeConfig config,
            int maxFreeTreeHeight, FoliageAttachment attachment, int foliageHeight, int foliageRadius, int offset);

    public abstract int foliageHeight(Random random, int height, TreeConfig config);

    protected boolean shouldSkipLocation(Random random, int dx, int dy, int dz, int range, boolean large) {
        return false;
    }

    /** Pose une feuille si l'emplacement est libre (air/feuilles/remplaçable). */
    protected static void tryPlaceLeaf(World level, BiConsumer<BlockPos, IBlockData> blockSetter,
                                       Random random, TreeConfig config, BlockPos pos) {
        BlockPosition p = pos.toNms();
        if (!level.isLoaded(p)) {
            return;
        }
        Block b = level.getType(p).getBlock();
        if (b == Blocks.AIR || b instanceof BlockLeaves || b.getMaterial().isReplaceable()) {
            blockSetter.accept(pos, config.leaf());
        }
    }

    /** Point d'accroche du feuillage renvoyé par un {@link TrunkPlacer}. */
    public static final class FoliageAttachment {

        private final BlockPos pos;
        private final int radiusOffset;
        private final boolean doubleTrunk;

        public FoliageAttachment(BlockPos pos, int radiusOffset, boolean doubleTrunk) {
            this.pos = pos;
            this.radiusOffset = radiusOffset;
            this.doubleTrunk = doubleTrunk;
        }

        public BlockPos pos() {
            return this.pos;
        }

        public int radiusOffset() {
            return this.radiusOffset;
        }

        public boolean doubleTrunk() {
            return this.doubleTrunk;
        }
    }
}
