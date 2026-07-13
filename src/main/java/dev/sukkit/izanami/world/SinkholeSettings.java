package dev.sukkit.izanami.world;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Gouffres d'accès aux cavernes (section "sinkholes") : puits verticaux secs,
 * évasés en surface, débouchant dans la bande des cavernes.
 *
 * <pre>
 * sinkholes:
 *   enabled: true
 *   spacing: 300       # distance approximative entre gouffres
 *   radius: 6          # rayon moyen (le bord est bruité, évasé en haut)
 *   floor-y: 0         # fond du gouffre (0 = auto : base-height - 32)
 * </pre>
 */
public final class SinkholeSettings {

    private final boolean enabled;
    private final int spacing;
    private final int radius;
    private final int floorY; // 0 = auto

    public SinkholeSettings(boolean enabled, int spacing, int radius, int floorY) {
        this.enabled = enabled;
        this.spacing = Math.max(64, Math.min(4000, spacing));
        this.radius = Math.max(3, Math.min(12, radius));
        this.floorY = floorY <= 0 ? 0 : Math.max(10, Math.min(100, floorY));
    }

    public static SinkholeSettings disabled() {
        return new SinkholeSettings(false, 300, 6, 0);
    }

    public static SinkholeSettings fromSection(ConfigurationSection section) {
        if (section == null) {
            return disabled();
        }
        return new SinkholeSettings(
                section.getBoolean("enabled", true),
                section.getInt("spacing", 300),
                section.getInt("radius", 6),
                section.getInt("floor-y", 0));
    }

    public void toSection(ConfigurationSection section) {
        section.set("enabled", this.enabled);
        section.set("spacing", this.spacing);
        section.set("radius", this.radius);
        section.set("floor-y", this.floorY);
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

    public int resolveFloorY(int baseHeight) {
        return this.floorY > 0 ? this.floorY : Math.max(12, baseHeight - 32);
    }
}
