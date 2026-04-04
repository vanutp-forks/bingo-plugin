package me._furiouspotato_.bingo.model;

import java.util.Arrays;
import java.util.List;

public enum GameDifficulty {
    BABY(0, "baby"),
    EASY(1, "easy"),
    MEDIUM(2, "medium"),
    HARD(3, "hard"),
    INSANE(4, "insane");

    private final int index;
    private final String key;

    GameDifficulty(int index, String key) {
        this.index = index;
        this.key = key;
    }

    public int index() {
        return index;
    }

    public String key() {
        return key;
    }

    public static GameDifficulty fromKey(String key) {
        for (GameDifficulty difficulty : values()) {
            if (difficulty.key.equalsIgnoreCase(key)) {
                return difficulty;
            }
        }
        throw new IllegalArgumentException("Unknown game difficulty: " + key);
    }

    public static List<String> keys() {
        return Arrays.stream(values()).map(GameDifficulty::key).toList();
    }
}
