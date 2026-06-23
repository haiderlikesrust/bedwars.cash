package cash.bedwars;

import com.google.gson.JsonObject;

// Live match snapshot pushed by the backend (queue, phase, pools).
public class LiveState {
    private volatile int queueSize;
    private volatile int queueCapacity = 16;
    private volatile int matchId = -1;
    private volatile String phase = "lobby";
    private volatile String rewardPoolSol = "0.0000";
    private volatile String betPoolSol = "0.0000";

    public void updateFromState(JsonObject state) {
        if (state == null) return;
        if (state.has("queue")) {
            JsonObject q = state.getAsJsonObject("queue");
            if (q.has("size")) queueSize = q.get("size").getAsInt();
            if (q.has("capacity")) queueCapacity = q.get("capacity").getAsInt();
        }
        if (state.has("match") && !state.get("match").isJsonNull()) {
            JsonObject m = state.getAsJsonObject("match");
            if (m.has("id")) matchId = m.get("id").getAsInt();
            if (m.has("phase")) phase = m.get("phase").getAsString();
            if (m.has("rewardPoolLamports")) {
                rewardPoolSol = formatSol(m.get("rewardPoolLamports").getAsString());
            }
        } else {
            phase = "lobby";
            matchId = -1;
        }
    }

    public void updateFromOdds(JsonObject odds) {
        if (odds == null) return;
        if (odds.has("phase")) phase = odds.get("phase").getAsString();
        if (odds.has("totalPoolLamports")) {
            betPoolSol = formatSol(odds.get("totalPoolLamports").getAsString());
        }
    }

    private static String formatSol(String lamports) {
        try {
            double sol = Long.parseLong(lamports) / 1_000_000_000.0;
            return String.format("%.4f", sol);
        } catch (NumberFormatException e) {
            return "0.0000";
        }
    }

    public int queueSize() { return queueSize; }
    public int queueCapacity() { return queueCapacity; }
    public int matchId() { return matchId; }
    public String phase() { return phase; }
    public String rewardPoolSol() { return rewardPoolSol; }
    public String betPoolSol() { return betPoolSol; }

    public String phaseLabel() {
        return switch (phase) {
            case "live" -> "§cLIVE";
            case "settling" -> "§6SETTLING";
            case "idle" -> "§7IDLE";
            default -> "§aLOBBY";
        };
    }

    public boolean matchActive() {
        return "live".equals(phase) || "settling".equals(phase);
    }
}
