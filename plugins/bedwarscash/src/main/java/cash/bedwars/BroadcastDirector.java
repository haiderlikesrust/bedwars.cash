package cash.bedwars;

import cash.bedwars.game.GameManager;
import cash.bedwars.game.TeamColor;
import cash.bedwars.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Cycles a dedicated broadcast account through team perspectives during live matches.
 * Pair with an external client capture (OBS/ffmpeg) pointed at {@code broadcast.stream-url} on the website.
 */
public final class BroadcastDirector {

    private final BedWarsCashPlugin plugin;
    private final GameManager game;
    private final WorldManager worlds;
    private final BackendClient backend;

    private final boolean enabled;
    private final String username;
    private final int secondsPerTeam;

    private int teamIndex;
    private BukkitTask rotateTask;

    public BroadcastDirector(BedWarsCashPlugin plugin, GameManager game, WorldManager worlds, BackendClient backend) {
        this.plugin = plugin;
        this.game = game;
        this.worlds = worlds;
        this.backend = backend;
        this.enabled = plugin.getConfig().getBoolean("broadcast.enabled", false);
        this.username = plugin.getConfig().getString("broadcast.username", "BWC_Cast");
        this.secondsPerTeam = Math.max(15, plugin.getConfig().getInt("broadcast.seconds-per-team", 120));
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean isBroadcast(Player player) {
        if (!enabled || player == null || username == null || username.isBlank()) return false;
        return player.getName().equalsIgnoreCase(username.trim());
    }

    /** Skip auto-queue and normal spectator onboarding for the cast account. */
    public boolean shouldSkipJoinFlow(Player player) {
        return isBroadcast(player);
    }

    public void onMatchStarting() {
        if (!enabled) return;
        Player cast = findCast();
        if (cast != null) prepareForMatch(cast);
    }

    public void onMatchLive() {
        if (!enabled) return;
        teamIndex = 0;
        Player cast = findCast();
        if (cast == null) {
            plugin.getLogger().warning("Broadcast enabled but '" + username + "' is not online — start your cast client.");
            return;
        }
        prepareForMatch(cast);
        focusNextTeam(cast);
        startRotation(cast);
    }

    public void onMatchEnd() {
        if (!enabled) return;
        stopRotation();
        Player cast = findCast();
        if (cast != null) releaseToLobby(cast);
        backend.clearBroadcastCamera();
    }

    public void onCastJoin(Player player) {
        if (!isBroadcast(player)) return;
        SpectatorHelper.leave(player);
        if (game.isLive()) {
            prepareForMatch(player);
            focusNextTeam(player);
            startRotation(player);
        } else if (game.isStarting()) {
            prepareForMatch(player);
        } else {
            releaseToLobby(player);
        }
    }

    private void startRotation(Player cast) {
        stopRotation();
        long periodTicks = secondsPerTeam * 20L;
        rotateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!game.isLive()) {
                stopRotation();
                return;
            }
            Player current = findCast();
            if (current == null) return;
            teamIndex = (teamIndex + 1) % TeamColor.values().length;
            focusNextTeam(current);
        }, periodTicks, periodTicks);
    }

    private void stopRotation() {
        if (rotateTask != null) {
            rotateTask.cancel();
            rotateTask = null;
        }
    }

    private void focusNextTeam(Player cast) {
        if (!game.isLive()) return;

        TeamColor[] teams = TeamColor.values();
        for (int attempt = 0; attempt < teams.length; attempt++) {
            TeamColor team = teams[(teamIndex + attempt) % teams.length];
            if (!game.hasAliveFighters(team)) continue;
            Player target = game.pickCameraTarget(team);
            if (target == null) continue;
            teamIndex = (teamIndex + attempt) % teams.length;
            focusTarget(cast, team, target);
            return;
        }

        backend.clearBroadcastCamera();
        cast.sendMessage("§7[Broadcast] No fighters left to follow.");
    }

    private void focusTarget(Player cast, TeamColor team, Player target) {
        Location here = target.getLocation();
        cast.teleport(here);
        cast.setGameMode(GameMode.SPECTATOR);
        cast.setSpectatorTarget(target);
        cast.sendMessage("§b[Broadcast] §fFollowing §" + teamColorCode(team) + target.getName()
                + " §7(" + team.id() + ")");
        backend.broadcastCamera(game.matchId(), team.id(), target.getName());
    }

    private static char teamColorCode(TeamColor team) {
        return switch (team) {
            case GREEN -> 'a';
            case BLUE -> '9';
            case RED -> 'c';
            case YELLOW -> 'e';
        };
    }

    private void prepareForMatch(Player cast) {
        SpectatorHelper.leave(cast);
        Location arenaSpawn = worlds.arenaWaitingSpawn();
        if (arenaSpawn != null) cast.teleport(arenaSpawn);
        cast.setGameMode(GameMode.SPECTATOR);
        cast.setSpectatorTarget(null);
    }

    public void releaseToLobby(Player cast) {
        stopRotation();
        SpectatorHelper.leave(cast);
        cast.setSpectatorTarget(null);
        cast.setGameMode(GameMode.ADVENTURE);
        cast.setFlying(false);
        cast.setAllowFlight(false);
        Location lobby = worlds.lobbySpawn();
        if (lobby != null) cast.teleport(lobby);
    }

    private Player findCast() {
        if (username == null || username.isBlank()) return null;
        return Bukkit.getPlayerExact(username.trim());
    }
}
