package cash.bedwars.game.shop;

import cash.bedwars.BedWarsCashPlugin;
import cash.bedwars.game.GameManager;
import cash.bedwars.game.TeamColor;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class ShopAccess {
    private static NamespacedKey shopItemKey;
    private static NamespacedKey shopNpcKey;

    private ShopAccess() {}

    public static void init(BedWarsCashPlugin plugin) {
        shopItemKey = new NamespacedKey(plugin, "shop_item");
        shopNpcKey = new NamespacedKey(plugin, "shop_npc");
    }

    public static NamespacedKey shopNpcKey() {
        return shopNpcKey;
    }

    public static ItemStack createShopItem() {
        ItemStack stack = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§a§lITEM SHOP"));
            meta.lore(java.util.List.of(
                    Component.text("§7Right-click to open"),
                    Component.text("§7or use §f/shop")
            ));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(shopItemKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static boolean isShopItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer()
                .has(shopItemKey, PersistentDataType.BYTE);
    }

    public static boolean tryOpen(Player player, GameManager game, ShopService shop) {
        if (!game.isLive()) {
            player.sendMessage("§cShop opens when the match goes live (after countdown).");
            return false;
        }
        if (!game.isActiveFighter(player)) {
            player.sendMessage("§cSpectators cannot use the shop.");
            return false;
        }
        TeamColor team = game.teamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage("§cYou are not on a team for this match.");
            return false;
        }
        shop.open(player, team);
        return true;
    }
}
