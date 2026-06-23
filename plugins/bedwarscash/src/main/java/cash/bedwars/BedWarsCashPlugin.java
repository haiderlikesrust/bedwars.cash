package cash.bedwars;

import cash.bedwars.commands.*;
import cash.bedwars.game.GameListener;
import cash.bedwars.game.GameManager;
import cash.bedwars.game.SpecialItemListener;
import cash.bedwars.game.SpecialItems;
import cash.bedwars.game.shop.ShopAccess;
import cash.bedwars.game.shop.ShopCatalog;
import cash.bedwars.game.shop.ShopPurchases;
import cash.bedwars.game.shop.ShopService;
import cash.bedwars.game.upgrades.UpgradeAccess;
import cash.bedwars.game.upgrades.UpgradeCatalog;
import cash.bedwars.game.upgrades.UpgradeShopService;
import cash.bedwars.listeners.CombatListener;
import cash.bedwars.listeners.DeathListener;
import cash.bedwars.listeners.JoinListener;
import cash.bedwars.listeners.LobbyListener;
import cash.bedwars.listeners.SpectatorListener;
import cash.bedwars.listeners.TeamUpgradeListener;
import cash.bedwars.world.WorldManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BedWarsCashPlugin extends JavaPlugin {

    private BackendClient backend;
    private OddsCache oddsCache;
    private LiveState liveState;
    private WorldManager worlds;
    private LobbyService lobby;
    private CashScoreboard scoreboard;
    private GameManager game;
    private ShopService shop;
    private UpgradeShopService upgrades;
    private BroadcastDirector broadcast;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        new java.io.File(getDataFolder(), "maps").mkdirs();
        saveResource("maps/README.txt", false);
        SpectatorHelper.init(this);
        ShopAccess.init(this);
        UpgradeAccess.init(this);
        SpecialItems.init(this);
        this.oddsCache = new OddsCache();
        this.liveState = new LiveState();
        this.worlds = new WorldManager(this);
        this.lobby = new LobbyService(this, worlds);
        this.scoreboard = new CashScoreboard(this, liveState);
        this.game = new GameManager(this, worlds, lobby);
        this.shop = new ShopService(ShopCatalog.load(this), game);
        this.upgrades = new UpgradeShopService(UpgradeCatalog.load(this), game);

        worlds.bootstrap();
        worlds.rebuildLobbyIfNeeded();
        lobby.initWorld();

        String wsUrl = getConfig().getString("backend.ws-url", "ws://localhost:8787/ws/plugin");
        String token = getConfig().getString("backend.token", "change-me-plugin-token");
        int reconnectSeconds = getConfig().getInt("backend.reconnect-seconds", 5);
        this.backend = new BackendClient(this, wsUrl, token, reconnectSeconds, oddsCache, game,
                liveState, lobby, scoreboard);
        this.broadcast = new BroadcastDirector(this, game, worlds, backend);
        this.backend.connect();

        scoreboard.startUpdater();

        getCommand("setwallet").setExecutor(new SetWalletCommand(backend));
        getCommand("bwlink").setExecutor(new LinkCommand(backend));
        getCommand("bet").setExecutor(new BetCommand(backend));
        getCommand("bets").setExecutor(new BetsCommand(oddsCache));
        getCommand("queue").setExecutor(new QueueCommand(backend));
        getCommand("party").setExecutor(new PartyCommand(backend));
        getCommand("bwresult").setExecutor(new ResultCommand(backend, game));
        getCommand("bwc").setExecutor(new SetLobbyCommand(lobby, backend, game, liveState));
        getCommand("shop").setExecutor(new ShopCommand(game, shop));
        getCommand("upgrades").setExecutor(new UpgradeCommand(game, upgrades));

        getServer().getPluginManager().registerEvents(new LobbyListener(this, lobby, worlds), this);
        getServer().getPluginManager().registerEvents(new GameListener(this, game, worlds, shop, upgrades), this);
        getServer().getPluginManager().registerEvents(new SpectatorListener(worlds), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this, backend), this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinListener(this, backend), this);
        getServer().getPluginManager().registerEvents(new SpecialItemListener(this, game, worlds), this);
        getServer().getPluginManager().registerEvents(new TeamUpgradeListener(game, worlds), this);

        if (broadcast.enabled()) {
            getLogger().info("Broadcast camera enabled for account: " + getConfig().getString("broadcast.username", "BWC_Cast"));
        }
        getLogger().info("BedWars.cash ready — shop, upgrades, special items, parties. Backend: " + wsUrl);
    }

    @Override
    public void onDisable() {
        SpectatorHelper.clearAll();
        if (backend != null) backend.close();
    }

    public BackendClient backend() { return backend; }
    public CashScoreboard scoreboard() { return scoreboard; }
    public GameManager game() { return game; }
    public WorldManager worlds() { return worlds; }
    public ShopService shop() { return shop; }
    public UpgradeShopService upgrades() { return upgrades; }
    public BroadcastDirector broadcast() { return broadcast; }
}
