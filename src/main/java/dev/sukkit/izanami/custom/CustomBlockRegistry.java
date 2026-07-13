package dev.sukkit.izanami.custom;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Registre des blocs custom : assignation stable nom → slot, persistée dans
 * plugins/Izanami/custom-blocks.yml.
 * <p>
 * Au premier lancement, un mapping par défaut est proposé pour les textures
 * connues du dossier textures/ ; les textures inconnues sont listées dans les
 * logs et se mappent à la main dans le YAML. Les slots déjà assignés ne sont
 * jamais réattribués (les blocs posés dans les mondes en dépendent).
 */
public final class CustomBlockRegistry {

    /** Slots cubes : mushroom blocks, 13 états valides chacun. */
    private static final int[] MUSHROOM_METAS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 0, 14, 15};
    /** Slots croix : red_flower metas 1-8 (0 = coquelicot vanilla, réservé). */
    private static final int[] FLOWER_METAS = {1, 2, 3, 4, 5, 6, 7, 8};
    /** Slots croix supplémentaires : champignons, buisson mort, dead shrub (anti-pop Sukkit). */
    private static final int[][] EXTRA_CROSS_SLOTS = {{39, 0}, {40, 0}, {32, 0}, {31, 0}};
    /** Slots couches fines : snow_layer metas 1-7 (la neige naturelle ne fait que meta 0). */
    private static final int[] LAYER_METAS = {1, 2, 3, 4, 5, 6, 7};
    /** Slots piliers : murets cobblestone (0) / mossy (1) — silhouette + collision. */
    private static final int[][] WALL_SLOTS = {{139, 0}, {139, 1}};

    private final File file;
    private final File texturesFolder;
    private final Logger logger;
    private final Map<String, CustomBlock> blocks = new LinkedHashMap<>();
    private String placeholderDrop = "BARRIER"; // Material Bukkit pour drops: placeholder

    public CustomBlockRegistry(File file, File texturesFolder, Logger logger) {
        this.file = file;
        this.texturesFolder = texturesFolder;
        this.logger = logger;
        load();
    }

    private void load() {
        this.blocks.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
        this.placeholderDrop = yaml.getString("placeholder-drop", "BARRIER");
        ConfigurationSection root = yaml.getConfigurationSection("blocks");
        if (root != null) {
            for (String name : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(name);
                if (s == null) {
                    continue;
                }
                try {
                    this.blocks.put(name, fromSection(name, s));
                } catch (IllegalArgumentException e) {
                    this.logger.severe("custom-blocks.yml, bloc '" + name + "' invalide : " + e.getMessage());
                }
            }
        }

        boolean changed = assignDefaults();
        if (changed) {
            save();
        }
        reportUnmappedTextures();
    }

    private CustomBlock fromSection(String name, ConfigurationSection s) {
        String slot = s.getString("slot", "");
        String[] parts = slot.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("slot attendu sous la forme 'bloc:meta', recu '" + slot + "'");
        }
        int blockId = blockIdForName(parts[0]);
        int meta = Integer.parseInt(parts[1]);
        CustomBlock.Shape shape = CustomBlock.Shape.valueOf(
                s.getString("shape", "cube").toUpperCase(Locale.ROOT));
        CustomBlock block = new CustomBlock(name, shape, blockId, parts[0], meta,
                s.getString("texture", name + ".png"), s.getString("texture-top", null),
                CustomBlock.Drops.valueOf(s.getString("drops", "none").toUpperCase(Locale.ROOT)),
                s.getString("effects-as", null));
        List<String> column = s.getStringList("column-textures");
        if (!column.isEmpty()) {
            block.columnTextures(column);
        }
        block.model(s.getString("model", null));
        block.attach(CustomBlock.Attach.valueOf(s.getString("attach", "any").toUpperCase(Locale.ROOT)));
        return block;
    }

    /** Mapping par défaut pour les textures connues non encore assignées. */
    private boolean assignDefaults() {
        // nom -> {texture, texture-top nullable, shape}
        Map<String, String[]> known = new LinkedHashMap<>();
        for (String cube : new String[]{"amethyst_block", "deepslate", "deepslate_coal_ore",
                "deepslate_copper_ore", "deepslate_diamond_ore", "deepslate_emerald_ore",
                "deepslate_gold_ore", "deepslate_iron_ore", "deepslate_lapis_ore",
                "deepslate_redstone_ore", "moss_block", "mud", "sculk", "calcite", "azalea_leaves"}) {
            known.put(cube, new String[]{cube + ".png", null, "cube"});
        }
        known.put("pale_oak_log", new String[]{"pale_oak_log.png", "pale_oak_log_top.png", "cube"});
        known.put("azalea", new String[]{"azalea_side.png", "azalea_top.png", "cube"});
        for (String cross : new String[]{"amethyst_cluster", "bamboo_stalk", "bamboo_stage0",
                "glow_berry_vine", "small_amethyst_bud", "medium_amethyst_bud",
                "large_amethyst_bud", "spore_blossom"}) {
            known.put(cross, new String[]{cross + ".png", null, "cross"});
        }
        known.get("glow_berry_vine")[0] = "cave_vines_lit.png"; // pas de png "glow_berry_vine"
        known.put("moss_carpet", new String[]{"moss_block.png", null, "layer"});
        // nénuphar réutilisé : pad plat sur l'eau (dripleaf des mares Lush)
        known.put("dripleaf_pad", new String[]{"big_dripleaf_top.png", null, "pad"});
        // dripstone sur hôtes champignons : croix = cutout natif, non teinté,
        // anti-pop/attache déjà gérés (BlockPlant), verticalité via CTM
        known.put("pointed_dripstone_down", new String[]{"pointed_dripstone_down_tip.png", null, "cross"});
        known.put("pointed_dripstone_up", new String[]{"pointed_dripstone_up_tip.png", null, "cross"});
        // végétation Deep Dark : nappes fines de sculk au sol
        known.put("sculk_vein", new String[]{"sculk.png", null, "layer"});

        // rendu CTM vertical — ordre RÉEL décodé du bytecode OptiFine 1.8
        // (getConnectedTextureVertical) : [pied (connecté dessus), milieu,
        // sommet (connecté dessous), isolé]
        Map<String, String[]> columns = new LinkedHashMap<>();
        columns.put("pointed_dripstone_up", new String[]{
                "pointed_dripstone_up_base.png", "pointed_dripstone_up_middle.png",
                "pointed_dripstone_up_tip.png", "pointed_dripstone_up_tip.png"});
        columns.put("pointed_dripstone_down", new String[]{
                "pointed_dripstone_down_tip.png", "pointed_dripstone_down_middle.png",
                "pointed_dripstone_down_base.png", "pointed_dripstone_down_tip.png"});
        columns.put("glow_berry_vine", new String[]{
                "cave_vines_lit.png", "cave_vines_plant_lit.png",
                "cave_vines_plant_lit.png", "cave_vines_lit.png"});
        columns.put("bamboo_stalk", new String[]{
                "bamboo_stalk.png", "bamboo_stalk.png",
                "bamboo_small_leaves.png", "bamboo_stage0.png"});

        boolean changed = false;
        for (Map.Entry<String, String[]> entry : known.entrySet()) {
            String name = entry.getKey();
            if (this.blocks.containsKey(name)) {
                continue;
            }
            String[] def = entry.getValue();
            if (!new File(this.texturesFolder, def[0]).exists()) {
                continue;
            }
            CustomBlock.Shape shape = CustomBlock.Shape.valueOf(def[2].toUpperCase(Locale.ROOT));
            int[] slot = nextFreeSlot(shape);
            if (slot == null) {
                this.logger.warning("Plus de slot libre (" + shape + ") pour '" + name + "'");
                continue;
            }
            CustomBlock block = new CustomBlock(name, shape, slot[0], blockNameForId(slot[0]),
                    slot[1], def[0], def[1], CustomBlock.Drops.NONE, "stone");
            if (columns.containsKey(name)) {
                block.columnTextures(java.util.Arrays.asList(columns.get(name)));
            }
            if ("spore_blossom".equals(name)) {
                block.model("spore_blossom.json"); // géométrie 3D via blockstates
                block.attach(CustomBlock.Attach.CEILING); // pop si pas de solide au-dessus
            }
            // intégrité de colonne : casser un maillon fait tomber le reste
            if ("glow_berry_vine".equals(name) || "pointed_dripstone_down".equals(name)) {
                block.attach(CustomBlock.Attach.CEILING);
            }
            if ("pointed_dripstone_up".equals(name)) {
                block.attach(CustomBlock.Attach.FLOOR);
            }
            this.blocks.put(name, block);
            changed = true;
        }
        return changed;
    }

    /** @return {blockId, meta} libre pour cette forme, ou null si épuisé */
    private int[] nextFreeSlot(CustomBlock.Shape shape) {
        Set<String> used = new LinkedHashSet<>();
        for (CustomBlock block : this.blocks.values()) {
            used.add(block.getBlockId() + ":" + block.getMeta());
        }
        if (shape == CustomBlock.Shape.CUBE) {
            for (int blockId : new int[]{99, 100}) {
                for (int meta : MUSHROOM_METAS) {
                    if (!used.contains(blockId + ":" + meta)) {
                        return new int[]{blockId, meta};
                    }
                }
            }
        } else if (shape == CustomBlock.Shape.CROSS) {
            for (int meta : FLOWER_METAS) {
                if (!used.contains(38 + ":" + meta)) {
                    return new int[]{38, meta};
                }
            }
            for (int[] slot : EXTRA_CROSS_SLOTS) {
                if (!used.contains(slot[0] + ":" + slot[1])) {
                    return slot.clone();
                }
            }
        } else if (shape == CustomBlock.Shape.LAYER) {
            for (int meta : LAYER_METAS) {
                if (!used.contains(78 + ":" + meta)) {
                    return new int[]{78, meta};
                }
            }
        } else if (shape == CustomBlock.Shape.PAD) {
            if (!used.contains("111:0")) {
                return new int[]{111, 0}; // waterlily : un seul état
            }
        } else if (shape == CustomBlock.Shape.WALL) {
            for (int[] slot : WALL_SLOTS) {
                if (!used.contains(slot[0] + ":" + slot[1])) {
                    return slot.clone();
                }
            }
        }
        return null;
    }

    private void reportUnmappedTextures() {
        File[] files = this.texturesFolder.listFiles(
                (dir, n) -> n.toLowerCase(Locale.ROOT).endsWith(".png"));
        if (files == null) {
            return;
        }
        Set<String> mapped = new LinkedHashSet<>();
        for (CustomBlock block : this.blocks.values()) {
            mapped.add(block.getTexture());
            if (block.getTextureTop() != null) {
                mapped.add(block.getTextureTop());
            }
            if (block.getColumnTextures() != null) {
                mapped.addAll(block.getColumnTextures());
            }
        }
        List<String> unmapped = new ArrayList<>();
        for (File f : files) {
            if (!mapped.contains(f.getName())) {
                unmapped.add(f.getName());
            }
        }
        if (!unmapped.isEmpty()) {
            this.logger.info("Textures non assignees (a mapper dans custom-blocks.yml si besoin) : "
                    + String.join(", ", unmapped));
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("Blocs custom Izanami - assignation STABLE nom -> slot (bloc:meta).\n"
                + "Ne JAMAIS changer un slot une fois des blocs poses dans un monde.\n"
                + "shape: cube (mushroom blocks) | cross (red_flower, sans collision, anti-pop Sukkit)\n"
                + "texture-top: optionnel, texture distincte pour les faces haut/bas (cubes)\n"
                + "drops: none | placeholder (item placeholder-drop) | default (drop vanilla de l'hote)\n"
                + "effects-as: bloc dont on emprunte particules/son de casse (ex. stone) - le son de\n"
                + "pas reste celui de l'hote (client-side, non remappable sans mod)");
        yaml.set("placeholder-drop", this.placeholderDrop);
        ConfigurationSection root = yaml.createSection("blocks");
        for (CustomBlock block : this.blocks.values()) {
            ConfigurationSection s = root.createSection(block.getName());
            s.set("slot", block.slotString());
            s.set("shape", block.getShape().name().toLowerCase(Locale.ROOT));
            s.set("texture", block.getTexture());
            if (block.getTextureTop() != null) {
                s.set("texture-top", block.getTextureTop());
            }
            s.set("drops", block.getDrops().name().toLowerCase(Locale.ROOT));
            if (block.getEffectsAs() != null) {
                s.set("effects-as", block.getEffectsAs());
            }
            if (block.getColumnTextures() != null) {
                s.set("column-textures", block.getColumnTextures());
            }
            if (block.getModel() != null) {
                s.set("model", block.getModel());
            }
            if (block.getAttach() != CustomBlock.Attach.ANY) {
                s.set("attach", block.getAttach().name().toLowerCase(Locale.ROOT));
            }
        }
        try {
            yaml.save(this.file);
        } catch (IOException e) {
            this.logger.severe("Sauvegarde custom-blocks.yml impossible : " + e.getMessage());
        }
    }

    private static int blockIdForName(String name) {
        switch (name) {
            case "brown_mushroom_block": return 99;
            case "red_mushroom_block": return 100;
            case "red_flower": return 38;
            case "brown_mushroom": return 39;
            case "red_mushroom": return 40;
            case "deadbush": return 32;
            case "tallgrass": return 31;
            case "snow_layer": return 78;
            case "waterlily": return 111;
            case "cobblestone_wall": return 139;
            default:
                throw new IllegalArgumentException("Bloc de slot non supporte : " + name);
        }
    }

    private static String blockNameForId(int id) {
        switch (id) {
            case 99: return "brown_mushroom_block";
            case 100: return "red_mushroom_block";
            case 38: return "red_flower";
            case 39: return "brown_mushroom";
            case 40: return "red_mushroom";
            case 32: return "deadbush";
            case 31: return "tallgrass";
            case 78: return "snow_layer";
            case 111: return "waterlily";
            case 139: return "cobblestone_wall";
            default:
                throw new IllegalArgumentException("id " + id);
        }
    }

    public String getPlaceholderDrop() {
        return this.placeholderDrop;
    }

    public CustomBlock get(String name) {
        return name == null ? null : this.blocks.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<CustomBlock> getAll() {
        return Collections.unmodifiableCollection(this.blocks.values());
    }
}
