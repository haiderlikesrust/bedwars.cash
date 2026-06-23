package cash.bedwars.game.upgrades;

import cash.bedwars.BedWarsCashPlugin;
import cash.bedwars.game.GameManager;
import cash.bedwars.game.TeamColor;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class UpgradeAccess {
    private static NamespacedKey upgradeNpcKey;

    private UpgradeAccess() {}

    public static void init(BedWarsCashPlugin plugin) {
        upgradeNpcKey = new NamespacedKey(plugin, "upgrade_npc");
    }

    public static NamespacedKey upgradeNpcKey() {
        return upgradeNpcKey;
    }

    public static boolean tryOpen(Player player, GameManager game, UpgradeShopService upgrades) {
        if (!game.isLive()) {
            player.sendMessage("§cUpgrades unlock when the match goes live.");
            return false;
        }
        if (!game.isActiveFighter(player)) {
            player.sendMessage("§cSpectators cannot buy upgrades.");
            return false;
        }
        TeamColor team = game.teamOf(player.getUniqueId());
        if (team == null) {
            player.sendMessage("§cYou are not on a team for this match.");
            return false;
        }
        upgrades.open(player, team);
        return true;
    }

    /** Apply team sharpness/protection to swords and armor in inventory. */
    public static void applyGearUpgrades(Player player, TeamUpgradeState state, TeamColor team) {
        int sharp = state.sharpness(team);
        int prot = state.protection(team);
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) continue;
            applyToStack(stack, sharp, prot);
        }
        for (ItemStack stack : player.getInventory().getArmorContents()) {
            if (stack == null) continue;
            applyToStack(stack, sharp, prot);
        }
    }

    private static void applyToStack(ItemStack stack, int sharp, int prot) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        String name = stack.getType().name();
        if (name.endsWith("_SWORD") && sharp > 0) {
            meta.addEnchant(Enchantment.SHARPNESS, sharp, true);
        }
        if ((name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")) && prot > 0) {
            meta.addEnchant(Enchantment.PROTECTION, prot, true);
        }
        stack.setItemMeta(meta);
    }
}
