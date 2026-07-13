package dev.sukkit.izanami.gen;

import dev.sukkit.izanami.world.IzanamiWorldConfig;
import net.minecraft.server.v1_8_R3.ChunkProviderGenerate;
import net.minecraft.server.v1_8_R3.IChunkProvider;
import net.minecraft.server.v1_8_R3.World;
import net.minecraft.server.v1_8_R3.WorldChunkManager;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.bukkit.craftbukkit.v1_8_R3.worldgen.NmsWorldGenerator;

/**
 * Générateur branché sur le hook WorldGen de Sukkit : fournit le
 * WorldChunkManager (biomes) et l'IChunkProvider (terrain + carvers) du monde.
 * La config est résolue et validée par le WorldService AVANT createWorld.
 */
public final class IzanamiGenerator implements NmsWorldGenerator {

    private final IzanamiWorldConfig config;
    private final ResolvedBiomeSettings resolvedBiomes; // null = biomes vanilla
    private final dev.sukkit.izanami.world.WorldService worldService;

    public IzanamiGenerator(IzanamiWorldConfig config, ResolvedBiomeSettings resolvedBiomes,
                            dev.sukkit.izanami.world.WorldService worldService) {
        this.config = config;
        this.resolvedBiomes = resolvedBiomes;
        this.worldService = worldService;
    }

    @Override
    public IChunkProvider createChunkProvider(WorldServer world) {
        registerBiomeSource(world);

        dev.sukkit.izanami.world.CaveSettings caves = this.config.getCaves();
        dev.sukkit.izanami.world.RiverSettings rivers = this.config.getRivers();
        dev.sukkit.izanami.world.UndergroundRiverSettings ugRivers = this.config.getUndergroundRivers();
        dev.sukkit.izanami.world.SinkholeSettings sinkholes = this.config.getSinkholes();
        dev.sukkit.izanami.world.GeodeSettings geodes = this.config.getGeodes();
        net.minecraft.server.v1_8_R3.IBlockData[] geodeBlocks = geodes.isEnabled() ? resolveGeodeBlocks() : null;
        CaveZoneMap zoneMap = CaveZoneMap.resolve(this.config.getCaveBiomes(),
                this.config.getBaseHeight(), world.getSeed());
        if (caves.getStyle() == dev.sukkit.izanami.world.CaveSettings.Style.VANILLA
                && !rivers.isEnabled() && !ugRivers.isEnabled()
                && !sinkholes.isEnabled() && geodeBlocks == null && zoneMap == null) {
            return new ChunkProviderGenerate(
                    world,
                    world.getSeed(),
                    world.getWorldData().shouldGenerateMapFeatures(),
                    world.getWorldData().getGeneratorOptions()
            );
        }
        return new IzanamiChunkProvider(
                world,
                world.getSeed(),
                world.getWorldData().shouldGenerateMapFeatures(),
                world.getWorldData().getGeneratorOptions(),
                caves,
                rivers,
                ugRivers,
                sinkholes,
                geodes,
                geodeBlocks,
                zoneMap,
                this.config.getBaseHeight()
        );
    }

    /**
     * Blocs des géodes depuis le registre de blocs custom :
     * {calcite, améthyste, cluster, petit/moyen/grand bourgeon}.
     * @return null (géodes désactivées) si un bloc manque
     */
    private net.minecraft.server.v1_8_R3.IBlockData[] resolveGeodeBlocks() {
        String[] names = {"calcite", "amethyst_block", "amethyst_cluster",
                "small_amethyst_bud", "medium_amethyst_bud", "large_amethyst_bud"};
        net.minecraft.server.v1_8_R3.IBlockData[] blocks = new net.minecraft.server.v1_8_R3.IBlockData[names.length];
        for (int i = 0; i < names.length; i++) {
            dev.sukkit.izanami.custom.CustomBlock block =
                    this.worldService.getPlugin().getCustomBlockRegistry().get(names[i]);
            if (block == null) {
                org.bukkit.Bukkit.getLogger().severe("[Izanami] Geodes desactivees : bloc custom '"
                        + names[i] + "' manquant (texture absente ?)");
                return null;
            }
            blocks[i] = net.minecraft.server.v1_8_R3.Block.getById(block.getBlockId())
                    .fromLegacyData(block.getMeta());
        }
        return blocks;
    }

    @Override
    public WorldChunkManager createWorldChunkManager(World world) {
        if (this.resolvedBiomes == null) {
            return null; // biomes de surface vanilla
        }
        return new IzanamiWorldChunkManager(world.getSeed(), this.resolvedBiomes);
    }

    /**
     * Publie la source de biomes (surface + cave) du monde. Appelé depuis
     * createChunkProvider : le WorldChunkManager (custom ou vanilla) existe
     * déjà à ce stade.
     */
    private void registerBiomeSource(WorldServer world) {
        final WorldChunkManager manager = world.getWorldChunkManager();
        dev.sukkit.izanami.api.SurfaceBiomeMap surface;
        if (manager instanceof IzanamiWorldChunkManager) {
            surface = (IzanamiWorldChunkManager) manager;
        } else {
            surface = (x, z) -> manager.getBiome(new net.minecraft.server.v1_8_R3.BlockPosition(x, 0, z), null);
        }
        CaveZoneMap zones = CaveZoneMap.resolve(this.config.getCaveBiomes(),
                this.config.getBaseHeight(), world.getSeed());
        dev.sukkit.izanami.api.CaveBiomeMap cave =
                this.config.getUndergroundRivers().isEnabled() || zones != null
                        ? new IzanamiCaveBiomeMap(world.getSeed(), this.config.getUndergroundRivers(),
                                zones, this.config.getBaseHeight())
                        : null;
        this.worldService.registerBiomeSource(this.config.getName(),
                new dev.sukkit.izanami.api.IzanamiBiomeSource(surface, cave));
    }

    public IzanamiWorldConfig getConfig() {
        return this.config;
    }
}
