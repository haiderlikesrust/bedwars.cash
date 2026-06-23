package cash.bedwars.game.shop;

import cash.bedwars.game.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.*;

public final class ShopGui {
    public static final int SIZE = 54;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private ShopGui() {}

    public static void open(Player player, ShopCatalog catalog, TeamColor team, String categoryId) {
        ShopInventoryHolder holder = ShopInventoryHolder.item();
        Inventory inv = Bukkit.createInventory(holder, SIZE, LEGACY.deserialize(catalog.title()));
        holder.bind(inv);
        render(inv, catalog, team, categoryId, player);
        player.openInventory(inv);
    }

    public static void render(Inventory inv, ShopCatalog catalog, TeamColor team, String categoryId, Player player) {
        inv.clear();
        fillFrame(inv);

        String active = categoryId;
        for (ShopCategory cat : catalog.categories()) {
            boolean selected = cat.id().equals(active);
            ItemStack icon = new ItemStack(cat.icon());
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(LEGACY.deserialize((selected ? "§a" : "§e") + stripColor(cat.name())));
                meta.lore(List.of(
                        LEGACY.deserialize(selected ? "§aSELECTED" : "§eClick to view!")
                ));
                if (selected) meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                icon.setItemMeta(meta);
            }
            inv.setItem(cat.slot(), icon);
        }

        for (ShopOffer offer : catalog.section(active)) {
            if (!canShowTiered(offer, player)) continue;
            inv.setItem(offer.slot(), displayItem(offer, team));
        }
    }

    private static boolean canShowTiered(ShopOffer offer, Player player) {
        if (!offer.isTieredTool()) return true;
        int owned = ShopPurchases.toolTier(player, offer.id());
        int showTier = owned + 1;
        return offer.tier() != null && offer.tier() == showTier;
    }

    public static ItemStack displayItem(ShopOffer offer, TeamColor team) {
        Material mat = offer.material() == Material.WHITE_WOOL ? team.wool() : offer.material();
        ItemStack stack = new ItemStack(mat, Math.max(1, offer.amount()));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.displayName(LEGACY.deserialize(offer.displayName()));
        List<Component> lore = new ArrayList<>();
        for (String line : offer.lore()) lore.add(LEGACY.deserialize(line));
        String currency = currencyName(offer.currency());
        lore.add(LEGACY.deserialize("§7Cost: " + currencyColor(offer.currency()) + offer.cost() + " " + currency));
        meta.lore(lore);

        for (Map.Entry<String, Integer> e : offer.enchantments().entrySet()) {
            Enchantment ench = Enchantment.getByName(e.getKey());
            if (ench != null) meta.addEnchant(ench, e.getValue(), true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }

    public static ShopOffer offerAt(ShopCatalog catalog, String categoryId, int slot, Player player) {
        for (ShopOffer offer : catalog.section(categoryId)) {
            if (offer.slot() != slot) continue;
            if (!canShowTiered(offer, player)) continue;
            return offer;
        }
        return null;
    }

    public static String categoryAt(ShopCatalog catalog, int slot) {
        for (ShopCategory cat : catalog.categories()) {
            if (cat.slot() == slot) return cat.id();
        }
        return null;
    }

    public static boolean isShopInventory(Inventory inv) {
        return inv != null && inv.getHolder() instanceof ShopInventoryHolder h && h.kind() == ShopInventoryHolder.Kind.ITEM;
    }

    public static boolean isShopTitle(String plain, ShopCatalog catalog) {
        String expected = stripColor(catalog.title());
        return expected.equals(plain) || plain.contains("Item Shop");
    }

    private static void fillFrame(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            glass.setItemMeta(meta);
        }
        for (int i = 9; i <= 17; i++) inv.setItem(i, glass);
    }

    private static String stripColor(String s) {
        return s.replaceAll("§.", "");
    }

    private static String currencyName(Material mat) {
        return switch (mat) {
            case GOLD_INGOT -> "Gold";
            case DIAMOND -> "Diamond";
            case EMERALD -> "Emerald";
            default -> "Iron";
        };
    }

    private static String currencyColor(Material mat) {
        return switch (mat) {
            case GOLD_INGOT -> "§6";
            case DIAMOND -> "§b";
            case EMERALD -> "§2";
            default -> "§f";
        };
    }
}
