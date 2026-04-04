package me._furiouspotato_.bingo;

import org.bukkit.plugin.java.JavaPlugin;

import me._furiouspotato_.bingo.commands.BingoCommand;
import me._furiouspotato_.bingo.listener.BingoGameplayListener;
import me._furiouspotato_.bingo.service.BoardService;
import me._furiouspotato_.bingo.service.GameSessionManager;
import me._furiouspotato_.bingo.service.ItemPoolService;
import me._furiouspotato_.bingo.service.RulesConfigService;
import me._furiouspotato_.bingo.service.ScoreboardService;
import me._furiouspotato_.bingo.service.TeamManager;
import me._furiouspotato_.bingo.service.TeamPreferenceStore;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

public final class Main extends JavaPlugin {
    private GameSessionManager session;

    @Override
    public void onEnable() {
        TeamPreferenceStore teamPreferenceStore = new TeamPreferenceStore(this);
        teamPreferenceStore.load();

        RulesConfigService rules = new RulesConfigService(this);
        rules.load();

        ItemPoolService itemPool = new ItemPoolService(this);
        itemPool.load();

        TeamManager teamManager = new TeamManager(teamPreferenceStore);
        teamManager.load();

        BoardService boardService = new BoardService();
        ScoreboardService scoreboardService = new ScoreboardService();
        session = new GameSessionManager(this, rules, itemPool, boardService, teamManager, scoreboardService);

        BingoCommand command = new BingoCommand(session, rules, itemPool, teamManager);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> command.register(event.registrar()));

        getServer().getPluginManager().registerEvents(new BingoGameplayListener(session), this);
        getLogger().info("Team Bingo plugin enabled.");
    }

    @Override
    public void onDisable() {
        if (session != null && session.isRunning()) {
            session.endGame(false);
        }
    }
}
