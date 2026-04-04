package me._furiouspotato_.bingo.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import me._furiouspotato_.bingo.model.GameDifficulty;
import me._furiouspotato_.bingo.model.GameMode;
import me._furiouspotato_.bingo.model.PlayerSession;
import me._furiouspotato_.bingo.model.TeamColor;
import me._furiouspotato_.bingo.model.TeamState;

public final class GameSessionManager {
    public enum Status {
        LOBBY,
        RUNNING
    }

    private final JavaPlugin plugin;
    private final RulesConfigService rules;
    private final ItemPoolService itemPool;
    private final BoardService boardService;
    private final TeamManager teamManager;
    private final ScoreboardService scoreboardService;

    private final Map<String, PlayerSession> participants = new HashMap<>();
    private final Map<String, Integer> globalPoints = new HashMap<>();
    private final Map<String, PunishmentState> punishments = new HashMap<>();

    private Status status = Status.LOBBY;
    private GameMode mode;
    private GameDifficulty difficulty;
    private BoardService.Board board;
    private int elapsedSeconds = 0;
    private int finishSeconds = 0;
    private BukkitTask tickerTask;

    public GameSessionManager(JavaPlugin plugin, RulesConfigService rules, ItemPoolService itemPool,
            BoardService boardService,
            TeamManager teamManager, ScoreboardService scoreboardService) {
        this.plugin = plugin;
        this.rules = rules;
        this.itemPool = itemPool;
        this.boardService = boardService;
        this.teamManager = teamManager;
        this.scoreboardService = scoreboardService;
        this.mode = rules.defaultMode();
        this.difficulty = rules.defaultDifficulty();
    }

    public Status status() {
        return status;
    }

    public boolean isRunning() {
        return status == Status.RUNNING;
    }

    public GameMode mode() {
        return mode;
    }

    public GameDifficulty difficulty() {
        return difficulty;
    }

    public TeamManager teamManager() {
        return teamManager;
    }

    public int elapsedSeconds() {
        return elapsedSeconds;
    }

    public int finishSeconds() {
        return finishSeconds;
    }

    public Collection<PlayerSession> participants() {
        return participants.values();
    }

    public void setMode(GameMode mode) {
        this.mode = mode;
        rules.setDefaultMode(mode);
    }

    public void setDifficulty(GameDifficulty difficulty) {
        this.difficulty = difficulty;
        rules.setDefaultDifficulty(difficulty);
    }

    public TeamColor joinPlayer(Player player, TeamColor requestedTeam) {
        String nickname = player.getName();
        PlayerSession session = participants.get(normalize(nickname));
        TeamColor team;
        if (requestedTeam == null) {
            team = teamManager.assignAutoBalanced(nickname);
        } else {
            team = teamManager.assignToTeam(nickname, requestedTeam, true);
        }

        if (session == null) {
            session = new PlayerSession(nickname, team);
            participants.put(normalize(nickname), session);
        } else {
            session.setTeamColor(team);
        }

        globalPoints.putIfAbsent(nickname, 0);
        player.sendMessage(Component.text("You joined team ", NamedTextColor.GOLD)
                .append(team.displayNameComponent())
                .append(Component.text(".", NamedTextColor.GOLD)));
        scoreboardService.update(this);
        return team;
    }

    public void removePlayer(Player player) {
        String nickname = player.getName();
        clearPunishment(nickname, true);
        participants.remove(normalize(nickname));
        teamManager.removePlayer(nickname);
        scoreboardService.update(this);
    }

    public Optional<TeamColor> teamOf(String nickname) {
        return teamManager.teamOf(nickname);
    }

    public void startGame(List<Player> selectedPlayers) {
        if (isRunning()) {
            throw new IllegalStateException("Game already running.");
        }
        if (selectedPlayers.isEmpty()) {
            throw new IllegalStateException("No players were selected.");
        }

        for (Player player : selectedPlayers) {
            if (!participants.containsKey(normalize(player.getName()))) {
                joinPlayer(player, null);
            }
        }

        if (teamManager.activeTeams().isEmpty()) {
            throw new IllegalStateException("No active teams.");
        }

        board = boardService.generateBoard(mode, difficulty, itemPool.entries());
        teamManager.resetRoundState();
        elapsedSeconds = 0;
        finishSeconds = rules.durationFor(mode, difficulty);
        status = Status.RUNNING;

        for (PlayerSession session : participants.values()) {
            Player player = Bukkit.getPlayerExact(session.nickname());
            if (player != null && player.isOnline()) {
                giveBingoCard(player);
            }
        }

        broadcast(Component.text("Game started: ", NamedTextColor.GREEN)
                .append(Component.text(mode.key(), NamedTextColor.GOLD))
                .append(Component.text(" / ", NamedTextColor.GREEN))
                .append(Component.text(difficulty.key(), NamedTextColor.GOLD)));
        scoreboardService.update(this);
        startTicker();
    }

    public void endGame(boolean announce) {
        if (!isRunning()) {
            return;
        }

        cancelTicker();
        status = Status.LOBBY;

        if (announce) {
            broadcast(Component.text("The game has ended.", NamedTextColor.GOLD));
        }

        List<TeamState> ranking = new ArrayList<>(teamManager.activeTeams());
        ranking.sort(Comparator.comparingInt(TeamState::score).reversed());
        int points = Math.max(0, ranking.size() - 1);
        for (TeamState state : ranking) {
            for (String nickname : state.members()) {
                globalPoints.put(nickname, globalPoints.getOrDefault(nickname, 0) + points);
            }
            if (points > 0) {
                points--;
            }
        }

        if (!ranking.isEmpty()) {
            broadcast(Component.text("Team ranking:", NamedTextColor.GOLD));
            int place = 1;
            for (TeamState state : ranking) {
                broadcast(Component.text(place + ". ", NamedTextColor.GOLD)
                        .append(state.color().displayNameComponent())
                        .append(Component.text(" - " + state.score(), NamedTextColor.WHITE)));
                place++;
            }
        }

        teamManager.resetRoundState();
        clearPunishments();
        board = null;
        elapsedSeconds = 0;
        finishSeconds = 0;
        scoreboardService.clearAll();
    }

    public void onItemProgress(Player player, Material material) {
        if (!isRunning() || board == null) {
            return;
        }
        PlayerSession session = participants.get(normalize(player.getName()));
        if (session == null) {
            return;
        }

        TeamState team = teamManager.stateOf(session.teamColor());
        boolean changed = false;
        for (int i = 0; i < board.items().length; i++) {
            Material target = board.items()[i];
            if (target == null || target != material || team.collected()[i]) {
                continue;
            }
            team.collected()[i] = true;
            changed = true;

            if (mode == GameMode.BUTFAST) {
                team.addScore(rewardForIndex(i));
            }
        }

        if (!changed) {
            return;
        }

        if (mode != GameMode.BUTFAST) {
            team.setScore(computeScore(team));
        }

        broadcast(team.color().displayNameComponent()
                .append(Component.text(" collected ", NamedTextColor.GOLD))
                .append(Component.text(prettify(material.name()), NamedTextColor.AQUA))
                .append(Component.text(".", NamedTextColor.GOLD)));
        checkFinish(team);
        scoreboardService.update(this);
    }

    public void onPlayerQuit(Player player) {
        clearPunishment(player.getName(), false);

        if (isRunning()) {
            scoreboardService.update(this);
            return;
        }

        if (participants.remove(normalize(player.getName())) != null) {
            teamManager.removePlayer(player.getName());
            scoreboardService.update(this);
        }
    }

    public String participantsSummary() {
        StringBuilder sb = new StringBuilder();
        List<TeamState> active = teamManager.activeTeams();
        active.sort(Comparator.comparingInt(t -> t.color().ordinal()));
        for (TeamState team : active) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(team.color().displayName()).append(" -> ").append(String.join(", ", team.members()));
        }
        return sb.toString();
    }

    public Map<String, Integer> globalPoints() {
        return globalPoints;
    }

    public boolean shouldCancelLethalDamage(Player player, double finalDamage) {
        if (!isRunning()) {
            return false;
        }
        if (!participants.containsKey(normalize(player.getName()))) {
            return false;
        }
        if (punishments.containsKey(normalize(player.getName()))) {
            return true;
        }
        return finalDamage >= player.getHealth();
    }

    public void punishPlayerDeath(Player player) {
        if (!isRunning()) {
            return;
        }
        String key = normalize(player.getName());
        if (!participants.containsKey(key) || punishments.containsKey(key)) {
            return;
        }

        int punishmentSeconds = rules.punishmentSeconds();
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        player.setFlying(false);
        if (punishmentSeconds > 0) {
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.BLINDNESS, punishmentSeconds * 20 + 40, 1, false, false, false));
        }
        player.sendMessage(Component.text("You died and are spectating for " + punishmentSeconds + " seconds.",
                NamedTextColor.RED));

        BukkitTask releaseTask = Bukkit.getScheduler().runTaskLater(plugin,
                () -> clearPunishment(player.getName(), true),
                Math.max(1, punishmentSeconds) * 20L);
        punishments.put(key, new PunishmentState(releaseTask));
    }

    public boolean lockMovementIfPunished(Player player, Location from, Location to) {
        PunishmentState state = punishments.get(normalize(player.getName()));
        if (state == null || to == null) {
            return false;
        }

        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return false;
        }
        return true;
    }

    public boolean isPunished(Player player) {
        return punishments.containsKey(normalize(player.getName()));
    }

    private void checkFinish(TeamState team) {
        boolean won = false;
        if (mode == GameMode.DEFAULT) {
            won = computeBestLine(team.collected()) >= 5;
        } else if (mode == GameMode.COLLECTALL) {
            won = allRequiredCollected(team.collected());
        } else {
            won = allRequiredCollected(team.collected());
        }

        if (won && !team.finished()) {
            team.setFinished(true);
            broadcast(team.color().displayNameComponent()
                    .append(Component.text(" has completed the board.", NamedTextColor.GOLD)));
        }

        int activeTeams = teamManager.activeTeams().size();
        long finished = teamManager.activeTeams().stream().filter(TeamState::finished).count();
        if (mode == GameMode.BUTFAST) {
            if (finished == activeTeams) {
                endGame(true);
            }
            return;
        }
        if (activeTeams > 0 && finished >= Math.max(1, activeTeams - 1)) {
            endGame(true);
        }
    }

    private int computeScore(TeamState team) {
        if (mode == GameMode.DEFAULT) {
            return computeBestLine(team.collected());
        }
        return countCollected(team.collected());
    }

    private int computeBestLine(boolean[] collected) {
        int[][] lines = {
                { 0, 1, 2, 3, 4 },
                { 5, 6, 7, 8, 9 },
                { 10, 11, 12, 13, 14 },
                { 15, 16, 17, 18, 19 },
                { 20, 21, 22, 23, 24 },
                { 0, 5, 10, 15, 20 },
                { 1, 6, 11, 16, 21 },
                { 2, 7, 12, 17, 22 },
                { 3, 8, 13, 18, 23 },
                { 4, 9, 14, 19, 24 },
                { 0, 6, 12, 18, 24 },
                { 4, 8, 12, 16, 20 }
        };

        int best = 0;
        for (int[] line : lines) {
            int value = 0;
            for (int idx : line) {
                if (board.items()[idx] != null && collected[idx]) {
                    value++;
                }
            }
            best = Math.max(best, value);
        }
        return best;
    }

    private int countCollected(boolean[] collected) {
        int sum = 0;
        for (int i = 0; i < collected.length; i++) {
            if (board.items()[i] != null && collected[i]) {
                sum++;
            }
        }
        return sum;
    }

    private boolean allRequiredCollected(boolean[] collected) {
        for (int i = 0; i < collected.length; i++) {
            if (board.items()[i] != null && !collected[i]) {
                return false;
            }
        }
        return true;
    }

    private int rewardForIndex(int index) {
        int teamsCollected = 0;
        for (TeamState state : teamManager.activeTeams()) {
            if (state.collected()[index]) {
                teamsCollected++;
            }
        }
        int activeTeams = Math.max(1, teamManager.activeTeams().size());
        return Math.max(1, (activeTeams - teamsCollected + 1) * 10);
    }

    private void giveBingoCard(Player player) {
        ItemStack card = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = card.getItemMeta();
        meta.displayName(Component.text("Bingo Card", NamedTextColor.GOLD));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        card.setItemMeta(meta);
        player.getInventory().addItem(card);
    }

    private void startTicker() {
        cancelTicker();
        tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            elapsedSeconds++;
            if (finishSeconds > 0 && elapsedSeconds >= finishSeconds) {
                endGame(true);
                return;
            }
            scoreboardService.update(this);
        }, 20L, 20L);
    }

    private void cancelTicker() {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
    }

    private static String prettify(String raw) {
        return raw.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private void broadcast(Component message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    private void clearPunishments() {
        List<String> names = new ArrayList<>(punishments.keySet());
        for (String key : names) {
            clearPunishment(key, true);
        }
    }

    private void clearPunishment(String nickname, boolean restorePlayer) {
        PunishmentState state = punishments.remove(normalize(nickname));
        if (state == null) {
            return;
        }
        state.releaseTask.cancel();

        if (!restorePlayer) {
            return;
        }
        Player player = Bukkit.getPlayerExact(nickname);
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!participants.containsKey(normalize(player.getName()))) {
            return;
        }

        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        Double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) != null
                ? player.getAttribute(Attribute.MAX_HEALTH).getValue()
                : 20.0;
        player.setHealth(Math.min(maxHealth, 20.0));
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setFireTicks(0);
        player.sendMessage(Component.text("Punishment is over. You are back in survival mode.", NamedTextColor.GREEN));
    }

    private static String normalize(String nickname) {
        return nickname.toLowerCase(Locale.ROOT);
    }

    private record PunishmentState(BukkitTask releaseTask) {
    }
}
