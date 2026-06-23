package cash.bedwars.game;

import cash.bedwars.BedWarsCashPlugin;
import cash.bedwars.world.WorldManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public class SpecialItemListener implements Listener {
    private static final String META = "bwc_special";

    private final BedWarsCashPlugin plugin;
    private final GameManager game;
    private final WorldManager worlds;

    public SpecialItemListener(BedWarsCashPlugin plugin, GameManager game, WorldManager worlds) {
        this.plugin = plugin;
        this.game = game;
        this.worlds = worlds;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        if (!worlds.isArenaWorld(p.getWorld()) || !game.isLive() || !game.isActiveFighter(p)) return;
        ItemStack hand = event.getItem();
        if (hand == null) return;
        String id = SpecialItems.id(hand);
        if (id == null) return;

        TeamColor team = game.teamOf(p.getUniqueId());
        if (team == null) return;

        switch (id) {
            case "fireball" -> {
                event.setCancelled(true);
                consumeOne(p, hand);
                Fireball fb = p.launchProjectile(Fireball.class);
                fb.setShooter(p);
                fb.setIsIncendiary(false);
                fb.setYield(0f);
                Vector v = p.getLocation().getDirection().normalize().multiply(1.6);
                fb.setVelocity(v);
            }
            case "bridge_egg" -> {
                event.setCancelled(true);
                consumeOne(p, hand);
                Egg egg = p.launchProjectile(Egg.class);
                egg.setShooter(p);
                egg.setMetadata(META, new FixedMetadataValue(plugin, team.name()));
            }
            case "bedbug" -> {
                event.setCancelled(true);
                consumeOne(p, hand);
                Snowball ball = p.launchProjectile(Snowball.class);
                ball.setShooter(p);
                ball.setMetadata(META, new FixedMetadataValue(plugin, "bedbug"));
            }
            case "tnt" -> {
                if (event.getClickedBlock() == null) return;
                event.setCancelled(true);
                consumeOne(p, hand);
                Block place = event.getClickedBlock().getRelative(event.getBlockFace());
                if (place.getType().isAir()) {
                    place.setType(Material.TNT);
                    TNTPrimed tnt = place.getWorld().spawn(place.getLocation().add(0.5, 0, 0.5), TNTPrimed.class);
                    tnt.setFuseTicks(50);
                    tnt.setSource(p);
                    place.setType(Material.AIR);
                }
            }
            case "tracker" -> event.setCancelled(true);
            default -> {}
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) return;
        if (!worlds.isArenaWorld(event.getEntity().getWorld()) || !game.isLive()) return;

        if (event.getEntity() instanceof Egg && event.getEntity().hasMetadata(META)) {
            TeamColor team = TeamColor.valueOf(event.getEntity().getMetadata(META).getFirst().asString());
            Location hit = event.getEntity().getLocation();
            Vector dir = shooter.getLocation().getDirection().setY(0).normalize();
            for (int i = 0; i < 12; i++) {
                Location at = hit.clone().add(dir.clone().multiply(i));
                Block b = at.getBlock();
                if (!b.getType().isAir()) break;
                b.setType(team.wool());
                ArenaBlockTracker.track(b);
            }
        }

        if (event.getEntity() instanceof Snowball && event.getEntity().hasMetadata(META)) {
            Location at = event.getEntity().getLocation();
            Silverfish fish = at.getWorld().spawn(at, Silverfish.class);
            fish.setMetadata(META, new FixedMetadataValue(plugin, shooter.getUniqueId().toString()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSilverfishDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Silverfish fish)) return;
        if (!fish.hasMetadata(META)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onTrackerTick(org.bukkit.event.player.PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!game.isLive() || !game.isActiveFighter(p)) return;
        ItemStack compass = findTracker(p);
        if (compass == null) return;
        Player nearest = nearestEnemy(p);
        if (nearest != null) {
            p.setCompassTarget(nearest.getLocation());
        }
    }

    private ItemStack findTracker(Player p) {
        for (ItemStack stack : p.getInventory().getContents()) {
            if (SpecialItems.isSpecial(stack, "tracker")) return stack;
        }
        return null;
    }

    private Player nearestEnemy(Player self) {
        TeamColor my = game.teamOf(self.getUniqueId());
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(self) || !game.isActiveFighter(other)) continue;
            if (game.teamOf(other.getUniqueId()) == my) continue;
            double d = other.getLocation().distanceSquared(self.getLocation());
            if (d < bestD) {
                bestD = d;
                best = other;
            }
        }
        return best;
    }

    private void consumeOne(Player p, ItemStack hand) {
        hand.setAmount(hand.getAmount() - 1);
    }
}
