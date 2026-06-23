package cash.bedwars.game;

import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Material;

public enum TeamColor {
    GREEN("GREEN", ChatColor.GREEN, DyeColor.LIME, Material.LIME_WOOL, Material.LIME_BED),
    BLUE("BLUE", ChatColor.BLUE, DyeColor.LIGHT_BLUE, Material.LIGHT_BLUE_WOOL, Material.LIGHT_BLUE_BED),
    RED("RED", ChatColor.RED, DyeColor.RED, Material.RED_WOOL, Material.RED_BED),
    YELLOW("YELLOW", ChatColor.YELLOW, DyeColor.YELLOW, Material.YELLOW_WOOL, Material.YELLOW_BED);

    private final String id;
    private final ChatColor chat;
    private final DyeColor dye;
    private final Material wool;
    private final Material bed;

    TeamColor(String id, ChatColor chat, DyeColor dye, Material wool, Material bed) {
        this.id = id;
        this.chat = chat;
        this.dye = dye;
        this.wool = wool;
        this.bed = bed;
    }

    public String id() { return id; }
    public ChatColor chat() { return chat; }
    public DyeColor dye() { return dye; }
    public Material wool() { return wool; }
    public Material bed() { return bed; }

    public static TeamColor fromId(String raw) {
        if (raw == null) return null;
        for (TeamColor t : values()) {
            if (t.id.equalsIgnoreCase(raw)) return t;
        }
        return null;
    }
}
