package dev.sukkit.izanami.tree.placer;

import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockLeaves;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.World;

import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;

/**
 * Base des placeurs de tronc, calquée sur {@code TrunkPlacer} des versions 1.16+
 * pour porter la logique existante avec un minimum de changements : garder le
 * corps de {@code placeTrunk}, remplacer les types Mojang par ceux de ce package
 * ({@link BlockPos}, {@link Direction}, {@link Mth}, {@link TreeConfig},
 * {@code World} NMS, {@code IBlockData}). Le Codec/registre/{@code type()} des
 * mods datapack n'est pas nécessaire ici.
 */
public abstract class TrunkPlacer {

    protected final int baseHeight;
    protected final int heightRandA;
    protected final int heightRandB;

    protected TrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        this.baseHeight = baseHeight;
        this.heightRandA = heightRandA;
        this.heightRandB = heightRandB;
    }

    /** Hauteur du tronc (convention vanilla base + rand(A) + rand(B)). */
    public int getTreeHeight(Random random) {
        return this.baseHeight + random.nextInt(this.heightRandA + 1) + random.nextInt(this.heightRandB + 1);
    }

    /**
     * Place le tronc et renvoie les points d'accroche du feuillage.
     *
     * @param startPos base du tronc, au niveau du sol
     */
    public abstract List<FoliagePlacer.FoliageAttachment> placeTrunk(
            World level, BiConsumer<BlockPos, IBlockData> blockSetter, Random random,
            int height, BlockPos startPos, TreeConfig config);

    /** Pose un rondin si l'emplacement est libre (air/feuilles/remplaçable). */
    public static boolean placeLog(World level, BiConsumer<BlockPos, IBlockData> blockSetter,
                                   Random random, BlockPos pos, TreeConfig config) {
        if (!canReplace(level, pos)) {
            return false;
        }
        blockSetter.accept(pos, config.log());
        return true;
    }

    /** Pose de la terre sous le tronc si ce n'est pas déjà un sol. */
    protected static void setDirtAt(World level, BiConsumer<BlockPos, IBlockData> blockSetter,
                                    Random random, BlockPos pos, TreeConfig config) {
        BlockPosition p = pos.toNms();
        if (!level.isLoaded(p)) {
            return;
        }
        Block b = level.getType(p).getBlock();
        if (b != Blocks.GRASS && b != Blocks.DIRT) {
            blockSetter.accept(pos, config.dirt());
        }
    }

    static boolean canReplace(World level, BlockPos pos) {
        BlockPosition p = pos.toNms();
        if (!level.isLoaded(p)) {
            return false; // hors chunks chargés : ne jamais forcer un chargement en pleine décoration
        }
        Block b = level.getType(p).getBlock();
        return b == Blocks.AIR || b instanceof BlockLeaves || b.getMaterial().isReplaceable();
    }
}
