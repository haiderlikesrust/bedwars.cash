package cash.bedwars.listeners;

import cash.bedwars.BackendClient;
import cash.bedwars.BedWarsCashPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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
        // Show a level badge right away; the backend refreshes it via a progression message.
        plugin.cosmetics().applyTab(event.getPlayer());
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

    // Remove disconnecting players from the queue so the count never shows phantoms.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        backend.queueLeave(event.getPlayer());
        plugin.cosmetics().forget(event.getPlayer().getUniqueId());
        plugin.quests().forget(event.getPlayer().getUniqueId());
    }
}
