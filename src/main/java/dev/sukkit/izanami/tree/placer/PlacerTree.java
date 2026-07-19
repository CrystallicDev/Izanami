package dev.sukkit.izanami.tree.placer;

import dev.sukkit.izanami.tree.IzanamiTree;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.World;

import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;

/**
 * Arbre procédural {@link IzanamiTree} piloté par un {@link TrunkPlacer} + un
 * {@link FoliagePlacer} (modèle des versions 1.16+). Le tronc est placé, puis le
 * feuillage sur chaque point d'accroche renvoyé — comme le fait
 * {@code TreeFeature} en vanilla. Écritures directes en flag 2 (pas de physique).
 */
public final class PlacerTree implements IzanamiTree {

    private final TrunkPlacer trunk;
    private final FoliagePlacer foliage;
    private final TreeConfig config;

    public PlacerTree(TrunkPlacer trunk, FoliagePlacer foliage, TreeConfig config) {
        this.trunk = trunk;
        this.foliage = foliage;
        this.config = config;
    }

    @Override
    public boolean place(World world, Random random, BlockPosition surface) {
        // sol plein sous la base (pas d'arbre sur l'eau ou dans le vide)
        if (!world.getType(surface.down()).getBlock().getMaterial().isBuildable()) {
            return false;
        }
        BiConsumer<BlockPos, IBlockData> setter = (pos, state) -> {
            BlockPosition p = pos.toNms();
            if (world.isLoaded(p)) {
                world.setTypeAndData(p, state, 2);
            }
        };

        int height = this.trunk.getTreeHeight(random);
        BlockPos start = new BlockPos(surface.getX(), surface.getY(), surface.getZ());

        List<FoliagePlacer.FoliageAttachment> attachments =
                this.trunk.placeTrunk(world, setter, random, height, start, this.config);
        for (FoliagePlacer.FoliageAttachment attachment : attachments) {
            int foliageHeight = this.foliage.foliageHeight(random, height, this.config);
            int radius = this.foliage.radius.sample(random) + attachment.radiusOffset();
            int offset = this.foliage.offset.sample(random);
            this.foliage.createFoliage(world, setter, random, this.config, height,
                    attachment, foliageHeight, radius, offset);
        }
        return true;
    }
}
