package cash.bedwars;

import cash.bedwars.game.TeamColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Progression-gated cosmetics. The backend reports each player's level over the
 * WebSocket; cosmetics scale with level: a coloured level badge (tab list, kill
 * feed, scoreboard), a lobby particle aura, and team-coloured win fireworks.
 */
public final class Cosmetics {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final BedWarsCashPlugin plugin;
    private final Map<UUID, Integer> levels = new ConcurrentHashMap<>();

    public Cosmetics(BedWarsCashPlugin plugin) {
        this.plugin = plugin;
    }

    public int level(UUID uuid) {
        return levels.getOrDefault(uuid, 1);
    }

    /** Set by the backend on join and after each settlement. */
    public void setLevel(UUID uuid, int level) {
        levels.put(uuid, Math.max(1, level));
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) applyTab(p);
    }

    public void forget(UUID uuid) {
        levels.remove(uuid);
    }

    // ── Tiers: higher levels unlock fancier badge colours and lobby auras ──
    private enum Tier {
        ROOKIE('7', null),
        BRONZE('a', Particle.HAPPY_VILLAGER),
        SILVER('b', Particle.FLAME),
        GOLD('6', Particle.END_ROD),
        DIAMOND('d', Particle.DRAGON_BREATH);

        final char color;
        final Particle aura;

        Tier(char color, Particle aura) {
            this.color = color;
            this.aura = aura;
        }
    }

    private static Tier tier(int level) {
        if (level >= 35) return Tier.DIAMOND;
        if (level >= 20) return Tier.GOLD;
        if (level >= 10) return Tier.SILVER;
        if (level >= 5) return Tier.BRONZE;
        return Tier.ROOKIE;
    }

    /** Legacy-coloured "[12] " badge for chat, kill feed, and the scoreboard. */
    public String badge(UUID uuid) {
        int lvl = level(uuid);
        return "§" + tier(lvl).color + "[" + lvl + "] ";
    }

    public void applyTab(Player p) {
        p.playerListName(LEGACY.deserialize(badge(p.getUniqueId()) + "§f" + p.getName()));
    }

    /** Repeating lobby aura — only for players standing in the lobby world. */
    public void startAuraTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR) continue;
                if (!plugin.worlds().isLobbyWorld(p.getWorld())) continue;
                Particle aura = tier(level(p.getUniqueId())).aura;
                if (aura == null) continue;
                p.getWorld().spawnParticle(aura, p.getLocation().add(0, 0.2, 0), 6, 0.3, 0.4, 0.3, 0.0);
            }
        }, 20L, 12L);
    }

    /** Team-coloured fireworks at each winner's feet. */
    public void celebrateWin(Collection<UUID> winners, TeamColor team) {
        Color color = teamColor(team);
        for (UUID u : winners) {
            Player p = Bukkit.getPlayer(u);
            if (p == null || !p.isOnline()) continue;
            launchFirework(p.getLocation(), color);
        }
    }

    private void launchFirework(Location loc, Color color) {
        Firework fw = loc.getWorld().spawn(loc, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .withColor(color)
                .withFade(Color.WHITE)
                .flicker(true)
                .with(FireworkEffect.Type.BALL_LARGE)
                .build());
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }

    private static Color teamColor(TeamColor team) {
        return switch (team) {
            case GREEN -> Color.LIME;
            case BLUE -> Color.BLUE;
            case RED -> Color.RED;
            case YELLOW -> Color.YELLOW;
        };
    }
}
