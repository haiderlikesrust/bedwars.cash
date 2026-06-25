package cash.bedwars.game;

import cash.bedwars.game.shop.ShopAccess;
import cash.bedwars.game.shop.ShopInventoryHolder;
import cash.bedwars.game.shop.ShopService;
import cash.bedwars.game.upgrades.UpgradeAccess;
import cash.bedwars.game.upgrades.UpgradeShopService;
import cash.bedwars.world.WorldManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;

public class GameListener implements Listener {
    private final JavaPlugin plugin;
    private final GameManager game;
    private final WorldManager worlds;
    private final ShopService shop;
    private final UpgradeShopService upgrades;

    public GameListener(JavaPlugin plugin, GameManager game, WorldManager worlds, ShopService shop,
                        UpgradeShopService upgrades) {
        this.plugin = plugin;
        this.game = game;
        this.worlds = worlds;
        this.shop = shop;
        this.upgrades = upgrades;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBedBreak(BlockBreakEvent event) {
        if (!worlds.isArenaWorld(event.getBlock().getWorld()) || !game.isLive()) {
            if (worlds.isArenaWorld(event.getBlock().getWorld())) event.setCancelled(true);
            return;
        }
        Material type = event.getBlock().getType();
        if (!type.name().endsWith("_BED")) return;

        Player breaker = event.getPlayer();
        if (!game.isActiveFighter(breaker)) {
            event.setCancelled(true);
            return;
        }
        TeamColor breakerTeam = game.teamOf(breaker.getUniqueId());
        TeamColor bedTeam = null;
        for (TeamColor team : TeamColor.values()) {
            if (type == team.bed()) {
                bedTeam = team;
                break;
            }
        }
        if (bedTeam == null) return;

        event.setCancelled(true);
        event.setDropItems(false);
        if (bedTeam == breakerTeam) {
            breaker.sendMessage("§cYou cannot break your own bed!");
            return;
        }
        if (!game.bedAlive(bedTeam)) return;

        breakBedBlock(event.getBlock());
        if (event.getBlock().getBlockData() instanceof Bed bed) {
            Block other = event.getBlock().getRelative(bed.getFacing().getOppositeFace());
            if (other.getType().name().endsWith("_BED")) breakBedBlock(other);
        }
        game.onBedBroken(bedTeam, breaker);
    }

    private void breakBedBlock(Block block) {
        block.setType(Material.AIR);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!worlds.isArenaWorld(event.getBlock().getWorld())) return;
        if (!game.isLive() || game.isStarting() || !game.isActiveFighter(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        Material type = event.getBlock().getType();
        if (type.name().endsWith("_BED")) return;

        if (!ArenaBlockTracker.isTracked(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        ArenaBlockTracker.untrack(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!worlds.isArenaWorld(event.getBlock().getWorld())) return;
        if (game.isStarting() || (game.isLive() && !game.isActiveFighter(event.getPlayer()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlaceTrack(BlockPlaceEvent event) {
        if (!worlds.isArenaWorld(event.getBlock().getWorld()) || !game.isLive()) return;
        if (!game.isActiveFighter(event.getPlayer())) return;
        ArenaBlockTracker.track(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!game.isLive() || !game.isMatchPlayer(victim) || !game.isActiveFighter(victim)) return;

        event.deathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(false);
        game.onPlayerDeath(victim, victim.getKiller());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (!game.isMatchPlayer(p)) return;

        if (game.shouldEnterSpectator(p.getUniqueId())) {
            Location here = p.getLocation();
            event.setRespawnLocation(here);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> game.applySpectatorAfterRespawn(p), 1L);
            return;
        }

        if (!game.isLive()) return;

        TeamColor team = game.teamOf(p.getUniqueId());
        if (team != null && game.bedAlive(team)) {
            Location spawn = worlds.teamSpawn(team);
            if (spawn != null) event.setRespawnLocation(spawn);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                game.applyRespawnKit(p);
                TeamColor t = game.teamOf(p.getUniqueId());
                if (t != null) game.applyTeamUpgradesToOnline(t);
            }, 1L);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> game.applyRespawnKit(p), 5L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVoidFall(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (!worlds.isArenaWorld(event.getTo().getWorld())) return;
        if (!game.isLive() && !game.isStarting()) return;

        Player p = event.getPlayer();
        double y = event.getTo().getY();

        if (game.isStarting() && game.isMatchPlayer(p)) {
            if (y < 50) {
                Location waiting = worlds.arenaWaitingSpawn();
                if (waiting != null) p.teleport(waiting);
            }
            return;
        }

        if (!game.isLive() || !game.isActiveFighter(p)) return;
        if (y < 50) {
            voidKill(p);
        }
    }

    private void voidKill(Player p) {
        if (!p.isOnline() || p.isDead()) return;
        p.setFallDistance(0);
        p.setNoDamageTicks(0);
        p.setHealth(0);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (worlds.isLobbyWorld(p.getWorld()) && p.getGameMode() != GameMode.SPECTATOR) {
            event.setCancelled(true);
            return;
        }
        if (worlds.isArenaWorld(p.getWorld()) && game.isStarting() && game.isMatchPlayer(p)) {
            event.setCancelled(true);
            return;
        }
        if (worlds.isArenaWorld(p.getWorld()) && game.isLive()
                && event.getCause() == EntityDamageEvent.DamageCause.VOID
                && game.isActiveFighter(p)) {
            event.setCancelled(false);
            event.setDamage(1000.0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!worlds.isArenaWorld(victim.getWorld()) || !game.isLive()) return;

        Player attacker = resolveDamagingPlayer(event.getDamager());
        if (attacker == null) return;

        if (!game.isActiveFighter(victim) || !game.isActiveFighter(attacker)) {
            event.setCancelled(true);
            return;
        }
        if (isSameTeam(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    /** Direct hits, arrows, fireballs, TNT, splash potions, etc. */
    private Player resolveDamagingPlayer(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        return null;
    }

    private boolean isSameTeam(Player a, Player b) {
        TeamColor ta = game.teamOf(a.getUniqueId());
        TeamColor tb = game.teamOf(b.getUniqueId());
        return ta != null && ta == tb;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShopClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        ShopInventoryHolder holder = shopHolder(event);
        if (holder == null) return;

        event.setCancelled(true);

        if (!game.isLive()) return;
        TeamColor team = game.teamOf(p.getUniqueId());
        if (team == null) return;
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        if (holder.kind() == ShopInventoryHolder.Kind.UPGRADE) {
            upgrades.handleClick(p, team, event.getRawSlot());
            return;
        }
        shop.handleClick(p, team, event.getRawSlot());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShopDrag(InventoryDragEvent event) {
        if (shopHolder(event) != null) {
            event.setCancelled(true);
        }
    }

    private ShopInventoryHolder shopHolder(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopInventoryHolder h) return h;
        return null;
    }

    private ShopInventoryHolder shopHolder(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopInventoryHolder h) return h;
        return null;
    }

    @EventHandler
    public void onShopClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopInventoryHolder holder)) return;
        event.getView().getTopInventory().clear();
        if (holder.kind() == ShopInventoryHolder.Kind.ITEM) {
            shop.close(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickupBed(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!worlds.isArenaWorld(p.getWorld()) || !game.isLive()) return;
        if (event.getItem().getItemStack().getType().name().endsWith("_BED")) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUpgradeVillager(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        if (!villager.getPersistentDataContainer().has(UpgradeAccess.upgradeNpcKey(), PersistentDataType.BYTE)) return;
        event.setCancelled(true);
        UpgradeAccess.tryOpen(event.getPlayer(), game, upgrades);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShopVillager(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        if (!villager.getPersistentDataContainer().has(ShopAccess.shopNpcKey(), PersistentDataType.BYTE)) return;
        event.setCancelled(true);
        ShopAccess.tryOpen(event.getPlayer(), game, shop);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShopItemUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        if (event.getItem() == null || !ShopAccess.isShopItem(event.getItem())) return;
        event.setCancelled(true);
        ShopAccess.tryOpen(p, game, shop);
    }
}
