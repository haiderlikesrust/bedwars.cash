package cash.bedwars.game.upgrades;

import cash.bedwars.game.TeamColor;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Per-team upgrade tiers for the active match. */
public final class TeamUpgradeState {
    private final Map<TeamColor, Map<String, Integer>> tiers = new EnumMap<>(TeamColor.class);

    public TeamUpgradeState() {
        for (TeamColor t : TeamColor.values()) tiers.put(t, new HashMap<>());
    }

    public int tier(TeamColor team, String upgradeId) {
        return tiers.getOrDefault(team, Map.of()).getOrDefault(upgradeId, 0);
    }

    public int increment(TeamColor team, String upgradeId) {
        Map<String, Integer> map = tiers.computeIfAbsent(team, k -> new HashMap<>());
        int next = map.getOrDefault(upgradeId, 0) + 1;
        map.put(upgradeId, next);
        return next;
    }

    public void clear() {
        for (TeamColor t : TeamColor.values()) tiers.get(t).clear();
    }

    public int sharpness(TeamColor team) { return tier(team, "sharpness"); }
    public int protection(TeamColor team) { return tier(team, "protection"); }
    public int forge(TeamColor team) { return tier(team, "forge"); }
    public int maniac(TeamColor team) { return tier(team, "maniac"); }
}
