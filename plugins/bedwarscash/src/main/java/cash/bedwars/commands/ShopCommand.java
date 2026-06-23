package cash.bedwars.commands;

import cash.bedwars.game.GameManager;
import cash.bedwars.game.shop.ShopAccess;
import cash.bedwars.game.shop.ShopService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShopCommand implements CommandExecutor {
    private final GameManager game;
    private final ShopService shop;

    public ShopCommand(GameManager game, ShopService shop) {
        this.game = game;
        this.shop = shop;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        ShopAccess.tryOpen(p, game, shop);
        return true;
    }
}
