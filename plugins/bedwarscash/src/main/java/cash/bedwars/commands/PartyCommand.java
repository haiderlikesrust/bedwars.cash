package cash.bedwars.commands;

import cash.bedwars.BackendClient;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PartyCommand implements CommandExecutor {
    private final BackendClient backend;

    public PartyCommand(BackendClient backend) {
        this.backend = backend;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            p.sendMessage("§e/party invite <player> §7· §e/party accept §7· §e/party leave §7· §e/party list");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "invite" -> {
                if (args.length < 2) {
                    p.sendMessage("§cUsage: /party invite <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    p.sendMessage("§cThat player is not online.");
                    return true;
                }
                backend.partyInvite(p, target);
            }
            case "accept" -> backend.partyAccept(p);
            case "leave" -> backend.partyLeave(p);
            case "list" -> backend.partyList(p);
            default -> p.sendMessage("§cUnknown subcommand. Use invite, accept, leave, or list.");
        }
        return true;
    }
}
