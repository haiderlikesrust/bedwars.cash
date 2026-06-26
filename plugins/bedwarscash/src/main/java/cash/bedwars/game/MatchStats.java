package cash.bedwars.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Kill / bed-break / death stats for the active match. */
public final class MatchStats {
    private final Map<UUID, int[]> stats = new HashMap<>();

    public void clear() {
        stats.clear();
    }

    public void addKill(UUID uuid) {
        bump(uuid, 0);
    }

    public void addFinalKill(UUID uuid) {
        bump(uuid, 0);
        bump(uuid, 1);
    }

    public void addBedBreak(UUID uuid) {
        bump(uuid, 2);
    }

    public void addDeath(UUID uuid) {
        bump(uuid, 3);
    }

    public int kills(UUID uuid) { return get(uuid, 0); }
    public int finalKills(UUID uuid) { return get(uuid, 1); }
    public int beds(UUID uuid) { return get(uuid, 2); }
    public int deaths(UUID uuid) { return get(uuid, 3); }

    public UUID topKiller() {
        return topKiller(stats);
    }

    public static UUID topKiller(Map<UUID, int[]> snap) {
        UUID best = null;
        int bestK = -1;
        for (Map.Entry<UUID, int[]> e : snap.entrySet()) {
            if (e.getValue()[0] > bestK) {
                bestK = e.getValue()[0];
                best = e.getKey();
            }
        }
        return best;
    }

    /** Copy of in-match stats before the match state is cleared. */
    public Map<UUID, int[]> snapshot() {
        Map<UUID, int[]> copy = new HashMap<>();
        for (Map.Entry<UUID, int[]> e : stats.entrySet()) {
            copy.put(e.getKey(), e.getValue().clone());
        }
        return copy;
    }

    private void bump(UUID uuid, int idx) {
        stats.computeIfAbsent(uuid, k -> new int[4])[idx]++;
    }

    private int get(UUID uuid, int idx) {
        int[] arr = stats.get(uuid);
        return arr == null ? 0 : arr[idx];
    }
}
