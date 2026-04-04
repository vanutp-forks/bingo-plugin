package me._furiouspotato_.bingo.service;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import me._furiouspotato_.bingo.model.TeamColor;

public final class TeamPreferenceStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, TeamColor> preferences = new ConcurrentHashMap<>();

    public TeamPreferenceStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "team-preferences.yml");
    }

    public void load() {
        preferences.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) {
            return;
        }

        for (String nickname : section.getKeys(false)) {
            String teamName = section.getString(nickname);
            if (teamName == null) {
                continue;
            }
            try {
                preferences.put(normalize(nickname), TeamColor.parse(teamName));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping invalid team preference for " + nickname + ": " + teamName);
            }
        }
    }

    public TeamColor getPreference(String nickname) {
        return preferences.get(normalize(nickname));
    }

    public void setPreference(String nickname, TeamColor teamColor) {
        preferences.put(normalize(nickname), teamColor);
        save();
    }

    public void removePreference(String nickname) {
        preferences.remove(normalize(nickname));
        save();
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection players = yaml.createSection("players");
        for (Map.Entry<String, TeamColor> entry : preferences.entrySet()) {
            players.set(entry.getKey(), entry.getValue().name());
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to save team-preferences.yml: " + exception.getMessage());
        }
    }

    private static String normalize(String nickname) {
        return nickname.toLowerCase(Locale.ROOT);
    }
}
