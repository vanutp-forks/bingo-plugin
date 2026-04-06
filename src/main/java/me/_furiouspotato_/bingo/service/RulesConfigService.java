package me._furiouspotato_.bingo.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import me._furiouspotato_.bingo.model.BoardClaimMode;
import me._furiouspotato_.bingo.model.GameDifficulty;
import me._furiouspotato_.bingo.model.GameMode;

public final class RulesConfigService {
    private final JavaPlugin plugin;

    private GameMode defaultMode = GameMode.DEFAULT;
    private GameDifficulty defaultDifficulty;
    private BoardClaimMode boardClaimMode = BoardClaimMode.AUTO;
    private final Map<String, GameDifficulty> difficultiesByKey = new LinkedHashMap<>();
    private int countdownSeconds = 10;
    private int punishmentSeconds = 10;
    private int afterGameSeconds = 10;
    private boolean enableSpeedBonus = true;
    private boolean enableFireResistanceEffect = false;
    private boolean enableWaterBreathingEffect = true;
    private boolean enableNightVisionEffect = true;
    private boolean giveInitialItems = true;
    private boolean preventFallDamage = true;
    private boolean teleportBackOnDeath = true;
    private boolean returnItemsOnDeath = true;
    private boolean fixBingoCard = false;
    private boolean showGlobalScore = true;
    private boolean consumeOnClaim = true;
    private String defaultWorldName = "world";
    private boolean debugPrintNodeGraphOnStartup = false;

    public RulesConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();

        FileConfiguration cfg = plugin.getConfig();
        defaultMode = parseMode(cfg.getString("default-mode"));
        loadDifficultyLevels(cfg);
        defaultDifficulty = parseDifficulty(cfg.getString("default-difficulty"));
        countdownSeconds = Math.max(0, cfg.getInt("countdown-time", 10));
        punishmentSeconds = Math.max(0, cfg.getInt("punishment-time", 10));
        afterGameSeconds = Math.max(0, cfg.getInt("after-game-time", 10));
        enableSpeedBonus = cfg.getBoolean("enable-speed-bonus", true);
        enableFireResistanceEffect = cfg.getBoolean("enable-fire-resistance-effect", false);
        enableWaterBreathingEffect = cfg.getBoolean("enable-water-breathing-effect", true);
        enableNightVisionEffect = cfg.getBoolean("enable-night-vision-effect", true);
        giveInitialItems = cfg.getBoolean("give-initial-items", true);
        preventFallDamage = cfg.getBoolean("prevent-fall-damage", true);
        teleportBackOnDeath = cfg.getBoolean("teleport-back-on-death", true);
        returnItemsOnDeath = cfg.getBoolean("return-items-on-death", true);
        fixBingoCard = cfg.getBoolean("fix-bingo-card", false);
        showGlobalScore = cfg.getBoolean("show-global-score", true);
        consumeOnClaim = cfg.getBoolean("consume-on-claim", true);
        defaultWorldName = cfg.getString("default-world-name", "world");
        boardClaimMode = parseClaimMode(cfg.getString("board-claim-mode", BoardClaimMode.AUTO.key()));
        debugPrintNodeGraphOnStartup = cfg.getBoolean("debug-print-node-graph-on-startup", false);
    }

    public GameMode defaultMode() {
        return defaultMode;
    }

    public GameDifficulty defaultDifficulty() {
        return defaultDifficulty;
    }

    public void setDefaultMode(GameMode mode) {
        defaultMode = mode;
    }

    public void setDefaultDifficulty(GameDifficulty difficulty) {
        defaultDifficulty = difficulty;
    }

    public List<GameDifficulty> difficulties() {
        List<GameDifficulty> list = new ArrayList<>(difficultiesByKey.values());
        list.sort(Comparator.naturalOrder());
        return List.copyOf(list);
    }

    public List<String> difficultyKeys() {
        return difficulties().stream().map(GameDifficulty::key).toList();
    }

    public GameDifficulty requireDifficulty(String key) {
        GameDifficulty difficulty = difficultiesByKey.get(normalize(key));
        if (difficulty == null) {
            throw new IllegalArgumentException("Unknown game difficulty: " + key);
        }
        return difficulty;
    }

    public BoardClaimMode boardClaimMode() {
        return boardClaimMode;
    }

    public void setBoardClaimMode(BoardClaimMode boardClaimMode) {
        this.boardClaimMode = boardClaimMode;
    }

    public int durationFor(GameMode mode, GameDifficulty difficulty) {
        return difficulty.durationFor(mode);
    }

    public int countdownSeconds() {
        return countdownSeconds;
    }

    public int punishmentSeconds() {
        return punishmentSeconds;
    }

    public int afterGameSeconds() {
        return afterGameSeconds;
    }

    public boolean enableSpeedBonus() {
        return enableSpeedBonus;
    }

    public boolean enableFireResistanceEffect() {
        return enableFireResistanceEffect;
    }

    public boolean enableWaterBreathingEffect() {
        return enableWaterBreathingEffect;
    }

    public boolean enableNightVisionEffect() {
        return enableNightVisionEffect;
    }

    public boolean giveInitialItems() {
        return giveInitialItems;
    }

    public boolean preventFallDamage() {
        return preventFallDamage;
    }

    public boolean teleportBackOnDeath() {
        return teleportBackOnDeath;
    }

    public boolean returnItemsOnDeath() {
        return returnItemsOnDeath;
    }

    public boolean fixBingoCard() {
        return fixBingoCard;
    }

    public boolean showGlobalScore() {
        return showGlobalScore;
    }

    public boolean consumeOnClaim() {
        return consumeOnClaim;
    }

    public void setConsumeOnClaim(boolean consumeOnClaim) {
        this.consumeOnClaim = consumeOnClaim;
    }

    public String defaultWorldName() {
        return defaultWorldName;
    }

    public boolean debugPrintNodeGraphOnStartup() {
        return debugPrintNodeGraphOnStartup;
    }

    public void saveRuntimeSettings() {
        FileConfiguration cfg = plugin.getConfig();
        cfg.set("default-mode", defaultMode.key());
        cfg.set("default-difficulty", defaultDifficulty.key());
        cfg.set("board-claim-mode", boardClaimMode.key());
        cfg.set("consume-on-claim", consumeOnClaim);
        plugin.saveConfig();
    }

    private static BoardClaimMode parseClaimMode(String raw) {
        try {
            return BoardClaimMode.fromKey(raw);
        } catch (IllegalArgumentException ignored) {
            return BoardClaimMode.AUTO;
        }
    }

    private static GameMode parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return GameMode.DEFAULT;
        }
        try {
            return GameMode.fromKey(raw);
        } catch (IllegalArgumentException ignored) {
            return GameMode.DEFAULT;
        }
    }

    private void loadDifficultyLevels(FileConfiguration cfg) {
        difficultiesByKey.clear();

        List<Map<?, ?>> levels = cfg.getMapList("difficulty-levels");
        if (levels.isEmpty()) {
            throw new IllegalStateException("difficulty-levels must be a non-empty list of maps.");
        }
        int index = 0;
        for (Map<?, ?> level : levels) {
            String key = normalize(stringValue(level.get("key")));
            if (key.isBlank()) {
                throw new IllegalStateException("difficulty-levels entry is missing key.");
            }
            int targetScore = intValue(level.get("target-score"), 50);
            int maxDeviation = Math.max(0, intValue(level.get("max-deviation"), 20));
            Map<GameMode, Integer> durations = readDurations(level, key);
            difficultiesByKey.putIfAbsent(key, new GameDifficulty(key, index++, targetScore, maxDeviation, durations));
        }
        if (difficultiesByKey.isEmpty()) {
            throw new IllegalStateException("difficulty-levels list is empty after normalization.");
        }
    }

    private static Map<GameMode, Integer> readDurations(Map<?, ?> level, String key) {
        Object rawDurations = level.get("durations");
        if (!(rawDurations instanceof Map<?, ?> durationsMap)) {
            throw new IllegalStateException("difficulty-levels entry '" + key + "' is missing durations.");
        }
        Map<GameMode, Integer> durations = new EnumMap<>(GameMode.class);
        for (GameMode mode : GameMode.values()) {
            Object rawValue = durationsMap.get(mode.key());
            if (rawValue == null) {
                throw new IllegalStateException(
                        "difficulty-levels entry '" + key + "' is missing durations." + mode.key());
            }
            durations.put(mode, Math.max(1, intValue(rawValue, 1)));
        }
        return durations;
    }

    private GameDifficulty parseDifficulty(String raw) {
        if (raw == null || raw.isBlank()) {
            return difficulties().getFirst();
        }
        GameDifficulty found = difficultiesByKey.get(normalize(raw));
        if (found != null) {
            return found;
        }
        return difficulties().getFirst();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
