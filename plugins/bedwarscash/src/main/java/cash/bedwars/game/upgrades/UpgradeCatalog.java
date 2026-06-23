package cash.bedwars.game.upgrades;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class UpgradeCatalog {
    private final String title;
    private final List<UpgradeOffer> offers;

    private UpgradeCatalog(String title, List<UpgradeOffer> offers) {
        this.title = title;
        this.offers = offers;
    }

    public String title() { return title; }
    public List<UpgradeOffer> offers() { return offers; }

    public UpgradeOffer byId(String id) {
        for (UpgradeOffer o : offers) if (o.id().equals(id)) return o;
        return null;
    }

    public static UpgradeCatalog load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "upgrades.yml");
        if (!file.exists()) plugin.saveResource("upgrades.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String title = color(yaml.getString("shop-title", "&8Team Upgrades"));
        List<UpgradeOffer> offers = new ArrayList<>();
        ConfigurationSection root = yaml.getConfigurationSection("upgrades");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) continue;
                Material icon = Material.matchMaterial(s.getString("icon", "PAPER"));
                if (icon == null) icon = Material.PAPER;
                Material currency = Material.matchMaterial(s.getString("currency", "DIAMOND"));
                if (currency == null) currency = Material.DIAMOND;
                List<Integer> costs = s.getIntegerList("costs");
                List<String> lore = new ArrayList<>();
                for (String line : s.getStringList("lore")) lore.add(color(line));
                offers.add(new UpgradeOffer(
                        id,
                        color(s.getString("name", id)),
                        icon,
                        s.getInt("slot", 13),
                        s.getInt("max-tier", 4),
                        currency,
                        costs,
                        lore
                ));
            }
        }
        return new UpgradeCatalog(title, offers);
    }

    private static String color(String s) {
        return s == null ? "" : s.replace('&', '§');
    }
}
