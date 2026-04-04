package me._furiouspotato_.bingo.service;

import java.util.EnumMap;
import java.util.Map;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import me._furiouspotato_.bingo.model.GameDifficulty;
import me._furiouspotato_.bingo.model.GameMode;

public final class RulesConfigService {
    private final JavaPlugin plugin;

    private GameMode defaultMode = GameMode.DEFAULT;
    private GameDifficulty defaultDifficulty = GameDifficulty.EASY;
    private final Map<GameMode, int[]> durationSeconds = new EnumMap<>(GameMode.class);
    private int punishmentSeconds = 10;

    public RulesConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        FileConfiguration cfg = plugin.getConfig();
        defaultMode = GameMode.DEFAULT;
        defaultDifficulty = GameDifficulty.EASY;

        durationSeconds.clear();
        durationSeconds.put(GameMode.DEFAULT, readDuration(cfg, "default-game-duration"));
        durationSeconds.put(GameMode.BUTFAST, readDuration(cfg, "butfast-game-duration"));
        durationSeconds.put(GameMode.COLLECTALL, readDuration(cfg, "collectall-game-duration"));
        punishmentSeconds = Math.max(0, cfg.getInt("punishment-time", 10));
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

    public int durationFor(GameMode mode, GameDifficulty difficulty) {
        return durationSeconds.get(mode)[difficulty.index()];
    }

    public int punishmentSeconds() {
        return punishmentSeconds;
    }

    private static int[] readDuration(FileConfiguration cfg, String path) {
        int[] values = new int[GameDifficulty.values().length];
        for (GameDifficulty difficulty : GameDifficulty.values()) {
            values[difficulty.index()] = cfg.getInt(path + "." + difficulty.index(), 600);
        }
        return values;
    }
}
