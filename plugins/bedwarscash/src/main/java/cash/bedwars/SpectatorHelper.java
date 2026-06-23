package cash.bedwars;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Hidden adventure-mode spectator: fly, no interact, invisible to fighters. */
public final class SpectatorHelper {
    private static JavaPlugin plugin;
    private static final Set<UUID> SPECTATORS = ConcurrentHashMap.newKeySet();

    private SpectatorHelper() {}

    public static void init(JavaPlugin pl) {
        plugin = pl;
    }

    public static boolean isSpectator(Player p) {
        return SPECTATORS.contains(p.getUniqueId());
    }

    public static void enter(Player p) {
        if (!p.isOnline() || plugin == null) return;
        SPECTATORS.add(p.getUniqueId());
        p.setGameMode(GameMode.ADVENTURE);
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setInvisible(true);
        p.setCollidable(false);
        p.setCanPickupItems(false);
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
        refreshVisibility(p);
        p.sendMessage("§7Spectator mode — fly around freely. Use §f/bet <team> <sol>§7.");
    }

    public static void leave(Player p) {
        if (!p.isOnline() || plugin == null) return;
        SPECTATORS.remove(p.getUniqueId());
        p.setInvisible(false);
        p.setCollidable(true);
        p.setCanPickupItems(true);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, p);
            p.showPlayer(plugin, other);
        }
    }

    public static void refreshVisibility(Player spectator) {
        if (!isSpectator(spectator)) return;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(spectator)) continue;
            if (isSpectator(other)) {
                other.showPlayer(plugin, spectator);
                spectator.showPlayer(plugin, other);
            } else {
                other.hidePlayer(plugin, spectator);
                spectator.showPlayer(plugin, other);
            }
        }
    }

    public static void onPlayerJoin(Player joined) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isSpectator(p) && !isSpectator(joined)) joined.hidePlayer(plugin, p);
            if (isSpectator(joined) && !isSpectator(p)) p.hidePlayer(plugin, joined);
        }
    }

    public static void clearAll() {
        for (UUID id : Set.copyOf(SPECTATORS)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) leave(p);
        }
        SPECTATORS.clear();
    }
}
