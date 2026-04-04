package me._furiouspotato_.bingo.service;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemPoolService {
    public record ItemEntry(Material material, int complexity) {
    }

    private final JavaPlugin plugin;
    private final List<ItemEntry> entries = new ArrayList<>();

    public ItemPoolService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File itemsFile = new File(plugin.getDataFolder(), "items.yml");
        if (!itemsFile.exists()) {
            plugin.saveResource("items.yml", false);
        }

        entries.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(itemsFile);
        for (String raw : yaml.getStringList("items")) {
            String[] split = raw.split(":");
            if (split.length != 2) {
                continue;
            }
            int complexity;
            try {
                complexity = Integer.parseInt(split[1]);
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (complexity == -1) {
                continue;
            }

            Material material = Material.matchMaterial(split[0]);
            if (material == null) {
                plugin.getLogger().warning("Skipping unknown material in items.yml: " + split[0]);
                continue;
            }
            entries.add(new ItemEntry(material, complexity));
        }
    }

    public List<ItemEntry> entries() {
        return Collections.unmodifiableList(entries);
    }
}
