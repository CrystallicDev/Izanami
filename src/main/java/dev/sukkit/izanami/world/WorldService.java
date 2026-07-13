package dev.sukkit.izanami.world;

import dev.sukkit.izanami.IzanamiPlugin;
import dev.sukkit.izanami.gen.IzanamiGenerator;
import dev.sukkit.izanami.gen.ResolvedBiomeSettings;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_8_R3.worldgen.NmsWorldGenRegistry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Gère le cycle de vie des mondes Izanami : enregistrement du générateur NMS
 * auprès de Sukkit (toujours AVANT {@code WorldCreator#createWorld()}),
 * création/chargement, et persistance dans config.yml.
 */
public final class WorldService {

    private final IzanamiPlugin plugin;
    private final Map<String, IzanamiWorldConfig> worlds = new LinkedHashMap<>();
    private final Map<String, dev.sukkit.izanami.api.IzanamiBiomeSource> biomeSources = new LinkedHashMap<>();

    public WorldService(IzanamiPlugin plugin) {
        this.plugin = plugin;
    }

    public IzanamiPlugin getPlugin() {
        return this.plugin;
    }

    public void loadFromConfig() {
        this.worlds.clear();
        ConfigurationSection root = this.plugin.getConfig().getConfigurationSection("worlds");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(name);
            if (section != null) {
                this.worlds.put(key(name), IzanamiWorldConfig.fromSection(name, section));
            }
        }
    }

    public void createAutoloadWorlds() {
        for (IzanamiWorldConfig config : this.worlds.values()) {
            if (config.isAutoload()) {
                try {
                    createOrLoad(config);
                } catch (IllegalArgumentException e) {
                    this.plugin.getLogger().severe("Monde '" + config.getName() + "' ignore : " + e.getMessage());
                }
            }
        }
    }

    /**
     * Crée un nouveau monde Izanami, l'ajoute à la config et le persiste.
     *
     * @throws IllegalStateException si un monde de ce nom existe déjà
     */
    public World create(String name, long seed) {
        if (Bukkit.getWorld(name) != null || this.worlds.containsKey(key(name))) {
            throw new IllegalStateException("Un monde nommé '" + name + "' existe déjà.");
        }
        IzanamiWorldConfig config = new IzanamiWorldConfig(name, seed, true, null,
                CaveSettings.vanilla(), RiverSettings.disabled(), UndergroundRiverSettings.disabled(),
                CaveBiomeSettings.disabled(), SinkholeSettings.disabled(), GeodeSettings.disabled(),
                IzanamiWorldConfig.VANILLA_BASE_HEIGHT);
        this.worlds.put(key(name), config);
        saveToConfig();
        return createOrLoad(config);
    }

    /**
     * Crée ou charge le monde décrit par cette config. Le générateur est
     * enregistré auprès de Sukkit avant l'appel à createWorld, condition
     * nécessaire pour que le hook NMS soit utilisé.
     *
     * @throws IllegalArgumentException si la config biomes est invalide (fail-fast,
     *         AVANT toute création : aucun monde vanilla n'est créé par erreur)
     */
    public World createOrLoad(IzanamiWorldConfig config) {
        World existing = Bukkit.getWorld(config.getName());
        if (existing != null) {
            return existing;
        }

        ResolvedBiomeSettings resolvedBiomes = config.getBiomes() == null
                ? null
                : ResolvedBiomeSettings.resolve(config.getBiomes());
        dev.sukkit.izanami.gen.CaveZoneMap.validate(config.getCaveBiomes()); // fail-fast noms de biomes de cave

        NmsWorldGenRegistry.register(config.getName(), new IzanamiGenerator(config, resolvedBiomes, this));

        long start = System.nanoTime();
        WorldCreator creator = new WorldCreator(config.getName())
                .environment(World.Environment.NORMAL);
        if (config.getSeed() != 0L) {
            creator.seed(config.getSeed());
        }
        if (config.getBaseHeight() != IzanamiWorldConfig.VANILLA_BASE_HEIGHT) {
            // ChunkProviderGenerate lit baseSize/seaLevel depuis les generator
            // options JSON même en monde NORMAL. Mapping calé sur le vanilla :
            // 64 -> baseSize 8.5, seaLevel 63.
            double baseSize = config.getBaseHeight() / 8.0 + 0.5;
            int seaLevel = config.getBaseHeight() - 1;
            creator.generatorSettings("{\"baseSize\":" + baseSize + ",\"seaLevel\":" + seaLevel + "}");
        }
        World world = creator.createWorld();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        // lacs des biomes de cave : phase populate (voir CaveLakePopulator)
        dev.sukkit.izanami.api.IzanamiBiomeSource source = getBiomeSource(config.getName());
        if (source != null && source.getCave() instanceof dev.sukkit.izanami.gen.IzanamiCaveBiomeMap) {
            dev.sukkit.izanami.gen.CaveZoneMap zones =
                    ((dev.sukkit.izanami.gen.IzanamiCaveBiomeMap) source.getCave()).getZones();
            if (zones != null) {
                world.getPopulators().add(new dev.sukkit.izanami.gen.CaveLakePopulator(world.getSeed(), zones));
            }
        }

        // écoulement de l'eau souterraine générée (après les lacs, pour les réveiller aussi)
        if (config.getUndergroundRivers().isEnabled() || config.getRivers().isEnabled()
                || config.getCaveBiomes().isEnabled()) {
            world.getPopulators().add(new dev.sukkit.izanami.gen.WaterSettlePopulator(config.getBaseHeight()));
        }

        String generatorName = ((org.bukkit.craftbukkit.v1_8_R3.CraftWorld) world).getHandle()
                .chunkProviderServer.chunkProvider.getClass().getSimpleName();
        this.plugin.getLogger().info("Monde '" + config.getName() + "' pret en " + elapsedMs
                + " ms (seed " + world.getSeed() + ", generateur " + generatorName + ")");
        if (resolvedBiomes != null) {
            // noms NMS : les biomes custom n'existent pas dans l'enum Bukkit
            net.minecraft.server.v1_8_R3.WorldServer handle =
                    ((org.bukkit.craftbukkit.v1_8_R3.CraftWorld) world).getHandle();
            this.plugin.getLogger().info("Biomes '" + config.getName() + "' : (0,0)="
                    + nmsBiomeName(handle, 0, 0)
                    + " (800,800)=" + nmsBiomeName(handle, 800, 800)
                    + " (-800,800)=" + nmsBiomeName(handle, -800, 800)
                    + " (800,-800)=" + nmsBiomeName(handle, 800, -800)
                    + " (-800,-800)=" + nmsBiomeName(handle, -800, -800));
            verifyExclusionRing(handle, config.getName(), resolvedBiomes);
        }
        if (config.getCaves().getStyle() == CaveSettings.Style.MODERN || config.getRivers().isEnabled()
                || config.getUndergroundRivers().isEnabled() || config.getCaveBiomes().isEnabled()
                || config.getSinkholes().isEnabled()) {
            scanTerrainFeatures(config, world);
        }
        return world;
    }

    /** Appelé par IzanamiGenerator à l'init du monde (main thread). */
    public void registerBiomeSource(String worldName, dev.sukkit.izanami.api.IzanamiBiomeSource source) {
        this.biomeSources.put(key(worldName), source);
    }

    /**
     * Source de biomes (surface 2D + cave 3D) d'un monde Izanami, ou null si
     * le monde n'est pas géré / utilise les biomes vanilla.
     */
    public dev.sukkit.izanami.api.IzanamiBiomeSource getBiomeSource(String worldName) {
        return this.biomeSources.get(key(worldName));
    }

    public IzanamiWorldConfig getWorldConfig(String name) {
        return this.worlds.get(key(name));
    }

    public Collection<IzanamiWorldConfig> getWorlds() {
        return Collections.unmodifiableCollection(this.worlds.values());
    }

    private void saveToConfig() {
        ConfigurationSection root = this.plugin.getConfig().createSection("worlds");
        for (IzanamiWorldConfig config : this.worlds.values()) {
            config.toSection(root.createSection(config.getName()));
        }
        this.plugin.saveConfig();
    }

    /**
     * Vérifie (une fois, à la création) que le biome central n'apparaît nulle
     * part dans l'anneau [radius, exclusion-radius]. Scan déterministe pas de
     * 8 blocs — coût unique négligeable, garantie forte.
     */
    private void verifyExclusionRing(net.minecraft.server.v1_8_R3.WorldServer handle,
                                     String worldName, ResolvedBiomeSettings resolved) {
        if (resolved.getCenterBiome() == null || resolved.getExclusionRadius() <= resolved.getCenterRadius()) {
            return;
        }
        net.minecraft.server.v1_8_R3.WorldChunkManager manager = handle.getWorldChunkManager();
        if (!(manager instanceof dev.sukkit.izanami.gen.IzanamiWorldChunkManager)) {
            return;
        }
        dev.sukkit.izanami.gen.IzanamiWorldChunkManager izanami =
                (dev.sukkit.izanami.gen.IzanamiWorldChunkManager) manager;
        long coreMax = Math.max(0, resolved.getCenterRadius() - resolved.getCenterEdgeNoise());
        long blobMax = resolved.getCenterRadius() + resolved.getCenterEdgeNoise();
        int outer = resolved.getExclusionRadius();
        int ringViolations = 0;
        int coreViolations = 0;
        for (int x = -outer; x <= outer; x += 8) {
            for (int z = -outer; z <= outer; z += 8) {
                long distSq = (long) x * x + (long) z * z;
                boolean isCenter = izanami.biomeAt(x, z) == resolved.getCenterBiome();
                if (distSq <= coreMax * coreMax && !isCenter) {
                    coreViolations++; // le cœur doit être 100% biome central
                } else if (distSq > blobMax * blobMax && distSq <= (long) outer * outer && isCenter) {
                    ringViolations++; // l'anneau ne doit jamais contenir le biome central
                }
            }
        }
        if (ringViolations == 0 && coreViolations == 0) {
            this.plugin.getLogger().info("Zone centrale '" + worldName + "' : OK (coeur plein <= "
                    + coreMax + ", anneau vide " + blobMax + ".." + outer + ")");
        } else {
            this.plugin.getLogger().severe("Zone centrale '" + worldName + "' : "
                    + coreViolations + " violation(s) coeur, " + ringViolations + " violation(s) anneau !");
        }
    }

    /**
     * Scan de contrôle de la zone de spawn (informatif) : densité d'air
     * souterrain, colonnes d'eau, features des biomes de cave.
     */
    private void scanTerrainFeatures(IzanamiWorldConfig config, World world) {
        net.minecraft.server.v1_8_R3.WorldServer handle =
                ((org.bukkit.craftbukkit.v1_8_R3.CraftWorld) world).getHandle();
        int caveTop = config.getBaseHeight() - 16;
        int waterY = config.getBaseHeight() - 2;
        int air = 0;
        int total = 0;
        int waterColumns = 0;
        int columns = 0;
        long surfaceSum = 0;
        for (int x = -80; x <= 80; x += 4) {
            for (int z = -80; z <= 80; z += 4) {
                columns++;
                surfaceSum += world.getHighestBlockYAt(x, z);
                for (int y = 12; y <= caveTop; y += 2) {
                    total++;
                    if (handle.getType(new net.minecraft.server.v1_8_R3.BlockPosition(x, y, z))
                            .getBlock() == net.minecraft.server.v1_8_R3.Blocks.AIR) {
                        air++;
                    }
                }
                if (handle.getType(new net.minecraft.server.v1_8_R3.BlockPosition(x, waterY, z))
                        .getBlock().getMaterial().isLiquid()) {
                    waterColumns++;
                }
            }
        }
        String message = String.format(
                "Scan terrain '%s' (spawn +/-80) : surface moyenne y%.0f, cavernes %.1f%% d'air (y12-%d), eau y%d sur %d/%d colonnes",
                config.getName(), (double) surfaceSum / columns, air * 100.0 / total, caveTop, waterY, waterColumns, columns);

        if (config.getUndergroundRivers().isEnabled()) {
            int ugWater = config.getUndergroundRivers().resolveWaterLevel(config.getBaseHeight());
            int gap = config.getUndergroundRivers().getAirGap();
            int navigable = 0;
            int falls = 0;
            for (int x = -80; x <= 80; x += 2) {
                for (int z = -80; z <= 80; z += 2) {
                    boolean waterAt = handle.getType(new net.minecraft.server.v1_8_R3.BlockPosition(x, ugWater, z))
                            .getBlock().getMaterial().isLiquid();
                    if (!waterAt) {
                        continue;
                    }
                    if (handle.getType(new net.minecraft.server.v1_8_R3.BlockPosition(x, ugWater + 2, z))
                            .getBlock() == net.minecraft.server.v1_8_R3.Blocks.AIR) {
                        navigable++;
                    } else if (handle.getType(new net.minecraft.server.v1_8_R3.BlockPosition(x, ugWater + gap + 2, z))
                            .getBlock().getMaterial().isLiquid()) {
                        falls++;
                    }
                }
            }
            message += String.format(", riviere souterraine y%d : %d colonnes navigables, %d colonnes de chute",
                    ugWater, navigable, falls);
        }
        if (config.getCaveBiomes().isEnabled()) {
            // peau des biomes de cave : blocs mushroom (slots custom) posés en profondeur
            int skinned = 0;
            for (int x = -80; x <= 80; x += 4) {
                for (int z = -80; z <= 80; z += 4) {
                    for (int y = 10; y <= config.getBaseHeight() - 16; y += 2) {
                        int id = net.minecraft.server.v1_8_R3.Block.getId(
                                handle.getType(new net.minecraft.server.v1_8_R3.BlockPosition(x, y, z)).getBlock());
                        if (id == 99 || id == 100 || id == 38) {
                            skinned++;
                        }
                    }
                }
            }
            message += ", peau biome cave : " + skinned + " blocs custom";
        }
        if (config.getSinkholes().isEnabled()) {
            int sunken = 0;
            for (int x = -80; x <= 80; x += 4) {
                for (int z = -80; z <= 80; z += 4) {
                    if (world.getHighestBlockYAt(x, z) < config.getBaseHeight() - 20) {
                        sunken++;
                    }
                }
            }
            message += ", gouffres : " + sunken + " colonnes effondrees";
        }
        this.plugin.getLogger().info(message);
    }

    private static String nmsBiomeName(net.minecraft.server.v1_8_R3.WorldServer handle, int x, int z) {
        return handle.getWorldChunkManager()
                .getBiome(new net.minecraft.server.v1_8_R3.BlockPosition(x, 0, z), null).ah;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
