package cash.bedwars.commands;

import cash.bedwars.game.GameManager;
import cash.bedwars.game.upgrades.UpgradeAccess;
import cash.bedwars.game.upgrades.UpgradeShopService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class UpgradeCommand implements CommandExecutor {
    private final GameManager game;
    private final UpgradeShopService upgrades;

    public UpgradeCommand(GameManager game, UpgradeShopService upgrades) {
        this.game = game;
        this.upgrades = upgrades;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        UpgradeAccess.tryOpen(p, game, upgrades);
        return true;
    }
}
