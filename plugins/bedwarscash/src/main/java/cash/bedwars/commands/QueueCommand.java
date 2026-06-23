package cash.bedwars.commands;

import cash.bedwars.BackendClient;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class QueueCommand implements CommandExecutor {
    private final BackendClient backend;

    public QueueCommand(BackendClient backend) {
        this.backend = backend;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("leave")) {
            backend.queueLeave(p);
            p.sendMessage("§7Left the queue.");
            return true;
        }
        backend.queueJoin(p);
        return true;
    }
}
