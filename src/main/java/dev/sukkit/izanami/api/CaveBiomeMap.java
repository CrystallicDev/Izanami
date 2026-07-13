package dev.sukkit.izanami.api;

/**
 * Placement des biomes de caverne : (x, y, z) → biome de cave, ou {@code null}
 * pour une caverne standard (pierre vanilla). Purement serveur — jamais envoyé
 * au client (voir {@link CaveBiome}). Implémentations immuables et thread-safe.
 */
public interface CaveBiomeMap {

    CaveBiome caveBiomeAt(int x, int y, int z);
}
