package cash.bedwars;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class CashScoreboard {
    private final BedWarsCashPlugin plugin;
    private final LiveState state;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public CashScoreboard(BedWarsCashPlugin plugin, LiveState state) {
        this.plugin = plugin;
        this.state = state;
    }

    public void startUpdater() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, 40L);
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != org.bukkit.GameMode.SPECTATOR) apply(p);
        }
    }

    public void apply(Player p) {
        if (!p.isOnline()) return;
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective(
                "bwcash",
                Criteria.DUMMY,
                Component.text("BEDWARS.CASH")
                        .color(NamedTextColor.AQUA)
                        .decorate(TextDecoration.BOLD)
        );
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        boolean inMatch = state.matchActive() || plugin.game().isLive() || plugin.game().isStarting();
        int matchId = plugin.game().matchId() > 0 ? plugin.game().matchId() : state.matchId();
        String queueLine = inMatch
                ? (matchId > 0 ? "§fMatch §7#" + matchId : "§fStatus: §cIn match")
                : "§fQueue: §a" + state.queueSize() + "§7/§f" + state.queueCapacity();
        String statusLine = "§fStatus: " + (inMatch && !state.matchActive()
                ? (plugin.game().isStarting() ? "§eSTARTING" : "§cLIVE")
                : state.phaseLabel());

        String[] lines = {
                "§8§m─────────────",
                "§7devnet",
                " ",
                queueLine,
                statusLine,
                "§fReward: §6" + state.rewardPoolSol() + " SOL",
                "§fBet pool: §6" + state.betPoolSol() + " SOL",
                " ",
                "§f/bet §7- wager SOL",
                "§f/setwallet §7- payouts",
                "§ebedwars.cash",
                "§8§m─────────────",
        };

        int score = lines.length;
        ChatColor[] unique = ChatColor.values();
        for (String line : lines) {
            // Invisible unique entry — display text lives only in team prefix (no duplication).
            String entry = unique[score % unique.length].toString() + "§r";
            Team team = board.registerNewTeam("l" + score);
            team.addEntry(entry);
            team.prefix(LEGACY.deserialize(line));
            obj.getScore(entry).setScore(score);
            score--;
        }

        p.setScoreboard(board);
    }
}
