package me._furiouspotato_.bingo.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import me._furiouspotato_.bingo.model.BoardClaimMode;
import me._furiouspotato_.bingo.model.GameDifficulty;
import me._furiouspotato_.bingo.model.GameMode;
import me._furiouspotato_.bingo.model.PlayerSession;
import me._furiouspotato_.bingo.model.TeamColor;
import me._furiouspotato_.bingo.model.TeamState;
import me._furiouspotato_.bingo.model.BoardEntry;

public final class GameSessionManager {
    public enum Status {
        LOBBY,
        RUNNING
    }

    private final JavaPlugin plugin;
    private final RulesConfigService rules;
    private final ItemPoolService itemPool;
    private final BoardService boardService;
    private final BoardUiService boardUiService;
    private final MessageStyleService messageStyle;
    private final PlayerRoundPreparationService roundPreparationService;
    private final TeamManager teamManager;
    private final ScoreboardService scoreboardService;

    private final Map<String, PlayerSession> participants = new HashMap<>();
    private final Set<String> activeRoundPlayers = new HashSet<>();
    private final Map<String, Integer> globalPoints = new HashMap<>();
    private final Map<String, PunishmentState> punishments = new HashMap<>();

    private Status status = Status.LOBBY;
    private GameMode mode;
    private GameDifficulty difficulty;
    private BoardService.Board board;
    private int elapsedSeconds = 0;
    private int finishSeconds = 0;
    private BukkitTask tickerTask;
    private BukkitTask countdownTask;
    private BukkitTask endGraceTask;
    private int endGraceRemaining = 0;
    private int countdownRemaining = 0;
    private int butfastRewardTeamCount = 1;
    private boolean preStartLocked = false;
    private World arenaWorld;
    private int arenaX;
    private int arenaZ;
    private final Set<Biome> forbiddenBiomes = new HashSet<>();
    private BoardService.Board lastBoard;
    private final Map<TeamColor, TeamState> lastBoardTeams = new HashMap<>();
    private GameMode lastBoardMode = GameMode.DEFAULT;
    private GameDifficulty lastBoardDifficulty;

    public GameSessionManager(JavaPlugin plugin, RulesConfigService rules, ItemPoolService itemPool,
            BoardService boardService, BoardUiService boardUiService, MessageStyleService messageStyle,
            PlayerRoundPreparationService roundPreparationService, TeamManager teamManager,
            ScoreboardService scoreboardService) {
        this.plugin = plugin;
        this.rules = rules;
        this.itemPool = itemPool;
        this.boardService = boardService;
        this.boardUiService = boardUiService;
        this.messageStyle = messageStyle;
        this.roundPreparationService = roundPreparationService;
        this.teamManager = teamManager;
        this.scoreboardService = scoreboardService;
        this.mode = rules.defaultMode();
        this.difficulty = rules.defaultDifficulty();
        forbiddenBiomes.add(Biome.OCEAN);
        forbiddenBiomes.add(Biome.COLD_OCEAN);
        forbiddenBiomes.add(Biome.DEEP_COLD_OCEAN);
        forbiddenBiomes.add(Biome.DEEP_FROZEN_OCEAN);
        forbiddenBiomes.add(Biome.DEEP_LUKEWARM_OCEAN);
        forbiddenBiomes.add(Biome.DEEP_OCEAN);
        forbiddenBiomes.add(Biome.FROZEN_OCEAN);
        forbiddenBiomes.add(Biome.LUKEWARM_OCEAN);
        forbiddenBiomes.add(Biome.WARM_OCEAN);
    }

    public Status status() {
        return status;
    }

    public boolean isRunning() {
        return status == Status.RUNNING;
    }

    public boolean hasActiveRoundPhase() {
        return isRunning() || countdownTask != null;
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

    public RulesConfigService rules() {
        return rules;
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

    public boolean isRoundParticipant(String nickname) {
        return activeRoundPlayers.contains(normalize(nickname));
    }

    public List<TeamState> visibleTeams() {
        return hasActiveRoundPhase() ? roundActiveTeams() : teamManager.activeTeams();
    }

    public BoardService.Board board() {
        return board;
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
        TeamColor previous = session == null ? null : session.teamColor();
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
        if (previous == null) {
            broadcast(messageStyle.joinedTeam(nickname, team));
        } else if (previous != team) {
            broadcast(messageStyle.switchedTeam(nickname, team));
        }
        scoreboardService.update(this);
        return team;
    }

    public void removePlayer(Player player) {
        String nickname = player.getName();
        clearPunishment(nickname, true);
        if (isRoundParticipant(nickname)) {
            activeRoundPlayers.remove(normalize(nickname));
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            player.setFlying(false);
            player.teleport(spectatorLocation());
            reconcileRoundStateAfterRosterChange();
        } else {
            participants.remove(normalize(nickname));
            activeRoundPlayers.remove(normalize(nickname));
            teamManager.removePlayer(nickname);
        }
        scoreboardService.update(this);
        refreshOpenBoards();
    }

    public Optional<TeamColor> teamOf(String nickname) {
        return teamManager.teamOf(nickname);
    }

    public void startGame(List<Player> selectedPlayers) {
        if (isRunning()) {
            throw new IllegalStateException("Game already running.");
        }
        if (countdownTask != null) {
            throw new IllegalStateException("Game countdown already running.");
        }
        if (selectedPlayers.isEmpty()) {
            throw new IllegalStateException("No players were selected.");
        }

        for (Player player : selectedPlayers) {
            if (!participants.containsKey(normalize(player.getName()))) {
                joinPlayer(player, null);
            }
        }

        activeRoundPlayers.clear();
        for (Player player : selectedPlayers) {
            activeRoundPlayers.add(normalize(player.getName()));
        }

        if (roundActiveTeams().isEmpty()) {
            throw new IllegalStateException("No active teams.");
        }

        board = boardService.generateBoard(mode, difficulty, itemPool.entries());
        teamManager.resetRoundState();
        elapsedSeconds = 0;
        finishSeconds = rules.durationFor(mode, difficulty);
        butfastRewardTeamCount = Math.max(1, roundActiveTeams().size());
        selectArena();
        buildCage();
        preStartLocked = true;

        for (String key : activeRoundPlayers) {
            PlayerSession session = participants.get(key);
            if (session == null) {
                continue;
            }
            Player player = Bukkit.getPlayerExact(session.nickname());
            if (player != null && player.isOnline()) {
                preparePlayerForRound(player);
                player.teleport(new Location(arenaWorld, arenaX + 0.5, 221, arenaZ + 0.5));
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isRoundParticipant(player.getName())) {
                player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                player.teleport(new Location(arenaWorld, arenaX + 0.5, 255, arenaZ + 0.5));
            }
        }

        int countdown = rules.countdownSeconds();
        if (countdown <= 0) {
            beginRoundNow();
            return;
        }

        cancelCountdown();
        countdownRemaining = countdown;
        broadcastActionbar(messageStyle.gameStartingIn(countdownRemaining));
        playOnlineSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
        refreshOpenBoards();
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            runPeriodicPlayerChecks();
            countdownRemaining--;
            if (countdownRemaining <= 0) {
                cancelCountdown();
                beginRoundNow();
                return;
            }
            broadcastActionbar(messageStyle.gameStartingIn(countdownRemaining));
            playOnlineSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
        }, 20L, 20L);
    }

    private void beginRoundNow() {
        status = Status.RUNNING;
        openCageFloor();
        preStartLocked = false;
        broadcast(messageStyle.gameStarted(mode, difficulty));
        broadcastActionbar(messageStyle.gameStartedNow());
        playOnlineSound(Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.1f);
        scoreboardService.update(this);
        refreshOpenBoards();
        startTicker();
    }

    public void endGame(boolean announce) {
        if (!isRunning() && countdownTask == null) {
            return;
        }

        cancelCountdown();
        cancelEndGrace();
        cancelTicker();
        status = Status.LOBBY;
        preStartLocked = false;

        if (announce) {
            broadcast(Component.text("The game has ended.", NamedTextColor.GOLD));
        }

        snapshotLastBoardState();
        List<TeamState> ranking = new ArrayList<>(roundActiveTeams());
        ranking.sort(teamRankingComparator());
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
                        .append(teamDisplayName(state))
                        .append(Component.text(" - ", NamedTextColor.GOLD))
                        .append(teamResultComponent(state)));
                place++;
            }
        }

        teamManager.resetRoundState();
        board = null;
        clearPunishments();
        activeRoundPlayers.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.removePotionEffect(PotionEffectType.SPEED);
            player.setGameMode(org.bukkit.GameMode.SURVIVAL);
            player.setFlying(false);
        }
        broadcastActionbar(messageStyle.gameEndedNow());
        playOnlineSound(Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 1.0f);
        clearCage();
        elapsedSeconds = 0;
        finishSeconds = 0;
        butfastRewardTeamCount = 1;
        scoreboardService.update(this);
        refreshOpenBoards();
    }

    public void onItemProgress(Player player, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }
        if (boardUiService.isBingoCard(stack) || roundPreparationService.isStarterItem(stack)) {
            return;
        }
        onItemProgress(player, stack.getType());
    }

    public void onItemProgress(Player player, Material material) {
        if (rules.boardClaimMode() != BoardClaimMode.AUTO) {
            return;
        }
        if (!isRunning() || board == null) {
            return;
        }
        if (!isRoundParticipant(player.getName())) {
            return;
        }
        PlayerSession session = participants.get(normalize(player.getName()));
        if (session == null) {
            return;
        }

        TeamState team = teamManager.stateOf(session.teamColor());
        boolean changed = false;
        for (int i = 0; i < board.entries().length; i++) {
            BoardEntry target = board.entries()[i];
            if (target == null || !target.matches(material) || team.collected()[i]) {
                continue;
            }
            int reward = mode == GameMode.BUTFAST ? rewardForIndex(i) : 0;
            team.collected()[i] = true;
            changed = true;

            if (mode == GameMode.BUTFAST) {
                team.addScore(reward);
            }
        }

        if (!changed) {
            return;
        }

        if (mode != GameMode.BUTFAST) {
            team.setScore(computeScore(team));
        }

        afterCollection(player, team, prettify(material.name()));
    }

    public boolean tryManualClaim(Player player, int boardIndex) {
        if (rules.boardClaimMode() != BoardClaimMode.MANUAL) {
            return false;
        }
        if (!isRunning() || board == null || boardIndex < 0 || boardIndex >= board.entries().length) {
            return false;
        }
        if (!isRoundParticipant(player.getName())) {
            player.sendActionBar(Component.text("You are not participating.", NamedTextColor.RED));
            return false;
        }

        PlayerSession session = participants.get(normalize(player.getName()));
        if (session == null) {
            player.sendActionBar(Component.text("You are not participating.", NamedTextColor.RED));
            return false;
        }
        TeamState team = teamManager.stateOf(session.teamColor());
        BoardEntry target = board.entries()[boardIndex];
        if (target == null || team.collected()[boardIndex]) {
            return false;
        }

        Material consumed = rules.consumeOnClaim() ? consumeAnyMatching(player, target)
                : findAnyMatching(player, target);
        if (consumed == null) {
            player.sendActionBar(messageStyle.claimFailed());
            return false;
        }

        int reward = mode == GameMode.BUTFAST ? rewardForIndex(boardIndex) : 0;
        team.collected()[boardIndex] = true;
        if (mode == GameMode.BUTFAST) {
            team.addScore(reward);
        } else {
            team.setScore(computeScore(team));
        }
        afterCollection(player, team, prettify(consumed.name()));
        return true;
    }

    private Material consumeAnyMatching(Player player, BoardEntry entry) {
        for (Material material : entry.match().acceptedMaterials()) {
            int slot = firstSlotWith(player, entry, material);
            if (slot < 0) {
                continue;
            }
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null) {
                continue;
            }
            if (stack.getAmount() <= 1) {
                player.getInventory().setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - 1);
            }
            return material;
        }
        return null;
    }

    private Material findAnyMatching(Player player, BoardEntry entry) {
        for (Material material : entry.match().acceptedMaterials()) {
            if (firstSlotWith(player, entry, material) >= 0) {
                return material;
            }
        }
        return null;
    }

    private int firstSlotWith(Player player, BoardEntry entry, Material material) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack != null && stack.getType() == material && !isProtectedInventoryItem(stack)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isProtectedInventoryItem(ItemStack stack) {
        return boardUiService.isBingoCard(stack) || roundPreparationService.isStarterItem(stack);
    }

    public void onPlayerQuit(Player player) {
        if (hasActiveRoundPhase()) {
            clearPunishment(player.getName(), false);
            if (activeRoundPlayers.remove(normalize(player.getName()))) {
                reconcileRoundStateAfterRosterChange();
            }
            scoreboardService.update(this);
            return;
        }

        clearPunishment(player.getName(), false);

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

    public boolean shouldCancelFallDamage(Player player) {
        return isRunning() && rules.preventFallDamage()
                && isRoundParticipant(player.getName());
    }

    public boolean shouldCancelCountdownDamage(Player player) {
        return countdownTask != null;
    }

    public boolean shouldCancelDripstoneDamage(Player player, EntityDamageEvent.DamageCause cause) {
        if (!isRunning() || !isRoundParticipant(player.getName())) {
            return false;
        }
        String causeName = cause.name();
        if (causeName.contains("STALACTITE") || causeName.contains("STALAGMITE")) {
            return true;
        }
        if (cause != EntityDamageEvent.DamageCause.FALL) {
            return false;
        }
        Location loc = player.getLocation();
        Material below = loc.getBlock().getRelative(0, -1, 0).getType();
        return below == Material.POINTED_DRIPSTONE;
    }

    public void onHeldSlotChange(Player player, int heldSlot) {
        if (!rules.enableSpeedBonus() || !hasActiveRoundPhase() || !isRoundParticipant(player.getName())
                || player.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
            player.removePotionEffect(PotionEffectType.SPEED);
            return;
        }
        ItemStack held = player.getInventory().getItem(heldSlot);
        if (boardUiService.isBingoCard(held)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false, false));
        } else {
            player.removePotionEffect(PotionEffectType.SPEED);
        }
    }

    public boolean shouldCancelLethalDamage(Player player, double finalDamage) {
        if (!isRunning()) {
            return false;
        }
        if (!isRoundParticipant(player.getName())) {
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
        if (!isRoundParticipant(player.getName()) || punishments.containsKey(key)) {
            return;
        }

        int punishmentSeconds = rules.punishmentSeconds();
        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        player.setFlying(false);
        if (punishmentSeconds > 0) {
            player.addPotionEffect(
                    new PotionEffect(PotionEffectType.BLINDNESS, punishmentSeconds * 20 + 40, 1, false, false, false));
        }

        ItemStack[] contents = null;
        ItemStack[] armor = null;
        ItemStack offHand = null;
        if (rules.returnItemsOnDeath()) {
            contents = cloneContents(player.getInventory().getContents());
            armor = cloneContents(player.getInventory().getArmorContents());
            offHand = player.getInventory().getItemInOffHand() == null
                    ? null
                    : player.getInventory().getItemInOffHand().clone();
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);
        }
        player.sendActionBar(
                Component.text("You died. Spectating for " + punishmentSeconds + "s.", NamedTextColor.RED));

        Location deathLocation = player.getLocation().clone();
        BukkitTask releaseTask = Bukkit.getScheduler().runTaskLater(plugin,
                () -> clearPunishment(player.getName(), true),
                Math.max(1, punishmentSeconds) * 20L);
        punishments.put(key, new PunishmentState(releaseTask,
                System.currentTimeMillis() + Math.max(1, punishmentSeconds) * 1000L,
                deathLocation, contents, armor, offHand));
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
            team.setFinishedAtSeconds(elapsedSeconds);
            broadcast(team.color().displayNameComponent()
                    .append(Component.text(" has completed the board.", NamedTextColor.GOLD)));
            moveFinishedTeamToSpectators(team.color());
            playParticipantsSound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        int activeTeams = roundActiveTeams().size();
        long finished = roundActiveTeams().stream().filter(TeamState::finished).count();
        if (mode == GameMode.BUTFAST) {
            if (finished == activeTeams) {
                scheduleEndAfterGrace();
            }
            return;
        }
        if (activeTeams > 0 && finished >= Math.max(1, activeTeams - 1)) {
            scheduleEndAfterGrace();
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
                if (board.entries()[idx] != null && collected[idx]) {
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
            if (board.entries()[i] != null && collected[i]) {
                sum++;
            }
        }
        return sum;
    }

    private boolean allRequiredCollected(boolean[] collected) {
        for (int i = 0; i < collected.length; i++) {
            if (board.entries()[i] != null && !collected[i]) {
                return false;
            }
        }
        return true;
    }

    private int rewardForIndex(int index) {
        int teamsCollected = 0;
        for (TeamState state : roundActiveTeams()) {
            if (state.collected()[index]) {
                teamsCollected++;
            }
        }
        int activeTeams = Math.max(1, butfastRewardTeamCount);
        return Math.max(1, activeTeams - teamsCollected);
    }

    public boolean onCardUse(Player player, ItemStack heldItem) {
        if (!boardUiService.isBingoCard(heldItem)) {
            return false;
        }
        BoardService.Board shownBoard = activeOrLastBoard();
        if (shownBoard == null) {
            player.sendActionBar(Component.text("No active bingo board right now.", NamedTextColor.RED));
            return true;
        }

        PlayerSession session = participants.get(normalize(player.getName()));
        if (session == null) {
            player.sendActionBar(Component.text("You are not participating.", NamedTextColor.RED));
            return true;
        }

        TeamState team = teamSnapshotForDisplay(session.teamColor());
        boardUiService.openBoard(player, shownBoard, team, activeOrLastMode(), activeOrLastDifficulty(),
                this::rewardForIndex);
        return true;
    }

    public boolean onBoardInventoryClick(Player player, InventoryHolder topHolder, int rawSlot) {
        if (!boardUiService.isBoardView(topHolder)) {
            return false;
        }
        BoardService.Board shownBoard = activeOrLastBoard();
        if (shownBoard == null) {
            return true;
        }
        PlayerSession session = participants.get(normalize(player.getName()));
        if (session == null) {
            return true;
        }
        TeamState team = teamSnapshotForDisplay(session.teamColor());
        BoardUiService.ClickAction action = boardUiService.mapClick(player, rawSlot);
        switch (action.type()) {
            case TOGGLE_MODE -> boardUiService.openBoard(player, shownBoard, team, activeOrLastMode(),
                    activeOrLastDifficulty(), this::rewardForIndex);
            case CLAIM -> {
                if (!hasActiveRoundPhase()) {
                    return true;
                }
                if (isRunning() && board != null) {
                    tryManualClaim(player, action.boardIndex());
                    team = teamManager.stateOf(session.teamColor());
                }
                boardUiService.openBoard(player, shownBoard, team, activeOrLastMode(), activeOrLastDifficulty(),
                        this::rewardForIndex);
            }
            default -> {
            }
        }
        return true;
    }

    public boolean isBoardInventory(InventoryHolder topHolder) {
        return boardUiService.isBoardView(topHolder);
    }

    public boolean isBingoCard(ItemStack item) {
        return boardUiService.isBingoCard(item);
    }

    public void returnCard(Player player) {
        if (player.getInventory().getItem(8) == null || !boardUiService.isBingoCard(player.getInventory().getItem(8))) {
            player.getInventory().setItem(8, boardUiService.createCard());
        }
    }

    public boolean shouldLockBlockActions(Player player) {
        return preStartLocked && isRoundParticipant(player.getName());
    }

    public boolean shouldCancelPvp(Player attacker, Player victim) {
        if (!isRunning()) {
            return false;
        }
        return isRoundParticipant(attacker.getName())
                && isRoundParticipant(victim.getName());
    }

    public void onPlayerJoin(Player player) {
        String key = normalize(player.getName());
        globalPoints.putIfAbsent(player.getName(), 0);
        PunishmentState punishment = punishments.get(key);
        if (punishment != null && (punishment.remainingTicks() <= 0 || !hasActiveRoundPhase())) {
            clearPunishment(player.getName(), true);
        }
        if (!isRunning() && countdownTask == null) {
            scoreboardService.update(this);
            return;
        }
        PlayerSession session = participants.get(key);
        if (session != null && isRoundParticipant(player.getName())) {
            punishment = punishments.get(key);
            if (punishment != null || teamManager.stateOf(session.teamColor()).finished()) {
                player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                player.setFlying(false);
                player.teleport(spectatorLocation());
                int remainingTicks = punishment == null ? 0 : punishment.remainingTicks();
                if (punishment != null && remainingTicks > 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, remainingTicks + 40, 1, false,
                            false, false));
                }
            } else {
                player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                onHeldSlotChange(player, player.getInventory().getHeldItemSlot());
                runPeriodicPlayerChecks(player);
            }
        } else {
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            player.teleport(spectatorLocation());
        }
        scoreboardService.update(this);
    }

    private void preparePlayerForRound(Player player) {
        clearPunishment(player.getName(), true);
        roundPreparationService.prepare(player);
        runPeriodicPlayerChecks(player);
    }

    private void selectArena() {
        World world = Bukkit.getWorld(rules.defaultWorldName());
        if (world == null) {
            world = Bukkit.getWorlds().getFirst();
        }
        arenaWorld = world;
        int[] coords = randomPlayableCoords(world);
        arenaX = coords[0];
        arenaZ = coords[1];
        world.setTime(12000L);
        world.setStorm(false);
    }

    private int[] randomPlayableCoords(World world) {
        Random random = new Random();
        int bound = 25_000;
        for (int i = 0; i < 200; i++) {
            int x = random.nextInt(-bound, bound);
            int z = random.nextInt(-bound, bound);
            Biome biome = world.getBiome(x, world.getHighestBlockYAt(x, z), z);
            if (forbiddenBiomes.contains(biome)) {
                continue;
            }
            return new int[] { x, z };
        }
        return new int[] { 0, 0 };
    }

    private void buildCage() {
        if (arenaWorld == null) {
            return;
        }
        fill(arenaX - 2, 220, arenaZ - 2, arenaX + 2, 220, arenaZ + 2, Material.GLASS);
        fill(arenaX - 2, 224, arenaZ - 2, arenaX + 2, 224, arenaZ + 2, Material.GLASS);
        fill(arenaX - 2, 220, arenaZ - 2, arenaX - 2, 224, arenaZ + 2, Material.GLASS);
        fill(arenaX + 2, 220, arenaZ - 2, arenaX + 2, 224, arenaZ + 2, Material.GLASS);
        fill(arenaX - 1, 220, arenaZ - 2, arenaX + 1, 224, arenaZ - 2, Material.GLASS);
        fill(arenaX - 1, 220, arenaZ + 2, arenaX + 1, 224, arenaZ + 2, Material.GLASS);
    }

    private void openCageFloor() {
        if (arenaWorld == null) {
            return;
        }
        fill(arenaX - 1, 220, arenaZ - 1, arenaX + 1, 220, arenaZ + 1, Material.AIR);
    }

    private void clearCage() {
        if (arenaWorld == null) {
            return;
        }
        fill(arenaX - 2, 220, arenaZ - 2, arenaX + 2, 235, arenaZ + 2, Material.AIR);
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        if (arenaWorld == null) {
            return;
        }
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    arenaWorld.getBlockAt(x, y, z).setType(material, false);
                }
            }
        }
    }

    private void startTicker() {
        cancelTicker();
        tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            elapsedSeconds++;
            runPeriodicPlayerChecks();
            if (finishSeconds > 0 && elapsedSeconds >= finishSeconds) {
                endGame(true);
                return;
            }
            String warning = timeWarningMessage();
            if (warning != null) {
                broadcastActionbar(messageStyle.timeWarning(warning));
                playOnlineSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.9f);
            }
            scoreboardService.update(this);
        }, 20L, 20L);
    }

    private String timeWarningMessage() {
        if (finishSeconds <= 0) {
            return null;
        }
        int left = finishSeconds - elapsedSeconds;
        if (left <= 0) {
            return null;
        }
        if (left % 600 == 0) {
            return left / 60 + " minutes left";
        }
        if (left <= 300 && left % 60 == 0) {
            return left / 60 + " minutes left";
        }
        if (left <= 30 && left % 10 == 0) {
            return left + " seconds left";
        }
        if (left <= 5) {
            return left + " seconds left";
        }
        return null;
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        countdownRemaining = 0;
    }

    private void scheduleEndAfterGrace() {
        if (endGraceTask != null) {
            return;
        }
        int graceSeconds = Math.max(0, rules.afterGameSeconds());
        if (graceSeconds <= 0) {
            endGame(true);
            return;
        }
        endGraceRemaining = graceSeconds;
        broadcastActionbar(messageStyle.gameEndingIn(endGraceRemaining));
        playOnlineSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.8f);
        endGraceTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            endGraceRemaining--;
            if (endGraceRemaining <= 0) {
                cancelEndGrace();
                endGame(true);
                return;
            }
            broadcastActionbar(messageStyle.gameEndingIn(endGraceRemaining));
            playOnlineSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.8f);
        }, 20L, 20L);
    }

    private void cancelTicker() {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
    }

    private void cancelEndGrace() {
        if (endGraceTask != null) {
            endGraceTask.cancel();
            endGraceTask = null;
        }
        endGraceRemaining = 0;
    }

    private static String prettify(String raw) {
        return raw.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private void broadcast(Component message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    private void broadcastActionbar(Component message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(message);
        }
    }

    private void afterCollection(Player collector, TeamState team, String itemName) {
        broadcast(messageStyle.teamCollected(collector.getName(), team.color(), team.memberCount(), itemName));
        playCollectionSounds(collector);
        checkFinish(team);
        scoreboardService.update(this);
        refreshOpenBoards();
    }

    private void refreshOpenBoards() {
        BoardService.Board shownBoard = activeOrLastBoard();
        if (shownBoard == null) {
            return;
        }
        GameMode shownMode = activeOrLastMode();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerSession session = participants.get(normalize(player.getName()));
            if (session == null) {
                continue;
            }
            boardUiService.refreshOpenBoard(player, shownBoard, teamSnapshotForDisplay(session.teamColor()), shownMode,
                    activeOrLastDifficulty(), this::rewardForIndex);
        }
    }

    private void playCollectionSounds(Player collector) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isRoundParticipant(player.getName())) {
                continue;
            }
            if (player.getUniqueId().equals(collector.getUniqueId())) {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
            }
        }
    }

    private void playParticipantsSound(Sound sound, float volume, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isRoundParticipant(player.getName())) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    private void playOnlineSound(Sound sound, float volume, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private void snapshotLastBoardState() {
        if (board == null) {
            return;
        }
        lastBoard = board;
        lastBoardMode = mode;
        lastBoardDifficulty = difficulty;
        lastBoardTeams.clear();
        for (TeamState state : roundActiveTeams()) {
            lastBoardTeams.put(state.color(), copyTeamState(state));
        }
    }

    private BoardService.Board activeOrLastBoard() {
        return board != null ? board : lastBoard;
    }

    private GameMode activeOrLastMode() {
        return board != null ? mode : lastBoardMode;
    }

    private GameDifficulty activeOrLastDifficulty() {
        return board != null ? difficulty : lastBoardDifficulty;
    }

    private List<TeamState> roundActiveTeams() {
        List<TeamState> result = new ArrayList<>();
        for (TeamState state : teamManager.activeTeams()) {
            if (activeRoundPlayers.stream()
                    .map(participants::get)
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(session -> session.teamColor() == state.color())) {
                result.add(state);
            }
        }
        return result;
    }

    private void moveFinishedTeamToSpectators(TeamColor color) {
        for (String key : activeRoundPlayers) {
            PlayerSession session = participants.get(key);
            if (session == null || session.teamColor() != color) {
                continue;
            }
            Player player = Bukkit.getPlayerExact(session.nickname());
            if (player == null || !player.isOnline()) {
                continue;
            }
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            player.setFlying(false);
            player.teleport(spectatorLocation());
        }
    }

    private void reconcileRoundStateAfterRosterChange() {
        if (!hasActiveRoundPhase()) {
            return;
        }
        List<TeamState> activeTeams = roundActiveTeams();
        butfastRewardTeamCount = Math.max(1, activeTeams.size());
        if (activeTeams.isEmpty()) {
            endGame(true);
            return;
        }
        if (!isRunning()) {
            return;
        }
        long finished = activeTeams.stream().filter(TeamState::finished).count();
        if (mode == GameMode.BUTFAST) {
            if (finished == activeTeams.size()) {
                scheduleEndAfterGrace();
            }
            return;
        }
        if (finished >= Math.max(1, activeTeams.size() - 1)) {
            scheduleEndAfterGrace();
        }
    }

    private TeamState teamSnapshotForDisplay(TeamColor color) {
        if (board != null) {
            return teamManager.stateOf(color);
        }
        TeamState snapshot = lastBoardTeams.get(color);
        if (snapshot != null) {
            return snapshot;
        }
        return copyTeamState(teamManager.stateOf(color));
    }

    private void runPeriodicPlayerChecks() {
        for (String key : activeRoundPlayers) {
            PlayerSession session = participants.get(key);
            if (session == null) {
                continue;
            }
            Player player = Bukkit.getPlayerExact(session.nickname());
            if (player == null || !player.isOnline()) {
                continue;
            }
            runPeriodicPlayerChecks(player);
        }
    }

    private void runPeriodicPlayerChecks(Player player) {
        onHeldSlotChange(player, player.getInventory().getHeldItemSlot());
        if (!isRunning() || board == null || rules.boardClaimMode() != BoardClaimMode.AUTO) {
            return;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            onItemProgress(player, stack);
        }
    }

    private void clearPunishments() {
        List<String> names = new ArrayList<>(punishments.keySet());
        for (String key : names) {
            clearPunishment(key, true);
        }
    }

    private void clearPunishment(String nickname, boolean restorePlayer) {
        String key = normalize(nickname);
        PunishmentState state = punishments.get(key);
        if (state == null) {
            return;
        }

        if (!restorePlayer) {
            punishments.remove(key);
            state.releaseTask.cancel();
            return;
        }
        Player player = Bukkit.getPlayerExact(nickname);
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!isRoundParticipant(player.getName())) {
            punishments.remove(key);
            state.releaseTask.cancel();
            return;
        }
        punishments.remove(key);
        state.releaseTask.cancel();

        PlayerSession session = participants.get(key);
        if (session != null && teamManager.stateOf(session.teamColor()).finished()) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
            player.setFlying(false);
            player.teleport(spectatorLocation());
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
        if (rules.teleportBackOnDeath() && state.deathLocation() != null) {
            player.teleport(state.deathLocation());
        }
        if (rules.returnItemsOnDeath() && state.contents() != null) {
            player.getInventory().setContents(cloneContents(state.contents()));
            player.getInventory().setArmorContents(cloneContents(state.armor()));
            player.getInventory().setItemInOffHand(state.offHand() == null ? null : state.offHand().clone());
            if (player.getInventory().getItem(8) == null) {
                player.getInventory().setItem(8, boardUiService.createCard());
            }
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 20, 0, false, false, false));
        player.sendActionBar(Component.text("You respawned.", NamedTextColor.GREEN));
    }

    private Location spectatorLocation() {
        return new Location(arenaWorld, arenaX + 0.5, 255, arenaZ + 0.5);
    }

    private static ItemStack[] cloneContents(ItemStack[] source) {
        if (source == null) {
            return null;
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    private Comparator<TeamState> teamRankingComparator() {
        if (mode != GameMode.DEFAULT) {
            return Comparator.comparingInt(TeamState::score).reversed();
        }
        return Comparator
                .comparing(TeamState::finished).reversed()
                .thenComparingInt(state -> state.finished() ? state.finishedAtSeconds() : Integer.MAX_VALUE)
                .thenComparing(Comparator.comparingInt(TeamState::score).reversed());
    }

    private Component teamDisplayName(TeamState state) {
        String value = state.memberCount() == 1 ? state.members().iterator().next() : state.color().displayName();
        return messageStyle.teamLabel(value, state.color(), state.memberCount());
    }

    private Component teamResultComponent(TeamState state) {
        if (mode != GameMode.DEFAULT) {
            return Component.text(state.score(), NamedTextColor.WHITE);
        }
        if (state.finished() && state.finishedAtSeconds() >= 0) {
            return Component.text(formatClock(state.finishedAtSeconds()), NamedTextColor.GREEN);
        }
        return Component.text(state.score() + "/5", NamedTextColor.RED);
    }

    private static String formatClock(int seconds) {
        int safe = Math.max(0, seconds);
        int hours = safe / 3600;
        int minutes = (safe % 3600) / 60;
        int secs = safe % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    private static TeamState copyTeamState(TeamState source) {
        TeamState copy = new TeamState(source.color());
        for (String member : source.members()) {
            copy.addMember(member);
        }
        boolean[] sourceCollected = source.collected();
        boolean[] copyCollected = copy.collected();
        for (int i = 0; i < sourceCollected.length; i++) {
            copyCollected[i] = sourceCollected[i];
        }
        copy.setScore(source.score());
        copy.setFinished(source.finished());
        copy.setFinishedAtSeconds(source.finishedAtSeconds());
        return copy;
    }

    private static String normalize(String nickname) {
        return nickname.toLowerCase(Locale.ROOT);
    }

    private record PunishmentState(BukkitTask releaseTask, long releaseAtMillis, Location deathLocation,
            ItemStack[] contents, ItemStack[] armor, ItemStack offHand) {
        private int remainingTicks() {
            long remainingMillis = Math.max(0L, releaseAtMillis - System.currentTimeMillis());
            return (int) Math.ceil(remainingMillis / 50.0d);
        }
    }
}
