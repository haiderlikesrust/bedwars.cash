package cash.bedwars.listeners;

import cash.bedwars.BackendClient;
import cash.bedwars.BedWarsCashPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Lightweight clicks-per-second check. GrimAC handles movement/combat; this fills
// the autoclicker gap GrimAC does not cover and reports flags to the backend.
public class CombatListener implements Listener {
    private final BedWarsCashPlugin plugin;
    private final BackendClient backend;
    private final int maxCps;

    private final Map<UUID, Deque<Long>> swings = new HashMap<>();
    private final Map<UUID, Long> lastFlag = new HashMap<>();

    public CombatListener(BedWarsCashPlugin plugin, BackendClient backend) {
        this.plugin = plugin;
        this.backend = backend;
        this.maxCps = plugin.getConfig().getInt("anticheat.max-cps", 20);
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        Deque<Long> window = swings.computeIfAbsent(id, k -> new ArrayDeque<>());
        window.addLast(now);
        while (!window.isEmpty() && now - window.peekFirst() > 1000L) window.pollFirst();

        if (window.size() > maxCps) {
            long last = lastFlag.getOrDefault(id, 0L);
            if (now - last > 10_000L) { // rate-limit flags to once per 10s per player
                lastFlag.put(id, now);
                backend.cheatFlag(p, "autoclicker", "cps=" + window.size() + " (limit " + maxCps + ")");
                plugin.getLogger().warning("[anticheat] " + p.getName() + " flagged: " + window.size() + " CPS");
            }
        }
    }
}
