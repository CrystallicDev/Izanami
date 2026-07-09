<div align="center">


# Izanami

[![Forge](https://img.shields.io/badge/Platform-Sukkit-darkgreen)](https://github.com/CrystallicDev/Sukkit)
[![Modrinth](https://img.shields.io/modrinth/dt/izanami)](https://modrinth.com/plugin/izanami)

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/nqtsu91)

</div>

<div align="center">

**Izanami** is a server plugin that replaces the vanilla 1.8 world generator with a modern,
fully-configurable one : biome control, 1.18-style noise caves, cave biomes, underground
rivers, amethyst geodes, schematic structures, and a square pre-generator, while keeping
everything **PvP-friendly** and **optimised**.

> ⚠️ **Izanami is not a drop-in Bukkit/Spigot plugin.** It relies on NMS hooks that only
> exist in **[Sukkit](#requirements)**, a patched 1.8.9 server fork (see below). It will not
> load on stock Spigot/Paper.

---

## Why a server fork?

The Bukkit `ChunkGenerator` API in 1.8 is far too limited for this kind of generation:
it only exposes a `byte[]` block array and a `BiomeGrid`, with no access to custom biomes,
carvers, or decoration. Izanami therefore drives generation through a small NMS hook added
to the server, and keeps all of the actual logic inside the plugin.

That hook, plus a handful of tiny, zero-cost-when-unused server-side patches, lives on the
**`WorldGen` branch** of Sukkit:

| Patch | Purpose |
|-------|---------|
| `NmsWorldGenRegistry` | Register a full NMS generator (`IChunkProvider` + `WorldChunkManager`) per world name, before `WorldCreator#createWorld()`. |
| `BiomeClientRemap` | Show custom server-side biomes to the client as a chosen vanilla biome (colours / weather). |
| Carver hook | Make `ChunkProviderGenerate`'s cave/canyon carvers replaceable. |
| `CustomBlockBehaviors` | Anti-pop, configurable drops, break-effect remap and column integrity for custom blocks. |

> **Only secondary worlds are supported.** The main world loads before plugins do, so its
> generator can't be registered in time. Create your Izanami worlds as extra worlds.

---

## Features

### World control
- **Biome whitelist** with per-biome weights.
- **Central biome**: a circular, noise-edged blob at spawn (configurable radius), with an
  optional **exclusion ring** where the central biome is forbidden, guaranteeing a clean
  border between the center and the rest of the map.
- **Organic biome borders** via domain-warped Voronoi cells (no more "square" chunk edges).
- **Custom biomes in Java**: subclass the API's builder, pick a client-side vanilla biome
  for rendering, set surface blocks, height, decoration and mob spawns.

### Terrain & caves
- **Configurable ground height** (`base-height`): raise it for tall,
  1.18-style caverns, without a patch.
- **Noise caves**: cheese (large rooms), spaghetti (long tunnels) and noodle (thin worms),
  sampled on a 4×4×4 grid and trilinearly interpolated (~4k noise evals/chunk).
- **`openness`** factor to make caves wider and more open.
- **Underground navigable rivers**: vaulted water tunnels with an air gap for boats, fed by
  **waterfalls** that drop in from the surface.
- **Sinkholes**: dry vertical shafts that open the caverns to the sky.
- **Amethyst geodes**: hollow spheres (calcite shell, amethyst layer, clusters & buds).

### Cave biomes (à la 1.18+)
A second, purely server-side biome layer (never sent to the client) that skins caverns and
places decorations:
- **Dripstone Caves**: stalactite/stalagmite columns.
- **Lush Caves**: moss floors, azaleas, glow-berry vines, spore blossoms, and **water lakes**
  (converted from vanilla lava lakes so they keep natural shapes).
- **Deep Dark**: sculk floor & sculk veins.
- Decorations spawn in **patches**, like vanilla features.

### Structures & vegetation
- **Schematics**: load `.schematic` files (MCEdit / WorldEdit format) and place them as
  structures or trees.
- **Mixed forests**: several tree types (vanilla generators or schematics) with independent
  spacing and density, consistent across chunk borders.

### Pre-generation
- `/<world> pregen`: square pre-generation around spawn.
- **TPS-aware**: adapts its per-tick budget to keep the server responsive.
- **Resumable**: already-generated chunks are skipped, so re-running the command continues
  where it left off.

### Custom blocks (OptiFine)
Since players are expected on **Lunar Client** (which bundles OptiFine), Izanami can render
extra block types by reusing *valid-but-unused* vanilla block states and retexturing them
with OptiFine CTM:
- Deepslate, tuff, blackstone, sculk, moss, amethyst, dripstone, glow berries, and more.
- **Height/biome-conditional retextures** (e.g. `stone → deepslate` below y50) that cost no
  block slot at all.
- **Custom JSON models** via vanilla blockstates (e.g. spore blossom geometry) — no client
  mod required.
- A ready-to-use **resource pack** (`IzanamiPack.zip`) is generated automatically.

---

## Requirements

- **Server:** [Sukkit](https://github.com/CrystallicDev/Sukkit), a 1.8.9 [PandaSpigot](https://github.com/hpfxd/PandaSpigot) fork,
  built from the **`WorldGen`** branch (contains the required NMS hooks).
  <!-- Replace the (#) above with your Sukkit repository URL. -->
- **Java:** 8+ to run (the server itself uses JDK 17 to build).
- **Client (optional, for custom blocks):** [OptiFine](https://optifine.net) already
  included in [Lunar Client](https://www.lunarclient.com/) 1.8.9.

---

## Installation

1. Build & run a **Sukkit** server from the `WorldGen` branch.
2. Drop `Izanami.jar` into `plugins/`.
3. Start the server once to generate `plugins/Izanami/config.yml`.
4. Configure your worlds (see below), restart, and explore.

For custom blocks, hand the generated `plugins/Izanami/IzanamiPack.zip` to your players
(or serve it via `resource-pack` in `server.properties`).

---

## Quick start

`plugins/Izanami/config.yml`:

```yaml
worlds:
  my_world:
    seed: 0            # 0 = random
    autoload: true

    terrain:
      base-height: 100   # taller underground for big caves (vanilla is 64)

    biomes:
      cell-size: 160
      border-noise: 24
      allowed:
        plains: 3
        forest: 2
        extreme_hills: 1
      center:
        biome: desert
        radius: 250
        edge-noise: 40
        exclusion-radius: 400

    caves:
      style: modern      # modern | vanilla | none
      max-y: 96
      openness: 1.35

    underground-rivers:
      enabled: true

    cave-biomes:
      cell-size: 120
      allowed:
        dripstone_caves: 1
        lush_caves: 1
        deep_dark: 1
        none: 1

    sinkholes:
      enabled: true
    geodes:
      enabled: true
```

Then in-game: `/izanami tp my_world`.

---

## Commands

All commands are under `/izanami` (alias `/iza`), permission `izanami.admin` (default: OP).

| Command | Description |
|---------|-------------|
| `/izanami create <world> [seed]` | Create a new Izanami world. |
| `/izanami load <world>` | Load a configured Izanami world. |
| `/izanami tp <world>` | Teleport to a world's spawn. |
| `/izanami list` | List Izanami worlds. |
| `/izanami info [world]` | Show a world's generator info. |
| `/izanami pregen <world> <blocks>` | Pre-generate a `±blocks` square. |
| `/izanami pregen stop <world>` / `status` | Stop / monitor pre-generation. |
| `/izanami schem list \| reload \| paste <name>` | Manage schematics. |
| `/izanami cb list \| place <name> \| pack` | Manage custom blocks & rebuild the resource pack. |

---

## Developer API

Plugins can register **custom biomes** and **cave biomes** in Java. A quick taste:

```java
// A custom surface biome, rendered client-side as a desert
CustomBiome.builder("Ashen Plains")
    .clientBiome(BiomeBase.DESERT)
    .temperature(2.0F).humidity(0.0F).noRain()
    .topBlock(Blocks.NETHERRACK.getBlockData())
    .register();

// A cave biome with a block palette, patched decorations and lakes
new CaveBiome("Lush Caves")
    .palette(moss, null, null)
    .floorDecorations(mossCarpet, azalea)
    .ceilingDecorations(glowBerries, sporeBlossom)
    .decorationPatches(20, 4)
    .pools(new IBlockData[]{ Blocks.CLAY.getBlockData() }, dripleafPad, 48, 5, 15, 4);
IzanamiCaveBiomeRegistry.register(...);
```

The cave-biome layer is exposed per world via `WorldService#getBiomeSource(name)`, so other
plugins can query the cave biome at any position (e.g. gameplay tied to being inside an
underground river).

---

## Building

Izanami compiles against the Sukkit server jar (which bundles the Bukkit API, NMS and the
WorldGen hooks):

```bash
# place the mapped Sukkit jar at libs/pandaspigot-server.jar, then:
./gradlew jar
```

The output `Izanami.jar` is written to the test server's `plugins/` folder (configurable in
`build.gradle.kts`). Block textures placed in `textures/` are bundled into the jar and
extracted on first launch.

---

## Credits & links

- **[PandaSpigot](https://github.com/hpfxd/PandaSpigot)**: the high-performance 1.8.9
  Paper fork that Sukkit is built on.
- **[OptiFine](https://optifine.net)** / **[Lunar Client](https://www.lunarclient.com/)**:
  connected-textures & custom-model support on the client.
- Modern-Minecraft feature references (noise caves, cave biomes) are re-implementations for
  the 1.8.9 engine.

---
