package cash.bedwars.world;

import cash.bedwars.game.GeneratorType;
import cash.bedwars.game.TeamColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;

import java.util.*;

/** Finds beds, spawns, and generator blocks on imported BedWars arena maps. */
public final class ArenaMapScanner {
    public record Layout(
            Map<TeamColor, Location> spawns,
            Map<TeamColor, Location> beds,
            Map<TeamColor, BlockFace> bedFacings,
            Map<TeamColor, EnumMap<GeneratorType, Location>> teamGenerators,
            Map<GeneratorType, List<Location>> globalGenerators,
            Location waitingSpawn
    ) {}

    private ArenaMapScanner() {}

    public static Layout scan(World world, int chunkRadius) {
        Location origin = world.getSpawnLocation();
        int centerCx = origin.getBlockX() >> 4;
        int centerCz = origin.getBlockZ() >> 4;

        Map<TeamColor, Location> beds = new EnumMap<>(TeamColor.class);
        Map<TeamColor, BlockFace> facings = new EnumMap<>(TeamColor.class);
        List<Location> ironBlocks = new ArrayList<>();
        List<Location> goldBlocks = new ArrayList<>();
        List<Location> diamondBlocks = new ArrayList<>();
        List<Location> emeraldBlocks = new ArrayList<>();

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                Chunk chunk = world.getChunkAt(centerCx + dx, centerCz + dz);
                if (!chunk.isLoaded()) chunk.load(true);
                scanChunk(chunk, beds, facings, ironBlocks, goldBlocks, diamondBlocks, emeraldBlocks);
            }
        }

        Map<TeamColor, Location> spawns = new EnumMap<>(TeamColor.class);
        Map<TeamColor, EnumMap<GeneratorType, Location>> teamGens = new EnumMap<>(TeamColor.class);
        for (TeamColor team : TeamColor.values()) {
            Location bed = beds.get(team);
            if (bed == null) continue;
            BlockFace facing = facings.getOrDefault(team, BlockFace.NORTH);
            BlockFace spawnDir = facing.getOppositeFace();
            Location spawn = bed.clone().add(
                    spawnDir.getModX() * 3.5,
                    1,
                    spawnDir.getModZ() * 3.5
            );
            spawn.setYaw(yawForFacing(spawnDir));
            spawn.setPitch(0f);
            spawns.put(team, spawn);

            EnumMap<GeneratorType, Location> gens = new EnumMap<>(GeneratorType.class);
            gens.put(GeneratorType.IRON, nearest(ironBlocks, bed, 40));
            gens.put(GeneratorType.GOLD, nearest(goldBlocks, bed, 40));
            teamGens.put(team, gens);
        }

        Map<GeneratorType, List<Location>> global = new EnumMap<>(GeneratorType.class);
        global.put(GeneratorType.DIAMOND, diamondBlocks);
        global.put(GeneratorType.EMERALD, emeraldBlocks);

        Location waiting = origin.clone().add(0.5, 1, 0.5);
        waiting.setX(origin.getX() + 0.5);
        waiting.setZ(origin.getZ() + 0.5);

        return new Layout(spawns, beds, facings, teamGens, global, waiting);
    }

    private static void scanChunk(
            Chunk chunk,
            Map<TeamColor, Location> beds,
            Map<TeamColor, BlockFace> facings,
            List<Location> ironBlocks,
            List<Location> goldBlocks,
            List<Location> diamondBlocks,
            List<Location> emeraldBlocks
    ) {
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    Block block = world.getBlockAt(baseX + x, y, baseZ + z);
                    Material type = block.getType();
                    switch (type) {
                        case IRON_BLOCK -> ironBlocks.add(block.getLocation());
                        case GOLD_BLOCK -> goldBlocks.add(block.getLocation());
                        case DIAMOND_BLOCK -> diamondBlocks.add(block.getLocation());
                        case EMERALD_BLOCK -> emeraldBlocks.add(block.getLocation());
                        default -> {
                            if (!type.name().endsWith("_BED")) break;
                            if (!(block.getBlockData() instanceof Bed bed) || bed.getPart() != Bed.Part.HEAD) break;
                            TeamColor team = teamForBed(type);
                            if (team == null || beds.containsKey(team)) break;
                            beds.put(team, block.getLocation());
                            facings.put(team, bed.getFacing());
                        }
                    }
                }
            }
        }
    }

    private static TeamColor teamForBed(Material bed) {
        for (TeamColor team : TeamColor.values()) {
            if (team.bed() == bed) return team;
        }
        return null;
    }

    private static Location nearest(List<Location> points, Location from, double maxDist) {
        Location best = null;
        double bestD = maxDist * maxDist;
        for (Location loc : points) {
            if (!loc.getWorld().equals(from.getWorld())) continue;
            double d = loc.distanceSquared(from);
            if (d < bestD) {
                bestD = d;
                best = loc;
            }
        }
        return best;
    }

    private static float yawForFacing(BlockFace face) {
        return switch (face) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> 0f;
        };
    }
}
