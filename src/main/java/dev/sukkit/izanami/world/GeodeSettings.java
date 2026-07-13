package dev.sukkit.izanami.world;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Géodes d'améthyste (section "geodes") : poches sphériques creuses en
 * profondeur — coquille calcite, couche améthyste, centre creux, clusters et
 * bourgeons posés au sol de la cavité (jamais orientés : uniquement à plat).
 *
 * <pre>
 * geodes:
 *   enabled: true
 *   spacing: 280     # distance approximative entre geodes
 *   radius: 5        # rayon moyen (3-8), bord bruité
 *   min-y: 12
 *   max-y: 40
 * </pre>
 */
public final class GeodeSettings {

    private final boolean enabled;
    private final int spacing;
    private final int radius;
    private final int minY;
    private final int maxY;

    public GeodeSettings(boolean enabled, int spacing, int radius, int minY, int maxY) {
        this.enabled = enabled;
        this.spacing = Math.max(64, Math.min(4000, spacing));
        this.radius = Math.max(3, Math.min(8, radius));
        this.minY = Math.max(8, minY);
        this.maxY = Math.max(this.minY + 2 * this.radius + 4, maxY);
    }

    public static GeodeSettings disabled() {
        return new GeodeSettings(false, 280, 5, 12, 40);
    }

    public static GeodeSettings fromSection(ConfigurationSection section) {
        if (section == null) {
            return disabled();
        }
        return new GeodeSettings(
                section.getBoolean("enabled", true),
                section.getInt("spacing", 280),
                section.getInt("radius", 5),
                section.getInt("min-y", 12),
                section.getInt("max-y", 40));
    }

    public void toSection(ConfigurationSection section) {
        section.set("enabled", this.enabled);
        section.set("spacing", this.spacing);
        section.set("radius", this.radius);
        section.set("min-y", this.minY);
        section.set("max-y", this.maxY);
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public int getSpacing() {
        return this.spacing;
    }

    public int getRadius() {
        return this.radius;
    }

    public int getMinY() {
        return this.minY;
    }

    public int getMaxY() {
        return this.maxY;
    }
}
