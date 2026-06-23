package cash.bedwars.commands;

import cash.bedwars.BackendClient;
import cash.bedwars.game.GameManager;
import cash.bedwars.game.TeamColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Set;

public class ResultCommand implements CommandExecutor {
    private static final Set<String> TEAMS = Set.of("GREEN", "BLUE", "RED", "YELLOW");
    private final BackendClient backend;
    private final GameManager game;

    public ResultCommand(BackendClient backend, GameManager game) {
        this.backend = backend;
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("bedwarscash.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length != 1 || !TEAMS.contains(args[0].toUpperCase())) {
            sender.sendMessage("§cUsage: /bwresult <green|blue|red|yellow>");
            return true;
        }
        TeamColor team = TeamColor.fromId(args[0]);
        if (team == null) {
            sender.sendMessage("§cInvalid team.");
            return true;
        }
        if (!game.isLive()) {
            sender.sendMessage("§cNo active match.");
            return true;
        }
        game.reportWinner(team);
        sender.sendMessage("§aReported winner: " + team.id());
        return true;
    }
}
