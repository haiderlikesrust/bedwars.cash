package cash.bedwars;

import cash.bedwars.world.WorldManager;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

public class LobbyService {
    private final BedWarsCashPlugin plugin;
    private final WorldManager worlds;

    public LobbyService(BedWarsCashPlugin plugin, WorldManager worlds) {
        this.plugin = plugin;
        this.worlds = worlds;
    }

    public void initWorld() {
        World world = worlds.lobbyWorld();
        if (world == null) return;
        world.setDifficulty(org.bukkit.Difficulty.PEACEFUL);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        world.getEntities().stream()
                .filter(e -> e instanceof Monster)
                .forEach(org.bukkit.entity.Entity::remove);
    }

    public void enterLobby(Player p) {
        enterLobby(p, false);
    }

    /** @param force when true, always teleport to lobby (e.g. after match end). */
    public void enterLobby(Player p, boolean force) {
        if (!p.isOnline()) return;
        if (plugin.broadcast().isBroadcast(p)) {
            if (force) plugin.broadcast().releaseToLobby(p);
            return;
        }
        if (!force && (plugin.game().isLive() || plugin.game().isStarting()
                || (!worlds.isLobbyWorld(p.getWorld())
                && ("live".equals(plugin.backend().matchPhase())
                || "settling".equals(plugin.backend().matchPhase()))))) {
            SpectatorHelper.enter(p);
            return;
        }
        SpectatorHelper.leave(p);
        Location spawn = worlds.lobbySpawn();
        if (spawn != null) p.teleport(spawn);
        p.setGameMode(GameMode.ADVENTURE);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.getInventory().clear();
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        plugin.scoreboard().apply(p);
    }

    public boolean isLobbyPlayer(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR) return false;
        return worlds.isLobbyWorld(p.getWorld());
    }

    public void saveLobbyHere(Player p) {
        Location loc = p.getLocation();
        plugin.getConfig().set("lobby.world", loc.getWorld().getName());
        plugin.getConfig().set("lobby.x", loc.getX());
        plugin.getConfig().set("lobby.y", loc.getY());
        plugin.getConfig().set("lobby.z", loc.getZ());
        plugin.getConfig().set("lobby.yaw", loc.getYaw());
        plugin.getConfig().set("lobby.pitch", loc.getPitch());
        plugin.saveConfig();
        worlds.setLobbySpawn(loc);
        initWorld();
        p.sendMessage("§a[BedWars.cash] Lobby spawn updated.");
    }
}
