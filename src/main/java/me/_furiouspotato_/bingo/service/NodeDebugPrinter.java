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

public final class NodeDebugPrinter {
    private NodeDebugPrinter() {
    }

    public static void print(Logger logger, ItemPoolService itemPool, DependencyCostService dependencyCostService) {
        logger.info("[node-debug] ===== node graph debug start =====");
        logger.info("[node-debug] nodes=" + itemPool.nodes().size() + " boardItems=" + itemPool.entries().size());

        List<CostNode> sortedNodes = new ArrayList<>(itemPool.nodes().values());
        sortedNodes.sort(Comparator.comparing(CostNode::id, String.CASE_INSENSITIVE_ORDER));
        for (CostNode node : sortedNodes) {
            String recipeLabel = node.recipe() == null ? "-"
                    : "out=" + node.recipe().outputAmount() + " ingredients=" + node.recipe().ingredients();
            String acceptedLabel = node.boardMatch() == null ? "-"
                    : node.boardMatch().acceptedMaterials().stream()
                            .sorted(Comparator.comparing(Enum::name))
                            .map(material -> material.name().toLowerCase(Locale.ROOT))
                            .collect(Collectors.joining(","));
            logger.info("[node-debug] id=" + node.id()
                    + " type=" + node.type().name().toLowerCase(Locale.ROOT)
                    + " atomic=" + node.isAtomic()
                    + " once=" + node.once()
                    + " base=" + format(node.base())
                    + " perBatch=" + format(node.perBatch())
                    + " beta=" + format(node.beta())
                    + " inferVanilla=" + node.inferRecipeFromVanilla()
                    + " display="
                    + (node.displayMaterial() == null ? "-" : node.displayMaterial().name().toLowerCase(Locale.ROOT))
                    + " accepted=" + acceptedLabel
                    + " recipe=" + recipeLabel);
        }

        List<BoardEntry> boardItems = new ArrayList<>(itemPool.entries());
        boardItems.sort(Comparator.comparing(BoardEntry::id, String.CASE_INSENSITIVE_ORDER));
        for (BoardEntry entry : boardItems) {
            DependencyCostService.Evaluation evaluation = dependencyCostService.evaluateNode(entry.costNodeId());
            Material display = entry.displayMaterial() == null ? Material.PAPER : entry.displayMaterial();
            String accepted = entry.match().acceptedMaterials().stream()
                    .sorted(Comparator.comparing(Enum::name))
                    .map(material -> material.name().toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining(","));
            logger.info("[board-item-debug] id=" + entry.id()
                    + " display=" + display.name().toLowerCase(Locale.ROOT)
                    + " costNode=" + entry.costNodeId()
                    + " accepted=" + accepted
                    + " score=" + format(evaluation.totalScore())
                    + " maxDepth=" + evaluation.maxDepth()
                    + " resources=" + evaluation.resourceCounts()
                    + " operations=" + evaluation.operationCounts()
                    + " layers=" + formatLayers(evaluation.operationCounts()));
        }

        logger.info("[node-debug] ===== node graph debug end =====");
    }

    private static String format(Double value) {
        if (value == null) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatLayers(Map<DependencyCostService.OperationDepthKey, Integer> operations) {
        Map<Integer, List<String>> byDepth = new LinkedHashMap<>();
        for (Map.Entry<DependencyCostService.OperationDepthKey, Integer> entry : operations.entrySet()) {
            byDepth.computeIfAbsent(entry.getKey().depth(), ignored -> new ArrayList<>())
                    .add(entry.getKey().nodeId() + " x" + entry.getValue());
        }
        return byDepth.entrySet().stream()
                .map(entry -> "d" + entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
