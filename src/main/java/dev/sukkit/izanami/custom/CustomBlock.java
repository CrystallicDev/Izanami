package dev.sukkit.izanami.custom;

/**
 * Bloc custom Izanami : un état valide-mais-inutilisé d'un bloc vanilla 1.8,
 * retexturé côté client par OptiFine (CTM par metadata).
 * <p>
 * Pourquoi pas des métadonnées "libres" (stone 7-15, etc.) : le client 1.8
 * résout chaque bloc via son registre d'états ; un état non enregistré retombe
 * sur de l'AIR (invisible, traversable). Seuls les états valides existent des
 * deux côtés. Slots utilisés :
 * <ul>
 * <li>cubes : brown/red_mushroom_block (13 états valides chacun, 26 slots)</li>
 * <li>croix : red_flower metas 1-8 (8 slots, sans collision)</li>
 * </ul>
 * L'assignation nom→slot est persistée dans custom-blocks.yml et ne doit JAMAIS
 * changer une fois des blocs posés dans un monde.
 */
public final class CustomBlock {

    public enum Shape { CUBE, CROSS, LAYER, PAD, WALL, FENCE }

    /** Politique de drop à la casse (patch Sukkit CustomBlockBehaviors). */
    public enum Drops { NONE, PLACEHOLDER, DEFAULT }

    /** Mode d'attache (pop de plante, patch Sukkit) : ANY = tient partout. */
    public enum Attach { ANY, CEILING, FLOOR }

    private final String name;
    private final Shape shape;
    private final int blockId;
    private final String blockName; // nom 1.8 pour matchBlocks OptiFine
    private final int meta;
    private final String texture;
    private final String textureTop; // nullable (cubes à face haut/bas distincte)
    private final Drops drops;
    private final String effectsAs;  // bloc dont on emprunte particules/son de casse, nullable
    /**
     * Rendu CTM vertical (colonnes empilées : dripstone, lianes) : 4 textures
     * dans l'ordre OptiFine 1.8 (décodé du bytecode) :
     * [pied (connecté dessus), milieu, sommet (connecté dessous), isolé].
     * null = rendu fixed classique.
     */
    private java.util.List<String> columnTextures;
    /**
     * Modèle JSON custom (géométrie 3D via blockstates vanilla, ex.
     * spore_blossom) : nom de fichier dans plugins/Izanami/models/.
     * Prioritaire sur le rendu CTM. Hôtes supportés : red_flower.
     */
    private String model;
    private Attach attach = Attach.ANY;
    /** Filtre de hauteur OptiFine "min-max" : hors bande, l'hôte redevient vanilla. */
    private String heights;
    /** Filtre de biomes OptiFine ("Jungle JungleHills") : hors biome, hôte vanilla. */
    private String biomes;

    public CustomBlock(String name, Shape shape, int blockId, String blockName, int meta,
                       String texture, String textureTop, Drops drops, String effectsAs) {
        this.name = name;
        this.shape = shape;
        this.blockId = blockId;
        this.blockName = blockName;
        this.meta = meta;
        this.texture = texture;
        this.textureTop = textureTop;
        this.drops = drops;
        this.effectsAs = effectsAs;
    }

    public String getName() {
        return this.name;
    }

    public Shape getShape() {
        return this.shape;
    }

    public int getBlockId() {
        return this.blockId;
    }

    public String getBlockName() {
        return this.blockName;
    }

    public int getMeta() {
        return this.meta;
    }

    public String getTexture() {
        return this.texture;
    }

    public String getTextureTop() {
        return this.textureTop;
    }

    public Drops getDrops() {
        return this.drops;
    }

    public String getEffectsAs() {
        return this.effectsAs;
    }

    public java.util.List<String> getColumnTextures() {
        return this.columnTextures;
    }

    public CustomBlock columnTextures(java.util.List<String> textures) {
        if (textures != null && textures.size() != 4) {
            throw new IllegalArgumentException("column-textures : 4 textures attendues [pied, milieu, sommet, isole]");
        }
        this.columnTextures = textures;
        return this;
    }

    public String getModel() {
        return this.model;
    }

    public CustomBlock model(String model) {
        this.model = model;
        return this;
    }

    public Attach getAttach() {
        return this.attach;
    }

    public CustomBlock attach(Attach attach) {
        this.attach = attach == null ? Attach.ANY : attach;
        return this;
    }

    public String getHeights() {
        return this.heights;
    }

    public CustomBlock heights(String heights) {
        this.heights = heights == null || heights.trim().isEmpty() ? null : heights.trim();
        return this;
    }

    public String getBiomes() {
        return this.biomes;
    }

    public CustomBlock biomes(String biomes) {
        this.biomes = biomes == null || biomes.trim().isEmpty() ? null : biomes.trim();
        return this;
    }

    /** Borne haute du filtre de hauteur, ou -1 si aucun filtre. */
    public int heightCap() {
        if (this.heights == null) {
            return -1;
        }
        int dash = this.heights.indexOf('-');
        try {
            return Integer.parseInt(dash < 0 ? this.heights : this.heights.substring(dash + 1).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public String slotString() {
        return this.blockName + ":" + this.meta;
    }
}
