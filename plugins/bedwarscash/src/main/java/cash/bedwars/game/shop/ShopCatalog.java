package cash.bedwars.game.shop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.util.*;

/** Loads BedWars2023-compatible shop.yml (categories, costs, slots). */
public final class ShopCatalog {
    private final String title;
    private final List<ShopCategory> categories;
    private final Map<String, List<ShopOffer>> sections;

    private ShopCatalog(String title, List<ShopCategory> categories, Map<String, List<ShopOffer>> sections) {
        this.title = title;
        this.categories = categories;
        this.sections = sections;
    }

    public String title() { return title; }
    public List<ShopCategory> categories() { return categories; }
    public List<ShopOffer> section(String id) { return sections.getOrDefault(id, List.of()); }

    public static ShopCatalog load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "shop.yml");
        if (!file.exists()) plugin.saveResource("shop.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        String title = color(yaml.getString("shop-title", "&8Item Shop"));
        List<ShopCategory> categories = new ArrayList<>();
        ConfigurationSection catSec = yaml.getConfigurationSection("categories");
        if (catSec != null) {
            for (String key : catSec.getKeys(false)) {
                ConfigurationSection c = catSec.getConfigurationSection(key);
                if (c == null) continue;
                Material icon = Material.matchMaterial(c.getString("icon", "CHEST"));
                if (icon == null) icon = Material.CHEST;
                categories.add(new ShopCategory(
                        key,
                        color(c.getString("name", key)),
                        c.getInt("slot", 0),
                        icon
                ));
            }
            categories.sort(Comparator.comparingInt(ShopCategory::slot));
        }

        Map<String, List<ShopOffer>> sections = new LinkedHashMap<>();
        ConfigurationSection secRoot = yaml.getConfigurationSection("sections");
        if (secRoot != null) {
            for (String sectionId : secRoot.getKeys(false)) {
                ConfigurationSection itemsSec = secRoot.getConfigurationSection(sectionId + ".items");
                if (itemsSec == null) continue;
                List<ShopOffer> offers = new ArrayList<>();
                for (String itemId : itemsSec.getKeys(false)) {
                    ConfigurationSection item = itemsSec.getConfigurationSection(itemId);
                    if (item == null) continue;
                    if (item.contains("tiers")) {
                        offers.addAll(parseTiered(itemId, item));
                    } else {
                        ShopOffer offer = parseOffer(itemId, item, null);
                        if (offer != null) offers.add(offer);
                    }
                }
                sections.put(sectionId, offers);
            }
        }
        return new ShopCatalog(title, categories, sections);
    }

    private static List<ShopOffer> parseTiered(String itemId, ConfigurationSection item) {
        ConfigurationSection tiers = item.getConfigurationSection("tiers");
        if (tiers == null) return List.of();
        List<ShopOffer> list = new ArrayList<>();
        for (String tierKey : tiers.getKeys(false)) {
            int tier = Integer.parseInt(tierKey);
            ConfigurationSection t = tiers.getConfigurationSection(tierKey);
            if (t == null) continue;
            ConfigurationSection merged = cloneSection(item, t);
            merged.set("slot", item.getInt("slot", 19));
            ShopOffer offer = parseOffer(itemId, merged, tier);
            if (offer != null) list.add(offer);
        }
        return list;
    }

    private static ConfigurationSection cloneSection(ConfigurationSection base, ConfigurationSection tier) {
        YamlConfiguration tmp = new YamlConfiguration();
        for (String key : base.getKeys(false)) {
            if ("tiers".equals(key)) continue;
            tmp.set(key, base.get(key));
        }
        for (String key : tier.getKeys(false)) tmp.set(key, tier.get(key));
        return tmp;
    }

    private static ShopOffer parseOffer(String itemId, ConfigurationSection item, Integer tier) {
        String matName = item.getString("material");
        if (matName == null) return null;
        Material material = Material.matchMaterial(matName);
        if (material == null) return null;

        String currencyName = item.getString("currency", "IRON_INGOT");
        Material currency = Material.matchMaterial(currencyName);
        if (currency == null) currency = Material.IRON_INGOT;

        String name = color(item.getString("shop_name", item.getString("name", itemId)));
        List<String> lore = new ArrayList<>();
        for (String line : item.getStringList("lore")) lore.add(color(line));

        Map<String, Integer> enchants = new HashMap<>();
        ConfigurationSection enchSec = item.getConfigurationSection("enchantments");
        if (enchSec != null) {
            for (String ench : enchSec.getKeys(false)) enchants.put(ench, enchSec.getInt(ench));
        }

        PotionType potionType = null;
        String potionRaw = item.getString("potion_type");
        if (potionRaw != null) {
            potionType = switch (potionRaw) {
                case "SPEED" -> PotionType.SWIFTNESS;
                case "JUMP" -> PotionType.LEAPING;
                case "INVISIBILITY" -> PotionType.INVISIBILITY;
                default -> {
                    try { yield PotionType.valueOf(potionRaw); }
                    catch (IllegalArgumentException ex) { yield null; }
                }
            };
        }

        return new ShopOffer(
                itemId,
                material,
                item.getInt("amount", 1),
                item.getInt("cost", 0),
                currency,
                name,
                lore,
                item.getInt("slot", 19),
                enchants,
                potionType,
                tier,
                item.getString("inventory_name") != null ? color(item.getString("inventory_name")) : name,
                armorSet(material)
        );
    }

    private static ShopOffer.ArmorSet armorSet(Material material) {
        return switch (material) {
            case CHAINMAIL_BOOTS -> ShopOffer.ArmorSet.CHAINMAIL;
            case IRON_BOOTS -> ShopOffer.ArmorSet.IRON;
            case DIAMOND_BOOTS -> ShopOffer.ArmorSet.DIAMOND;
            default -> ShopOffer.ArmorSet.NONE;
        };
    }

    private static String color(String raw) {
        if (raw == null) return "";
        return raw.replace('&', '§');
    }
}
