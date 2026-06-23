package cash.bedwars.integration;

import cash.bedwars.BackendClient;
import com.tomkeuper.bedwars.api.arena.team.ITeam;
import com.tomkeuper.bedwars.api.arena.team.TeamColor;
import com.tomkeuper.bedwars.api.events.gameplay.GameEndEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.UUID;

// Reports the winning team + members to the backend so it can settle the match.
public class GameEndListener implements Listener {
    private final BedWarsHook hook;
    private final BackendClient backend;

    public GameEndListener(BedWarsHook hook, BackendClient backend) {
        this.hook = hook;
        this.backend = backend;
    }

    @EventHandler
    public void onGameEnd(GameEndEvent event) {
        int matchId = hook.currentMatchId();
        if (matchId <= 0) return; // not a ranked/wagered match we started

        ITeam winner = event.getTeamWinner();
        List<UUID> winners = event.getWinners();
        if (winner == null || winners == null || winners.isEmpty()) {
            backend.matchAborted(matchId, "no winner reported");
            hook.clearMatch();
            return;
        }
        backend.matchResult(matchId, mapColor(winner.getColor()), winners);
        hook.clearMatch();
    }

    // Map BedWars team colors onto the four colors the backend expects.
    private static String mapColor(TeamColor color) {
        String name = color.name().toUpperCase();
        return switch (name) {
            case "GREEN", "LIME", "DARK_GREEN" -> "GREEN";
            case "BLUE", "AQUA", "CYAN", "DARK_AQUA", "DARK_BLUE", "LIGHT_BLUE" -> "BLUE";
            case "RED", "DARK_RED" -> "RED";
            case "YELLOW", "GOLD", "ORANGE" -> "YELLOW";
            default -> name;
        };
    }
}
