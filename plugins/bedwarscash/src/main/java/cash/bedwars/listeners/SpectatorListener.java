package cash.bedwars.listeners;

import cash.bedwars.SpectatorHelper;
import cash.bedwars.world.WorldManager;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

public class SpectatorListener implements Listener {
    private final WorldManager worlds;

    public SpectatorListener(WorldManager worlds) {
        this.worlds = worlds;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        SpectatorHelper.onPlayerJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventory(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player p && SpectatorHelper.isSpectator(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        SpectatorHelper.leave(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && SpectatorHelper.isSpectator(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (SpectatorHelper.isSpectator(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (SpectatorHelper.isSpectator(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (SpectatorHelper.isSpectator(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (SpectatorHelper.isSpectator(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (SpectatorHelper.isSpectator(p)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!SpectatorHelper.isSpectator(p)) return;
        if (worlds.isArenaWorld(p.getWorld()) || worlds.isLobbyWorld(p.getWorld())) {
            p.setAllowFlight(true);
            if (!p.isFlying() && p.getGameMode() == GameMode.ADVENTURE) p.setFlying(true);
        }
    }
}
