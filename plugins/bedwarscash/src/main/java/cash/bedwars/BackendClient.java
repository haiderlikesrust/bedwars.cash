package cash.bedwars;

import cash.bedwars.game.GameManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

// Persistent WebSocket connection to the bedwars.cash backend (the hub).
public class BackendClient implements WebSocket.Listener {

    private final BedWarsCashPlugin plugin;
    private final URI uri;
    private final String token;
    private final int reconnectSeconds;
    private final OddsCache oddsCache;
    private final GameManager game;
    private final LiveState liveState;
    private final LobbyService lobby;
    private final CashScoreboard scoreboard;
    private final Gson gson = new Gson();

    private volatile WebSocket ws;
    private final StringBuilder buffer = new StringBuilder();
    private volatile boolean closing = false;
    private volatile String matchPhase = "lobby";

    public BackendClient(BedWarsCashPlugin plugin, String wsUrl, String token, int reconnectSeconds,
                         OddsCache oddsCache, GameManager game, LiveState liveState,
                         LobbyService lobby, CashScoreboard scoreboard) {
        this.plugin = plugin;
        this.uri = URI.create(wsUrl + "?token=" + token);
        this.token = token;
        this.reconnectSeconds = reconnectSeconds;
        this.oddsCache = oddsCache;
        this.game = game;
        this.liveState = liveState;
        this.lobby = lobby;
        this.scoreboard = scoreboard;
    }

    public void connect() {
        if (closing) return;
        try {
            HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(uri, this)
                    .whenComplete((socket, err) -> {
                        if (err != null) {
                            plugin.getLogger().warning("Backend connect failed: " + err.getMessage());
                            scheduleReconnect();
                        }
                    });
        } catch (Exception e) {
            plugin.getLogger().warning("Backend connect error: " + e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (closing) return;
        Bukkit.getScheduler().runTaskLater(plugin, this::connect, reconnectSeconds * 20L);
    }

    public void close() {
        closing = true;
        if (ws != null) ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
    }

    // ---- WebSocket.Listener ----
    @Override
    public void onOpen(WebSocket webSocket) {
        this.ws = webSocket;
        plugin.getLogger().info("Connected to bedwars.cash backend.");
        JsonObject hello = new JsonObject();
        hello.addProperty("type", "hello");
        hello.addProperty("token", token);
        webSocket.sendText(gson.toJson(hello), true);
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        buffer.append(data);
        if (last) {
            String full = buffer.toString();
            buffer.setLength(0);
            handleOnMainThread(full);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        plugin.getLogger().warning("Backend connection closed: " + reason);
        scheduleReconnect();
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        plugin.getLogger().warning("Backend connection error: " + error.getMessage());
        scheduleReconnect();
    }

    private void handleOnMainThread(String json) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                handle(json);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to handle backend message: " + e.getMessage());
            }
        });
    }

    private void handle(String json) {
        JsonObject msg = gson.fromJson(json, JsonObject.class);
        if (msg == null || !msg.has("type")) return;
        switch (msg.get("type").getAsString()) {
            case "welcome" -> plugin.getLogger().info("Backend handshake complete.");
            case "ack" -> {
                if (msg.has("message")) plugin.getLogger().fine(msg.get("message").getAsString());
            }
            case "odds" -> {
                if (msg.has("odds")) {
                    JsonObject odds = msg.getAsJsonObject("odds");
                    oddsCache.update(odds);
                    liveState.updateFromOdds(odds);
                    scoreboard.refreshAll();
                }
            }
            case "notice" -> {
                String message = msg.has("message") ? msg.get("message").getAsString() : "";
                String line = "§b[BedWars.cash] §f" + message;
                if (msg.has("mcUuid") && !msg.get("mcUuid").isJsonNull()) {
                    Player p = Bukkit.getPlayer(UUID.fromString(msg.get("mcUuid").getAsString()));
                    if (p != null) p.sendMessage(line);
                } else {
                    for (Player pl : Bukkit.getOnlinePlayers()) pl.sendMessage(line);
                    Bukkit.getConsoleSender().sendMessage(line);
                }
            }
            case "start_match" -> {
                int matchId = msg.get("matchId").getAsInt();
                matchPhase = "live";
                game.startMatch(matchId, msg.getAsJsonObject("teams"));
            }
            case "state" -> {
                if (msg.has("state")) {
                    JsonObject state = msg.getAsJsonObject("state");
                    if (state.has("match") && !state.get("match").isJsonNull()) {
                        matchPhase = state.getAsJsonObject("match").get("phase").getAsString();
                    } else {
                        matchPhase = "lobby";
                    }
                    liveState.updateFromState(state);
                    scoreboard.refreshAll();
                }
            }
            case "join_action" -> handleJoinAction(msg);
            case "progression" -> {
                if (msg.has("mcUuid") && msg.has("level")) {
                    plugin.cosmetics().setLevel(
                            UUID.fromString(msg.get("mcUuid").getAsString()),
                            msg.get("level").getAsInt());
                }
            }
            case "quests" -> handleQuests(msg);
            default -> { /* ignore unknown */ }
        }
    }

    private void handleQuests(JsonObject msg) {
        if (!msg.has("mcUuid") || !msg.has("quests")) return;
        UUID uuid = UUID.fromString(msg.get("mcUuid").getAsString());
        List<QuestBoard.Quest> list = new java.util.ArrayList<>();
        for (JsonElement el : msg.getAsJsonArray("quests")) {
            JsonObject q = el.getAsJsonObject();
            list.add(new QuestBoard.Quest(
                    q.get("id").getAsString(),
                    q.get("name").getAsString(),
                    q.get("description").getAsString(),
                    q.get("target").getAsInt(),
                    q.get("progress").getAsInt(),
                    q.get("completed").getAsBoolean(),
                    q.get("xp").getAsInt()));
        }
        plugin.quests().update(uuid, list);
    }

    private void handleJoinAction(JsonObject msg) {
        if (!msg.has("mcUuid")) return;
        Player p = Bukkit.getPlayer(UUID.fromString(msg.get("mcUuid").getAsString()));
        if (p == null || !p.isOnline()) return;

        String action = msg.has("action") ? msg.get("action").getAsString() : "denied";
        String message = msg.has("message") ? msg.get("message").getAsString() : "";

        switch (action) {
            case "spectate" -> {
                SpectatorHelper.enter(p);
                if (!message.isBlank()) p.sendMessage("§b[BedWars.cash] §f" + message);
            }
            case "queued" -> {
                // Already in lobby after post-match return — don't re-enter spectator via stale backend phase.
                if (!lobby.isLobbyPlayer(p)) {
                    lobby.enterLobby(p);
                }
                if (!message.isBlank()) p.sendMessage("§b[BedWars.cash] §a" + message);
                scoreboard.apply(p);
            }
            default -> {
                lobby.enterLobby(p);
                if (!message.isBlank()) p.sendMessage("§b[BedWars.cash] §c" + message);
            }
        }
    }

    public String matchPhase() {
        return matchPhase;
    }

    // ---- Outbound helpers ----
    private void send(JsonObject obj) {
        WebSocket socket = this.ws;
        if (socket != null) socket.sendText(gson.toJson(obj), true);
        else plugin.getLogger().warning("Not connected to backend; dropping " + obj.get("type"));
    }

    public void setWallet(Player p, String address) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "setwallet");
        o.addProperty("mcUuid", p.getUniqueId().toString());
        o.addProperty("mcUsername", p.getName());
        o.addProperty("address", address);
        send(o);
    }

    public void verify(Player p, String code) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "verify");
        o.addProperty("mcUuid", p.getUniqueId().toString());
        o.addProperty("mcUsername", p.getName());
        o.addProperty("code", code);
        send(o);
    }

    public void queueJoin(Player p) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "queue_join");
        o.addProperty("mcUuid", p.getUniqueId().toString());
        send(o);
    }

    public void playerJoin(Player p) {
        playerJoin(p, 5);
    }

    private void playerJoin(Player p, int attemptsLeft) {
        if (!p.isOnline()) return;
        WebSocket socket = this.ws;
        if (socket == null) {
            if (attemptsLeft <= 0) {
                p.sendMessage("§c[BedWars.cash] Backend offline — try §f/queue§c when it's back.");
                return;
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> playerJoin(p, attemptsLeft - 1), 40L);
            return;
        }
        if (plugin.broadcast().isBroadcast(p)) {
            plugin.broadcast().onCastJoin(p);
        } else if ("live".equals(matchPhase) || "settling".equals(matchPhase)) {
            if (plugin.game().shouldEnterSpectator(p.getUniqueId())) {
                plugin.game().applySpectatorAfterRespawn(p);
            } else if (!plugin.game().isMatchPlayer(p)) {
                SpectatorHelper.enter(p);
            }
        }
        JsonObject o = new JsonObject();
        o.addProperty("type", "player_join");
        o.addProperty("mcUuid", p.getUniqueId().toString());
        o.addProperty("mcUsername", p.getName());
        send(o);
    }

    public void queueLeave(Player p) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "queue_leave");
        o.addProperty("mcUuid", p.getUniqueId().toString());
        send(o);
    }

    public void placeBet(Player p, String team, double amountSol) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "place_bet");
        o.addProperty("mcUuid", p.getUniqueId().toString());
        o.addProperty("team", team);
        o.addProperty("amountSol", amountSol);
        send(o);
    }

    public record MatchPlayerStat(
            String mcUuid,
            String mcUsername,
            int kills,
            int finalKills,
            int bedsBroken,
            int deaths
    ) {}

    public void matchResult(int matchId, String winningTeam, List<UUID> winners, List<MatchPlayerStat> playerStats) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "match_result");
        o.addProperty("matchId", matchId);
        o.addProperty("winningTeam", winningTeam);
        JsonArray arr = new JsonArray();
        for (UUID u : winners) arr.add(u.toString());
        o.add("winnerUuids", arr);
        JsonArray statsArr = new JsonArray();
        if (playerStats != null) {
            for (MatchPlayerStat s : playerStats) {
                JsonObject row = new JsonObject();
                row.addProperty("mcUuid", s.mcUuid());
                row.addProperty("mcUsername", s.mcUsername());
                row.addProperty("kills", s.kills());
                row.addProperty("finalKills", s.finalKills());
                row.addProperty("bedsBroken", s.bedsBroken());
                row.addProperty("deaths", s.deaths());
                statsArr.add(row);
            }
        }
        o.add("playerStats", statsArr);
        send(o);
    }

    public void matchResult(int matchId, String winningTeam, List<UUID> winners) {
        matchResult(matchId, winningTeam, winners, List.of());
    }

    public void matchAborted(int matchId, String reason) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "match_aborted");
        o.addProperty("matchId", matchId);
        o.addProperty("reason", reason);
        send(o);
    }

    public void broadcastCamera(int matchId, String team, String playerName) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "match_camera");
        o.addProperty("matchId", matchId);
        o.addProperty("team", team);
        o.addProperty("playerName", playerName);
        send(o);
    }

    public void clearBroadcastCamera() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "match_camera_clear");
        send(o);
    }

    public void cheatFlag(Player p, String check, String details) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "cheat_flag");
        o.addProperty("mcUuid", p.getUniqueId().toString());
        o.addProperty("check", check);
        o.addProperty("details", details);
        send(o);
    }

    public void forceStart(Player p) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "force_start");
        o.addProperty("mcUuid", p.getUniqueId().toString());
        send(o);
    }

    public void forceAbort(Player p, String reason) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "force_abort");
        o.addProperty("mcUuid", p.getUniqueId().toString());
        o.addProperty("reason", reason);
        send(o);
    }

    public void partyInvite(Player leader, Player target) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "party_invite");
        o.addProperty("mcUuid", leader.getUniqueId().toString());
        o.addProperty("leaderName", leader.getName());
        o.addProperty("targetUuid", target.getUniqueId().toString());
        send(o);
    }

    public void partyAccept(Player p) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "party_accept");
        o.addProperty("mcUuid", p.getUniqueId().toString());
        send(o);
    }

    public void partyLeave(Player p) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "party_leave");
        o.addProperty("mcUuid", p.getUniqueId().toString());
        send(o);
    }

    public void partyList(Player p) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "party_list");
        o.addProperty("mcUuid", p.getUniqueId().toString());
        send(o);
    }

    public JsonElement parse(String json) {
        return gson.fromJson(json, JsonElement.class);
    }
}
