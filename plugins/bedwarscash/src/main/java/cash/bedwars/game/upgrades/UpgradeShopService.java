package cash.bedwars.game.upgrades;

import cash.bedwars.game.GameManager;
import cash.bedwars.game.TeamColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class UpgradeShopService {
    private final UpgradeCatalog catalog;
    private final GameManager game;

    public UpgradeShopService(UpgradeCatalog catalog, GameManager game) {
        this.catalog = catalog;
        this.game = game;
    }

    public UpgradeCatalog catalog() { return catalog; }

    public void open(Player player, TeamColor team) {
        UpgradeShopGui.open(player, catalog, game, team);
    }

    public void handleClick(Player player, TeamColor team, int rawSlot) {
        UpgradeOffer offer = UpgradeShopGui.offerAt(catalog, rawSlot);
        if (offer == null) return;

        int current = game.upgrades().tier(team, offer.id());
        if (current >= offer.maxTier()) {
            player.sendMessage("§cThat upgrade is already maxed.");
            return;
        }
        int next = current + 1;
        int cost = offer.costForTier(next);
        if (cost < 0) return;
        if (!hasCost(player, offer.currency(), cost)) {
            player.sendMessage("§cYou don't have enough " + currencyLabel(offer.currency()) + "!");
            return;
        }
        removeCost(player, offer.currency(), cost);
        game.upgrades().increment(team, offer.id());
        String plain = offer.displayName().replaceAll("§.", "");
        Bukkit.broadcastMessage(team.chat() + "§l" + plain + " §7purchased (Tier " + next + ")!");
        game.applyTeamUpgradesToOnline(team);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_YES, 1f, 1.2f);
        if (player.getOpenInventory().getTopInventory().getSize() == UpgradeShopGui.SIZE) {
            UpgradeShopGui.render(player.getOpenInventory().getTopInventory(), catalog, game, team);
        }
    }

    private static boolean hasCost(Player p, Material mat, int amt) {
        int count = 0;
        for (ItemStack stack : p.getInventory().getContents()) {
            if (stack != null && stack.getType() == mat) count += stack.getAmount();
        }
        return count >= amt;
    }

    private static void removeCost(Player p, Material mat, int amt) {
        int left = amt;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != mat) continue;
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) contents[i] = null;
            left -= take;
        }
        p.getInventory().setContents(contents);
    }

    private static String currencyLabel(Material mat) {
        return switch (mat) {
            case GOLD_INGOT -> "gold";
            case DIAMOND -> "diamonds";
            case EMERALD -> "emeralds";
            default -> "iron";
        };
    }
}
