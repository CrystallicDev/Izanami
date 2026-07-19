package dev.sukkit.izanami.tree.placer;

import net.minecraft.server.v1_8_R3.IBlockData;

/**
 * Équivalent minimal de {@code TreeConfiguration} pour les placers : les blocs
 * tronc/feuillage (et terre sous le tronc). Les placers reçoivent ce config de
 * façon opaque et le passent aux helpers {@code placeLog}/{@code tryPlaceLeaf}.
 */
public final class TreeConfig {

    private final IBlockData log;
    private final IBlockData leaf;
    private final IBlockData dirt;

    public TreeConfig(IBlockData log, IBlockData leaf, IBlockData dirt) {
        this.log = log;
        this.leaf = leaf;
        this.dirt = dirt;
    }

    public TreeConfig(IBlockData log, IBlockData leaf) {
        this(log, leaf, net.minecraft.server.v1_8_R3.Blocks.DIRT.getBlockData());
    }

    public IBlockData log() {
        return this.log;
    }

    public IBlockData leaf() {
        return this.leaf;
    }

    public IBlockData dirt() {
        return this.dirt;
    }
}
