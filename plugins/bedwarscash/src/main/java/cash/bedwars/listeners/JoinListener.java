package cash.bedwars.listeners;

import cash.bedwars.BackendClient;
import cash.bedwars.BedWarsCashPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

// Auto-queue on join (lobby phase) or spectator mode (live match).
public class JoinListener implements Listener {
    private final BedWarsCashPlugin plugin;
    private final BackendClient backend;

    public JoinListener(BedWarsCashPlugin plugin, BackendClient backend) {
        this.plugin = plugin;
        this.backend = backend;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.broadcast().shouldSkipJoinFlow(event.getPlayer())) {
            plugin.getServer().getScheduler().runTaskLater(
                    plugin,
                    () -> plugin.broadcast().onCastJoin(event.getPlayer()),
                    plugin.getConfig().getInt("join.delay-ticks", 20)
            );
            return;
        }
        if (!plugin.getConfig().getBoolean("join.auto-queue", true)) return;
        int delayTicks = plugin.getConfig().getInt("join.delay-ticks", 20);
        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> backend.playerJoin(event.getPlayer()),
                delayTicks
        );
    }
}
