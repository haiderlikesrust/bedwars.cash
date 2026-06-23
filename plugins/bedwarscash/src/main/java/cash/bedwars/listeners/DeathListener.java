package cash.bedwars.listeners;

import cash.bedwars.BedWarsCashPlugin;
import cash.bedwars.game.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Ensures eliminated fighters enter spectator after respawn completes. */
public class DeathListener implements Listener {
    private final BedWarsCashPlugin plugin;

    public DeathListener(BedWarsCashPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        GameManager game = plugin.game();
        if (!game.isLive() || !game.isMatchPlayer(p)) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            if (game.shouldEnterSpectator(p.getUniqueId())) {
                game.applySpectatorAfterRespawn(p);
            }
        }, 2L);
    }
}
