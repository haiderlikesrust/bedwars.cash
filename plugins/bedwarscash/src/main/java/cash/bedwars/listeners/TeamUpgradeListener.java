package cash.bedwars.listeners;

import cash.bedwars.game.GameManager;
import cash.bedwars.game.TeamColor;
import cash.bedwars.world.WorldManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Maniac Miner haste while near team island. */
public class TeamUpgradeListener implements Listener {
    private final GameManager game;
    private final WorldManager worlds;

    public TeamUpgradeListener(GameManager game, WorldManager worlds) {
        this.game = game;
        this.worlds = worlds;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        Player p = event.getPlayer();
        if (!game.isLive() || !game.isActiveFighter(p)) return;
        TeamColor team = game.teamOf(p.getUniqueId());
        if (team == null) return;
        int maniac = game.upgrades().maniac(team);
        if (maniac <= 0) return;
        Location spawn = worlds.teamSpawn(team);
        if (spawn == null) return;
        if (p.getLocation().distance(spawn) <= 22) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 50, maniac - 1, false, false, false));
        }
    }
}
