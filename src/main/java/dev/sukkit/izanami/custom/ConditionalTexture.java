package dev.sukkit.izanami.custom;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Règle de retexture conditionnelle OptiFine (CTM) : purement visuelle,
 * appliquée par le client à un bloc VANILLA selon la hauteur et/ou le biome —
 * sans consommer de slot d'état custom, sans rien changer côté serveur.
 * Ex. : stone rendue comme deepslate sous y16.
 * <p>
 * Notes :
 * <ul>
 * <li>"biomes" matche le biome vu par le CLIENT — pour un biome custom Izanami,
 *     c'est donc son clientBiome (remap Sukkit) : choisir des clientBiomes
 *     distincts fait office de canal de conditions par biome.</li>
 * <li>Les biomes de cave n'étant jamais envoyés au client, seules les
 *     conditions de hauteur peuvent les approximer.</li>
 * <li>method=random + weights permet une bande de transition progressive.</li>
 * </ul>
 */
public final class ConditionalTexture {

    private final String name;
    private final String matchBlock;
    private final String metadata;    // null = toutes ; liste possible ("1 2")
    private final String heights;     // "min-max", null = toutes hauteurs
    private final String faces;       // "sides", "top bottom"... null = toutes faces
    private final List<String> biomes; // vide = tous biomes
    private final String method;      // fixed | random
    private final List<String> tiles; // .png du dossier textures/ OU chemin vanilla (textures/blocks/...)
    private final List<Integer> weights; // vide = équiprobable (random)

    private ConditionalTexture(String name, String matchBlock, String metadata, String heights,
                               String faces, List<String> biomes, String method, List<String> tiles,
                               List<Integer> weights) {
        this.name = name;
        this.matchBlock = matchBlock;
        this.metadata = metadata;
        this.heights = heights;
        this.faces = faces;
        this.biomes = biomes;
        this.method = method;
        this.tiles = tiles;
        this.weights = weights;
    }

    /** Charge conditional-textures.yml (créé avec un exemple deepslate si absent). */
    public static List<ConditionalTexture> loadAll(File file, Logger logger) {
        if (!file.exists()) {
            writeDefault(file, logger);
        }
        List<ConditionalTexture> rules = new ArrayList<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("rules");
        if (root == null) {
            return rules;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null) {
                continue;
            }
            String match = s.getString("match");
            List<String> tiles = s.getStringList("tiles");
            if (match == null || match.isEmpty() || tiles.isEmpty()) {
                logger.severe("conditional-textures.yml : regle '" + name + "' sans 'match' ou 'tiles', ignoree");
                continue;
            }
            rules.add(new ConditionalTexture(
                    name,
                    match,
                    s.contains("metadata") ? String.valueOf(s.get("metadata")) : null,
                    s.getString("heights", null),
                    s.getString("faces", null),
                    s.getStringList("biomes"),
                    s.getString("method", "fixed"),
                    tiles,
                    s.getIntegerList("weights")));
        }
        return Collections.unmodifiableList(rules);
    }

    private static void writeDefault(File file, Logger logger) {
        String content = "# Retextures conditionnelles OptiFine (visuelles uniquement, aucun slot consomme).\n"
                + "#   match: bloc vanilla 1.8 ; metadata: optionnelle (ex. 0 = stone pure)\n"
                + "#   heights: \"min-max\" optionnel ; biomes: liste optionnelle (biomes vus par le CLIENT)\n"
                + "#   method: fixed | random ; tiles: png du dossier textures/ ou chemin vanilla\n"
                + "#   weights: poids pour method random\n"
                + "# Cavernes deepslate sous y50 : stone pure -> deepslate, granite -> tuff,\n"
                + "# diorite -> bloc d'amethyste, minerais -> variantes deepslate.\n"
                + "rules:\n"
                + "  deepslate_layer:\n"
                + "    match: stone\n"
                + "    metadata: 0\n"
                + "    heights: 0-50\n"
                + "    method: fixed\n"
                + "    tiles: [deepslate.png]\n"
                + "  deepslate_transition:\n"
                + "    match: stone\n"
                + "    metadata: 0\n"
                + "    heights: 51-58\n"
                + "    method: random\n"
                + "    tiles: [deepslate.png, textures/blocks/stone]\n"
                + "    weights: [1, 1]\n"
                + "  granite_tuff:\n"
                + "    match: stone\n"
                + "    metadata: 1 2\n"
                + "    heights: 0-50\n"
                + "    method: fixed\n"
                + "    tiles: [tuff.png]\n"
                + "  diorite_dripstone:\n"
                + "    match: stone\n"
                + "    metadata: 3 4\n"
                + "    heights: 0-50\n"
                + "    method: fixed\n"
                + "    tiles: [dripstone_block.png]\n"
                + "  deepslate_coal:\n"
                + "    match: coal_ore\n"
                + "    heights: 0-50\n"
                + "    method: fixed\n"
                + "    tiles: [deepslate_coal_ore.png]\n"
                + "  deepslate_iron:\n"
                + "    match: iron_ore\n"
                + "    heights: 0-50\n"
                + "    method: fixed\n"
                + "    tiles: [deepslate_iron_ore.png]\n"
                + "  deepslate_gold:\n"
                + "    match: gold_ore\n"
                + "    heights: 0-50\n"
                + "    method: fixed\n"
                + "    tiles: [deepslate_gold_ore.png]\n"
                + "  deepslate_diamond:\n"
                + "    match: diamond_ore\n"
                + "    heights: 0-50\n"
                + "    method: fixed\n"
                + "    tiles: [deepslate_diamond_ore.png]\n"
                + "  deepslate_redstone:\n"
                + "    match: redstone_ore\n"
                + "    heights: 0-50\n"
                + "    method: fixed\n"
                + "    tiles: [deepslate_redstone_ore.png]\n"
                + "  deepslate_lapis:\n"
                + "    match: lapis_ore\n"
                + "    heights: 0-50\n"
                + "    method: fixed\n"
                + "    tiles: [deepslate_lapis_ore.png]\n"
                + "  deepslate_emerald:\n"
                + "    match: emerald_ore\n"
                + "    heights: 0-50\n"
                + "    method: fixed\n"
                + "    tiles: [deepslate_emerald_ore.png]\n"
                + "  mycelium_sculk:          # sol du Deep Dark (pose du mycelium vanilla)\n"
                + "    match: mycelium\n"
                + "    method: fixed\n"
                + "    tiles: [sculk.png]\n"
                + "  andesite_blackstone:     # cavernes profondes\n"
                + "    match: stone\n"
                + "    metadata: 5 6\n"
                + "    heights: 0-30\n"
                + "    method: fixed\n"
                + "    faces: sides\n"
                + "    tiles: [blackstone.png]\n"
                + "  andesite_blackstone_top:\n"
                + "    match: stone\n"
                + "    metadata: 5 6\n"
                + "    heights: 0-30\n"
                + "    method: fixed\n"
                + "    faces: top bottom\n"
                + "    tiles: [blackstone_top.png]\n"
                + "# Debug : active cette regle, pose une colonne de 3 stalactites et note\n"
                + "# la couleur de chaque segment -> donne l'ordre reel des tiles vertical :\n"
                + "# [0=rouge, 1=jaune, 2=vert, 3=bleu]\n"
                + "#  debug_vertical_order:\n"
                + "#    match: brown_mushroom\n"
                + "#    method: vertical\n"
                + "#    tiles: [textures/blocks/wool_colored_red, textures/blocks/wool_colored_yellow,\n"
                + "#            textures/blocks/wool_colored_lime, textures/blocks/wool_colored_blue]\n"
                + "# Exemple a activer selon besoin :\n"
                + "#  moss_dirt:              # dirt des cavernes -> mousse/sculk\n"
                + "#    match: dirt\n"
                + "#    metadata: 0\n"
                + "#    heights: 0-40\n"
                + "#    method: random\n"
                + "#    tiles: [moss_block.png, sculk.png, textures/blocks/dirt]\n"
                + "#    weights: [2, 1, 1]\n";
        try {
            java.nio.file.Files.write(file.toPath(), content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            logger.severe("Ecriture de conditional-textures.yml impossible : " + e.getMessage());
        }
    }

    public String getName() {
        return this.name;
    }

    public String getMatchBlock() {
        return this.matchBlock;
    }

    public String getMetadata() {
        return this.metadata;
    }

    public String getHeights() {
        return this.heights;
    }

    public String getFaces() {
        return this.faces;
    }

    public List<String> getBiomes() {
        return this.biomes;
    }

    public String getMethod() {
        return this.method;
    }

    public List<String> getTiles() {
        return this.tiles;
    }

    public List<Integer> getWeights() {
        return this.weights;
    }
}
