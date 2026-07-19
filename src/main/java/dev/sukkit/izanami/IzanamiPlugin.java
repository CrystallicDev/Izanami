package dev.sukkit.izanami;

import dev.sukkit.izanami.command.IzanamiCommand;
import dev.sukkit.izanami.pregen.PregenService;
import dev.sukkit.izanami.world.WorldService;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Izanami — génération et prégénération de monde custom pour Sukkit.
 * <p>
 * Repose sur le hook WorldGen de Sukkit ({@code NmsWorldGenRegistry}) : le
 * générateur NMS complet est enregistré par nom de monde avant la création
 * du monde. Seuls les mondes secondaires sont supportés (le monde principal
 * charge avant les plugins).
 */
public final class IzanamiPlugin extends JavaPlugin {

    private WorldService worldService;
    private PregenService pregenService;
    private dev.sukkit.izanami.schem.SchematicManager schematicManager;
    private dev.sukkit.izanami.custom.CustomBlockRegistry customBlockRegistry;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.worldService = new WorldService(this);
        getServer().getPluginManager().registerEvents(this.worldService, this);
        this.worldService.loadFromConfig();
        this.pregenService = new PregenService(this);
        extractBundled("schematics/"); // arbres redwood du biome de démo, éditables ensuite
        this.schematicManager = new dev.sukkit.izanami.schem.SchematicManager(
                new java.io.File(getDataFolder(), "schematics"), getLogger());
        setupCustomBlocks();

        getCommand("izanami").setExecutor(new IzanamiCommand(this));

        // AVANT createAutoloadWorlds : les mondes peuvent référencer ces biomes
        if (getConfig().getBoolean("debug.demo-biome", false)) {
            registerDemoBiome();
        }

        this.worldService.createAutoloadWorlds();
    }

    /**
     * Biome de démonstration de l'API (activable via debug.demo-biome) :
     * plaines sèches affichées comme un désert côté client (remap Sukkit),
     * avec une forêt mixte Izanami — chênes vanilla (espacement 6) + arbre
     * schematic de démo (espacement 20).
     */
    private void registerDemoBiome() {
        java.io.File demoFile = new java.io.File(this.schematicManager.getFolder(), "izanami_demo_tree.schematic");
        if (!demoFile.exists()) {
            try {
                writeDemoSchematic(demoFile);
                this.schematicManager.reload();
            } catch (java.io.IOException e) {
                getLogger().severe("Ecriture du schematic de demo impossible : " + e.getMessage());
            }
        }

        dev.sukkit.izanami.tree.IzanamiForest.Builder forest = dev.sukkit.izanami.tree.IzanamiForest.builder()
                .add(dev.sukkit.izanami.tree.JavaTree.oak(), 6, 0.6);
        dev.sukkit.izanami.schem.Schematic demoTree = this.schematicManager.get("izanami_demo_tree");
        if (demoTree != null) {
            forest.add(new dev.sukkit.izanami.tree.SchematicTree(demoTree), 20, 1.0);
        }

        dev.sukkit.izanami.api.CustomBiome biome = dev.sukkit.izanami.api.CustomBiome.builder("Ashen Plains")
                .clientBiome(net.minecraft.server.v1_8_R3.BiomeBase.DESERT)
                .temperature(2.0F).humidity(0.0F).noRain()
                .height(0.125F, 0.05F)
                .grass(6).flowers(0)
                .forest(forest.build())
                .register();
        getLogger().info("Biome de demo enregistre : '" + biome.ah + "' (id " + biome.id
                + ", client=" + biome.getClientBiome().ah + ", foret mixte oak+schematic)");

        registerRedwoodBiome();
        registerDemoCaveBiome();
    }

    /**
     * Forêt de Redwood (équivalent Taiga non enneigée) : arbres schematic
     * IZ_RedwoodTree_* placés en variantes aléatoires (une grille dense) +
     * quelques sapins vanilla en remplissage.
     */
    private void registerRedwoodBiome() {
        java.util.List<dev.sukkit.izanami.tree.IzanamiTree> redwoods = new java.util.ArrayList<>();
        for (String name : this.schematicManager.names()) {
            if (name.startsWith("iz_redwoodtree")) {
                redwoods.add(new dev.sukkit.izanami.tree.SchematicTree(
                        this.schematicManager.get(name), true));
            }
        }
        if (redwoods.isEmpty()) {
            getLogger().warning("Biome Redwood ignore : aucun schematic IZ_RedwoodTree_* dans schematics/");
            return;
        }

        // arbre Feature basé SDF (framework SALM/bclib porté) — Pythadendron de démo
        dev.sukkit.izanami.tree.feature.trees.PythadendronTree pythadendron =
                new dev.sukkit.izanami.tree.feature.trees.PythadendronTree(
                        new dev.sukkit.izanami.tree.feature.trees.PythadendronConfig(
                                net.minecraft.server.v1_8_R3.Blocks.LOG.fromLegacyData(0),        // chêne
                                net.minecraft.server.v1_8_R3.Blocks.LEAVES.fromLegacyData(0 | 4))); // chêne persistant

        // redwoods schematic (très larges) espacés + Pythadendron SDF + sapins vanilla
        dev.sukkit.izanami.tree.IzanamiForest forest = dev.sukkit.izanami.tree.IzanamiForest.builder()
                .add(new dev.sukkit.izanami.tree.RandomTree(redwoods), 26, 0.6)
                .add(pythadendron, 24, 0.5)
                .add(dev.sukkit.izanami.tree.JavaTree.spruce(), 10, 0.3)
                .build();

        dev.sukkit.izanami.api.CustomBiome biome = dev.sukkit.izanami.api.CustomBiome.builder("Redwood Forest")
                .clientBiome(net.minecraft.server.v1_8_R3.BiomeBase.TAIGA)
                .temperature(0.3F).humidity(0.8F)
                .height(0.2F, 0.2F)
                .grass(10).flowers(0)
                .forest(forest)
                .register();
        getLogger().info("Biome enregistre : '" + biome.ah + "' (id " + biome.id + ", client=Taiga, "
                + redwoods.size() + " variantes redwood + sapins)");
    }

    /**
     * Biomes de cave de démo. Note : la deepslate/tuff/etc. des parois vient
     * des règles CTM par hauteur (conditional-textures.yml), pas d'une palette
     * de blocs — les palettes ne servent qu'aux blocs impossibles par hauteur
     * (mousse, mycelium/sculk, améthyste zonale).
     */
    private void registerDemoCaveBiome() {
        net.minecraft.server.v1_8_R3.IBlockData stalagmite = customBlockData("pointed_dripstone_up", null);
        net.minecraft.server.v1_8_R3.IBlockData stalactite = customBlockData("pointed_dripstone_down", null);
        dev.sukkit.izanami.api.IzanamiCaveBiomeRegistry.register(
                new dev.sukkit.izanami.api.CaveBiome("Dripstone Caves")
                        .decorations(stalagmite, stalactite, 0.35)
                        .decorationHeight(3, 3)
                        .decorationPatches(22, 5));

        net.minecraft.server.v1_8_R3.IBlockData moss = customBlockData("moss_block",
                net.minecraft.server.v1_8_R3.Blocks.MOSSY_COBBLESTONE.getBlockData());
        net.minecraft.server.v1_8_R3.IBlockData glowBerries = customBlockData("glow_berry_vine", null);
        net.minecraft.server.v1_8_R3.IBlockData sporeBlossom = customBlockData("spore_blossom", null);
        net.minecraft.server.v1_8_R3.IBlockData azalea = customBlockData("azalea", null);
        net.minecraft.server.v1_8_R3.IBlockData mossCarpet = customBlockData("moss_carpet", null);
        net.minecraft.server.v1_8_R3.IBlockData dripleafPad = customBlockData("dripleaf_pad", null);
        dev.sukkit.izanami.api.IzanamiCaveBiomeRegistry.register(
                new dev.sukkit.izanami.api.CaveBiome("Lush Caves")
                        .palette(moss, null, null)
                        .floorDecorations(mossCarpet, azalea)
                        .ceilingDecorations(glowBerries, sporeBlossom)
                        .decorationChance(0.30)
                        .decorationHeight(1, 3) // lianes en colonnes, le reste 1 bloc
                        .decorationPatches(20, 4)
                        // lacs en cuvette : rayon 5-15, profondeur 4 au centre, fond argile
                        .pools(new net.minecraft.server.v1_8_R3.IBlockData[]{
                                net.minecraft.server.v1_8_R3.Blocks.CLAY.getBlockData()},
                                dripleafPad, 48, 5, 15, 4));

        // sol mycelium vanilla, affiché sculk par la règle CTM mycelium->sculk ;
        // pas de decay : le mycelium ne pourrit que couvert par un bloc opaque
        net.minecraft.server.v1_8_R3.IBlockData sculkVein = customBlockData("sculk_vein", null);
        dev.sukkit.izanami.api.IzanamiCaveBiomeRegistry.register(
                new dev.sukkit.izanami.api.CaveBiome("Deep Dark")
                        .palette(net.minecraft.server.v1_8_R3.Blocks.MYCELIUM.getBlockData(), null, null)
                        .floorDecorations(sculkVein)
                        .decorationChance(0.40)
                        .decorationPatches(18, 5));

        getLogger().info("Biomes de cave de demo : Dripstone Caves, Lush Caves, Deep Dark"
                + " (amethyste : geodes, section 'geodes' de la config monde)");
    }

    /** IBlockData d'un bloc custom par nom, ou fallback si non mappé. */
    private net.minecraft.server.v1_8_R3.IBlockData customBlockData(String name,
            net.minecraft.server.v1_8_R3.IBlockData fallback) {
        dev.sukkit.izanami.custom.CustomBlock block = this.customBlockRegistry.get(name);
        if (block == null) {
            return fallback;
        }
        return net.minecraft.server.v1_8_R3.Block.getById(block.getBlockId())
                .fromLegacyData(block.getMeta());
    }

    /** Schematic de démo : tronc de bûches 5 de haut, glowstone au sommet (3x6x3). */
    private void writeDemoSchematic(java.io.File file) throws java.io.IOException {
        short width = 3, height = 6, length = 3;
        byte[] blocks = new byte[width * height * length];
        byte[] data = new byte[blocks.length];
        for (int y = 0; y < 5; y++) {
            blocks[(y * length + 1) * width + 1] = (byte) 17; // bûche de chêne
        }
        blocks[(5 * length + 1) * width + 1] = (byte) 89; // glowstone

        net.minecraft.server.v1_8_R3.NBTTagCompound tag = new net.minecraft.server.v1_8_R3.NBTTagCompound();
        tag.setShort("Width", width);
        tag.setShort("Height", height);
        tag.setShort("Length", length);
        tag.setString("Materials", "Alpha");
        tag.setByteArray("Blocks", blocks);
        tag.setByteArray("Data", data);
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            net.minecraft.server.v1_8_R3.NBTCompressedStreamTools.a(tag, out);
        }
    }

    @Override
    public void onDisable() {
        if (this.pregenService != null) {
            this.pregenService.stopAll();
        }
    }

    public WorldService getWorldService() {
        return this.worldService;
    }

    public PregenService getPregenService() {
        return this.pregenService;
    }

    public dev.sukkit.izanami.schem.SchematicManager getSchematicManager() {
        return this.schematicManager;
    }

    public dev.sukkit.izanami.custom.CustomBlockRegistry getCustomBlockRegistry() {
        return this.customBlockRegistry;
    }

    /** Blocs custom : extraction des textures embarquées, registre, comportements Sukkit, resource pack. */
    private void setupCustomBlocks() {
        extractBundledTextures();
        java.io.File texturesFolder = new java.io.File(getDataFolder(), "textures");
        this.customBlockRegistry = new dev.sukkit.izanami.custom.CustomBlockRegistry(
                new java.io.File(getDataFolder(), "custom-blocks.yml"), texturesFolder, getLogger());
        applyCustomBlockBehaviors();
        regenerateResourcePack();
    }

    /**
     * Enregistre chaque bloc custom dans le patch Sukkit CustomBlockBehaviors :
     * anti-pop (les fleurs-hôtes tiennent partout, s'empilent, pendent),
     * drops configurés, effet de casse remappé.
     */
    private void applyCustomBlockBehaviors() {
        org.bukkit.Material placeholder = org.bukkit.Material.matchMaterial(
                this.customBlockRegistry.getPlaceholderDrop());
        if (placeholder != null) {
            org.bukkit.craftbukkit.v1_8_R3.worldgen.CustomBlockBehaviors.setPlaceholderDrop(
                    org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack.asNMSCopy(
                            new org.bukkit.inventory.ItemStack(placeholder)));
        }

        int effects = 0;
        for (dev.sukkit.izanami.custom.CustomBlock block : this.customBlockRegistry.getAll()) {
            int dropsMode;
            switch (block.getDrops()) {
                case NONE: dropsMode = org.bukkit.craftbukkit.v1_8_R3.worldgen.CustomBlockBehaviors.DROPS_NONE; break;
                case PLACEHOLDER: dropsMode = org.bukkit.craftbukkit.v1_8_R3.worldgen.CustomBlockBehaviors.DROPS_PLACEHOLDER; break;
                default: dropsMode = org.bukkit.craftbukkit.v1_8_R3.worldgen.CustomBlockBehaviors.DROPS_DEFAULT;
            }
            int attachMode;
            switch (block.getAttach()) {
                case CEILING: attachMode = org.bukkit.craftbukkit.v1_8_R3.worldgen.CustomBlockBehaviors.ATTACH_CEILING; break;
                case FLOOR: attachMode = org.bukkit.craftbukkit.v1_8_R3.worldgen.CustomBlockBehaviors.ATTACH_FLOOR; break;
                default: attachMode = org.bukkit.craftbukkit.v1_8_R3.worldgen.CustomBlockBehaviors.ATTACH_ANY;
            }
            org.bukkit.craftbukkit.v1_8_R3.worldgen.CustomBlockBehaviors.register(
                    block.getBlockId(), block.getMeta(), dropsMode, attachMode);

            if (block.getEffectsAs() != null) {
                String[] parts = block.getEffectsAs().split(":");
                org.bukkit.Material material = org.bukkit.Material.matchMaterial(parts[0]);
                if (material == null) {
                    getLogger().severe("Bloc custom '" + block.getName() + "' : effects-as inconnu '"
                            + block.getEffectsAs() + "'");
                    continue;
                }
                int effectMeta = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                org.bukkit.craftbukkit.v1_8_R3.worldgen.CustomBlockBehaviors.setBreakEffect(
                        block.getBlockId(), block.getMeta(), material.getId(), effectMeta);
                effects++;
            }
        }
        getLogger().info("Comportements Sukkit appliques a " + this.customBlockRegistry.getAll().size()
                + " blocs custom (" + effects + " remaps d'effet de casse, placeholder="
                + this.customBlockRegistry.getPlaceholderDrop() + ")");
    }

    public void regenerateResourcePack() {
        try {
            writeDefaultModels();
            java.util.List<dev.sukkit.izanami.custom.ConditionalTexture> rules =
                    dev.sukkit.izanami.custom.ConditionalTexture.loadAll(
                            new java.io.File(getDataFolder(), "conditional-textures.yml"), getLogger());
            java.io.File zip = dev.sukkit.izanami.custom.ResourcePackGenerator.generate(
                    new java.io.File(getDataFolder(), "textures"),
                    new java.io.File(getDataFolder(), "models"),
                    new java.io.File(getDataFolder(), "resourcepack"),
                    new java.io.File(getDataFolder(), "IzanamiPack.zip"),
                    this.customBlockRegistry.getAll(), rules);
            getLogger().info(this.customBlockRegistry.getAll().size() + " blocs custom, "
                    + rules.size() + " regle(s) conditionnelle(s), resource pack genere : " + zip.getName());
        } catch (java.io.IOException e) {
            getLogger().severe("Generation du resource pack impossible : " + e.getMessage());
        }
    }

    /**
     * Modèles 3D par défaut (éditables dans plugins/Izanami/models/) :
     * spore blossom = fleur plate sous le plafond + feuilles en croix.
     */
    private void writeDefaultModels() throws java.io.IOException {
        java.io.File modelsFolder = new java.io.File(getDataFolder(), "models");
        if (!modelsFolder.exists() && !modelsFolder.mkdirs()) {
            throw new java.io.IOException("mkdirs " + modelsFolder);
        }
        java.io.File sporeBlossom = new java.io.File(modelsFolder, "spore_blossom.json");
        if (sporeBlossom.exists()) {
            return;
        }
        // géométrie VANILLA (models/block/spore_blossom.json du client moderne) :
        // plateau _base 14x14 collé au plafond + 4 pétales 16x16 qui débordent
        // du bloc, pivotés de ±22.5° depuis leur arête haute
        String json = "{\n"
                + "  \"ambientocclusion\": false,\n"
                + "  \"textures\": {\n"
                + "    \"particle\": \"blocks/izanami/spore_blossom\",\n"
                + "    \"flower\": \"blocks/izanami/spore_blossom\",\n"
                + "    \"base\": \"blocks/izanami/spore_blossom_base\"\n"
                + "  },\n"
                + "  \"elements\": [\n"
                + "    { \"from\": [1, 15.9, 1], \"to\": [15, 15.9, 15],\n"
                + "      \"shade\": false,\n"
                + "      \"faces\": {\n"
                + "        \"up\":   { \"uv\": [1, 1, 15, 15], \"texture\": \"#base\" },\n"
                + "        \"down\": { \"uv\": [1, 1, 15, 15], \"texture\": \"#base\" } } },\n"
                + "    { \"from\": [8, 15.7, 0], \"to\": [24, 15.7, 16],\n"
                + "      \"rotation\": { \"origin\": [8, 16, 0], \"axis\": \"z\", \"angle\": -22.5 },\n"
                + "      \"shade\": false,\n"
                + "      \"faces\": {\n"
                + "        \"up\":   { \"uv\": [0, 0, 16, 16], \"texture\": \"#flower\", \"rotation\": 90 },\n"
                + "        \"down\": { \"uv\": [0, 16, 16, 0], \"texture\": \"#flower\", \"rotation\": 270 } } },\n"
                + "    { \"from\": [-8, 15.7, 0], \"to\": [8, 15.7, 16],\n"
                + "      \"rotation\": { \"origin\": [8, 16, 0], \"axis\": \"z\", \"angle\": 22.5 },\n"
                + "      \"shade\": false,\n"
                + "      \"faces\": {\n"
                + "        \"up\":   { \"uv\": [0, 0, 16, 16], \"texture\": \"#flower\", \"rotation\": 270 },\n"
                + "        \"down\": { \"uv\": [0, 16, 16, 0], \"texture\": \"#flower\", \"rotation\": 90 } } },\n"
                + "    { \"from\": [0, 15.7, 8], \"to\": [16, 15.7, 24],\n"
                + "      \"rotation\": { \"origin\": [0, 16, 8], \"axis\": \"x\", \"angle\": 22.5 },\n"
                + "      \"shade\": false,\n"
                + "      \"faces\": {\n"
                + "        \"up\":   { \"uv\": [16, 16, 0, 0], \"texture\": \"#flower\" },\n"
                + "        \"down\": { \"uv\": [16, 0, 0, 16], \"texture\": \"#flower\" } } },\n"
                + "    { \"from\": [0, 15.7, -8], \"to\": [16, 15.7, 8],\n"
                + "      \"rotation\": { \"origin\": [0, 16, 8], \"axis\": \"x\", \"angle\": -22.5 },\n"
                + "      \"shade\": false,\n"
                + "      \"faces\": {\n"
                + "        \"up\":   { \"uv\": [0, 0, 16, 16], \"texture\": \"#flower\" },\n"
                + "        \"down\": { \"uv\": [0, 16, 16, 0], \"texture\": \"#flower\" } } }\n"
                + "  ]\n}\n";
        java.nio.file.Files.write(sporeBlossom.toPath(),
                json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void extractBundledTextures() {
        extractBundled("textures/");
    }

    /** Copie les entrées {@code prefix...} du jar vers le dossier data (sans écraser). */
    private void extractBundled(String prefix) {
        try (java.util.zip.ZipFile jar = new java.util.zip.ZipFile(getFile())) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(prefix)) {
                    continue;
                }
                java.io.File out = new java.io.File(getDataFolder(), entry.getName());
                if (out.exists()) {
                    continue; // ne jamais écraser un fichier édité par l'utilisateur
                }
                out.getParentFile().mkdirs();
                try (java.io.InputStream in = jar.getInputStream(entry)) {
                    java.nio.file.Files.copy(in, out.toPath());
                }
            }
        } catch (java.io.IOException e) {
            getLogger().severe("Extraction des fichiers embarques (" + prefix + ") : " + e.getMessage());
        }
    }
}
