package cash.bedwars.game;



import cash.bedwars.BedWarsCashPlugin;

import cash.bedwars.BackendClient;

import cash.bedwars.LobbyService;

import cash.bedwars.SpectatorHelper;

import cash.bedwars.world.WorldManager;

import com.google.gson.JsonArray;

import com.google.gson.JsonElement;

import com.google.gson.JsonObject;

import net.kyori.adventure.text.Component;

import net.kyori.adventure.text.format.NamedTextColor;

import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;

import org.bukkit.GameMode;

import org.bukkit.Location;

import org.bukkit.Material;

import org.bukkit.World;

import org.bukkit.entity.Player;

import org.bukkit.inventory.ItemStack;
import cash.bedwars.game.GameItems;
import cash.bedwars.game.shop.ShopAccess;
import cash.bedwars.game.shop.ShopPurchases;
import cash.bedwars.game.upgrades.TeamUpgradeState;
import cash.bedwars.game.upgrades.UpgradeAccess;



import java.time.Duration;

import java.util.*;



/** Embedded BedWars match — no external BedWars plugin required. */

public class GameManager {

    private final BedWarsCashPlugin plugin;

    private final WorldManager worlds;

    private final LobbyService lobby;

    private final GeneratorService generators;



    private int matchId = -1;

    private boolean live = false;

    private boolean starting = false;

    private int countdownTask = -1;
    /** Bumps when a match ends so delayed spectator tasks cannot run on the next lobby. */
    private int matchGeneration = 0;

    private final Map<UUID, TeamColor> playerTeam = new HashMap<>();

    private final Map<TeamColor, Set<UUID>> teamMembers = new EnumMap<>(TeamColor.class);

    private final Set<TeamColor> bedAlive = EnumSet.noneOf(TeamColor.class);

    private final Set<UUID> eliminated = new HashSet<>();
    /** Players who must enter spectator after respawn (survives brief match-end race). */
    private final Set<UUID> pendingSpectator = new HashSet<>();

    private final TeamUpgradeState upgrades = new TeamUpgradeState();
    private final MatchStats stats = new MatchStats();



    public GameManager(BedWarsCashPlugin plugin, WorldManager worlds, LobbyService lobby) {

        this.plugin = plugin;

        this.worlds = worlds;

        this.lobby = lobby;

        this.generators = new GeneratorService(plugin, this, worlds);

        for (TeamColor t : TeamColor.values()) teamMembers.put(t, new HashSet<>());

    }



    public boolean isLive() { return live; }

    public boolean isStarting() { return starting; }

    public int matchId() { return matchId; }

    public TeamColor teamOf(UUID uuid) { return playerTeam.get(uuid); }

    public TeamUpgradeState upgrades() { return upgrades; }

    public MatchStats stats() { return stats; }

    public void applyTeamUpgradesToOnline(TeamColor team) {
        for (UUID u : teamMembers.get(team)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline() && isActiveFighter(p)) {
                UpgradeAccess.applyGearUpgrades(p, upgrades, team);
            }
        }
    }

    public boolean bedAlive(TeamColor team) { return bedAlive.contains(team); }

    public boolean isMatchPlayer(Player p) {
        if (p == null) return false;
        UUID id = p.getUniqueId();
        if (pendingSpectator.contains(id)) return true;
        return (live || starting) && playerTeam.containsKey(id);
    }

    public boolean isEliminated(UUID uuid) {
        return eliminated.contains(uuid);
    }

    public boolean shouldEnterSpectator(UUID uuid) {
        return pendingSpectator.contains(uuid) || eliminated.contains(uuid);
    }

    /** Alive fighter who can PvP, build, and use the shop. */
    public boolean isActiveFighter(Player p) {
        if (p == null || !live) return false;
        UUID id = p.getUniqueId();
        if (!playerTeam.containsKey(id) || eliminated.contains(id)) return false;
        if (shouldEnterSpectator(id)) return false;
        return !SpectatorHelper.isSpectator(p);
    }

    public boolean hasAliveFighters(TeamColor team) {
        return teamMembers.get(team).stream().anyMatch(u -> !eliminated.contains(u));
    }

    /** Best live fighter on a team for the broadcast camera to follow. */
    public Player pickCameraTarget(TeamColor team) {
        for (UUID uuid : teamMembers.get(team)) {
            if (eliminated.contains(uuid)) continue;
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && isActiveFighter(p)) return p;
        }
        for (UUID uuid : teamMembers.get(team)) {
            if (eliminated.contains(uuid)) continue;
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) return p;
        }
        return null;
    }

    /** Clears spectator/elimination UI state so a respawned player can fight and shop again. */
    private void restoreAsFighter(Player p) {
        UUID id = p.getUniqueId();
        pendingSpectator.remove(id);
        SpectatorHelper.leave(p);
        p.setGameMode(GameMode.SURVIVAL);
        p.setFlying(false);
        p.setAllowFlight(false);
        p.setInvisible(false);
        p.setCollidable(true);
        p.setCanPickupItems(true);
    }



    public void startMatch(int id, JsonObject teams) {

        World arena = worlds.arenaWorld();

        if (arena == null) {

            plugin.getLogger().warning("Arena world missing — cannot start match.");

            return;

        }

        resetMatchState();

        worlds.buildArena(arena);



        matchId = id;

        starting = true;

        live = false;

        playerTeam.clear();

        eliminated.clear();
        pendingSpectator.clear();

        bedAlive.clear();

        for (TeamColor t : TeamColor.values()) {

            teamMembers.get(t).clear();

            bedAlive.add(t);

        }



        for (Map.Entry<String, JsonElement> entry : teams.entrySet()) {

            TeamColor team = TeamColor.fromId(entry.getKey());

            if (team == null) continue;

            JsonArray arr = entry.getValue().getAsJsonArray();

            for (JsonElement el : arr) {

                UUID uuid = UUID.fromString(el.getAsString());

                playerTeam.put(uuid, team);

                teamMembers.get(team).add(uuid);

            }

        }



        Bukkit.broadcastMessage("§b§lBedWars.cash §fMatch §e#" + id + " §f— get ready!");

        Location waiting = worlds.arenaWaitingSpawn();

        for (Map.Entry<UUID, TeamColor> e : playerTeam.entrySet()) {

            Player p = Bukkit.getPlayer(e.getKey());

            if (p == null || !p.isOnline()) continue;

            prepareFighter(p, e.getValue(), waiting);

        }

        for (Player p : Bukkit.getOnlinePlayers()) {

            if (!playerTeam.containsKey(p.getUniqueId())) {

                if (plugin.broadcast().isBroadcast(p)) {
                    plugin.broadcast().onMatchStarting();
                    continue;
                }

                SpectatorHelper.enter(p);

                p.sendMessage("§7You are spectating. Use §f/bet <team> <sol>§7.");

            }

        }



        int seconds = plugin.getConfig().getInt("game.countdown-seconds", 10);

        beginCountdown(seconds);

    }



    private void prepareFighter(Player p, TeamColor team, Location waiting) {

        restoreAsFighter(p);
        p.getInventory().clear();

        p.setGameMode(GameMode.ADVENTURE);

        if (waiting != null) p.teleport(waiting);

        p.sendMessage(team.chat() + "You are on team " + team.id() + "! Fight starts soon.");

    }



    private void beginCountdown(int seconds) {

        if (!starting) return;

        if (seconds <= 0) {

            launchMatch();

            return;

        }

        Bukkit.broadcastMessage("§eMatch starts in §f" + seconds + "§e...");

        Title title = Title.title(

                Component.text(String.valueOf(seconds), NamedTextColor.GOLD),

                Component.text("Get ready!", NamedTextColor.GRAY),

                Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(200))

        );

        for (UUID uuid : playerTeam.keySet()) {

            Player p = Bukkit.getPlayer(uuid);

            if (p != null && p.isOnline()) p.showTitle(title);

        }

        countdownTask = Bukkit.getScheduler().runTaskLater(plugin, () -> beginCountdown(seconds - 1), 20L).getTaskId();

    }



    private void launchMatch() {

        starting = false;

        live = true;

        countdownTask = -1;

        worlds.setArenaMatchDifficulty(true);

        Bukkit.broadcastMessage("§a§lFIGHT!");

        for (Map.Entry<UUID, TeamColor> e : playerTeam.entrySet()) {

            Player p = Bukkit.getPlayer(e.getKey());

            if (p == null || !p.isOnline()) continue;

            spawnFighter(p, e.getValue());

        }

        generators.start();
        worlds.spawnShopVillagers();
        worlds.spawnUpgradeVillagers();
        plugin.broadcast().onMatchLive();
        Bukkit.broadcastMessage("§eShops: §f/shop §e· §6/upgrades §e· right-click villagers · nether star in slot 9");
    }



    private void spawnFighter(Player p, TeamColor team) {

        Location spawn = worlds.teamSpawn(team);

        if (spawn == null) return;

        restoreAsFighter(p);
        p.getInventory().clear();

        p.setGameMode(GameMode.SURVIVAL);

        p.teleport(spawn);

        giveKit(p, team);

        UpgradeAccess.applyGearUpgrades(p, upgrades, team);

    }



    private void giveKit(Player p, TeamColor team) {
        p.getInventory().clear();
        stripBedItems(p);
        ItemStack sword = new ItemStack(Material.WOODEN_SWORD);
        GameItems.markPermanent(sword);
        p.getInventory().addItem(sword);
        p.getInventory().addItem(new ItemStack(team.wool(), 16));
        p.getInventory().setItem(8, ShopAccess.createShopItem());
    }

    private void stripBedItems(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && contents[i].getType().name().endsWith("_BED")) {
                contents[i] = null;
            }
        }
        p.getInventory().setContents(contents);
    }



    public void onBedBroken(TeamColor team, Player breaker) {

        if (!live || !bedAlive.contains(team)) return;

        bedAlive.remove(team);
        stats.addBedBreak(breaker.getUniqueId());

        Bukkit.broadcastMessage(team.chat() + "§lBED DESTROYED! §7Team " + team.id() + " can no longer respawn.");
        Title bedTitle = Title.title(
                Component.text("BED DESTROYED!", NamedTextColor.RED),
                Component.text("Team " + team.id(), NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(300))
        );
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (worlds.isArenaWorld(p.getWorld()) || worlds.isLobbyWorld(p.getWorld())) {
                p.showTitle(bedTitle);
            }
        }
        breaker.playSound(breaker.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.2f);

        checkWinner();

    }



    public void onPlayerDeath(Player victim, Player killer) {
        if (!live || !playerTeam.containsKey(victim.getUniqueId())) return;

        stats.addDeath(victim.getUniqueId());

        TeamColor team = playerTeam.get(victim.getUniqueId());
        boolean finalKill = !bedAlive.contains(team);

        if (killer != null && isActiveFighter(killer) && !killer.getUniqueId().equals(victim.getUniqueId())) {
            TeamColor killerTeam = playerTeam.get(killer.getUniqueId());
            if (finalKill) stats.addFinalKill(killer.getUniqueId());
            else stats.addKill(killer.getUniqueId());
            String msg = killerTeam.chat() + killer.getName() + " §7→ " + team.chat() + victim.getName();
            if (finalKill) msg += " §c§lFINAL KILL!";
            Bukkit.broadcastMessage(msg);
            if (finalKill) {
                Title fk = Title.title(
                        Component.text("FINAL KILL!", NamedTextColor.RED),
                        Component.text(killer.getName(), NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1000), Duration.ofMillis(400))
                );
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (worlds.isArenaWorld(p.getWorld())) p.showTitle(fk);
                }
                killer.playSound(killer.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        } else if (finalKill) {
            Bukkit.broadcastMessage(team.chat() + victim.getName() + " §7was eliminated.");
        }

        if (bedAlive.contains(team)) {
            return;
        }

        eliminated.add(victim.getUniqueId());
        pendingSpectator.add(victim.getUniqueId());
        scheduleSpectatorEnter(victim);
        checkWinner();
    }

    private void scheduleSpectatorEnter(Player p) {
        int generation = matchGeneration;
        for (long delay : new long[]{1L, 3L, 5L, 10L, 20L}) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (generation != matchGeneration) return;
                applySpectatorAfterRespawn(p);
            }, delay);
        }
    }

    /** Called after respawn (and retried) until spectator mode sticks. */
    public void applySpectatorAfterRespawn(Player p) {
        if (p == null || !p.isOnline()) return;
        if (!live && !starting) {
            pendingSpectator.remove(p.getUniqueId());
            return;
        }
        if (!pendingSpectator.contains(p.getUniqueId())) return;

        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        SpectatorHelper.enter(p);
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);

        if (SpectatorHelper.isSpectator(p) && p.getAllowFlight()) {
            pendingSpectator.remove(p.getUniqueId());
            p.sendMessage("§7You were eliminated. Spectate and bet with §f/bet <team> <sol>§7.");
        }
    }

    /** Re-kit a fighter who still has a bed. */
    public void applyRespawnKit(Player p) {
        if (!live || p == null || !p.isOnline()) return;
        UUID id = p.getUniqueId();
        if (shouldEnterSpectator(id)) {
            applySpectatorAfterRespawn(p);
            return;
        }
        if (!playerTeam.containsKey(id) || eliminated.contains(id)) return;
        TeamColor team = teamOf(id);
        if (team == null || !bedAlive.contains(team)) return;

        restoreAsFighter(p);
        Location spawn = worlds.teamSpawn(team);
        if (spawn != null) p.teleport(spawn);
        giveKit(p, team);
        UpgradeAccess.applyGearUpgrades(p, upgrades, team);
    }



    private void checkWinner() {

        if (!live) return;

        List<TeamColor> aliveTeams = new ArrayList<>();

        for (TeamColor t : TeamColor.values()) {

            boolean any = teamMembers.get(t).stream().anyMatch(u -> !eliminated.contains(u));

            if (any) aliveTeams.add(t);

        }

        if (aliveTeams.size() <= 1) {

            finishMatch(aliveTeams.isEmpty() ? null : aliveTeams.getFirst());

        }

    }



    public void finishMatch(TeamColor winner) {

        if (!live && !starting) return;

        plugin.broadcast().onMatchEnd();

        int finishedMatchId = matchId;
        Set<UUID> fighters = new HashSet<>(playerTeam.keySet());
        Map<UUID, int[]> statsSnapshot = stats.snapshot();
        List<UUID> winners = winner == null ? List.of() : new ArrayList<>(teamMembers.get(winner));

        resetMatchState();

        if (winner != null) {

            Bukkit.broadcastMessage(winner.chat() + "§l" + winner.id() + " WINS!");
            UUID mvp = MatchStats.topKiller(statsSnapshot);
            if (mvp != null) {
                int[] mvpStats = statsSnapshot.getOrDefault(mvp, new int[4]);
                Player mvpP = Bukkit.getPlayer(mvp);
                String name = mvpP != null ? mvpP.getName() : Bukkit.getOfflinePlayer(mvp).getName();
                if (name == null) name = mvp.toString().substring(0, 8);
                Bukkit.broadcastMessage("§6 MVP: §f" + name + " §7(" + mvpStats[0] + " kills, "
                        + mvpStats[1] + " final, " + mvpStats[2] + " beds)");
            }

            plugin.backend().matchResult(finishedMatchId, winner.id(), winners, buildPlayerStatLines(fighters, statsSnapshot));

        } else {

            Bukkit.broadcastMessage("§cMatch ended with no winner.");

        }

        matchId = -1;

        playerTeam.clear();

        eliminated.clear();

        World arena = worlds.arenaWorld();
        if (arena != null) worlds.wipeArena(arena);

        Bukkit.getScheduler().runTaskLater(plugin, this::returnEveryoneToLobby, 60L);

    }



    public void reportWinner(TeamColor winner) {
        if (!live) return;
        finishMatch(winner);
    }

    /** Admin/testing: void the match, refund bets on backend, send everyone to lobby. */
    public void abortMatch(String reason) {
        if (!live && !starting) return;
        int id = matchId;
        forceCleanupLocal(reason);
        if (id > 0) plugin.backend().matchAborted(id, reason);
    }

    /** Clears plugin-side match state and returns all players to lobby (works even if desynced). */
    public void forceCleanupLocal(String reason) {
        plugin.broadcast().onMatchEnd();
        resetMatchState();
        Bukkit.broadcastMessage("§c§lMatch ended. §7" + reason);
        matchId = -1;
        playerTeam.clear();
        eliminated.clear();
        bedAlive.clear();
        World arena = worlds.arenaWorld();
        if (arena != null) {
            worlds.removeShopVillagers(arena);
            worlds.removeUpgradeVillagers(arena);
            worlds.wipeArena(arena);
        }
        returnEveryoneToLobby();
    }

    private void returnEveryoneToLobby() {
        pendingSpectator.clear();
        boolean autoQueue = plugin.getConfig().getBoolean("join.auto-queue", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            ShopPurchases.reset(p);
            lobby.enterLobby(p, true);
            if (autoQueue && !plugin.broadcast().shouldSkipJoinFlow(p)) {
                plugin.backend().queueJoin(p);
            }
        }
    }



    private void resetMatchState() {

        matchGeneration++;
        live = false;

        starting = false;
        pendingSpectator.clear();

        worlds.setArenaMatchDifficulty(false);

        generators.stop();

        upgrades.clear();
        stats.clear();

        if (countdownTask != -1) {

            Bukkit.getScheduler().cancelTask(countdownTask);

            countdownTask = -1;

        }

    }



    private void endMatchSilently() {

        resetMatchState();

        matchId = -1;

        playerTeam.clear();

        eliminated.clear();
        pendingSpectator.clear();

        bedAlive.clear();

    }

    private List<BackendClient.MatchPlayerStat> buildPlayerStatLines(Set<UUID> fighters, Map<UUID, int[]> snap) {
        List<BackendClient.MatchPlayerStat> lines = new ArrayList<>();
        for (UUID uuid : fighters) {
            int[] s = snap.getOrDefault(uuid, new int[4]);
            Player online = Bukkit.getPlayer(uuid);
            String name = online != null ? online.getName() : Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) name = uuid.toString().substring(0, 8);
            lines.add(new BackendClient.MatchPlayerStat(
                    uuid.toString(),
                    name,
                    s[0],
                    s[1],
                    s[2],
                    s[3]
            ));
        }
        return lines;
    }

}


