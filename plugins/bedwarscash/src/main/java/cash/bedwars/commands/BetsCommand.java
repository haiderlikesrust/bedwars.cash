package cash.bedwars.commands;

import cash.bedwars.OddsCache;
import com.google.gson.JsonObject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// Shows the live parimutuel board: each team's pool and current dynamic multiplier.
public class BetsCommand implements CommandExecutor {
    private final OddsCache oddsCache;

    public BetsCommand(OddsCache oddsCache) {
        this.oddsCache = oddsCache;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        JsonObject odds = oddsCache.latest();
        if (odds == null) {
            p.sendMessage("§7No live odds yet. Betting opens in the lobby.");
            return true;
        }
        p.sendMessage("§b===== Live Betting Board =====");
        double total = lamportsToSol(odds.get("totalPoolLamports").getAsString());
        p.sendMessage("§7Total pool: §f" + String.format("%.4f", total) + " SOL §7(rake "
                + (odds.get("rakeFraction").getAsDouble() * 100) + "%)");
        for (var el : odds.getAsJsonArray("teams")) {
            JsonObject t = el.getAsJsonObject();
            String team = t.get("team").getAsString();
            double pool = lamportsToSol(t.get("poolLamports").getAsString());
            double mult = t.get("impliedMultiplier").getAsDouble();
            int bettors = t.get("bettors").getAsInt();
            p.sendMessage(color(team) + team + " §7pool=§f" + String.format("%.4f", pool)
                    + " §7| bettors=§f" + bettors
                    + " §7| §epays §f" + String.format("%.2fx", mult) + " §7if it wins");
        }
        p.sendMessage("§7Bet with §f/bet <team> <amountSol>");
        return true;
    }

    private static double lamportsToSol(String lamports) {
        return Double.parseDouble(lamports) / 1_000_000_000d;
    }

    private static String color(String team) {
        return switch (team) {
            case "GREEN" -> "§a";
            case "BLUE" -> "§9";
            case "RED" -> "§c";
            case "YELLOW" -> "§e";
            default -> "§f";
        };
    }
}
