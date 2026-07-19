package dev.sukkit.izanami.custom;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Génère le resource pack OptiFine des blocs custom : un dossier
 * plugins/Izanami/resourcepack/ + une archive IzanamiPack.zip prête à mettre
 * dans le dossier resourcepacks du client (ou à servir via resource-pack de
 * server.properties).
 * <p>
 * Technique : CTM MCPatcher/OptiFine "fixed" par metadata —
 * assets/minecraft/mcpatcher/ctm/izanami/&lt;nom&gt;/&lt;nom&gt;.properties.
 * Les .png.mcmeta d'animation (ex. amethyst_cluster) sont copiés s'ils existent.
 */
public final class ResourcePackGenerator {

    private ResourcePackGenerator() {}

    /** Variantes de blockstate de red_flower, indexées par metadata (1.8). */
    private static final String[] RED_FLOWER_TYPES = {"poppy", "blue_orchid", "allium", "houstonia",
            "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy"};

    /** @return le zip généré */
    public static File generate(File texturesFolder, File modelsFolder, File packFolder, File zipFile,
                                Collection<CustomBlock> blocks,
                                Collection<ConditionalTexture> rules) throws IOException {
        deleteRecursive(packFolder);
        File ctmRoot = new File(packFolder, "assets/minecraft/mcpatcher/ctm/izanami");
        if (!ctmRoot.mkdirs()) {
            throw new IOException("mkdirs " + ctmRoot);
        }

        write(new File(packFolder, "pack.mcmeta"),
                "{\"pack\":{\"pack_format\":1,\"description\":\"Izanami - blocs custom (OptiFine requis)\"}}");

        File rulesRoot = new File(packFolder, "assets/minecraft/mcpatcher/ctm/izanami_rules");
        for (ConditionalTexture rule : rules) {
            writeConditionalRule(texturesFolder, rulesRoot, rule);
        }

        writeCustomModels(texturesFolder, modelsFolder, packFolder, blocks);
        writeWallOverrides(texturesFolder, packFolder, blocks);

        for (CustomBlock block : blocks) {
            if (block.getModel() != null) {
                continue; // rendu par modèle blockstates, pas de CTM
            }
            File dir = new File(ctmRoot, block.getName());
            if (!dir.mkdirs()) {
                throw new IOException("mkdirs " + dir);
            }
            copyTexture(texturesFolder, block.getTexture(), dir, block.getName() + ".png");

            if (block.getColumnTextures() != null) {
                // rendu vertical : colonnes empilées base/milieu/pointe
                StringBuilder tiles = new StringBuilder();
                int index = 0;
                for (String texture : block.getColumnTextures()) {
                    String tileName = block.getName() + "_col" + index++;
                    copyTexture(texturesFolder, texture, dir, tileName + ".png");
                    if (tiles.length() > 0) {
                        tiles.append(' ');
                    }
                    tiles.append(tileName);
                }
                write(new File(dir, block.getName() + ".properties"),
                        "method=vertical\nmatchBlocks=" + block.getBlockName()
                                + "\nmetadata=" + block.getMeta() + "\ntiles=" + tiles + '\n'
                                + filters(block));
            } else if (block.getTextureTop() == null) {
                write(new File(dir, block.getName() + ".properties"),
                        ctmProperties(block, block.getName(), null));
            } else {
                copyTexture(texturesFolder, block.getTextureTop(), dir, block.getName() + "_top.png");
                write(new File(dir, block.getName() + "_side.properties"),
                        ctmProperties(block, block.getName(), "sides"));
                write(new File(dir, block.getName() + "_top.properties"),
                        ctmProperties(block, block.getName() + "_top", "top bottom"));
            }
        }

        zipFolder(packFolder, zipFile);
        return zipFile;
    }

    /**
     * Géométries custom via le système de modèles vanilla 1.8 : les blocs avec
     * "model:" obtiennent un vrai modèle 3D par variante de blockstate (pas
     * besoin d'OptiFine pour ça). Hôte supporté : red_flower — le fichier
     * blockstates/red_flower.json est réécrit en gardant les modèles vanilla
     * pour toutes les autres variantes.
     */
    private static void writeCustomModels(File texturesFolder, File modelsFolder, File packFolder,
                                          Collection<CustomBlock> blocks) throws IOException {
        java.util.List<CustomBlock> withModels = new java.util.ArrayList<>();
        java.util.Map<Integer, CustomBlock> flowerModels = new java.util.LinkedHashMap<>();
        for (CustomBlock block : blocks) {
            if (block.getModel() == null) {
                continue;
            }
            withModels.add(block);
            if (block.getBlockId() == 38) {
                flowerModels.put(block.getMeta(), block);
            } else if (block.getBlockId() != 39 && block.getBlockId() != 40
                    && block.getBlockId() != 32 && block.getBlockId() != 111) {
                throw new IOException("model: hote non supporte pour '" + block.getName()
                        + "' (" + block.getBlockName() + ") - supportes : red_flower et hotes mono-etat");
            }
        }
        if (withModels.isEmpty()) {
            return;
        }

        File modelsRoot = new File(packFolder, "assets/minecraft/models/block/izanami");
        File texturesRoot = new File(packFolder, "assets/minecraft/textures/blocks/izanami");
        if (!modelsRoot.mkdirs() || !texturesRoot.mkdirs()) {
            throw new IOException("mkdirs models/textures izanami");
        }
        File blockstates = new File(packFolder, "assets/minecraft/blockstates");
        if (!blockstates.mkdirs()) {
            throw new IOException("mkdirs " + blockstates);
        }

        // 1.8 : les fleurs utilisent un StateMap withName(TYPE) — le client lit
        // UN FICHIER PAR FLEUR (blockstates/oxeye_daisy.json, variante "normal"),
        // jamais red_flower.json. Idem principe pour les hôtes mono-état.
        for (CustomBlock block : withModels) {
            String stateFile = block.getBlockId() == 38
                    ? RED_FLOWER_TYPES[block.getMeta()] : block.getBlockName();
            write(new File(blockstates, stateFile + ".json"),
                    "{\n  \"variants\": {\n    \"normal\": { \"model\": \"izanami/"
                            + block.getName() + "\" }\n  }\n}\n");
        }

        for (CustomBlock block : withModels) {
            File modelFile = new File(modelsFolder, block.getModel());
            if (!modelFile.exists()) {
                throw new IOException("Modele introuvable : models/" + block.getModel());
            }
            String json = new String(Files.readAllBytes(modelFile.toPath()), StandardCharsets.UTF_8);
            write(new File(modelsRoot, block.getName() + ".json"), json);
            // copie des textures referencees "blocks/izanami/<nom>"
            java.util.regex.Matcher matcher =
                    java.util.regex.Pattern.compile("blocks/izanami/([a-z0-9_]+)").matcher(json);
            java.util.Set<String> copied = new java.util.HashSet<>();
            while (matcher.find()) {
                String texture = matcher.group(1);
                if (copied.add(texture)) {
                    copyTexture(texturesFolder, texture + ".png", texturesRoot, texture + ".png");
                }
            }
        }
    }

    /**
     * Blocs custom sur murets (dripstone) : le rendu vanilla des murets
     * connecte visuellement aux voisins (logique client sur les propriétés
     * d'état, pas la metadata). On réécrit blockstates/cobblestone_wall.json
     * en énumérant les 64 combinaisons d'états vers UN modèle croix fixe par
     * variante — plus aucune liaison. Le CTM vertical continue de choisir
     * tip/milieu. Le rendu croix exige le layer cutout : ajouté via
     * optifine/block.properties (layer.cutout).
     */
    private static void writeWallOverrides(File texturesFolder, File packFolder,
                                           Collection<CustomBlock> blocks) throws IOException {
        CustomBlock cobble = null;
        CustomBlock mossy = null;
        for (CustomBlock block : blocks) {
            if (block.getBlockId() == 139) {
                if (block.getMeta() == 0) {
                    cobble = block;
                } else {
                    mossy = block;
                }
            }
        }
        if (cobble == null && mossy == null) {
            return;
        }

        File modelsRoot = new File(packFolder, "assets/minecraft/models/block/izanami");
        File texturesRoot = new File(packFolder, "assets/minecraft/textures/blocks/izanami");
        modelsRoot.mkdirs();
        texturesRoot.mkdirs();
        for (CustomBlock block : new CustomBlock[]{cobble, mossy}) {
            if (block == null) {
                continue;
            }
            String texture = block.getTexture().replace(".png", "");
            copyTexture(texturesFolder, block.getTexture(), texturesRoot, texture + ".png");
            write(new File(modelsRoot, block.getName() + ".json"),
                    "{\n  \"parent\": \"block/cross\",\n  \"ambientocclusion\": false,\n"
                            + "  \"textures\": { \"cross\": \"blocks/izanami/" + texture + "\" }\n}\n");
        }

        // 64 combinaisons : east, north, south, up, variant, west (ordre alphabétique 1.8)
        StringBuilder variants = new StringBuilder();
        String[] bools = {"false", "true"};
        for (String east : bools) {
            for (String north : bools) {
                for (String south : bools) {
                    for (String up : bools) {
                        for (int variant = 0; variant < 2; variant++) {
                            CustomBlock block = variant == 0 ? cobble : mossy;
                            String model = block != null ? "izanami/" + block.getName()
                                    : (variant == 0 ? "cobblestone_wall_post" : "mossy_cobblestone_wall_post");
                            String variantName = variant == 0 ? "cobblestone" : "mossy_cobblestone";
                            for (String west : bools) {
                                if (variants.length() > 0) {
                                    variants.append(",\n");
                                }
                                variants.append("    \"east=").append(east)
                                        .append(",north=").append(north)
                                        .append(",south=").append(south)
                                        .append(",up=").append(up)
                                        .append(",variant=").append(variantName)
                                        .append(",west=").append(west)
                                        .append("\": { \"model\": \"").append(model).append("\" }");
                            }
                        }
                    }
                }
            }
        }
        File blockstates = new File(packFolder, "assets/minecraft/blockstates");
        blockstates.mkdirs();
        write(new File(blockstates, "cobblestone_wall.json"),
                "{\n  \"variants\": {\n" + variants + "\n  }\n}\n");

        // rendu croix sur un bloc au layer SOLID : bascule en cutout via OptiFine
        File optifine = new File(packFolder, "assets/minecraft/optifine");
        if (!optifine.mkdirs()) {
            throw new IOException("mkdirs " + optifine);
        }
        write(new File(optifine, "block.properties"), "layer.cutout=cobblestone_wall\n");
    }

    private static void writeConditionalRule(File texturesFolder, File rulesRoot,
                                             ConditionalTexture rule) throws IOException {
        File dir = new File(rulesRoot, rule.getName());
        if (!dir.mkdirs()) {
            throw new IOException("mkdirs " + dir);
        }
        StringBuilder tiles = new StringBuilder();
        for (String tile : rule.getTiles()) {
            if (tiles.length() > 0) {
                tiles.append(' ');
            }
            if (tile.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
                // texture Izanami : copiée dans le dossier de la règle
                String base = tile.substring(0, tile.length() - 4);
                copyTexture(texturesFolder, tile, dir, base + ".png");
                tiles.append(base);
            } else {
                // référence vanilla (ex. textures/blocks/stone) : telle quelle
                tiles.append(tile);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("method=").append(rule.getMethod()).append('\n');
        sb.append("matchBlocks=").append(rule.getMatchBlock()).append('\n');
        if (rule.getMetadata() != null) {
            sb.append("metadata=").append(rule.getMetadata()).append('\n');
        }
        if (rule.getHeights() != null) {
            sb.append("heights=").append(rule.getHeights()).append('\n');
        }
        if (rule.getFaces() != null) {
            sb.append("faces=").append(rule.getFaces()).append('\n');
        }
        if (!rule.getBiomes().isEmpty()) {
            sb.append("biomes=").append(String.join(" ", rule.getBiomes())).append('\n');
        }
        sb.append("tiles=").append(tiles).append('\n');
        if (!rule.getWeights().isEmpty()) {
            StringBuilder weights = new StringBuilder();
            for (Integer weight : rule.getWeights()) {
                if (weights.length() > 0) {
                    weights.append(' ');
                }
                weights.append(weight);
            }
            sb.append("weights=").append(weights).append('\n');
        }
        write(new File(dir, rule.getName() + ".properties"), sb.toString());
    }

    private static String ctmProperties(CustomBlock block, String tile, String faces) {
        StringBuilder sb = new StringBuilder();
        sb.append("method=fixed\n");
        sb.append("matchBlocks=").append(block.getBlockName()).append('\n');
        sb.append("metadata=").append(block.getMeta()).append('\n');
        sb.append("tiles=").append(tile).append('\n');
        if (faces != null) {
            sb.append("faces=").append(faces).append('\n');
        }
        sb.append(filters(block));
        return sb.toString();
    }

    /** Lignes heights=/biomes= des filtres client du bloc (hors filtre : hôte vanilla). */
    private static String filters(CustomBlock block) {
        StringBuilder sb = new StringBuilder();
        if (block.getHeights() != null) {
            sb.append("heights=").append(block.getHeights()).append('\n');
        }
        if (block.getBiomes() != null) {
            sb.append("biomes=").append(block.getBiomes()).append('\n');
        }
        return sb.toString();
    }

    private static void copyTexture(File texturesFolder, String textureName,
                                    File targetDir, String targetName) throws IOException {
        File source = new File(texturesFolder, textureName);
        if (!source.exists()) {
            throw new IOException("Texture introuvable : " + textureName);
        }
        Files.copy(source.toPath(), new File(targetDir, targetName).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        // animation : mcmeta fourni, sinon auto-généré pour les bandes verticales
        // de frames (ex. sculk.png 16x64) — sans lui, le client affiche la
        // texture "manquante" violette/noire
        File meta = new File(texturesFolder, textureName + ".mcmeta");
        if (meta.exists()) {
            Files.copy(meta.toPath(), new File(targetDir, targetName + ".mcmeta").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } else {
            int[] size = pngSize(source);
            if (size != null && size[1] > size[0] && size[1] % size[0] == 0) {
                write(new File(targetDir, targetName + ".mcmeta"), "{\"animation\":{}}");
            }
        }
    }

    /** @return {largeur, hauteur} lus dans l'en-tête IHDR, ou null si illisible */
    private static int[] pngSize(File png) {
        try (FileInputStream in = new FileInputStream(png)) {
            byte[] header = new byte[26];
            if (in.read(header) < 26) {
                return null;
            }
            int width = ((header[16] & 255) << 24) | ((header[17] & 255) << 16)
                    | ((header[18] & 255) << 8) | (header[19] & 255);
            int height = ((header[20] & 255) << 24) | ((header[21] & 255) << 16)
                    | ((header[22] & 255) << 8) | (header[23] & 255);
            return new int[]{width, height};
        } catch (IOException e) {
            return null;
        }
    }

    private static void write(File file, String content) throws IOException {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static void zipFolder(File folder, File zipFile) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zipRecursive(folder, folder, zip);
        }
    }

    private static void zipRecursive(File root, File current, ZipOutputStream zip) throws IOException {
        File[] files = current.listFiles();
        if (files == null) {
            return;
        }
        byte[] buffer = new byte[8192];
        for (File file : files) {
            if (file.isDirectory()) {
                zipRecursive(root, file, zip);
                continue;
            }
            String entry = root.toPath().relativize(file.toPath()).toString().replace('\\', '/');
            zip.putNextEntry(new ZipEntry(entry));
            try (FileInputStream in = new FileInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) > 0) {
                    zip.write(buffer, 0, read);
                }
            }
            zip.closeEntry();
        }
    }

    private static void deleteRecursive(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursive(child);
            }
        }
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }
}
