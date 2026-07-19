package dev.sukkit.izanami.tree.feature.trees;

import net.minecraft.server.v1_8_R3.IBlockData;

/**
 * Config du Pythadendron (porté de Crystallite, sans Codec) : blocs + paramètres
 * de taille/branches/feuillage. Le constructeur court applique les valeurs par
 * défaut du datapack d'origine.
 */
public final class PythadendronConfig {

    public final IBlockData logBlock;
    public final IBlockData leavesBlock;
    public final float minSize;
    public final float maxSize;
    public final float trunkRadius;
    public final float trunkIrregularity;
    public final int maxBranchDepth;
    public final float branchAngleVariation;
    public final float branchSizeMinMultiplier;
    public final float branchSizeMaxMultiplier;
    public final float branchOffsetVariation;
    public final float leavesMinRadius;
    public final float leavesMaxRadius;
    public final float leavesVerticalScale;

    public PythadendronConfig(IBlockData logBlock, IBlockData leavesBlock,
            float minSize, float maxSize, float trunkRadius, float trunkIrregularity, int maxBranchDepth,
            float branchAngleVariation, float branchSizeMinMultiplier, float branchSizeMaxMultiplier,
            float branchOffsetVariation, float leavesMinRadius, float leavesMaxRadius, float leavesVerticalScale) {
        this.logBlock = logBlock;
        this.leavesBlock = leavesBlock;
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.trunkRadius = trunkRadius;
        this.trunkIrregularity = trunkIrregularity;
        this.maxBranchDepth = maxBranchDepth;
        this.branchAngleVariation = branchAngleVariation;
        this.branchSizeMinMultiplier = branchSizeMinMultiplier;
        this.branchSizeMaxMultiplier = branchSizeMaxMultiplier;
        this.branchOffsetVariation = branchOffsetVariation;
        this.leavesMinRadius = leavesMinRadius;
        this.leavesMaxRadius = leavesMaxRadius;
        this.leavesVerticalScale = leavesVerticalScale;
    }

    /** Valeurs par défaut du datapack Crystallite (seuls les blocs sont requis). */
    public PythadendronConfig(IBlockData logBlock, IBlockData leavesBlock) {
        this(logBlock, leavesBlock, 10F, 20F, 1.7F, 1.1F, 3,
                0.1F, 0.75F, 0.95F, 0.3F, 4.5F, 6.5F, 0.6F);
    }
}
