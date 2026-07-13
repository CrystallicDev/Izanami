package dev.sukkit.izanami.world;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Paramètres de rivières d'un monde Izanami (section "rivers").
 * Rivières par bruit "ridged" 2D : larges, sinueuses, au niveau de la mer,
 * creusant des berges dans le relief — indépendantes des biomes (le
 * WorldChunkManager Izanami n'a pas de GenLayer rivière).
 *
 * <pre>
 * rivers:
 *   enabled: true
 *   width: 16        # largeur approximative (blocs)
 *   scale: 300       # longueur d'onde des meandres (blocs)
 *   depth: 5         # profondeur max au centre (blocs sous la surface de l'eau)
 * </pre>
 */
public final class RiverSettings {

    private final boolean enabled;
    private final int width;
    private final int scale;
    private final int depth;

    public RiverSettings(boolean enabled, int width, int scale, int depth) {
        this.enabled = enabled;
        this.width = Math.max(4, Math.min(48, width));
        this.scale = Math.max(80, Math.min(2000, scale));
        this.depth = Math.max(2, Math.min(12, depth));
    }

    public static RiverSettings disabled() {
        return new RiverSettings(false, 16, 300, 5);
    }

    public static RiverSettings fromSection(ConfigurationSection section) {
        if (section == null) {
            return disabled();
        }
        return new RiverSettings(
                section.getBoolean("enabled", true),
                section.getInt("width", 16),
                section.getInt("scale", 300),
                section.getInt("depth", 5));
    }

    public void toSection(ConfigurationSection section) {
        section.set("enabled", this.enabled);
        section.set("width", this.width);
        section.set("scale", this.scale);
        section.set("depth", this.depth);
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public int getWidth() {
        return this.width;
    }

    public int getScale() {
        return this.scale;
    }

    public int getDepth() {
        return this.depth;
    }
}
