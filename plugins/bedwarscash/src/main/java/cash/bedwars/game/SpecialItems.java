package cash.bedwars.game;

import cash.bedwars.BedWarsCashPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;

public final class SpecialItems {
    private static NamespacedKey specialKey;

    private SpecialItems() {}

    public static void init(BedWarsCashPlugin plugin) {
        specialKey = new NamespacedKey(plugin, "special_item");
    }

    public static void tag(ItemStack stack, String id) {
        if (stack == null || id == null) return;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(specialKey, PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
    }

    public static String id(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(specialKey, PersistentDataType.STRING);
    }

    public static boolean isSpecial(ItemStack stack, String id) {
        String found = id(stack);
        return found != null && found.equals(id);
    }

    public static final Set<String> UTILITY_IDS = Set.of(
            "fireball", "bridge_egg", "tnt", "bedbug", "tracker", "dream_defender", "tower"
    );

    public static boolean isUtilityOffer(String offerId) {
        return UTILITY_IDS.contains(offerId);
    }
}
