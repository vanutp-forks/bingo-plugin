package me._furiouspotato_.bingo.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.bukkit.Material;

import me._furiouspotato_.bingo.model.GameDifficulty;
import me._furiouspotato_.bingo.model.GameMode;

public final class BoardService {
    public record Board(Material[] items, int[] complexities) {
    }

    private static final int[] COLLECT_ALL_SLOTS = { 6, 7, 8, 11, 12, 13, 16, 17, 18 };

    public Board generateBoard(GameMode mode, GameDifficulty difficulty, List<ItemPoolService.ItemEntry> pool) {
        if (pool.isEmpty()) {
            throw new IllegalStateException("Item pool is empty.");
        }

        DifficultyRule rule = DifficultyRule.forDifficulty(difficulty);
        List<ItemPoolService.ItemEntry> candidates = new ArrayList<>();
        for (ItemPoolService.ItemEntry entry : pool) {
            if (rule.matches(entry.complexity())) {
                candidates.add(entry);
            }
        }

        int required = mode == GameMode.COLLECTALL ? 9 : 25;
        if (candidates.size() < required) {
            throw new IllegalStateException("Not enough candidate items for board generation.");
        }

        Random random = new Random();
        Board bestBoard = null;
        int bestDistance = Integer.MAX_VALUE;

        for (int attempt = 0; attempt < 800; attempt++) {
            Collections.shuffle(candidates, random);
            List<ItemPoolService.ItemEntry> chosen = candidates.subList(0, required);
            Board board = placeOnBoard(mode, chosen);
            int distance = scoreDistance(mode, rule, board);
            if (distance >= 0 && distance < bestDistance) {
                bestDistance = distance;
                bestBoard = board;
            }
            if (distance == 0) {
                break;
            }
        }

        if (bestBoard == null) {
            throw new IllegalStateException("Could not generate a valid board.");
        }
        return bestBoard;
    }

    private static Board placeOnBoard(GameMode mode, List<ItemPoolService.ItemEntry> chosen) {
        Material[] items = new Material[25];
        int[] complexities = new int[25];
        if (mode == GameMode.COLLECTALL) {
            for (int i = 0; i < COLLECT_ALL_SLOTS.length; i++) {
                int slot = COLLECT_ALL_SLOTS[i];
                items[slot] = chosen.get(i).material();
                complexities[slot] = chosen.get(i).complexity();
            }
        } else {
            for (int i = 0; i < 25; i++) {
                items[i] = chosen.get(i).material();
                complexities[i] = chosen.get(i).complexity();
            }
        }
        return new Board(items, complexities);
    }

    private static int scoreDistance(GameMode mode, DifficultyRule rule, Board board) {
        if (mode == GameMode.DEFAULT) {
            int distance = 0;
            for (int[] line : lines()) {
                int sum = 0;
                for (int idx : line) {
                    sum += board.complexities[idx];
                }
                if (!rule.acceptLineAverage(sum / 5)) {
                    return -1;
                }
                distance += square(sum - rule.targetAvg() * 5);
            }
            return distance;
        }

        int sum = 0;
        int count = 0;
        for (int i = 0; i < board.items.length; i++) {
            if (board.items[i] == null) {
                continue;
            }
            sum += board.complexities[i];
            count++;
        }
        int avg = sum / Math.max(1, count);
        if (!rule.acceptLineAverage(avg)) {
            return -1;
        }
        return Math.abs(sum - rule.targetAvg() * count);
    }

    private static List<int[]> lines() {
        List<int[]> lines = new ArrayList<>();
        for (int row = 0; row < 5; row++) {
            lines.add(new int[] { row * 5, row * 5 + 1, row * 5 + 2, row * 5 + 3, row * 5 + 4 });
        }
        for (int col = 0; col < 5; col++) {
            lines.add(new int[] { col, col + 5, col + 10, col + 15, col + 20 });
        }
        lines.add(new int[] { 0, 6, 12, 18, 24 });
        lines.add(new int[] { 4, 8, 12, 16, 20 });
        return lines;
    }

    private static int square(int x) {
        return x * x;
    }

    private record DifficultyRule(int min, int max, int minAvg, int maxAvg, int targetAvg) {
        static DifficultyRule forDifficulty(GameDifficulty difficulty) {
            return switch (difficulty) {
                case BABY -> new DifficultyRule(-1, 25, -1, 20, 10);
                case EASY -> new DifficultyRule(-1, 60, -1, 45, 32);
                case MEDIUM -> new DifficultyRule(40, 100, 50, 90, 75);
                case HARD -> new DifficultyRule(80, 200, 90, 180, 150);
                case INSANE -> new DifficultyRule(150, -1, 175, -1, 400);
            };
        }

        boolean matches(int complexity) {
            return (min == -1 || complexity >= min) && (max == -1 || complexity <= max);
        }

        boolean acceptLineAverage(int avg) {
            return (minAvg == -1 || avg >= minAvg) && (maxAvg == -1 || avg <= maxAvg);
        }
    }
}
