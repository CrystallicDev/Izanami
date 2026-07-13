package dev.sukkit.izanami.command;

import dev.sukkit.izanami.world.IzanamiWorldConfig;
import dev.sukkit.izanami.world.WorldService;
import net.minecraft.server.v1_8_R3.IChunkProvider;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.entity.Player;

import java.util.regex.Pattern;

public final class IzanamiCommand implements CommandExecutor {

    private static final Pattern WORLD_NAME = Pattern.compile("[a-zA-Z0-9_-]+");

    private final dev.sukkit.izanami.IzanamiPlugin plugin;
    private final WorldService worldService;

    public IzanamiCommand(dev.sukkit.izanami.IzanamiPlugin plugin) {
        this.plugin = plugin;
        this.worldService = plugin.getWorldService();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }
        switch (args[0].toLowerCase()) {
            case "create":
                return create(sender, args);
            case "load":
                return load(sender, args);
            case "tp":
                return tp(sender, args);
            case "list":
                return list(sender);
            case "info":
                return info(sender, args);
            case "pregen":
                return pregen(sender, args);
            case "schem":
                return schem(sender, args);
            case "cb":
                return customBlocks(sender, args);
            default:
                return false;
        }
    }

    private boolean create(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /izanami create <monde> [seed]");
            return true;
        }
        String name = args[1];
        if (!WORLD_NAME.matcher(name).matches()) {
            sender.sendMessage(ChatColor.RED + "Nom de monde invalide (autorisé : a-z, 0-9, _ et -).");
            return true;
        }
        long seed = 0L;
        if (args.length >= 3) {
            try {
                seed = Long.parseLong(args[2]);
            } catch (NumberFormatException e) {
                seed = args[2].hashCode(); // même convention que vanilla pour les seeds textuelles
            }
        }
        sender.sendMessage(ChatColor.GRAY + "Création du monde '" + name + "'... (le serveur peut figer quelques secondes)");
        try {
            World world = this.worldService.create(name, seed);
            sender.sendMessage(ChatColor.GREEN + "Monde '" + name + "' créé (seed " + world.getSeed() + "). "
                    + ChatColor.GRAY + "/izanami tp " + name);
        } catch (IllegalStateException e) {
            sender.sendMessage(ChatColor.RED + e.getMessage());
        }
        return true;
    }

    private boolean load(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /izanami load <monde>");
            return true;
        }
        IzanamiWorldConfig config = this.worldService.getWorldConfig(args[1]);
        if (config == null) {
            sender.sendMessage(ChatColor.RED + "Monde inconnu de la config Izanami : " + args[1]);
            return true;
        }
        this.worldService.createOrLoad(config);
        sender.sendMessage(ChatColor.GREEN + "Monde '" + config.getName() + "' chargé.");
        return true;
    }

    private boolean tp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Commande joueur uniquement.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /izanami tp <monde>");
            return true;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "Monde non chargé : " + args[1]);
            return true;
        }
        ((Player) sender).teleport(world.getSpawnLocation());
        sender.sendMessage(ChatColor.GREEN + "Téléporté au spawn de '" + world.getName() + "'.");
        return true;
    }

    private boolean list(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Mondes Izanami :");
        if (this.worldService.getWorlds().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "  (aucun — /izanami create <monde>)");
            return true;
        }
        for (IzanamiWorldConfig config : this.worldService.getWorlds()) {
            boolean loaded = Bukkit.getWorld(config.getName()) != null;
            sender.sendMessage(ChatColor.YELLOW + "  " + config.getName()
                    + ChatColor.GRAY + " — " + (loaded ? ChatColor.GREEN + "chargé" : ChatColor.RED + "non chargé")
                    + ChatColor.GRAY + ", autoload=" + config.isAutoload());
        }
        return true;
    }

    @SuppressWarnings("deprecation") // setTypeIdAndData/getTargetBlock : API 1.8, debug uniquement
    private boolean customBlocks(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            sender.sendMessage(ChatColor.GOLD + "Blocs custom ("
                    + this.plugin.getCustomBlockRegistry().getAll().size() + ") :");
            for (dev.sukkit.izanami.custom.CustomBlock block : this.plugin.getCustomBlockRegistry().getAll()) {
                sender.sendMessage(ChatColor.YELLOW + "  " + block.getName() + ChatColor.GRAY
                        + " -> " + block.slotString() + " (" + block.getShape().name().toLowerCase() + ")");
            }
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("pack")) {
            this.plugin.regenerateResourcePack();
            sender.sendMessage(ChatColor.GREEN + "Resource pack régénéré : plugins/Izanami/IzanamiPack.zip");
            return true;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("place")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Commande joueur uniquement.");
                return true;
            }
            dev.sukkit.izanami.custom.CustomBlock block = this.plugin.getCustomBlockRegistry().get(args[2]);
            if (block == null) {
                sender.sendMessage(ChatColor.RED + "Bloc custom inconnu : " + args[2]
                        + " (voir /izanami cb list)");
                return true;
            }
            Player player = (Player) sender;
            org.bukkit.block.Block target = player.getTargetBlock((java.util.HashSet<Byte>) null, 6);
            if (target == null || target.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "Vise un bloc à moins de 6 blocs.");
                return true;
            }
            target.setTypeIdAndData(block.getBlockId(), (byte) block.getMeta(), true);
            sender.sendMessage(ChatColor.GREEN + "Bloc '" + block.getName() + "' placé ("
                    + block.slotString() + "). " + ChatColor.GRAY
                    + "Sans le pack OptiFine, il apparaît comme " + (block.getShape()
                    == dev.sukkit.izanami.custom.CustomBlock.Shape.CUBE ? "bloc de champignon" : "fleur") + ".");
            return true;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("raw")) {
            return rawBlock(sender, args);
        }
        sender.sendMessage(ChatColor.RED + "Usage: /izanami cb list | place <nom> | pack | raw <id> [meta]");
        return true;
    }

    /**
     * Test empirique d'un état de bloc INVALIDE (ex. stone:9) : écrit
     * id&lt;&lt;4|meta brut dans la section de chunk (en contournant la
     * canonicalisation IBlockData) puis renvoie le chunk au client.
     * Attendu : état non enregistré → air des deux côtés (invisible,
     * traversable). Debug uniquement.
     */
    @SuppressWarnings("deprecation")
    private boolean rawBlock(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Commande joueur uniquement.");
            return true;
        }
        int id;
        int meta;
        try {
            id = Integer.parseInt(args[2]);
            meta = args.length >= 4 ? Integer.parseInt(args[3]) : 9;
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Usage: /izanami cb raw <id> [meta]");
            return true;
        }
        Player player = (Player) sender;
        org.bukkit.block.Block target = player.getTargetBlock((java.util.HashSet<Byte>) null, 6);
        if (target == null || target.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Vise un bloc à moins de 6 blocs.");
            return true;
        }

        net.minecraft.server.v1_8_R3.WorldServer handle =
                ((org.bukkit.craftbukkit.v1_8_R3.CraftWorld) player.getWorld()).getHandle();
        net.minecraft.server.v1_8_R3.Chunk chunk = handle.getChunkAt(target.getX() >> 4, target.getZ() >> 4);
        net.minecraft.server.v1_8_R3.ChunkSection section = chunk.getSections()[target.getY() >> 4];
        if (section == null) {
            sender.sendMessage(ChatColor.RED + "Section vide, vise un bloc solide.");
            return true;
        }
        int index = (target.getY() & 15) << 8 | (target.getZ() & 15) << 4 | (target.getX() & 15);
        section.getIdArray()[index] = (char) (id << 4 | (meta & 15));
        try { // isDirty est package-private : best-effort pour invalider le cache de ChunkMap
            java.lang.reflect.Field dirty = section.getClass().getDeclaredField("isDirty");
            dirty.setAccessible(true);
            dirty.setBoolean(section, true);
        } catch (ReflectiveOperationException ignored) {
        }
        player.getWorld().refreshChunk(target.getX() >> 4, target.getZ() >> 4);
        sender.sendMessage(ChatColor.GREEN + "Écrit brut " + id + ":" + (meta & 15) + " en "
                + target.getX() + "," + target.getY() + "," + target.getZ() + ChatColor.GRAY
                + " — côté serveur ce bloc est maintenant résolu comme "
                + (net.minecraft.server.v1_8_R3.Block.d.a((char) (id << 4 | (meta & 15))) == null
                        ? "AIR (état non enregistré)" : "un état valide") + ".");
        return true;
    }

    private boolean schem(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            sender.sendMessage(ChatColor.GOLD + "Schematics : " + ChatColor.WHITE
                    + String.join(", ", this.plugin.getSchematicManager().names()));
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
            this.plugin.getSchematicManager().reload();
            sender.sendMessage(ChatColor.GREEN + "Schematics recharges ("
                    + this.plugin.getSchematicManager().names().size() + ").");
            return true;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("paste")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Commande joueur uniquement.");
                return true;
            }
            dev.sukkit.izanami.schem.Schematic schematic = this.plugin.getSchematicManager().get(args[2]);
            if (schematic == null) {
                sender.sendMessage(ChatColor.RED + "Schematic inconnu : " + args[2]
                        + " (voir /izanami schem list)");
                return true;
            }
            Player player = (Player) sender;
            org.bukkit.Location loc = player.getLocation();
            schematic.paste(((org.bukkit.craftbukkit.v1_8_R3.CraftWorld) player.getWorld()).getHandle(),
                    new net.minecraft.server.v1_8_R3.BlockPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), 0);
            sender.sendMessage(ChatColor.GREEN + "Schematic '" + schematic.getName() + "' colle ("
                    + schematic.getWidth() + "x" + schematic.getHeight() + "x" + schematic.getLength() + ").");
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Usage: /izanami schem list | reload | paste <nom>");
        return true;
    }

    private boolean pregen(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("status")) {
            if (this.plugin.getPregenService().getJobs().isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "Aucune prégénération en cours.");
            } else {
                for (dev.sukkit.izanami.pregen.PregenJob job : this.plugin.getPregenService().getJobs()) {
                    sender.sendMessage(ChatColor.YELLOW + "Pregen " + job.statusLine());
                }
            }
            return true;
        }
        if (args.length >= 3 && args[1].equalsIgnoreCase("stop")) {
            if (this.plugin.getPregenService().stop(args[2])) {
                sender.sendMessage(ChatColor.GREEN + "Prégénération de '" + args[2]
                        + "' arrêtée. Relancer la commande reprendra où elle en était.");
            } else {
                sender.sendMessage(ChatColor.RED + "Aucune prégénération en cours pour '" + args[2] + "'.");
            }
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /izanami pregen <monde> <distance-blocs> | stop <monde> | status");
            return true;
        }

        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "Monde non chargé : " + args[1]);
            return true;
        }
        int distance;
        try {
            distance = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Distance invalide : " + args[2]);
            return true;
        }
        if (distance < 16) {
            sender.sendMessage(ChatColor.RED + "Distance minimale : 16 blocs.");
            return true;
        }

        java.util.UUID senderId = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        try {
            this.plugin.getPregenService().start(world, distance, senderId);
            sender.sendMessage(ChatColor.GREEN + "Prégénération de '" + world.getName()
                    + "' lancée sur ±" + distance + " blocs. Suivi : /izanami pregen status");
        } catch (IllegalStateException e) {
            sender.sendMessage(ChatColor.RED + e.getMessage());
        }
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        World world;
        if (args.length >= 2) {
            world = Bukkit.getWorld(args[1]);
        } else if (sender instanceof Player) {
            world = ((Player) sender).getWorld();
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /izanami info <monde>");
            return true;
        }
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "Monde non chargé.");
            return true;
        }

        WorldServer handle = ((CraftWorld) world).getHandle();
        IChunkProvider generator = handle.chunkProviderServer.chunkProvider;
        boolean izanami = this.worldService.getWorldConfig(world.getName()) != null;

        sender.sendMessage(ChatColor.GOLD + "Monde '" + world.getName() + "' :");
        sender.sendMessage(ChatColor.YELLOW + "  Seed : " + ChatColor.WHITE + world.getSeed());
        sender.sendMessage(ChatColor.YELLOW + "  Géré par Izanami : " + ChatColor.WHITE + (izanami ? "oui" : "non"));
        sender.sendMessage(ChatColor.YELLOW + "  Générateur : " + ChatColor.WHITE + generator.getClass().getSimpleName());
        sender.sendMessage(ChatColor.YELLOW + "  Biomes : " + ChatColor.WHITE
                + handle.getWorldChunkManager().getClass().getSimpleName());
        sender.sendMessage(ChatColor.YELLOW + "  Chunks chargés : " + ChatColor.WHITE
                + handle.chunkProviderServer.getLoadedChunks());
        sender.sendMessage(ChatColor.YELLOW + "  Biome (0,0) : " + ChatColor.WHITE + world.getBiome(0, 0));
        if (sender instanceof Player && ((Player) sender).getWorld() == world) {
            org.bukkit.Location loc = ((Player) sender).getLocation();
            sender.sendMessage(ChatColor.YELLOW + "  Biome ici (" + loc.getBlockX() + "," + loc.getBlockZ()
                    + ") : " + ChatColor.WHITE + world.getBiome(loc.getBlockX(), loc.getBlockZ()));
        }
        return true;
    }
}
