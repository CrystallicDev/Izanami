package dev.sukkit.izanami.world;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * Paramètres de cavernes d'un monde Izanami (section "caves").
 *
 * <pre>
 * caves:
 *   style: modern     # modern = cavernes a bruit type 1.18+ ; vanilla ; none
 *   cheese: true      # grandes salles ouvertes
 *   spaghetti: true   # longs tunnels sinueux
 *   noodle: true      # boyaux etroits
 * </pre>
 *
 * Section absente = style vanilla (aucun changement).
 */
public final class CaveSettings {

    public enum Style { MODERN, VANILLA, NONE }

    private final Style style;
    private final boolean cheese;
    private final boolean spaghetti;
    private final boolean noodle;
    private final int maxY; // 0 = auto (base-height + 24)
    private final double openness; // 1.0 = normal ; >1 = cavernes plus larges/ouvertes

    public CaveSettings(Style style, boolean cheese, boolean spaghetti, boolean noodle,
                        int maxY, double openness) {
        this.style = style;
        this.cheese = cheese;
        this.spaghetti = spaghetti;
        this.noodle = noodle;
        this.maxY = maxY <= 0 ? 0 : Math.max(32, Math.min(124, maxY));
        this.openness = Math.max(0.5, Math.min(2.0, openness));
    }

    public static CaveSettings vanilla() {
        return new CaveSettings(Style.VANILLA, false, false, false, 0, 1.0);
    }

    public static CaveSettings fromSection(ConfigurationSection section) {
        if (section == null) {
            return vanilla();
        }
        Style style;
        String raw = section.getString("style", "modern").toLowerCase(Locale.ROOT);
        switch (raw) {
            case "modern": style = Style.MODERN; break;
            case "none": style = Style.NONE; break;
            case "vanilla": style = Style.VANILLA; break;
            default:
                throw new IllegalArgumentException("caves.style inconnu : '" + raw + "' (modern|vanilla|none)");
        }
        return new CaveSettings(style,
                section.getBoolean("cheese", true),
                section.getBoolean("spaghetti", true),
                section.getBoolean("noodle", true),
                section.getInt("max-y", 0),
                section.getDouble("openness", 1.0));
    }

    public void toSection(ConfigurationSection section) {
        section.set("style", this.style.name().toLowerCase(Locale.ROOT));
        section.set("cheese", this.cheese);
        section.set("spaghetti", this.spaghetti);
        section.set("noodle", this.noodle);
        section.set("max-y", this.maxY);
        section.set("openness", this.openness);
    }

    /** Facteur d'ouverture des cavernes (1.0 normal, 1.5 très ouvert). */
    public double getOpenness() {
        return this.openness;
    }

    /** Plafond des cavernes (tunnels spaghetti). Auto : base-height + 24. */
    public int resolveMaxY(int baseHeight) {
        return this.maxY > 0 ? this.maxY : Math.min(124, baseHeight + 24);
    }

    public Style getStyle() {
        return this.style;
    }

    public boolean hasCheese() {
        return this.cheese;
    }

    public boolean hasSpaghetti() {
        return this.spaghetti;
    }

    public boolean hasNoodle() {
        return this.noodle;
    }
}
