package me._furiouspotato_.bingo.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import me._furiouspotato_.bingo.model.BoardEntry;
import me._furiouspotato_.bingo.model.GameDifficulty;
import me._furiouspotato_.bingo.model.GameMode;

public final class BoardService {
    public record Board(BoardEntry[] entries, int[] effectiveDifficulties) {
    }

    private static final int[] COLLECT_ALL_SLOTS = { 6, 7, 8, 11, 12, 13, 16, 17, 18 };
    private final DependencyCostService dependencyCostService;

    public BoardService(RulesConfigService rules, DependencyCostService dependencyCostService) {
        this.dependencyCostService = dependencyCostService;
    }

    public Board generateBoard(GameMode mode, GameDifficulty difficulty, List<BoardEntry> pool) {
        if (pool.isEmpty()) {
            throw new IllegalStateException("Board entry pool is empty.");
        }

        List<Candidate> candidates = new ArrayList<>();
        for (BoardEntry entry : pool) {
            int score = (int) Math.round(dependencyCostService.scoreForNode(entry.costNodeId()));
            if (Math.abs(score - difficulty.targetScore()) <= difficulty.maxDeviation()) {
                candidates.add(new Candidate(entry, score));
            }
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException("No board items fit the selected difficulty score band.");
        }

        int required = mode == GameMode.COLLECTALL ? 9 : 25;
        if (candidates.size() < required) {
            throw new IllegalStateException("Not enough candidate entries for board generation.");
        }

        Random random = new Random();
        Board bestBoard = null;
        int bestDistance = Integer.MAX_VALUE;

        for (int attempt = 0; attempt < 1200; attempt++) {
            Collections.shuffle(candidates, random);
            List<Candidate> chosen = candidates.subList(0, required);
            Board board = placeOnBoard(mode, chosen);
            int distance = scoreDistance(mode, difficulty, board);
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

    private static Board placeOnBoard(GameMode mode, List<Candidate> chosen) {
        BoardEntry[] entries = new BoardEntry[25];
        int[] effectiveDifficulties = new int[25];
        if (mode == GameMode.COLLECTALL) {
            for (int i = 0; i < COLLECT_ALL_SLOTS.length; i++) {
                int slot = COLLECT_ALL_SLOTS[i];
                entries[slot] = chosen.get(i).entry();
                effectiveDifficulties[slot] = chosen.get(i).effectiveDifficulty();
            }
        } else {
            for (int i = 0; i < 25; i++) {
                entries[i] = chosen.get(i).entry();
                effectiveDifficulties[i] = chosen.get(i).effectiveDifficulty();
            }
        }
        return new Board(entries, effectiveDifficulties);
    }

    private static int scoreDistance(GameMode mode, GameDifficulty difficulty, Board board) {
        if (mode == GameMode.DEFAULT) {
            int distance = 0;
            for (int[] line : lines()) {
                int sum = 0;
                for (int idx : line) {
                    sum += board.effectiveDifficulties[idx];
                }
                int avg = sum / 5;
                if (Math.abs(avg - difficulty.targetScore()) > difficulty.maxDeviation()) {
                    return -1;
                }
                distance += square(sum - difficulty.targetScore() * 5);
            }
            return distance;
        }

        int sum = 0;
        int count = 0;
        for (int i = 0; i < board.entries.length; i++) {
            if (board.entries[i] == null) {
                continue;
            }
            sum += board.effectiveDifficulties[i];
            count++;
        }
        int avg = sum / Math.max(1, count);
        if (Math.abs(avg - difficulty.targetScore()) > difficulty.maxDeviation()) {
            return -1;
        }
        return Math.abs(sum - difficulty.targetScore() * count);
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

    private record Candidate(BoardEntry entry, int effectiveDifficulty) {
    }
}
