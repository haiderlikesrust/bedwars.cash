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

    public WorldManager(BedWarsCashPlugin plugin) {
        this.plugin = plugin;
    }

    public void bootstrap() {
        World lobby = loadLobbyWorld();
        World arena = ensureVoidWorld(ARENA_WORLD);
        if (lobby != null) {
            applyLobbyRules(lobby);
            if (!customLobby && !isBuilt(lobby)) buildLobby(lobby);
            else loadLobbySpawnFromConfig(lobby);
        }
        if (arena != null) {
            applyLobbyRules(arena);
            buildArena(arena);
        }
        plugin.getLogger().info("Worlds ready: " + lobbyWorldName() + ", " + ARENA_WORLD
                + (customLobby ? " (custom lobby map)" : ""));
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
        int radius = 28;
        int y = 65;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > radius) continue;
                world.getBlockAt(x, y, z).setType(Material.LIGHT_GRAY_STAINED_GLASS);
                world.getBlockAt(x, y - 1, z).setType(Material.BARRIER);
                if (dist >= radius - 1) {
                    for (int h = 1; h <= 3; h++) world.getBlockAt(x, y + h, z).setType(Material.BARRIER);
                }
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) world.getBlockAt(x, y, z).setType(Material.QUARTZ_BLOCK);
        }
        world.getBlockAt(0, 64, 0).setType(Material.EMERALD_BLOCK);
        world.getBlockAt(0, y + 1, 0).setType(Material.SEA_LANTERN);

        placeTeamPad(world, TeamColor.GREEN, -18, y, -18);
        placeTeamPad(world, TeamColor.BLUE, 18, y, -18);
        placeTeamPad(world, TeamColor.RED, 18, y, 18);
        placeTeamPad(world, TeamColor.YELLOW, -18, y, 18);

        placeSign(world, 0, y + 1, -6, new String[]{
                "§b§lBEDWARS.CASH", "§7Ranked queue", "§fJoin = auto-queue", "§ebedwars.cash"
        });

        lobbySpawn = new Location(world, 0.5, y + 1, 0.5, 0f, 0f);
        world.setSpawnLocation(lobbySpawn);
        plugin.getConfig().set("lobby.world", LOBBY_WORLD);
        plugin.getConfig().set("lobby.x", lobbySpawn.getX());
        plugin.getConfig().set("lobby.y", lobbySpawn.getY());
        plugin.getConfig().set("lobby.z", lobbySpawn.getZ());
        plugin.saveConfig();
    }

    private void placeTeamPad(World world, TeamColor team, int cx, int y, int cz) {
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) world.getBlockAt(x, y, z).setType(team.wool());
        }
        placeSign(world, cx, y + 1, cz, new String[]{
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

        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) world.getBlockAt(x, y + 1, z).setType(Material.QUARTZ_BLOCK);
        }
        arenaWaitingSpawn = new Location(world, 0.5, y + 2, 0.5, 0f, 0f);
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
        removeArenaEntities(world);
        clearArenaRegion(world);
    }

    private void buildBridges(World world, int y, int dist) {
        for (int i = -8; i <= 8; i++) {
            world.getBlockAt(i, y, -dist + 10).setType(Material.WHITE_WOOL);
            world.getBlockAt(i, y, dist - 10).setType(Material.WHITE_WOOL);
            world.getBlockAt(-dist + 10, y, i).setType(Material.WHITE_WOOL);
            world.getBlockAt(dist - 10, y, i).setType(Material.WHITE_WOOL);
        }
    }

    private void buildMid(World world, int y) {
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                if (Math.abs(x) <= 2 && Math.abs(z) <= 2) {
                    world.getBlockAt(x, y, z).setType(Material.OBSIDIAN);
                } else {
                    world.getBlockAt(x, y, z).setType(Material.WHITE_WOOL);
                }
            }
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
        for (Location loc : diamonds) world.getBlockAt(loc).setType(Material.DIAMOND_BLOCK);
        for (Location loc : emeralds) world.getBlockAt(loc).setType(Material.EMERALD_BLOCK);
        globalGenerators.put(GeneratorType.DIAMOND, diamonds);
        globalGenerators.put(GeneratorType.EMERALD, emeralds);
    }

    private void buildIsland(World world, TeamColor team, int cx, int y, int cz, BlockFace towardCenter) {
        int r = 10;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (Math.abs(x - cx) > r || Math.abs(z - cz) > r) continue;
                world.getBlockAt(x, y - 1, z).setType(Material.BEDROCK);
                world.getBlockAt(x, y, z).setType(team.wool());
            }
        }

        BlockFace bedFacing = towardCenter;
        int spawnOffset = 4;
        int spawnX = cx + bedFacing.getModX() * spawnOffset;
        int spawnZ = cz + bedFacing.getModZ() * spawnOffset;
        float yaw = yawForFacing(bedFacing);
        teamSpawns.put(team, new Location(world, spawnX + 0.5, y + 1, spawnZ + 0.5, yaw, 0f));

        int bedX = cx - bedFacing.getModX() * 5;
        int bedZ = cz - bedFacing.getModZ() * 5;
        // Small bed room: one wool pedestal + single bed on top
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(bedX + dx, y, bedZ + dz).setType(Material.BEDROCK);
                world.getBlockAt(bedX + dx, y + 1, bedZ + dz).setType(Material.AIR);
                world.getBlockAt(bedX + dx, y + 2, bedZ + dz).setType(Material.AIR);
            }
        }
        world.getBlockAt(bedX, y + 1, bedZ).setType(team.wool());
        Location bedHead = placeBed(world, team, bedX, y + 1, bedZ, bedFacing);
        teamBeds.put(team, bedHead);

        Location ironGen = new Location(world, cx + 3, y + 1, cz);
        Location goldGen = new Location(world, cx - 3, y + 1, cz);
        world.getBlockAt(ironGen).setType(Material.IRON_BLOCK);
        world.getBlockAt(goldGen).setType(Material.GOLD_BLOCK);
        EnumMap<GeneratorType, Location> gens = new EnumMap<>(GeneratorType.class);
        gens.put(GeneratorType.IRON, ironGen);
        gens.put(GeneratorType.GOLD, goldGen);
        teamGenerators.put(team, gens);
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
    public World arenaWorld() { return Bukkit.getWorld(ARENA_WORLD); }
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
        if (customLobby) return;
        World lobby = lobbyWorld();
        if (lobby == null) return;
        if (lobby.getBlockAt(27, 68, 0).getType() != Material.BARRIER) patchLobbyWalls(lobby);
    }

    private void patchLobbyWalls(World world) {
        int radius = 28;
        int y = 65;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist < radius - 1 || dist > radius) continue;
                for (int h = 1; h <= 3; h++) world.getBlockAt(x, y + h, z).setType(Material.BARRIER);
            }
        }
    }

    public boolean isInsideLobbyPlatform(Location loc) {
        if (!isLobbyWorld(loc.getWorld())) return false;
        if (customLobby) {
            int minY = plugin.getConfig().getInt("lobby.min-y", 50);
            return loc.getY() >= minY;
        }
        double dist = Math.sqrt(loc.getX() * loc.getX() + loc.getZ() * loc.getZ());
        return loc.getY() >= 64 && loc.getY() <= 70 && dist <= 26;
    }

    public boolean isLobbyWorld(World world) {
        if (world == null) return false;
        return lobbyWorldName().equals(world.getName());
    }

    public boolean isArenaWorld(World world) {
        return world != null && ARENA_WORLD.equals(world.getName());
    }

    /** Peaceful between matches; normal during live combat so void/PvP deaths work. */
    public void setArenaMatchDifficulty(boolean combat) {
        World arena = arenaWorld();
        if (arena == null) return;
        arena.setDifficulty(combat ? Difficulty.NORMAL : Difficulty.PEACEFUL);
    }
}
