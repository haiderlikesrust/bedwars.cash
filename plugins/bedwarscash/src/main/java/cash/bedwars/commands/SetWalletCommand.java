package cash.bedwars.commands;

import cash.bedwars.BackendClient;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetWalletCommand implements CommandExecutor {
    private final BackendClient backend;

    public SetWalletCommand(BackendClient backend) {
        this.backend = backend;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length != 1) {
            p.sendMessage("§cUsage: /setwallet <solana_address>");
            return true;
        }
        String address = args[0].trim();
        if (address.length() < 32 || address.length() > 44) {
            p.sendMessage("§cThat does not look like a valid Solana address.");
            return true;
        }
        backend.setWallet(p, address);
        p.sendMessage("§aPayout wallet submitted. Winnings will be sent there.");
        return true;
    }
}
