plugins {
    java
}

group = "dev.sukkit"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    // Server jar contains both Bukkit API and NMS/CraftBukkit classes (incl. Sukkit WorldGen hook)
    compileOnly(files("libs/pandaspigot-server.jar"))
}

tasks.jar {
    archiveFileName.set("Izanami.jar")
    // No shading needed — all deps are provided by the server
}

tasks.compileJava {
    options.encoding = "UTF-8"
}

tasks.processResources {
    filteringCharset = "UTF-8"
    // textures des blocs custom embarquees dans le jar, extraites au premier lancement
    from("textures") {
        into("textures")
        exclude("*.zip")
    }
}
