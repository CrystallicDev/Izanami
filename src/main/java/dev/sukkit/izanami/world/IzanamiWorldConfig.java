package dev.sukkit.izanami.world;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Configuration d'un monde géré par Izanami, telle que persistée dans config.yml.
 * Les paramètres de génération (biomes, etc.) s'ajouteront ici au fil des jalons.
 */
public final class IzanamiWorldConfig {

    /** Hauteur moyenne du sol vanilla (référence des plages de cavernes). */
    public static final int VANILLA_BASE_HEIGHT = 64;

    private final String name;
    private final long seed;
    private final boolean autoload;
    private final BiomeSettings biomes; // null = biomes vanilla
    private final CaveSettings caves;
    private final RiverSettings rivers;
    private final UndergroundRiverSettings undergroundRivers;
    private final CaveBiomeSettings caveBiomes;
    private final SinkholeSettings sinkholes;
    private final GeodeSettings geodes;
    private final int baseHeight;

    public IzanamiWorldConfig(String name, long seed, boolean autoload, BiomeSettings biomes,
                              CaveSettings caves, RiverSettings rivers,
                              UndergroundRiverSettings undergroundRivers,
                              CaveBiomeSettings caveBiomes, SinkholeSettings sinkholes,
                              GeodeSettings geodes, int baseHeight) {
        this.name = name;
        this.seed = seed;
        this.autoload = autoload;
        this.biomes = biomes;
        this.caves = caves;
        this.rivers = rivers;
        this.undergroundRivers = undergroundRivers;
        this.caveBiomes = caveBiomes;
        this.sinkholes = sinkholes;
        this.geodes = geodes;
        this.baseHeight = Math.max(48, Math.min(160, baseHeight));
    }

    public static IzanamiWorldConfig fromSection(String name, ConfigurationSection section) {
        ConfigurationSection terrain = section.getConfigurationSection("terrain");
        return new IzanamiWorldConfig(
                name,
                section.getLong("seed", 0L),
                section.getBoolean("autoload", true),
                BiomeSettings.fromSection(section.getConfigurationSection("biomes")),
                CaveSettings.fromSection(section.getConfigurationSection("caves")),
                RiverSettings.fromSection(section.getConfigurationSection("rivers")),
                UndergroundRiverSettings.fromSection(section.getConfigurationSection("underground-rivers")),
                CaveBiomeSettings.fromSection(section.getConfigurationSection("cave-biomes")),
                SinkholeSettings.fromSection(section.getConfigurationSection("sinkholes")),
                GeodeSettings.fromSection(section.getConfigurationSection("geodes")),
                terrain == null ? VANILLA_BASE_HEIGHT : terrain.getInt("base-height", VANILLA_BASE_HEIGHT)
        );
    }

    public void toSection(ConfigurationSection section) {
        section.set("seed", this.seed);
        section.set("autoload", this.autoload);
        if (this.baseHeight != VANILLA_BASE_HEIGHT) {
            section.createSection("terrain").set("base-height", this.baseHeight);
        }
        if (this.biomes != null) {
            this.biomes.toSection(section.createSection("biomes"));
        }
        if (this.caves.getStyle() != CaveSettings.Style.VANILLA) {
            this.caves.toSection(section.createSection("caves"));
        }
        if (this.rivers.isEnabled()) {
            this.rivers.toSection(section.createSection("rivers"));
        }
        if (this.undergroundRivers.isEnabled()) {
            this.undergroundRivers.toSection(section.createSection("underground-rivers"));
        }
        if (this.caveBiomes.isEnabled()) {
            this.caveBiomes.toSection(section.createSection("cave-biomes"));
        }
        if (this.sinkholes.isEnabled()) {
            this.sinkholes.toSection(section.createSection("sinkholes"));
        }
        if (this.geodes.isEnabled()) {
            this.geodes.toSection(section.createSection("geodes"));
        }
    }

    public String getName() {
        return this.name;
    }

    /** 0 = aléatoire (la seed réelle est figée par le serveur à la création). */
    public long getSeed() {
        return this.seed;
    }

    public boolean isAutoload() {
        return this.autoload;
    }

    /** @return les paramètres de biomes, ou null pour les biomes vanilla */
    public BiomeSettings getBiomes() {
        return this.biomes;
    }

    public CaveSettings getCaves() {
        return this.caves;
    }

    public RiverSettings getRivers() {
        return this.rivers;
    }

    public UndergroundRiverSettings getUndergroundRivers() {
        return this.undergroundRivers;
    }

    public CaveBiomeSettings getCaveBiomes() {
        return this.caveBiomes;
    }

    public SinkholeSettings getSinkholes() {
        return this.sinkholes;
    }

    public GeodeSettings getGeodes() {
        return this.geodes;
    }

    /**
     * Hauteur moyenne du sol (vanilla : 64). Une valeur haute (ex. 100) donne
     * plus de volume souterrain pour les cavernes, comme les mondes 1.18+.
     * Le niveau de la mer suit (baseHeight - 1).
     */
    public int getBaseHeight() {
        return this.baseHeight;
    }
}
