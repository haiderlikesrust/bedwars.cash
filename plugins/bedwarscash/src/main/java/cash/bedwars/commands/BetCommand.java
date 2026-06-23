package cash.bedwars.commands;

import cash.bedwars.BackendClient;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;

public class BetCommand implements CommandExecutor {
    private static final Set<String> TEAMS = Set.of("GREEN", "BLUE", "RED", "YELLOW");
    private final BackendClient backend;

    public BetCommand(BackendClient backend) {
        this.backend = backend;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length != 2) {
            p.sendMessage("§cUsage: /bet <green|blue|red|yellow> <amountSol>");
            return true;
        }
        String team = args[0].toUpperCase();
        if (!TEAMS.contains(team)) {
            p.sendMessage("§cTeam must be green, blue, red, or yellow.");
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            p.sendMessage("§cAmount must be a number, e.g. 0.05");
            return true;
        }
        if (amount <= 0) {
            p.sendMessage("§cAmount must be positive.");
            return true;
        }
        backend.placeBet(p, team, amount);
        p.sendMessage("§7Placing bet of §f" + amount + " SOL §7on §f" + team + "§7...");
        return true;
    }
}
