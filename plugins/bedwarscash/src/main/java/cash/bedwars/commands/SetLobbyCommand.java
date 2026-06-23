package cash.bedwars.commands;

import cash.bedwars.BackendClient;
import cash.bedwars.LiveState;
import cash.bedwars.LobbyService;
import cash.bedwars.game.GameManager;
import cash.bedwars.game.TeamColor;
import cash.bedwars.world.WorldManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetLobbyCommand implements CommandExecutor {
    private final LobbyService lobby;
    private final BackendClient backend;
    private final GameManager game;
    private final LiveState liveState;
    private final WorldManager worlds;

    public SetLobbyCommand(LobbyService lobby, BackendClient backend, GameManager game, LiveState liveState,
                           WorldManager worlds) {
        this.lobby = lobby;
        this.backend = backend;
        this.game = game;
        this.liveState = liveState;
        this.worlds = worlds;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!p.isOp() && !p.hasPermission("bedwarscash.admin")) {
            p.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length < 1) {
            p.sendMessage("§cUsage: /bwc <setlobby|setwait|setbed|forcestart|end>");
            return true;
        }
        if (args[0].equalsIgnoreCase("setlobby")) {
            lobby.saveLobbyHere(p);
            return true;
        }
        if (args[0].equalsIgnoreCase("setwait")) {
            if (!worlds.isArenaWorld(p.getWorld())) {
                p.sendMessage("§cStand in the arena world first.");
                return true;
            }
            worlds.saveArenaWaitingHere(p);
            return true;
        }
        if (args[0].equalsIgnoreCase("setbed")) {
            if (args.length < 2) {
                p.sendMessage("§cUsage: /bwc setbed <green|blue|red|yellow>");
                return true;
            }
            TeamColor team = TeamColor.fromId(args[1]);
            if (team == null) {
                p.sendMessage("§cUnknown team.");
                return true;
            }
            if (!worlds.isArenaWorld(p.getWorld())) {
                p.sendMessage("§cStand in the arena world and look at the bed.");
                return true;
            }
            worlds.saveTeamBedHere(p, team);
            return true;
        }
        if (args[0].equalsIgnoreCase("forcestart")) {
            if (game.isLive() || game.isStarting() || liveState.matchActive()) {
                p.sendMessage("§cA match is already running.");
                return true;
            }
            backend.forceStart(p);
            p.sendMessage("§aForce-starting match...");
            return true;
        }
        if (args[0].equalsIgnoreCase("end")) {
            String reason = "Ended by " + p.getName();
            boolean pluginActive = game.isLive() || game.isStarting();
            boolean backendActive = liveState.matchActive();

            if (!pluginActive && !backendActive) {
                p.sendMessage("§cNo active match to end.");
                return true;
            }

            if (pluginActive) {
                game.abortMatch(reason);
            } else {
                game.forceCleanupLocal(reason);
                backend.forceAbort(p, reason);
            }
            p.sendMessage("§aMatch ended.");
            return true;
        }
        p.sendMessage("§cUsage: /bwc <setlobby|forcestart|end>");
        return true;
    }
}
