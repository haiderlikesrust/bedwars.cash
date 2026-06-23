package cash.bedwars.game.upgrades;

import cash.bedwars.game.GameManager;
import cash.bedwars.game.TeamColor;
import cash.bedwars.game.shop.ShopInventoryHolder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class UpgradeShopGui {
    public static final int SIZE = 27;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private UpgradeShopGui() {}

    public static void open(Player player, UpgradeCatalog catalog, GameManager game, TeamColor team) {
        ShopInventoryHolder holder = ShopInventoryHolder.upgrade();
        Inventory inv = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize(catalog.title()));
        holder.bind(inv);
        render(inv, catalog, game, team);
        player.openInventory(inv);
    }

    public static void render(Inventory inv, UpgradeCatalog catalog, GameManager game, TeamColor team) {
        inv.clear();
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        if (gm != null) {
            gm.displayName(LEGACY.deserialize(" "));
            glass.setItemMeta(gm);
        }
        for (int i = 0; i < SIZE; i++) inv.setItem(i, glass);

        for (UpgradeOffer offer : catalog.offers()) {
            int current = game.upgrades().tier(team, offer.id());
            int next = current + 1;
            boolean maxed = current >= offer.maxTier();
            ItemStack icon = new ItemStack(offer.icon());
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(LEGACY.deserialize(offer.displayName()
                        + (maxed ? " §a§lMAX" : " §7(Tier " + next + "/" + offer.maxTier() + ")")));
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                for (String line : offer.lore()) lore.add(LEGACY.deserialize(line));
                if (maxed) {
                    lore.add(LEGACY.deserialize("§a§lPURCHASED"));
                } else {
                    lore.add(LEGACY.deserialize("§7Cost: " + currencyColor(offer.currency())
                            + offer.costForTier(next) + " " + currencyName(offer.currency())));
                    lore.add(LEGACY.deserialize("§eClick to purchase!"));
                }
                meta.lore(lore);
                if (current > 0) meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                icon.setItemMeta(meta);
            }
            inv.setItem(offer.slot(), icon);
        }
    }

    public static UpgradeOffer offerAt(UpgradeCatalog catalog, int slot) {
        for (UpgradeOffer o : catalog.offers()) if (o.slot() == slot) return o;
        return null;
    }

    public static boolean isUpgradeInventory(Inventory inv) {
        return inv != null && inv.getHolder() instanceof ShopInventoryHolder h
                && h.kind() == ShopInventoryHolder.Kind.UPGRADE;
    }

    public static boolean isUpgradeTitle(String title, UpgradeCatalog catalog) {
        if (title == null) return false;
        String plain = title.replaceAll("§.", "");
        String expected = catalog.title().replaceAll("§.", "");
        return plain.equals(expected) || plain.contains("Team Upgrades") || plain.contains("Upgrades");
    }

    private static String currencyName(Material mat) {
        return switch (mat) {
            case GOLD_INGOT -> "gold";
            case EMERALD -> "emeralds";
            case DIAMOND -> "diamonds";
            default -> "iron";
        };
    }

    private static String currencyColor(Material mat) {
        return switch (mat) {
            case GOLD_INGOT -> "§6";
            case EMERALD -> "§2";
            case DIAMOND -> "§b";
            default -> "§f";
        };
    }
}
