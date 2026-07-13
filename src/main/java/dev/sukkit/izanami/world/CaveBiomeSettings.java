package dev.sukkit.izanami.world;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Zones de biomes de cave d'un monde (section "cave-biomes") : Voronoï 2D en
 * colonnes sur la bande souterraine [8, base-height - 16].
 *
 * <pre>
 * cave-biomes:
 *   cell-size: 200
 *   allowed:               # nom -> poids ; "none" = pierre naturelle
 *     deepslate_caves: 1
 *     none: 2
 * </pre>
 */
public final class CaveBiomeSettings {

    private final int cellSize;
    private final Map<String, Integer> allowed;

    public CaveBiomeSettings(int cellSize, Map<String, Integer> allowed) {
        this.cellSize = Math.max(32, cellSize);
        this.allowed = allowed;
    }

    public static CaveBiomeSettings disabled() {
        return new CaveBiomeSettings(200, new LinkedHashMap<>());
    }

    public static CaveBiomeSettings fromSection(ConfigurationSection section) {
        if (section == null) {
            return disabled();
        }
        Map<String, Integer> allowed = new LinkedHashMap<>();
        ConfigurationSection allowedSection = section.getConfigurationSection("allowed");
        if (allowedSection != null) {
            for (String name : allowedSection.getKeys(false)) {
                allowed.put(name, Math.max(1, allowedSection.getInt(name, 1)));
            }
        } else {
            List<String> list = section.getStringList("allowed");
            for (String name : list) {
                allowed.put(name, 1);
            }
        }
        return new CaveBiomeSettings(section.getInt("cell-size", 200), allowed);
    }

    public void toSection(ConfigurationSection section) {
        section.set("cell-size", this.cellSize);
        ConfigurationSection allowedSection = section.createSection("allowed");
        for (Map.Entry<String, Integer> entry : this.allowed.entrySet()) {
            allowedSection.set(entry.getKey(), entry.getValue());
        }
    }

    public boolean isEnabled() {
        return !this.allowed.isEmpty();
    }

    public int getCellSize() {
        return this.cellSize;
    }

    public Map<String, Integer> getAllowed() {
        return this.allowed;
    }
}
