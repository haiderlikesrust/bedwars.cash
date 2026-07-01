package cash.bedwars.world;

import cash.bedwars.BedWarsCashPlugin;
import cash.bedwars.game.ArenaBlockTracker;
import cash.bedwars.game.GeneratorType;
import cash.bedwars.game.TeamColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Villager;
import net.kyori.adventure.text.Component;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import cash.bedwars.game.shop.ShopAccess;
import cash.bedwars.game.upgrades.UpgradeAccess;

import java.util.*;
import java.io.IOException;

/** Creates void lobby + arena (procedural or template-driven). */
public class WorldManager {
    public static final String LOBBY_WORLD = "bwc_lobby";
    public static final String ARENA_WORLD = "bwc_arena";

    private final BedWarsCashPlugin plugin;
    private final Map<TeamColor, Location> teamSpawns = new EnumMap<>(TeamColor.class);
    private final Map<TeamColor, Location> teamBeds = new EnumMap<>(TeamColor.class);
    private final Map<TeamColor, EnumMap<GeneratorType, Location>> teamGenerators = new EnumMap<>(TeamColor.class);
    private final Map<GeneratorType, List<Location>> globalGenerators = new EnumMap<>(GeneratorType.class);
    private Location lobbySpawn;
    private Location arenaWaitingSpawn;
    private boolean customLobby;
    private boolean customArena;
    private final Map<TeamColor, BlockFace> teamBedFacings = new EnumMap<>(TeamColor.class);

    public WorldManager(BedWarsCashPlugin plugin) {
        this.plugin = plugin;
    }

    public void bootstrap() {
        World lobby = loadLobbyWorld();
        if (lobby != null) {
            applyLobbyRules(lobby);
            if (!customLobby && !isBuilt(lobby)) buildLobby(lobby);
            else loadLobbySpawnFromConfig(lobby);
        }
        World arena = loadArenaWorld();
        if (arena != null) {
            applyLobbyRules(arena);
            if (customArena) {
                bindCustomArena(arena);
            } else {
                buildArena(arena);
            }
        }
        plugin.getLogger().info("Worlds ready: " + lobbyWorldName() + ", " + arenaWorldName()
                + (customLobby ? " (custom lobby)" : "")
                + (customArena ? " (custom arena: " + plugin.getConfig().getString("arena.template") + ")" : ""));
    }

    private World loadArenaWorld() {
        customArena = false;
        String mode = plugin.getConfig().getString("arena.mode", "procedural");
        String worldName = arenaWorldName();
        if ("custom".equalsIgnoreCase(mode)) {
            String template = plugin.getConfig().getString("arena.template", "");
            if (MapImporter.templateExists(plugin, template)) {
                try {
                    customArena = true;
                    return MapImporter.loadOrImport(plugin, worldName, template);
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to import arena map '" + template + "': " + e.getMessage());
                }
            } else {
                plugin.getLogger().warning("arena.template '" + template + "' not found — using procedural arena.");
            }
        }
        return ensureVoidWorld(worldName);
    }

    private String arenaWorldName() {
        String configured = plugin.getConfig().getString("arena.world", ARENA_WORLD);
        if (configured == null || configured.isBlank()) return ARENA_WORLD;
        return configured;
    }

    private void bindCustomArena(World world) {
        int radius = plugin.getConfig().getInt("arena.scan-chunk-radius", 20);
        ArenaMapScanner.Layout layout = ArenaMapScanner.scan(world, radius);
        applyArenaLayout(world, layout);
        for (TeamColor team : TeamColor.values()) {
            if (!teamBeds.containsKey(team)) {
                plugin.getLogger().warning("Custom arena: could not find " + team.id() + " bed — stand near it and use /bwc setbed "
                        + team.id().toLowerCase());
            }
        }
    }

    private void applyArenaLayout(World world, ArenaMapScanner.Layout layout) {
        teamSpawns.clear();
        teamBeds.clear();
        teamBedFacings.clear();
        teamGenerators.clear();
        globalGenerators.clear();
        teamSpawns.putAll(layout.spawns());
        teamBeds.putAll(layout.beds());
        teamBedFacings.putAll(layout.bedFacings());
        teamGenerators.putAll(layout.teamGenerators());
        globalGenerators.putAll(layout.globalGenerators());
        arenaWaitingSpawn = layout.waitingSpawn();
        if (plugin.getConfig().contains("arena.waiting.x")) {
            arenaWaitingSpawn = readArenaWaitingFromConfig(world);
        }
    }

    private Location readArenaWaitingFromConfig(World world) {
        double x = plugin.getConfig().getDouble("arena.waiting.x");
        double y = plugin.getConfig().getDouble("arena.waiting.y");
        double z = plugin.getConfig().getDouble("arena.waiting.z");
        float yaw = (float) plugin.getConfig().getDouble("arena.waiting.yaw");
        float pitch = (float) plugin.getConfig().getDouble("arena.waiting.pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    public void saveArenaWaitingHere(org.bukkit.entity.Player p) {
        Location loc = p.getLocation();
        plugin.getConfig().set("arena.waiting.x", loc.getX());
        plugin.getConfig().set("arena.waiting.y", loc.getY());
        plugin.getConfig().set("arena.waiting.z", loc.getZ());
        plugin.getConfig().set("arena.waiting.yaw", (double) loc.getYaw());
        plugin.getConfig().set("arena.waiting.pitch", (double) loc.getPitch());
        plugin.saveConfig();
        arenaWaitingSpawn = loc.clone();
        p.sendMessage("§a[BedWars.cash] Arena waiting spawn updated.");
    }

    public void saveTeamBedHere(org.bukkit.entity.Player p, TeamColor team) {
        Block target = p.getTargetBlockExact(6);
        if (target == null || !target.getType().name().endsWith("_BED")) {
            p.sendMessage("§cLook at a bed block (within 6 blocks).");
            return;
        }
        BlockFace facing = BlockFace.NORTH;
        if (target.getBlockData() instanceof Bed bed) {
            facing = bed.getFacing();
            if (bed.getPart() == Bed.Part.FOOT) {
                target = target.getRelative(bed.getFacing());
            }
        }
        Location head = target.getLocation();
        teamBeds.put(team, head);
        teamBedFacings.put(team, facing);
        BlockFace spawnDir = facing.getOppositeFace();
        Location spawn = head.clone().add(spawnDir.getModX() * 3.5, 1, spawnDir.getModZ() * 3.5);
        spawn.setYaw(yawForFacing(spawnDir));
        spawn.setPitch(0f);
        teamSpawns.put(team, spawn);
        p.sendMessage("§a[BedWars.cash] " + team.id() + " bed + spawn set.");
    }

    public boolean usesCustomArena() {
        return customArena;
    }

    private World loadLobbyWorld() {
        customLobby = false;
        String mode = plugin.getConfig().getString("lobby.mode", "procedural");
        String worldName = lobbyWorldName();
        if ("custom".equalsIgnoreCase(mode)) {
            String template = plugin.getConfig().getString("lobby.template", "");
            if (MapImporter.templateExists(plugin, template)) {
                try {
                    customLobby = true;
                    return MapImporter.loadOrImport(plugin, worldName, template);
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to import lobby map '" + template + "': " + e.getMessage());
                }
            } else {
                plugin.getLogger().warning("lobby.template '" + template + "' not found — using procedural lobby.");
            }
        }
        return ensureVoidWorld(worldName);
    }

    private World ensureVoidWorld(String name) {
        World existing = Bukkit.getWorld(name);
        if (existing != null) return existing;
        WorldCreator creator = new WorldCreator(name);
        creator.generator(new VoidGenerator());
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.NORMAL);
        return creator.createWorld();
    }

    private String lobbyWorldName() {
        String configured = plugin.getConfig().getString("lobby.world", LOBBY_WORLD);
        if (configured == null || configured.isBlank()) return LOBBY_WORLD;
        return configured;
    }

    private void loadLobbySpawnFromConfig(World world) {
        if (plugin.getConfig().contains("lobby.x")) {
            lobbySpawn = readSpawnFromConfig(world);
            return;
        }
        lobbySpawn = world.getSpawnLocation().clone();
        plugin.getConfig().set("lobby.world", world.getName());
        plugin.getConfig().set("lobby.x", lobbySpawn.getX());
        plugin.getConfig().set("lobby.y", lobbySpawn.getY());
        plugin.getConfig().set("lobby.z", lobbySpawn.getZ());
        plugin.getConfig().set("lobby.yaw", (double) lobbySpawn.getYaw());
        plugin.getConfig().set("lobby.pitch", (double) lobbySpawn.getPitch());
        plugin.saveConfig();
        plugin.getLogger().info("Lobby spawn set from world default — stand on spawn and run /bwc setlobby to refine.");
    }

    private Location readSpawnFromConfig(World world) {
        double x = plugin.getConfig().getDouble("lobby.x");
        double y = plugin.getConfig().getDouble("lobby.y");
        double z = plugin.getConfig().getDouble("lobby.z");
        float yaw = (float) plugin.getConfig().getDouble("lobby.yaw");
        float pitch = (float) plugin.getConfig().getDouble("lobby.pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    public boolean usesCustomLobby() {
        return customLobby;
    }

    private void applyLobbyRules(World world) {
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        world.getEntities().stream()
                .filter(e -> e instanceof org.bukkit.entity.Monster)
                .forEach(org.bukkit.entity.Entity::remove);
    }

    private boolean isBuilt(World world) {
        return world.getBlockAt(0, 64, 0).getType() == Material.EMERALD_BLOCK;
    }

    private void buildLobby(World world) {
        int radius = 30;
        int base = 65; // grass surface baseline
        java.util.Random rng = new java.util.Random(20240607L);

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius) continue;
                int surf = lobbySurfaceY(x, z, base);
                world.getBlockAt(x, surf, z).setType(Material.GRASS_BLOCK);
                world.getBlockAt(x, surf - 1, z).setType(Material.DIRT);
                world.getBlockAt(x, surf - 2, z).setType(Material.DIRT);
                world.getBlockAt(x, surf - 3, z).setType(Material.STONE);
                // rounded underside so the hub floats like an island
                int depth = 3 + (int) ((radius - dist) * 0.5);
                for (int h = 4; h <= depth; h++) world.getBlockAt(x, surf - h, z).setType(Material.STONE);
                // scattered flowers / grass away from the centre
                if (dist > 7 && rng.nextInt(8) == 0) {
                    Material deco = switch (rng.nextInt(6)) {
                        case 0 -> Material.POPPY;
                        case 1 -> Material.DANDELION;
                        case 2 -> Material.CORNFLOWER;
                        case 3 -> Material.AZURE_BLUET;
                        case 4 -> Material.OXEYE_DAISY;
                        default -> Material.SHORT_GRASS;
                    };
                    world.getBlockAt(x, surf + 1, z).setType(deco);
                }
            }
        }

        // foundation marker used by isBuilt()
        world.getBlockAt(0, base - 1, 0).setType(Material.EMERALD_BLOCK);

        // trees scattered around the ring (kept clear of the hub + betting pads)
        for (int i = 0; i < 18; i++) {
            int tx = rng.nextInt(radius * 2 + 1) - radius;
            int tz = rng.nextInt(radius * 2 + 1) - radius;
            double d = Math.sqrt(tx * tx + tz * tz);
            if (d < 10 || d > radius - 3 || nearLobbyPad(tx, tz)) continue;
            buildLobbyTree(world, tx, lobbySurfaceY(tx, tz, base) + 1, tz, rng);
        }

        buildLobbyHub(world, base);

        placeLobbyPad(world, TeamColor.GREEN, -18, base, -18);
        placeLobbyPad(world, TeamColor.BLUE, 18, base, -18);
        placeLobbyPad(world, TeamColor.RED, 18, base, 18);
        placeLobbyPad(world, TeamColor.YELLOW, -18, base, 18);

        lobbySpawn = new Location(world, 0.5, base + 1, 0.5, 0f, 0f);
        world.setSpawnLocation(lobbySpawn);
        plugin.getConfig().set("lobby.world", LOBBY_WORLD);
        plugin.getConfig().set("lobby.x", lobbySpawn.getX());
        plugin.getConfig().set("lobby.y", lobbySpawn.getY());
        plugin.getConfig().set("lobby.z", lobbySpawn.getZ());
        plugin.saveConfig();
    }

    private int lobbySurfaceY(int x, int z, int base) {
        double d = Math.sqrt(x * x + z * z);
        if (d < 8) return base; // flat around the hub + spawn
        double hills = 2.2 * Math.sin(x * 0.16) + 2.2 * Math.cos(z * 0.16) + 1.4 * Math.sin((x + z) * 0.09);
        return base + Math.max(0, (int) Math.round(hills));
    }

    private boolean nearLobbyPad(int x, int z) {
        int[][] pads = {{-18, -18}, {18, -18}, {18, 18}, {-18, 18}};
        for (int[] p : pads) {
            if (Math.abs(x - p[0]) <= 3 && Math.abs(z - p[1]) <= 3) return true;
        }
        return false;
    }

    private void buildLobbyHub(World world, int base) {
        // paved plaza around spawn
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                if (x * x + z * z > 40) continue;
                boolean rim = x * x + z * z >= 26;
                world.getBlockAt(x, base, z).setType(rim ? Material.CHISELED_STONE_BRICKS : Material.SMOOTH_STONE);
                for (int h = 1; h <= 4; h++) world.getBlockAt(x, base + h, z).setType(Material.AIR);
            }
        }
        // raised spawn dais
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) world.getBlockAt(x, base, z).setType(Material.QUARTZ_BLOCK);
        }
        world.getBlockAt(0, base, 0).setType(Material.SEA_LANTERN);
        // corner pillars with lanterns
        int[][] corners = {{-5, -5}, {5, -5}, {5, 5}, {-5, 5}};
        for (int[] c : corners) {
            for (int h = 1; h <= 3; h++) world.getBlockAt(c[0], base + h, c[1]).setType(Material.STONE_BRICKS);
            world.getBlockAt(c[0], base + 4, c[1]).setType(Material.SEA_LANTERN);
        }
        placeSign(world, 0, base + 1, -5, new String[]{
                "§b§lBEDWARS.CASH", "§7Ranked queue", "§fJoin = auto-queue", "§ebedwars.cash"
        });
    }

    private void buildLobbyTree(World world, int x, int surf, int z, java.util.Random rng) {
        boolean spruce = rng.nextBoolean();
        Material log = spruce ? Material.SPRUCE_LOG : Material.OAK_LOG;
        Material leaf = spruce ? Material.SPRUCE_LEAVES : Material.OAK_LEAVES;
        int h = 4 + rng.nextInt(3);
        for (int i = 0; i < h; i++) world.getBlockAt(x, surf + i, z).setType(log);
        for (int dy = h - 2; dy <= h + 1; dy++) {
            int r = dy >= h ? 1 : 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r + 1) continue;
                    if (dx == 0 && dz == 0 && dy < h) continue;
                    Block b = world.getBlockAt(x + dx, surf + dy, z + dz);
                    if (b.getType() == Material.AIR) b.setType(leaf);
                }
            }
        }
    }

    private void placeLobbyPad(World world, TeamColor team, int cx, int base, int cz) {
        int surf = lobbySurfaceY(cx, cz, base);
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                world.getBlockAt(x, surf, z).setType(team.wool());
                world.getBlockAt(x, surf - 1, z).setType(Material.DIRT);
                for (int y = surf + 1; y <= surf + 3; y++) world.getBlockAt(x, y, z).setType(Material.AIR);
            }
        }
        placeSign(world, cx, surf + 1, cz, new String[]{
                team.chat() + "§l" + team.id(), "§7Back this team", "§f/bet " + team.id().toLowerCase(), ""
        });
    }

    private void placeSign(World world, int x, int y, int z, String[] lines) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.OAK_SIGN);
        if (block.getState() instanceof Sign sign) {
            for (int i = 0; i < Math.min(4, lines.length); i++) sign.setLine(i, lines[i]);
            sign.update();
        }
    }

    public void buildArena(World world) {
        if (customArena) {
            prepareCustomArena(world);
            return;
        }
        teamSpawns.clear();
        teamBeds.clear();
        teamGenerators.clear();
        globalGenerators.clear();
        removeArenaEntities(world);
        clearArenaRegion(world);

        int y = 64;
        int dist = 50;
        buildIsland(world, TeamColor.GREEN, -dist, y, 0, BlockFace.EAST);
        buildIsland(world, TeamColor.RED, dist, y, 0, BlockFace.WEST);
        buildIsland(world, TeamColor.BLUE, 0, y, -dist, BlockFace.SOUTH);
        buildIsland(world, TeamColor.YELLOW, 0, y, dist, BlockFace.NORTH);

        buildBridges(world, y, dist);
        buildMid(world, y);
        buildGlobalGenerators(world, y);

        arenaWaitingSpawn = new Location(world, 0.5, y + 1, 0.5, 0f, 0f);
    }

    /** Reset a custom imported map between matches (keep terrain, restore beds). */
    private void prepareCustomArena(World world) {
        removeArenaEntities(world);
        ArenaBlockTracker.clearWorld(world);
        restoreAllTeamBeds(world);
    }

    private void restoreAllTeamBeds(World world) {
        for (TeamColor team : TeamColor.values()) {
            Location head = teamBeds.get(team);
            BlockFace facing = teamBedFacings.get(team);
            if (head == null || facing == null) continue;
            placeBed(world, team, head.getBlockX(), head.getBlockY(), head.getBlockZ(), facing);
        }
    }

    private void removeArenaEntities(World world) {
        removeShopVillagers(world);
        removeUpgradeVillagers(world);
        world.getEntities().forEach(e -> {
            if (e instanceof org.bukkit.entity.Item) e.remove();
        });
    }

    /** Wipe prior match blocks (beds, wool, player builds) before rebuilding the template. */
    private void clearArenaRegion(World world) {
        ArenaBlockTracker.clearWorld(world);
        int bound = 80;
        int minY = 40;
        int maxY = 100;
        for (int x = -bound; x <= bound; x++) {
            for (int z = -bound; z <= bound; z++) {
                for (int h = minY; h <= maxY; h++) {
                    world.getBlockAt(x, h, z).setType(Material.AIR);
                }
            }
        }
    }

    /** Remove player-placed blocks and entities without rebuilding islands (match end). */
    public void wipeArena(World world) {
        if (world == null) return;
        if (customArena) {
            prepareCustomArena(world);
            return;
        }
        removeArenaEntities(world);
        clearArenaRegion(world);
    }

    private void buildBridges(World world, int y, int dist) {
        // 3-wide quartz walkways from mid out to each island along the axes
        for (int i = 8; i <= dist - 8; i++) {
            for (int w = -1; w <= 1; w++) {
                Material mat = w == 0 ? Material.SMOOTH_QUARTZ : Material.QUARTZ_BLOCK;
                world.getBlockAt(i, y, w).setType(mat);
                world.getBlockAt(-i, y, w).setType(mat);
                world.getBlockAt(w, y, i).setType(mat);
                world.getBlockAt(w, y, -i).setType(mat);
            }
        }
    }

    private void buildMid(World world, int y) {
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 7.3) continue;
                boolean ring = d > 5.5;
                world.getBlockAt(x, y, z).setType(ring ? Material.CHISELED_QUARTZ_BLOCK : Material.SMOOTH_QUARTZ);
                world.getBlockAt(x, y - 1, z).setType(Material.QUARTZ_BLOCK);
                int depth = (int) ((7 - d) * 0.7);
                for (int h = 2; h <= depth + 1; h++) world.getBlockAt(x, y - h, z).setType(Material.END_STONE);
                world.getBlockAt(x, y - depth - 2, z).setType(Material.BEDROCK);
            }
        }
        // off-centre accent pillars (don't block the waiting spawn at 0,0)
        int[][] pillars = {{-5, -5}, {5, -5}, {5, 5}, {-5, 5}};
        for (int[] p : pillars) {
            world.getBlockAt(p[0], y + 1, p[1]).setType(Material.QUARTZ_PILLAR);
            world.getBlockAt(p[0], y + 2, p[1]).setType(Material.SEA_LANTERN);
        }
    }

    private void buildGlobalGenerators(World world, int y) {
        List<Location> diamonds = List.of(
                new Location(world, -24, y + 1, 0),
                new Location(world, 24, y + 1, 0),
                new Location(world, 0, y + 1, -24),
                new Location(world, 0, y + 1, 24)
        );
        List<Location> emeralds = List.of(
                new Location(world, -12, y + 1, -12),
                new Location(world, 12, y + 1, 12)
        );
        for (Location loc : diamonds) buildGenPedestal(world, loc, Material.DIAMOND_BLOCK, Material.LAPIS_BLOCK);
        for (Location loc : emeralds) {
            buildGenPlatform(world, loc, y); // emeralds sit off the axes — give them a small island
            buildGenPedestal(world, loc, Material.EMERALD_BLOCK, Material.MOSS_BLOCK);
        }
        globalGenerators.put(GeneratorType.DIAMOND, diamonds);
        globalGenerators.put(GeneratorType.EMERALD, emeralds);
    }

    private void buildGenPedestal(World world, Location gen, Material top, Material trim) {
        int x = gen.getBlockX(), y = gen.getBlockY(), z = gen.getBlockZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(x + dx, y - 1, z + dz).setType((dx == 0 || dz == 0) ? trim : Material.STONE_BRICKS);
            }
        }
        world.getBlockAt(x, y - 1, z).setType(Material.CHISELED_STONE_BRICKS);
        world.getBlockAt(x, y, z).setType(top); // the registered generator block
    }

    private void buildGenPlatform(World world, Location center, int y) {
        int cx = center.getBlockX(), cz = center.getBlockZ();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz <= 5) world.getBlockAt(cx + dx, y, cz + dz).setType(Material.SMOOTH_QUARTZ);
            }
        }
    }

    private void buildIsland(World world, TeamColor team, int cx, int y, int cz, BlockFace towardCenter) {
        int r = 9;
        int mx = towardCenter.getModX();
        int mz = towardCenter.getModZ();
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d > r + 0.4) continue;
                boolean accent = d > r - 2.5;
                world.getBlockAt(x, y, z).setType(accent ? team.wool() : Material.SMOOTH_SANDSTONE);
                world.getBlockAt(x, y - 1, z).setType(Material.SANDSTONE);
                int depth = (int) ((r - d) * 0.8);
                for (int h = 2; h <= depth + 1; h++) world.getBlockAt(x, y - h, z).setType(Material.END_STONE);
                world.getBlockAt(x, y - depth - 2, z).setType(Material.BEDROCK);
                // low rim wall for cover, with a doorway toward centre for the bridge
                if (d >= r - 0.6) {
                    int dot = (x - cx) * mx + (z - cz) * mz;
                    int perp = (x - cx) * (-mz) + (z - cz) * mx;
                    boolean opening = dot >= r - 1 && Math.abs(perp) <= 2;
                    if (!opening) world.getBlockAt(x, y + 1, z).setType(Material.SANDSTONE_WALL);
                }
            }
        }

        int spawnOffset = 4;
        int spawnX = cx + mx * spawnOffset;
        int spawnZ = cz + mz * spawnOffset;
        teamSpawns.put(team, new Location(world, spawnX + 0.5, y + 1, spawnZ + 0.5, yawForFacing(towardCenter), 0f));

        int bedX = cx - mx * 5;
        int bedZ = cz - mz * 5;
        buildBedAlcove(world, team, bedX, bedZ, y, towardCenter);
        Location bedHead = placeBed(world, team, bedX, y + 1, bedZ, towardCenter);
        teamBeds.put(team, bedHead);

        Location ironGen = new Location(world, cx + 3, y + 1, cz);
        Location goldGen = new Location(world, cx - 3, y + 1, cz);
        buildForge(world, ironGen, Material.IRON_BLOCK);
        buildForge(world, goldGen, Material.GOLD_BLOCK);
        EnumMap<GeneratorType, Location> gens = new EnumMap<>(GeneratorType.class);
        gens.put(GeneratorType.IRON, ironGen);
        gens.put(GeneratorType.GOLD, goldGen);
        teamGenerators.put(team, gens);
    }

    private void buildBedAlcove(World world, TeamColor team, int bedX, int bedZ, int y, BlockFace facing) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(bedX + dx, y, bedZ + dz).setType(Material.SMOOTH_SANDSTONE);
                for (int h = 1; h <= 3; h++) world.getBlockAt(bedX + dx, y + h, bedZ + dz).setType(Material.AIR);
            }
        }
        world.getBlockAt(bedX, y + 1, bedZ).setType(team.wool());
        // three walls + slab roof; the opening faces the island interior (towards centre)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue;
                if (facing.getModX() != 0 && dx == facing.getModX() * 2) continue;
                if (facing.getModZ() != 0 && dz == facing.getModZ() * 2) continue;
                for (int h = 1; h <= 2; h++) world.getBlockAt(bedX + dx, y + h, bedZ + dz).setType(team.wool());
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) world.getBlockAt(bedX + dx, y + 3, bedZ + dz).setType(Material.SANDSTONE_SLAB);
        }
    }

    private void buildForge(World world, Location gen, Material top) {
        int x = gen.getBlockX(), y = gen.getBlockY(), z = gen.getBlockZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.CHISELED_STONE_BRICKS);
            }
        }
        world.getBlockAt(x, y - 1, z).setType(Material.STONE_BRICKS);
        world.getBlockAt(x, y, z).setType(top); // registered generator block
    }

    private float yawForFacing(BlockFace face) {
        return switch (face) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> 0f;
        };
    }

    private Location placeBed(World world, TeamColor team, int x, int y, int z, BlockFace facing) {
        Block headBlock = world.getBlockAt(x, y, z);
        clearBedBlocksNear(headBlock);
        headBlock.setType(team.bed());
        if (headBlock.getBlockData() instanceof Bed bed) {
            bed.setFacing(facing);
            bed.setPart(Bed.Part.HEAD);
            headBlock.setBlockData(bed);
            Block foot = headBlock.getRelative(facing.getOppositeFace());
            foot.setType(Material.AIR);
            foot.setType(team.bed());
            if (foot.getBlockData() instanceof Bed footBed) {
                footBed.setFacing(facing);
                footBed.setPart(Bed.Part.FOOT);
                foot.setBlockData(footBed);
            }
        }
        return headBlock.getLocation();
    }

    private void clearBedBlocksNear(Block center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block block = center.getRelative(dx, dy, dz);
                    if (!block.getType().name().endsWith("_BED")) continue;
                    if (block.getBlockData() instanceof Bed bed) {
                        Block partner = block.getRelative(
                                bed.getPart() == Bed.Part.HEAD
                                        ? bed.getFacing().getOppositeFace()
                                        : bed.getFacing());
                        partner.setType(Material.AIR);
                    }
                    block.setType(Material.AIR);
                }
            }
        }
    }

    public List<Location> globalGenerators(GeneratorType type) {
        return globalGenerators.getOrDefault(type, List.of());
    }

    public Location lobbySpawn() {
        if (lobbySpawn != null) return lobbySpawn;
        World w = lobbyWorld();
        if (w == null) return null;
        if (plugin.getConfig().contains("lobby.x")) {
            lobbySpawn = readSpawnFromConfig(w);
            return lobbySpawn;
        }
        return new Location(w, 0.5, 66, 0.5);
    }

    public World lobbyWorld() {
        World w = Bukkit.getWorld(lobbyWorldName());
        return w != null ? w : Bukkit.getWorld(LOBBY_WORLD);
    }
    public World arenaWorld() {
        World w = Bukkit.getWorld(arenaWorldName());
        return w != null ? w : Bukkit.getWorld(ARENA_WORLD);
    }
    public Location teamSpawn(TeamColor team) { return teamSpawns.get(team); }
    public Location teamBed(TeamColor team) { return teamBeds.get(team); }

    public Location teamGenerator(TeamColor team, GeneratorType type) {
        EnumMap<GeneratorType, Location> gens = teamGenerators.get(team);
        return gens == null ? null : gens.get(type);
    }

    public void setLobbySpawn(Location loc) {
        if (loc != null) lobbySpawn = loc.clone();
    }

    public Location arenaWaitingSpawn() { return arenaWaitingSpawn; }

    /** Hypixel-style shop NPC at each team island. */
    public void spawnShopVillagers() {
        World world = arenaWorld();
        if (world == null) return;
        removeShopVillagers(world);
        for (TeamColor team : TeamColor.values()) {
            Location spawn = teamSpawns.get(team);
            if (spawn == null) continue;
            Location at = spawn.clone().add(spawn.getDirection().setY(0).normalize().multiply(-2));
            at.setY(spawn.getY());
            world.spawn(at, Villager.class, v -> {
                v.setAI(false);
                v.setInvulnerable(true);
                v.setSilent(true);
                v.setCollidable(false);
                v.setRemoveWhenFarAway(false);
                v.setProfession(Villager.Profession.WEAPONSMITH);
                v.customName(Component.text("§a§lITEM SHOP"));
                v.setCustomNameVisible(true);
                v.getPersistentDataContainer().set(ShopAccess.shopNpcKey(), PersistentDataType.BYTE, (byte) 1);
            });
        }
    }

    public void removeShopVillagers(World world) {
        world.getEntities().stream()
                .filter(e -> e instanceof Villager)
                .filter(e -> e.getPersistentDataContainer().has(ShopAccess.shopNpcKey(), PersistentDataType.BYTE))
                .forEach(org.bukkit.entity.Entity::remove);
    }

    /** Team upgrade NPC beside each island item shop. */
    public void spawnUpgradeVillagers() {
        World world = arenaWorld();
        if (world == null) return;
        removeUpgradeVillagers(world);
        for (TeamColor team : TeamColor.values()) {
            Location spawn = teamSpawns.get(team);
            if (spawn == null) continue;
            Vector back = spawn.getDirection().setY(0).normalize().multiply(-2);
            Vector side = new Vector(-back.getZ(), 0, back.getX()).normalize().multiply(1.5);
            Location at = spawn.clone().add(back).add(side);
            at.setY(spawn.getY());
            world.spawn(at, Villager.class, v -> {
                v.setAI(false);
                v.setInvulnerable(true);
                v.setSilent(true);
                v.setCollidable(false);
                v.setRemoveWhenFarAway(false);
                v.setProfession(Villager.Profession.LIBRARIAN);
                v.customName(Component.text("§6§lUPGRADES"));
                v.setCustomNameVisible(true);
                v.getPersistentDataContainer().set(UpgradeAccess.upgradeNpcKey(), PersistentDataType.BYTE, (byte) 1);
            });
        }
    }

    public void removeUpgradeVillagers(World world) {
        world.getEntities().stream()
                .filter(e -> e instanceof Villager)
                .filter(e -> e.getPersistentDataContainer().has(UpgradeAccess.upgradeNpcKey(), PersistentDataType.BYTE))
                .forEach(org.bukkit.entity.Entity::remove);
    }

    public void rebuildLobbyIfNeeded() {
        // The grassy hub island is fully built when the lobby world is first created;
        // there are no barrier walls to patch (players who fall are caught by the
        // lobby containment check and teleported back to spawn).
    }

    public boolean isInsideLobbyPlatform(Location loc) {
        if (!isLobbyWorld(loc.getWorld())) return false;
        if (customLobby) {
            int minY = plugin.getConfig().getInt("lobby.min-y", 50);
            return loc.getY() >= minY;
        }
        double dist = Math.sqrt(loc.getX() * loc.getX() + loc.getZ() * loc.getZ());
        return loc.getY() >= 55 && loc.getY() <= 95 && dist <= 34;
    }

    public boolean isLobbyWorld(World world) {
        if (world == null) return false;
        return lobbyWorldName().equals(world.getName());
    }

    public boolean isArenaWorld(World world) {
        return world != null && arenaWorldName().equals(world.getName());
    }

    /** Peaceful between matches; normal during live combat so void/PvP deaths work. */
    public void setArenaMatchDifficulty(boolean combat) {
        World arena = arenaWorld();
        if (arena == null) return;
        arena.setDifficulty(combat ? Difficulty.NORMAL : Difficulty.PEACEFUL);
    }
}
