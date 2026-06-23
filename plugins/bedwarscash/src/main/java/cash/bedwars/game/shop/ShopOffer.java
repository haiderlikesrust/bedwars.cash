package cash.bedwars.game.shop;

import org.bukkit.Material;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Map;

public record ShopOffer(
        String id,
        Material material,
        int amount,
        int cost,
        Material currency,
        String displayName,
        List<String> lore,
        int slot,
        Map<String, Integer> enchantments,
        PotionType potionType,
        Integer tier,
        String inventoryName,
        ArmorSet armorSet
) {
    public enum ArmorSet { NONE, CHAINMAIL, IRON, DIAMOND }

    public boolean isTieredTool() {
        return tier != null && (id.equals("pickaxe") || id.equals("axe"));
    }
}
