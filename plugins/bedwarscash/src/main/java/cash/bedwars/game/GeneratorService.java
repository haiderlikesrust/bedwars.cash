package cash.bedwars.game;

import cash.bedwars.BedWarsCashPlugin;
import cash.bedwars.world.WorldManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

/** Iron/gold per team + diamond/emerald at map generators (Hypixel 4v4 timings). */
public class GeneratorService {
    private final BedWarsCashPlugin plugin;
    private final GameManager game;
    private final WorldManager worlds;
    private final Map<GeneratorType, Integer> tickCounters = new EnumMap<>(GeneratorType.class);
    private final Map<String, Integer> nearbyCounts = new HashMap<>();
    private int taskId = -1;

    public GeneratorService(BedWarsCashPlugin plugin, GameManager game, WorldManager worlds) {
        this.plugin = plugin;
        this.game = game;
        this.worlds = worlds;
        for (GeneratorType t : GeneratorType.values()) tickCounters.put(t, 0);
    }

    public void start() {
        stop();
        nearbyCounts.clear();
        for (GeneratorType t : GeneratorType.values()) tickCounters.put(t, 0);
        taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 20L, 1L);
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void tick() {
        if (!game.isLive()) return;
        World arena = worlds.arenaWorld();
        if (arena == null) return;

        for (GeneratorType type : GeneratorType.values()) {
            int left = tickCounters.get(type) - 1;
            if (left > 0) {
                tickCounters.put(type, left);
                continue;
            }
            tickCounters.put(type, minIntervalForType(type));
            if (type.perTeam()) {
                for (TeamColor team : TeamColor.values()) {
                    Location loc = worlds.teamGenerator(team, type);
                    if (loc != null) tryDrop(arena, loc, type, team);
                }
            } else {
                for (Location loc : worlds.globalGenerators(type)) {
                    tryDrop(arena, loc, type, null);
                }
            }
        }
    }

    private void tryDrop(World world, Location loc, GeneratorType type, TeamColor team) {
        String key = key(loc, type);
        int nearby = countNearby(world, loc, type.drop());
        if (nearby >= type.stackLimit()) return;
        dropItem(world, loc, new ItemStack(type.drop(), 1));
        nearbyCounts.put(key, nearby + 1);
    }

    private int intervalTicks(GeneratorType type, TeamColor team) {
        int base = type.intervalTicks();
        if (team == null || (type != GeneratorType.IRON && type != GeneratorType.GOLD)) return base;
        int forge = game.upgrades().forge(team);
        return Math.max(5, base - forge * 2);
    }

    private int minIntervalForType(GeneratorType type) {
        if (type != GeneratorType.IRON && type != GeneratorType.GOLD) return type.intervalTicks();
        int min = type.intervalTicks();
        for (TeamColor team : TeamColor.values()) {
            min = Math.min(min, intervalTicks(type, team));
        }
        return min;
    }

    private int countNearby(World world, Location loc, Material mat) {
        int count = 0;
        Location center = loc.clone().add(0.5, 1, 0.5);
        for (Item item : world.getNearbyEntities(center, 2, 2, 2, e -> e instanceof Item)
                .stream().map(e -> (Item) e).toList()) {
            if (item.getItemStack().getType() == mat) count += item.getItemStack().getAmount();
        }
        return count;
    }

    private String key(Location loc, GeneratorType type) {
        return type.name() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private void dropItem(World world, Location loc, ItemStack stack) {
        Location at = loc.clone().add(0.5, 1.05, 0.5);
        Item item = world.dropItem(at, stack);
        item.setVelocity(new Vector(0, 0.02, 0));
        item.setPickupDelay(0);
    }
}
