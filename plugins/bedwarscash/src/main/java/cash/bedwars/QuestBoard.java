package cash.bedwars;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Caches each player's daily quests, pushed from the backend over WebSocket. */
public final class QuestBoard {

    public record Quest(
            String id,
            String name,
            String description,
            int target,
            int progress,
            boolean completed,
            int xp
    ) {}

    private final Map<UUID, List<Quest>> quests = new ConcurrentHashMap<>();

    public void update(UUID uuid, List<Quest> list) {
        quests.put(uuid, List.copyOf(list));
    }

    public List<Quest> get(UUID uuid) {
        return quests.getOrDefault(uuid, List.of());
    }

    public void forget(UUID uuid) {
        quests.remove(uuid);
    }
}
