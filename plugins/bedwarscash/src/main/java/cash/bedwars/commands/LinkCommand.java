package cash.bedwars.commands;

import cash.bedwars.BackendClient;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LinkCommand implements CommandExecutor {
    private final BackendClient backend;

    public LinkCommand(BackendClient backend) {
        this.backend = backend;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length != 1) {
            p.sendMessage("§cUsage: /bwlink <code>  (get a code at bedwars.cash/bet)");
            return true;
        }
        backend.verify(p, args[0].trim());
        p.sendMessage("§7Linking code submitted...");
        return true;
    }
}
