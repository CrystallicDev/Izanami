package dev.sukkit.izanami.pregen;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Réglages globaux de la prégénération (section "pregen" de config.yml).
 */
public final class PregenSettings {

    private final int maxMillisPerTick;
    private final double targetTps;
    private final int maxLoadedChunks;
    private final int logIntervalSeconds;

    private PregenSettings(int maxMillisPerTick, double targetTps, int maxLoadedChunks, int logIntervalSeconds) {
        this.maxMillisPerTick = clamp(maxMillisPerTick, 1, 45);
        this.targetTps = Math.max(1.0, Math.min(20.0, targetTps));
        this.maxLoadedChunks = Math.max(200, maxLoadedChunks);
        this.logIntervalSeconds = Math.max(1, logIntervalSeconds);
    }

    public static PregenSettings fromConfig(ConfigurationSection root) {
        ConfigurationSection section = root == null ? null : root.getConfigurationSection("pregen");
        if (section == null) {
            return new PregenSettings(30, 18.0, 2500, 10);
        }
        return new PregenSettings(
                section.getInt("max-millis-per-tick", 30),
                section.getDouble("target-tps", 18.0),
                section.getInt("max-loaded-chunks", 2500),
                section.getInt("log-interval-seconds", 10)
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Budget max de génération par tick (ms). Le budget réel s'adapte au TPS. */
    public int getMaxMillisPerTick() {
        return this.maxMillisPerTick;
    }

    /** Si le TPS descend sous ce seuil, le budget par tick est réduit. */
    public double getTargetTps() {
        return this.targetTps;
    }

    /** Backpressure : au-delà de ce nombre de chunks chargés, on laisse la file d'unload se vider. */
    public int getMaxLoadedChunks() {
        return this.maxLoadedChunks;
    }

    public int getLogIntervalSeconds() {
        return this.logIntervalSeconds;
    }
}
