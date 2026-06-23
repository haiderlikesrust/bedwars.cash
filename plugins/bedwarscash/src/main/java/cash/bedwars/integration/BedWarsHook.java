package cash.bedwars.integration;

import cash.bedwars.BackendClient;
import cash.bedwars.BedWarsCashPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tomkeuper.bedwars.api.BedWars;
import com.tomkeuper.bedwars.api.arena.IArena;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Soft integration with BedWars2023. All API-typed code is guarded by `hooked`
// so the plugin still loads (standalone) when BedWars2023 is absent.
public class BedWarsHook {
    private final BedWarsCashPlugin plugin;
    private boolean hooked = false;
    private BedWars api;
    private int currentMatchId = -1;
    // Team color -> member UUIDs for the active match (set from the backend's start_match).
    private final Map<String, List<UUID>> currentTeams = new HashMap<>();

    public BedWarsHook(BedWarsCashPlugin plugin) {
        this.plugin = plugin;
    }

    public void tryHook(BackendClient backend) {
        if (Bukkit.getPluginManager().getPlugin("BedWars2023") == null) {
            plugin.getLogger().warning("BedWars2023 not detected. Running in standalone mode (no auto match start/result).");
            return;
        }
        RegisteredServiceProvider<BedWars> rsp = Bukkit.getServicesManager().getRegistration(BedWars.class);
        if (rsp == null) {
            plugin.getLogger().warning("BedWars2023 present but API service not registered yet.");
            return;
        }
        this.api = rsp.getProvider();
        this.hooked = true;
        Bukkit.getPluginManager().registerEvents(new GameEndListener(this, backend), plugin);
        plugin.getLogger().info("Hooked into BedWars2023 API.");
    }

    public boolean isHooked() {
        return hooked;
    }

    public int currentMatchId() {
        return currentMatchId;
    }

    public void clearMatch() {
        this.currentMatchId = -1;
        this.currentTeams.clear();
    }

    // Fork-agnostic fallback: report a winner using the team mapping the backend sent
    // at match start. Used by /bwresult when no BedWars GameEndEvent hook is active.
    public boolean reportWinnerByTeam(BackendClient backend, String team) {
        String color = team.toUpperCase();
        if (currentMatchId <= 0) return false;
        List<UUID> winners = currentTeams.getOrDefault(color, List.of());
        backend.matchResult(currentMatchId, color, new ArrayList<>(winners));
        clearMatch();
        return true;
    }

    // Called when the backend instructs a match to start with assigned teams.
    public void startMatch(int matchId, JsonObject teams) {
        this.currentMatchId = matchId;
        this.currentTeams.clear();
        for (Map.Entry<String, com.google.gson.JsonElement> e : teams.entrySet()) {
            List<UUID> ids = new ArrayList<>();
            for (var el : e.getValue().getAsJsonArray()) ids.add(UUID.fromString(el.getAsString()));
            this.currentTeams.put(e.getKey().toUpperCase(), ids);
        }
        if (!hooked) {
            plugin.getLogger().info("start_match received (standalone): match " + matchId + ". Set up the arena manually.");
            return;
        }
        IArena arena = resolveArena();
        if (arena == null) {
            plugin.getLogger().warning("No BedWars arena available to start match " + matchId + ".");
            return;
        }
        int added = 0;
        for (Map.Entry<String, com.google.gson.JsonElement> entry : teams.entrySet()) {
            JsonArray uuids = entry.getValue().getAsJsonArray();
            for (var el : uuids) {
                Player p = Bukkit.getPlayer(UUID.fromString(el.getAsString()));
                if (p != null && p.isOnline()) {
                    try {
                        if (arena.addPlayer(p, true)) added++;
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to add " + p.getName() + " to arena: " + e.getMessage());
                    }
                }
            }
        }
        plugin.getLogger().info("Match " + matchId + ": added " + added + " players to arena " + arena.getDisplayName() + ".");
    }

    private IArena resolveArena() {
        String name = plugin.getConfig().getString("arena.name", "");
        if (name != null && !name.isBlank()) {
            IArena byName = api.getArenaUtil().getArenaByName(name);
            if (byName == null) byName = api.getArenaUtil().getArenaByIdentifier(name);
            if (byName != null) return byName;
        }
        var arenas = api.getArenaUtil().getArenas();
        return arenas.isEmpty() ? null : arenas.getFirst();
    }
}
