package cash.bedwars.listeners;

import cash.bedwars.BedWarsCashPlugin;
import cash.bedwars.LobbyService;
import cash.bedwars.world.WorldManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.Location;

public class LobbyListener implements Listener {
    private final BedWarsCashPlugin plugin;
    private final LobbyService lobby;
    private final WorldManager worlds;

    public LobbyListener(BedWarsCashPlugin plugin, LobbyService lobby, WorldManager worlds) {
        this.plugin = plugin;
        this.lobby = lobby;
        this.worlds = worlds;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> lobby.enterLobby(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (lobby.isLobbyPlayer(p)) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                rescueFromVoid(p);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        Player p = event.getPlayer();
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        if (!worlds.isLobbyWorld(p.getWorld())) return;
        if (plugin.game().isLive()) return;

        Location to = event.getTo();
        if (to.getY() < 60 || !worlds.isInsideLobbyPlatform(to)) {
            rescueFromVoid(p);
        }
    }

    private void rescueFromVoid(Player p) {
        Location spawn = worlds.lobbySpawn();
        if (spawn != null) {
            p.teleport(spawn);
            p.setFallDistance(0);
            p.setVelocity(p.getVelocity().zero());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (lobby.isLobbyPlayer(p)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (lobby.isLobbyPlayer(event.getPlayer()) && !event.getPlayer().isOp()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (lobby.isLobbyPlayer(event.getPlayer()) && !event.getPlayer().isOp()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (worlds.isLobbyWorld(event.getLocation().getWorld()) && event.getEntity() instanceof Monster) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        if (!plugin.game().isLive()) {
            var spawn = worlds.lobbySpawn();
            if (spawn != null) event.setRespawnLocation(spawn);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> lobby.enterLobby(p), 1L);
        }
    }
}
