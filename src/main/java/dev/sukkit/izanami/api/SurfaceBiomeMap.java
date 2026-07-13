package dev.sukkit.izanami.api;

import net.minecraft.server.v1_8_R3.BiomeBase;

/**
 * Fonction de placement des biomes de surface : (x, z) bloc → biome.
 * C'est ce biome qui est écrit dans le chunk, envoyé au client, et utilisé
 * par le terrain/la décoration/les spawns vanilla.
 * <p>
 * Implémentations : immuables et thread-safe (interrogeables pendant la
 * prégénération et par des plugins tiers).
 */
public interface SurfaceBiomeMap {

    BiomeBase surfaceBiomeAt(int x, int z);
}
