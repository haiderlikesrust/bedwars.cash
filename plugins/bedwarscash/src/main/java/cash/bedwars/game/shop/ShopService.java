package cash.bedwars.game.shop;

import cash.bedwars.game.GameManager;
import cash.bedwars.game.TeamColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopService {
    private final ShopCatalog catalog;
    private final GameManager game;
    private final Map<UUID, String> openCategory = new ConcurrentHashMap<>();

    public ShopService(ShopCatalog catalog, GameManager game) {
        this.catalog = catalog;
        this.game = game;
    }

    public ShopCatalog catalog() { return catalog; }

    public void open(Player player, TeamColor team) {
        openCategory.put(player.getUniqueId(), defaultCategory());
        ShopGui.open(player, catalog, team, defaultCategory());
    }

    public void handleClick(Player player, TeamColor team, int rawSlot) {
        String category = openCategory.getOrDefault(player.getUniqueId(), defaultCategory());
        String switchTo = ShopGui.categoryAt(catalog, rawSlot);
        if (switchTo != null) {
            openCategory.put(player.getUniqueId(), switchTo);
            if (player.getOpenInventory().getTopInventory().getSize() == ShopGui.SIZE) {
                ShopGui.render(player.getOpenInventory().getTopInventory(), catalog, team, switchTo, player);
            }
            return;
        }
        ShopOffer offer = ShopGui.offerAt(catalog, category, rawSlot, player);
        if (offer == null) return;
        if (ShopPurchases.buy(player, offer, team, game.upgrades())) {
            ShopGui.render(player.getOpenInventory().getTopInventory(), catalog, team, category, player);
        }
    }

    public void close(Player player) {
        openCategory.remove(player.getUniqueId());
    }

    private String defaultCategory() {
        return catalog.categories().isEmpty() ? "blocks" : catalog.categories().getFirst().id();
    }
}
