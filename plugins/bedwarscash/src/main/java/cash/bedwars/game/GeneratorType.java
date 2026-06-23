package cash.bedwars.game;

import org.bukkit.Material;

/** Hypixel 4v4v4v4 generator tiers (tier I defaults). Delays are in ticks (20/s). */
public enum GeneratorType {
    IRON(Material.IRON_INGOT, 10, 128, true),
    GOLD(Material.GOLD_INGOT, 40, 32, true),
    DIAMOND(Material.DIAMOND, 600, 6, false),
    EMERALD(Material.EMERALD, 1200, 2, false);

    private final Material drop;
    private final int intervalTicks;
    private final int stackLimit;
    private final boolean perTeam;

    GeneratorType(Material drop, int intervalTicks, int stackLimit, boolean perTeam) {
        this.drop = drop;
        this.intervalTicks = intervalTicks;
        this.stackLimit = stackLimit;
        this.perTeam = perTeam;
    }

    public Material drop() { return drop; }
    public int intervalTicks() { return intervalTicks; }
    public int stackLimit() { return stackLimit; }
    public boolean perTeam() { return perTeam; }
}
