package cash.bedwars.game;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Shared item rules for BedWars gear (no durability bar on tools/weapons/armor). */
public final class GameItems {
    private GameItems() {}

    public static boolean usesDurability(Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.endsWith("_SWORD")
                || name.endsWith("_PICKAXE")
                || name.endsWith("_AXE")
                || name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }

    public static void markPermanent(ItemStack stack) {
        if (stack == null || !usesDurability(stack.getType())) return;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        stack.setItemMeta(meta);
    }
}
