package cash.bedwars.game;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks player-placed arena blocks so they can be removed between matches. */
public final class ArenaBlockTracker {
    private static final Set<String> PLACED = ConcurrentHashMap.newKeySet();

    private ArenaBlockTracker() {}

    public static void track(Block block) {
        PLACED.add(key(block));
    }

    public static void untrack(Block block) {
        PLACED.remove(key(block));
    }

    public static boolean isTracked(Block block) {
        return PLACED.contains(key(block));
    }

    public static void clearWorld(World world) {
        String prefix = world.getName() + ":";
        Iterator<String> it = PLACED.iterator();
        while (it.hasNext()) {
            String entry = it.next();
            if (!entry.startsWith(prefix)) continue;
            String[] parts = entry.split(":");
            if (parts.length != 4) {
                it.remove();
                continue;
            }
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type != Material.AIR && type != Material.VOID_AIR && type != Material.CAVE_AIR) {
                block.setType(Material.AIR);
            }
            it.remove();
        }
    }

    public static void clearAll() {
        PLACED.clear();
    }

    private static String key(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
