package me._furiouspotato_.bingo.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.bukkit.Material;

import me._furiouspotato_.bingo.model.BoardEntry;
import me._furiouspotato_.bingo.model.CostNode;
import me._furiouspotato_.bingo.model.GameDifficulty;
import me._furiouspotato_.bingo.model.GameMode;

public final class NodeDebugPrinter {
    private static final String NODE_PREFIX = "[node-debug] ";
    private static final String BOARD_PREFIX = "[board-debug] ";
    private static final int WRAP_WIDTH = 96;

    private NodeDebugPrinter() {
    }

    public static void print(Logger logger, ItemPoolService itemPool, DependencyCostService dependencyCostService) {
        log(logger, NODE_PREFIX, "===== debug information start =====");
        log(logger, NODE_PREFIX, "summary");
        log(logger, NODE_PREFIX, "  nodes: " + itemPool.nodes().size());
        log(logger, NODE_PREFIX, "  board items: " + itemPool.entries().size());

        List<CostNode> sortedNodes = new ArrayList<>(itemPool.nodes().values());
        sortedNodes.sort(Comparator.comparing(CostNode::id, String.CASE_INSENSITIVE_ORDER));
        for (CostNode node : sortedNodes) {
            log(logger, NODE_PREFIX, "node " + node.id());
            log(logger, NODE_PREFIX, "  type: " + node.type().name().toLowerCase(Locale.ROOT));
            log(logger, NODE_PREFIX, "  atomic: " + node.isAtomic() + " | once: " + node.once());
            log(logger, NODE_PREFIX, "  base/perBatch/beta: " + format(node.base()) + " / "
                    + format(node.perBatch()) + " / " + format(node.beta()));
            log(logger, NODE_PREFIX, "  infer vanilla recipe: " + node.inferRecipeFromVanilla());
            log(logger, NODE_PREFIX, "  display: "
                    + (node.displayMaterial() == null ? "-" : lower(node.displayMaterial().name())));
            printWrapped(logger, NODE_PREFIX, "  accepted: ", acceptedMaterials(node));
            if (node.recipe() == null) {
                log(logger, NODE_PREFIX, "  recipe: -");
            } else {
                log(logger, NODE_PREFIX, "  recipe output: " + node.recipe().outputAmount());
                printWrapped(logger, NODE_PREFIX, "  recipe ingredients: ", recipeIngredients(node));
            }
        }

        List<BoardEntry> boardItems = new ArrayList<>(itemPool.entries());
        boardItems.sort(Comparator.comparing(BoardEntry::id, String.CASE_INSENSITIVE_ORDER));
        for (BoardEntry entry : boardItems) {
            DependencyCostService.Evaluation evaluation = dependencyCostService.evaluateNode(entry.costNodeId());
            Material display = entry.displayMaterial() == null ? Material.PAPER : entry.displayMaterial();
            log(logger, NODE_PREFIX, "board item " + entry.id());
            log(logger, NODE_PREFIX, "  display: " + lower(display.name()));
            log(logger, NODE_PREFIX, "  cost node: " + entry.costNodeId());
            printWrapped(logger, NODE_PREFIX, "  accepted: ", acceptedMaterials(entry));
            log(logger, NODE_PREFIX, "  score: " + format(evaluation.totalScore()) + " | max depth: "
                    + evaluation.maxDepth());
            printCountMap(logger, NODE_PREFIX, "  resources:", evaluation.resourceCounts());
            printOperations(logger, NODE_PREFIX, "  operations:", evaluation.operationCounts());
        }

        log(logger, NODE_PREFIX, "===== debug information end =====");
    }

    public static void printGeneratedBoard(Logger logger, GameMode mode, GameDifficulty difficulty,
            BoardService.GeneratedBoard generatedBoard, List<BoardEntry> pool,
            DependencyCostService dependencyCostService) {
        BoardService.Board board = generatedBoard.board();
        List<CandidateEntry> candidates = buildCandidates(pool, difficulty, dependencyCostService);
        List<CellDebug> activeCells = buildActiveCells(board, dependencyCostService);

        log(logger, BOARD_PREFIX, "===== generated board start =====");
        log(logger, BOARD_PREFIX, "summary");
        log(logger, BOARD_PREFIX, "  mode: " + mode.key());
        log(logger, BOARD_PREFIX, "  difficulty: " + difficulty.key());
        log(logger, BOARD_PREFIX, "  target score: " + difficulty.targetScore());
        log(logger, BOARD_PREFIX, "  max deviation: " + difficulty.maxDeviation());
        log(logger, BOARD_PREFIX, "  pool size: " + generatedBoard.poolSize());
        log(logger, BOARD_PREFIX, "  candidates in band: " + generatedBoard.candidateCount());
        log(logger, BOARD_PREFIX, "  required cells: " + generatedBoard.requiredCount());
        log(logger, BOARD_PREFIX, "  attempts tried: " + generatedBoard.attemptsTried());
        log(logger, BOARD_PREFIX, "  selection distance: " + generatedBoard.selectionDistance());
        log(logger, BOARD_PREFIX, "  selection metric: "
                + (mode == GameMode.DEFAULT ? "sum of squared line deltas" : "absolute total delta"));

        printWrapped(logger, BOARD_PREFIX, "candidate band: ",
                candidates.stream()
                        .map(candidate -> candidate.entry().id() + "(" + candidate.effectiveDifficulty() + ")")
                        .toList());

        log(logger, BOARD_PREFIX, "cells");
        for (CellDebug cell : activeCells) {
            log(logger, BOARD_PREFIX, "  slot " + formatSlot(cell.slot()) + " | " + cell.entry().id()
                    + " | effective difficulty " + cell.effectiveDifficulty());
            printCountMap(logger, BOARD_PREFIX, "    resources:", cell.evaluation().resourceCounts());
            printOperations(logger, BOARD_PREFIX, "    operations:", cell.evaluation().operationCounts());
        }

        if (mode == GameMode.DEFAULT) {
            log(logger, BOARD_PREFIX, "completion lines");
            int lineIndex = 1;
            for (int[] line : defaultLines()) {
                printLineSummary(logger, activeCells, line, lineIndex++, difficulty, dependencyCostService);
            }
        } else {
            log(logger, BOARD_PREFIX, "completion units");
            for (CellDebug cell : activeCells) {
                log(logger, BOARD_PREFIX, "  slot " + formatSlot(cell.slot()) + " completes independently.");
            }
        }

        log(logger, BOARD_PREFIX, "===== generated board end =====");
    }

    private static List<CandidateEntry> buildCandidates(List<BoardEntry> pool, GameDifficulty difficulty,
            DependencyCostService dependencyCostService) {
        List<CandidateEntry> candidates = new ArrayList<>();
        for (BoardEntry entry : pool) {
            int score = (int) Math.round(dependencyCostService.scoreForNode(entry.costNodeId()));
            if (Math.abs(score - difficulty.targetScore()) <= difficulty.maxDeviation()) {
                candidates.add(new CandidateEntry(entry, score));
            }
        }
        candidates.sort(Comparator.comparingInt(CandidateEntry::effectiveDifficulty)
                .thenComparing(candidate -> candidate.entry().id(), String.CASE_INSENSITIVE_ORDER));
        return candidates;
    }

    private static List<CellDebug> buildActiveCells(BoardService.Board board,
            DependencyCostService dependencyCostService) {
        List<CellDebug> cells = new ArrayList<>();
        for (int slot = 0; slot < board.entries().length; slot++) {
            BoardEntry entry = board.entries()[slot];
            if (entry == null) {
                continue;
            }
            cells.add(new CellDebug(slot, entry, board.effectiveDifficulties()[slot],
                    dependencyCostService.evaluateNode(entry.costNodeId())));
        }
        return cells;
    }

    private static void printLineSummary(Logger logger, List<CellDebug> cells, int[] line, int lineIndex,
            GameDifficulty difficulty, DependencyCostService dependencyCostService) {
        Map<Integer, CellDebug> bySlot = new LinkedHashMap<>();
        for (CellDebug cell : cells) {
            bySlot.put(cell.slot(), cell);
        }

        List<CellDebug> lineCells = new ArrayList<>();
        for (int slot : line) {
            CellDebug cell = bySlot.get(slot);
            if (cell != null) {
                lineCells.add(cell);
            }
        }
        if (lineCells.isEmpty()) {
            return;
        }

        DependencyCostService.Evaluation combinedEvaluation = dependencyCostService.evaluateNodes(
                lineCells.stream().map(cell -> cell.entry().costNodeId()).toList());
        double totalDifficulty = combinedEvaluation.totalScore();
        double averageDifficulty = totalDifficulty / lineCells.size();
        double deltaFromTarget = totalDifficulty - difficulty.targetScore() * lineCells.size();
        log(logger, BOARD_PREFIX, "  line " + lineIndex + " | slots " + formatSlots(lineCells));
        printWrapped(logger, BOARD_PREFIX, "    cells: ",
                lineCells.stream()
                        .map(cell -> cell.entry().id() + "(" + cell.effectiveDifficulty() + ")")
                        .toList());
        log(logger, BOARD_PREFIX, "    combined score total/avg/delta: " + format(totalDifficulty) + " / "
                + format(averageDifficulty) + " / " + format(deltaFromTarget));
        printCountMap(logger, BOARD_PREFIX, "    aggregated resources:", combinedEvaluation.resourceCounts());
        printOperations(logger, BOARD_PREFIX, "    aggregated operations:", combinedEvaluation.operationCounts());
    }

    private static void printCountMap(Logger logger, String prefix, String label, Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            log(logger, prefix, label + " none");
            return;
        }
        printWrapped(logger, prefix, label + " ",
                counts.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                        .map(entry -> entry.getKey() + " x" + entry.getValue())
                        .toList());
    }

    private static void printOperations(Logger logger, String prefix, String label,
            Map<DependencyCostService.OperationDepthKey, Integer> operations) {
        if (operations.isEmpty()) {
            log(logger, prefix, label + " none");
            return;
        }
        log(logger, prefix, label);
        Map<Integer, List<String>> byDepth = new LinkedHashMap<>();
        for (Map.Entry<DependencyCostService.OperationDepthKey, Integer> entry : operations.entrySet()) {
            byDepth.computeIfAbsent(entry.getKey().depth(), ignored -> new ArrayList<>())
                    .add(entry.getKey().nodeId() + " x" + entry.getValue());
        }
        for (Map.Entry<Integer, List<String>> entry : byDepth.entrySet()) {
            printWrapped(logger, prefix, "    depth " + entry.getKey() + ": ", entry.getValue());
        }
    }

    private static void printWrapped(Logger logger, String prefix, String firstLabel, List<String> items) {
        if (items.isEmpty()) {
            log(logger, prefix, firstLabel + "-");
            return;
        }
        List<String> lines = wrap(items, WRAP_WIDTH - firstLabel.length());
        for (int i = 0; i < lines.size(); i++) {
            log(logger, prefix, (i == 0 ? firstLabel : spaces(firstLabel.length())) + lines.get(i));
        }
    }

    private static List<String> wrap(List<String> items, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String item : items) {
            if (current.isEmpty()) {
                current.append(item);
                continue;
            }
            if (current.length() + 2 + item.length() <= maxWidth) {
                current.append(", ").append(item);
                continue;
            }
            lines.add(current.toString());
            current.setLength(0);
            current.append(item);
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static List<int[]> defaultLines() {
        List<String> raw = List.of(
                "0,1,2,3,4",
                "5,6,7,8,9",
                "10,11,12,13,14",
                "15,16,17,18,19",
                "20,21,22,23,24",
                "0,5,10,15,20",
                "1,6,11,16,21",
                "2,7,12,17,22",
                "3,8,13,18,23",
                "4,9,14,19,24",
                "0,6,12,18,24",
                "4,8,12,16,20");
        List<int[]> lines = new ArrayList<>();
        for (String value : raw) {
            String[] parts = value.split(",");
            int[] line = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                line[i] = Integer.parseInt(parts[i]);
            }
            lines.add(line);
        }
        return lines;
    }

    private static List<String> acceptedMaterials(CostNode node) {
        if (node.boardMatch() == null) {
            return List.of("-");
        }
        return node.boardMatch().acceptedMaterials().stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(material -> lower(material.name()))
                .toList();
    }

    private static List<String> acceptedMaterials(BoardEntry entry) {
        return entry.match().acceptedMaterials().stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(material -> lower(material.name()))
                .toList();
    }

    private static List<String> recipeIngredients(CostNode node) {
        return node.recipe().ingredients().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> entry.getKey() + " x" + entry.getValue())
                .toList();
    }

    private static String format(Double value) {
        if (value == null) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatSlots(List<CellDebug> cells) {
        return cells.stream().map(cell -> formatSlot(cell.slot())).collect(Collectors.joining(", "));
    }

    private static String formatSlot(int slot) {
        return String.format(Locale.ROOT, "%02d", slot);
    }

    private static String spaces(int count) {
        return " ".repeat(Math.max(0, count));
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static void log(Logger logger, String prefix, String message) {
        logger.info(prefix + message);
    }

    private record CandidateEntry(BoardEntry entry, int effectiveDifficulty) {
    }

    private record CellDebug(int slot, BoardEntry entry, int effectiveDifficulty,
            DependencyCostService.Evaluation evaluation) {
    }
}
