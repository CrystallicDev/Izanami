package dev.sukkit.izanami.gen;

import dev.sukkit.izanami.world.CaveSettings;
import dev.sukkit.izanami.world.RiverSettings;
import dev.sukkit.izanami.world.UndergroundRiverSettings;
import net.minecraft.server.v1_8_R3.ChunkProviderGenerate;
import net.minecraft.server.v1_8_R3.WorldGenBase;
import net.minecraft.server.v1_8_R3.WorldServer;

/**
 * Provider Izanami : terrain vanilla ({@link ChunkProviderGenerate}), mais
 * carvers substitués via les champs u (caves) / z (canyons) rendus protected
 * par Sukkit (patch 0134).
 * <p>
 * - style modern : u = {@link IzanamiCarver} (cavernes bruit + rivières),
 *   canyons vanilla désactivés (WorldGenBase de base = no-op).<br>
 * - style none : plus aucune caverne (u no-op), rivières optionnelles.<br>
 * - style vanilla + rivières : caves vanilla conservées, les rivières
 *   remplacent les canyons.
 */
public final class IzanamiChunkProvider extends ChunkProviderGenerate {

    public IzanamiChunkProvider(WorldServer world, long seed, boolean mapFeatures, String options,
                                CaveSettings caves, RiverSettings rivers,
                                UndergroundRiverSettings ugRivers,
                                dev.sukkit.izanami.world.SinkholeSettings sinkholes,
                                dev.sukkit.izanami.world.GeodeSettings geodes,
                                net.minecraft.server.v1_8_R3.IBlockData[] geodeBlocks,
                                CaveZoneMap zoneMap, int baseHeight) {
        super(world, seed, mapFeatures, options);

        boolean extraCarving = rivers.isEnabled() || ugRivers.isEnabled()
                || sinkholes.isEnabled() || geodes.isEnabled() || zoneMap != null;
        switch (caves.getStyle()) {
            case MODERN:
                this.u = new IzanamiCarver(seed, caves, rivers, ugRivers, sinkholes, geodes, geodeBlocks, zoneMap, baseHeight);
                this.z = new WorldGenBase(); // canyons vanilla off, le carver gère tout
                break;
            case NONE:
                this.u = extraCarving
                        ? new IzanamiCarver(seed, caves, rivers, ugRivers, sinkholes, geodes, geodeBlocks, zoneMap, baseHeight)
                        : new WorldGenBase();
                this.z = new WorldGenBase();
                break;
            case VANILLA:
                if (extraCarving) {
                    // caves vanilla conservées ; le reste (rivières, gouffres, géodes, peau) au slot canyon
                    this.z = new IzanamiCarver(seed, caves, rivers, ugRivers, sinkholes, geodes, geodeBlocks, zoneMap, baseHeight);
                }
                break;
        }
    }
}
