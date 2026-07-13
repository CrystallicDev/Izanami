package dev.sukkit.izanami.api;

import net.minecraft.server.v1_8_R3.IBlockData;

/**
 * Biome de caverne — concept serveur uniquement (en 1.8 le biome d'un chunk est
 * un tableau 2D par colonne : impossible d'en envoyer un par Y au client). Il
 * pilote la palette de blocs des parois et les décorations ; interrogeable via
 * {@link IzanamiBiomeSource}. Palette/décos acceptent n'importe quel
 * {@link IBlockData}, blocs custom OptiFine compris.
 */
public class CaveBiome {

    private final String name;
    private IBlockData floorBlock;      // null = sol naturel
    private IBlockData wallBlock;       // null = parois naturelles
    private IBlockData ceilingBlock;    // null = plafond naturel
    private IBlockData[] floorDecorations;   // posées sur les sols exposés (variante par hash)
    private IBlockData[] ceilingDecorations; // posées sous les plafonds
    private double decorationChance = 0.08;
    private int floorDecorationMaxHeight = 1;   // colonnes 1..N (variante 0 uniquement)
    private int ceilingDecorationMaxHeight = 1;
    // patches : les décorations n'apparaissent que par taches (façon vanilla)
    private int decorationPatchSpacing = 0;     // 0 = uniforme partout
    private int decorationPatchRadius = 4;
    // lacs : étendues d'eau en cuvette sur les sols du biome
    private IBlockData[] poolLiners;  // fond du lac (variantes) — null = pas de lacs
    private IBlockData poolPad;       // pad flottant optionnel (nénuphar/dripleaf)
    private int poolSpacing = 0;
    private int poolMinRadius = 5;
    private int poolMaxRadius = 15;
    private int poolDepth = 4;

    public CaveBiome(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Nom de biome de cave vide");
        }
        this.name = name.trim();
    }

    /** Blocs remplaçant la pierre exposée des cavernes dans ce biome (null = naturel). */
    public CaveBiome palette(IBlockData floor, IBlockData wall, IBlockData ceiling) {
        this.floorBlock = floor;
        this.wallBlock = wall;
        this.ceilingBlock = ceiling;
        return this;
    }

    /** Décorations posées dans l'air des cavernes, contre sol/plafond (null = aucune). */
    public CaveBiome decorations(IBlockData floorDeco, IBlockData ceilingDeco, double chance) {
        this.floorDecorations = floorDeco == null ? null : new IBlockData[]{floorDeco};
        this.ceilingDecorations = ceilingDeco == null ? null : new IBlockData[]{ceilingDeco};
        this.decorationChance = Math.max(0.0, Math.min(1.0, chance));
        return this;
    }

    /** Variantes de décoration au sol (choisies par hash déterministe par position). */
    public CaveBiome floorDecorations(IBlockData... blocks) {
        this.floorDecorations = blocks == null || blocks.length == 0 ? null : blocks;
        return this;
    }

    /** Variantes de décoration au plafond. */
    public CaveBiome ceilingDecorations(IBlockData... blocks) {
        this.ceilingDecorations = blocks == null || blocks.length == 0 ? null : blocks;
        return this;
    }

    public CaveBiome decorationChance(double chance) {
        this.decorationChance = Math.max(0.0, Math.min(1.0, chance));
        return this;
    }

    /**
     * Hauteur max des colonnes de décoration (1..6), sol et plafond séparés.
     * Seule la PREMIÈRE variante de chaque liste est empilée en colonne
     * (stalagmites, lianes) — les autres restent à 1 bloc. Combiné au rendu
     * CTM vertical du bloc custom, la colonne affiche base/milieu/pointe.
     */
    public CaveBiome decorationHeight(int floorMax, int ceilingMax) {
        this.floorDecorationMaxHeight = Math.max(1, Math.min(6, floorMax));
        this.ceilingDecorationMaxHeight = Math.max(1, Math.min(6, ceilingMax));
        return this;
    }

    public CaveBiome decorationHeight(int maxHeight) {
        return decorationHeight(maxHeight, maxHeight);
    }

    /**
     * Regroupe les décorations en taches (façon features vanilla) : un patch
     * potentiel tous les ~spacing blocs, de ~radius blocs de rayon. En dehors
     * des patches, aucune décoration. decorationChance s'applique DANS le patch.
     */
    public CaveBiome decorationPatches(int spacing, int radius) {
        this.decorationPatchSpacing = Math.max(0, spacing);
        this.decorationPatchRadius = Math.max(2, Math.min(spacing / 2, radius));
        return this;
    }

    /**
     * Lacs en cuvette sur les sols du biome (ex. Lush Caves : fond argile,
     * dripleaf flottants). Profondeur maximale au centre, bords à 1 bloc.
     *
     * @param liners    variantes du bloc de fond (choisies par hash)
     * @param pad       bloc flottant posé sur l'eau (~15% des surfaces), nullable
     * @param spacing   distance approximative entre lacs (blocs)
     * @param minRadius rayon minimum (2..24)
     * @param maxRadius rayon maximum (varié par lac)
     * @param depth     profondeur max au centre (1..6)
     */
    public CaveBiome pools(IBlockData[] liners, IBlockData pad, int spacing,
                           int minRadius, int maxRadius, int depth) {
        this.poolLiners = liners == null || liners.length == 0 ? null : liners;
        this.poolPad = pad;
        this.poolSpacing = Math.max(16, spacing);
        this.poolMinRadius = Math.max(2, Math.min(24, minRadius));
        this.poolMaxRadius = Math.max(this.poolMinRadius, Math.min(24, maxRadius));
        this.poolDepth = Math.max(1, Math.min(6, depth));
        return this;
    }

    public final String getName() {
        return this.name;
    }

    public IBlockData getFloorBlock() {
        return this.floorBlock;
    }

    public IBlockData getWallBlock() {
        return this.wallBlock;
    }

    public IBlockData getCeilingBlock() {
        return this.ceilingBlock;
    }

    public IBlockData[] getFloorDecorations() {
        return this.floorDecorations;
    }

    public IBlockData[] getCeilingDecorations() {
        return this.ceilingDecorations;
    }

    public double getDecorationChance() {
        return this.decorationChance;
    }

    public int getFloorDecorationMaxHeight() {
        return this.floorDecorationMaxHeight;
    }

    public int getCeilingDecorationMaxHeight() {
        return this.ceilingDecorationMaxHeight;
    }

    public int getDecorationPatchSpacing() {
        return this.decorationPatchSpacing;
    }

    public int getDecorationPatchRadius() {
        return this.decorationPatchRadius;
    }

    public IBlockData[] getPoolLiners() {
        return this.poolLiners;
    }

    public IBlockData getPoolPad() {
        return this.poolPad;
    }

    public int getPoolSpacing() {
        return this.poolSpacing;
    }

    public int getPoolMinRadius() {
        return this.poolMinRadius;
    }

    public int getPoolMaxRadius() {
        return this.poolMaxRadius;
    }

    public int getPoolDepth() {
        return this.poolDepth;
    }

    @Override
    public String toString() {
        return "CaveBiome[" + this.name + "]";
    }
}
