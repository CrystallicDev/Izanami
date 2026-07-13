package dev.sukkit.izanami.api;

/**
 * Biomes de cave fournis par Izanami. Les plugins peuvent tester l'identité
 * par référence (ex. {@code source.caveBiomeAt(x,y,z) == IzanamiCaveBiomes.UNDERGROUND_RIVER}).
 */
public final class IzanamiCaveBiomes {

    /** Rivière souterraine navigable (tunnels d'eau + chutes d'entrée). */
    public static final CaveBiome UNDERGROUND_RIVER = new CaveBiome("Riviere Souterraine");

    private IzanamiCaveBiomes() {}
}
