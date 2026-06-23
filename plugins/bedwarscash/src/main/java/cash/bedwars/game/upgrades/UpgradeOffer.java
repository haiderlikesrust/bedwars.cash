package cash.bedwars.game.upgrades;

import org.bukkit.Material;

import java.util.List;

public record UpgradeOffer(
        String id,
        String displayName,
        Material icon,
        int slot,
        int maxTier,
        Material currency,
        List<Integer> costs,
        List<String> lore
) {
    public int costForTier(int nextTier) {
        if (nextTier < 1 || nextTier > costs.size()) return -1;
        return costs.get(nextTier - 1);
    }
}
