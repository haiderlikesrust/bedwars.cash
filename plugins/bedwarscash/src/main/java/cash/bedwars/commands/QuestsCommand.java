package cash.bedwars.commands;

import cash.bedwars.QuestBoard;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class QuestsCommand implements CommandExecutor {
    private final QuestBoard quests;

    public QuestsCommand(QuestBoard quests) {
        this.quests = quests;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        List<QuestBoard.Quest> list = quests.get(p.getUniqueId());
        p.sendMessage("§b§lDaily Quests §7(reset daily)");
        if (list.isEmpty()) {
            p.sendMessage("§7Loading your quests… try again in a moment.");
            return true;
        }
        for (QuestBoard.Quest q : list) {
            int shown = Math.min(q.progress(), q.target());
            String head = q.completed() ? "§a✓ " : "§e• ";
            p.sendMessage(head + "§f" + q.description() + " §7(" + shown + "/" + q.target() + ") §6+" + q.xp() + " XP");
            p.sendMessage("   " + progressBar(q.progress(), q.target()));
        }
        return true;
    }

    private static String progressBar(int progress, int target) {
        int slots = 10;
        int filled = target > 0 ? Math.min(slots, (int) Math.round((double) progress / target * slots)) : slots;
        StringBuilder sb = new StringBuilder("§8[");
        for (int i = 0; i < slots; i++) sb.append(i < filled ? "§a|" : "§7|");
        sb.append("§8]");
        return sb.toString();
    }
}
