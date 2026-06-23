package cash.bedwars.game.shop;

import cash.bedwars.game.SpecialItems;
import cash.bedwars.game.TeamColor;
import cash.bedwars.game.upgrades.TeamUpgradeState;
import cash.bedwars.game.upgrades.UpgradeAccess;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ShopPurchases {
    private static final Map<UUID, Map<String, Integer>> TOOL_TIERS = new HashMap<>();

    private ShopPurchases() {}

    public static void reset(Player player) {
        TOOL_TIERS.remove(player.getUniqueId());
    }

    public static void resetAll() {
        TOOL_TIERS.clear();
    }

    public static int toolTier(Player player, String toolId) {
        return TOOL_TIERS.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(toolId, 0);
    }

    public static boolean buy(Player player, ShopOffer offer, TeamColor team, TeamUpgradeState upgrades) {
        if (!hasCost(player, offer.currency(), offer.cost())) {
            player.sendMessage("§cYou don't have enough " + currencyLabel(offer.currency()) + "!");
            return false;
        }
        if (offer.isTieredTool() && offer.tier() != null && offer.tier() != toolTier(player, offer.id()) + 1) {
            player.sendMessage("§cUpgrade your tool tier first.");
            return false;
        }
        removeCost(player, offer.currency(), offer.cost());
        giveReward(player, offer, team, upgrades);
        if (offer.isTieredTool() && offer.tier() != null) {
            TOOL_TIERS.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(offer.id(), offer.tier());
        }
        player.sendMessage("§aPurchased §f" + offer.displayName().replaceAll("§.", ""));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_YES, 1f, 1f);
        return true;
    }

    private static void giveReward(Player player, ShopOffer offer, TeamColor team, TeamUpgradeState upgrades) {
        if (offer.armorSet() != ShopOffer.ArmorSet.NONE) {
            applyArmorSet(player, offer.armorSet());
            UpgradeAccess.applyGearUpgrades(player, upgrades, team);
            return;
        }
        Material mat = offer.material() == Material.WHITE_WOOL ? team.wool() : offer.material();
        ItemStack stack = new ItemStack(mat, offer.amount());

        if (offer.potionType() != null) {
            ItemMeta meta = stack.getItemMeta();
            if (meta instanceof PotionMeta pm) {
                pm.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection().deserialize(offer.inventoryName()));
                applyPotion(pm, offer.potionType());
                stack.setItemMeta(pm);
            }
            player.getInventory().addItem(stack);
            return;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize(offer.inventoryName()));
            for (Map.Entry<String, Integer> e : offer.enchantments().entrySet()) {
                Enchantment ench = mapEnchant(e.getKey());
                if (ench != null) meta.addEnchant(ench, e.getValue(), true);
            }
            if (offer.id().equals("kb_stick")) meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
            stack.setItemMeta(meta);
        }
        if (SpecialItems.isUtilityOffer(offer.id())) {
            SpecialItems.tag(stack, offer.id());
        }
        player.getInventory().addItem(stack);
        UpgradeAccess.applyGearUpgrades(player, upgrades, team);
    }

    private static Enchantment mapEnchant(String name) {
        return switch (name) {
            case "DIG_SPEED" -> Enchantment.EFFICIENCY;
            case "ARROW_DAMAGE" -> Enchantment.POWER;
            case "ARROW_KNOCKBACK" -> Enchantment.PUNCH;
            default -> Enchantment.getByName(name);
        };
    }

    private static void applyPotion(PotionMeta pm, PotionType type) {
        pm.clearCustomEffects();
        if (type == PotionType.SWIFTNESS) {
            pm.setBasePotionType(PotionType.SWIFTNESS);
            pm.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 45, 1), true);
        } else if (type == PotionType.LEAPING) {
            pm.setBasePotionType(PotionType.LEAPING);
            pm.addCustomEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 45, 4), true);
        } else if (type == PotionType.INVISIBILITY) {
            pm.setBasePotionType(PotionType.INVISIBILITY);
            pm.addCustomEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 30, 0), true);
        } else {
            pm.setBasePotionType(type);
        }
    }

    private static void applyArmorSet(Player player, ShopOffer.ArmorSet set) {
        PlayerInventory inv = player.getInventory();
        switch (set) {
            case CHAINMAIL -> {
                inv.setBoots(new ItemStack(Material.CHAINMAIL_BOOTS));
                inv.setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
                inv.setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
                inv.setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
            }
            case IRON -> {
                inv.setBoots(new ItemStack(Material.IRON_BOOTS));
                inv.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
                inv.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                inv.setHelmet(new ItemStack(Material.IRON_HELMET));
            }
            case DIAMOND -> {
                inv.setBoots(new ItemStack(Material.DIAMOND_BOOTS));
                inv.setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
                inv.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
                inv.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
            }
            default -> {}
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
