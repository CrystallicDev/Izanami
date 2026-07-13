package dev.sukkit.izanami.schem;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Charge les .schematic du dossier plugins/Izanami/schematics/ au démarrage.
 * Accès par nom de fichier sans extension, insensible à la casse.
 */
public final class SchematicManager {

    private final File folder;
    private final Logger logger;
    private final Map<String, Schematic> schematics = new LinkedHashMap<>();

    public SchematicManager(File folder, Logger logger) {
        this.folder = folder;
        this.logger = logger;
        if (!folder.exists() && !folder.mkdirs()) {
            logger.warning("Impossible de creer le dossier schematics : " + folder);
        }
        reload();
    }

    public void reload() {
        this.schematics.clear();
        File[] files = this.folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".schematic"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                Schematic schematic = Schematic.load(file);
                this.schematics.put(schematic.getName(), schematic);
            } catch (IOException e) {
                this.logger.severe("Schematic illisible '" + file.getName() + "' : " + e.getMessage());
            }
        }
        this.logger.info(this.schematics.size() + " schematic(s) charge(s) depuis " + this.folder.getName() + "/");
    }

    /** @return le schematic, ou null s'il n'existe pas */
    public Schematic get(String name) {
        return name == null ? null : this.schematics.get(name.toLowerCase(Locale.ROOT).replace(".schematic", ""));
    }

    public Set<String> names() {
        return Collections.unmodifiableSet(this.schematics.keySet());
    }

    public File getFolder() {
        return this.folder;
    }
}
