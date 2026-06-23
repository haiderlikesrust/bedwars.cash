package cash.bedwars;

import com.google.gson.JsonObject;

// Holds the most recent odds snapshot pushed by the backend, for the /bets board.
public class OddsCache {
    private volatile JsonObject latest;

    public void update(JsonObject odds) {
        this.latest = odds;
    }

    public JsonObject latest() {
        return latest;
    }
}
