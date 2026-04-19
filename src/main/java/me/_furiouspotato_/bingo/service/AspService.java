package me._furiouspotato_.bingo.service;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.world.properties.SlimeProperties;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Random;

public final class AspService {
    private final Plugin plugin;
    private final Random random = new Random();

    private AdvancedSlimePaperAPI asp;

    private String currentWorldName;

    public AspService(Plugin plugin) {
        this.plugin = plugin;
    }

    public String currentWorldName() {
        return currentWorldName;
    }

    private void init() {
        if (asp == null) {
            asp = AdvancedSlimePaperAPI.instance();
        }
    }

    private void unloadWorld() {
        init();
        if (currentWorldName == null) {
            return;
        }
        List.of("normal", "nether", "the_end").forEach(type -> {
            final var worldName = type.equals("normal") ? currentWorldName : currentWorldName + "_" + type;
            final var world = Bukkit.getWorld(worldName);
            if (world == null) {
                return;
            }
            final var players = world.getPlayers();
            final var spawn = Bukkit.getWorlds().getFirst().getSpawnLocation();
            for (final var player : players) {
                player.teleport(spawn);
            }
            boolean unloaded = Bukkit.unloadWorld(world, false);
            if (!unloaded) {
                plugin.getLogger().warning("ASP support: Failed to unload world " + worldName);
            }
        });
        currentWorldName = null;
    }

    private String getRandomWorldName() {
        final var chars = "abcdefghijklmnoprstuvwxyz";
        final var res = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            res.append(chars.charAt(random.nextInt(chars.length())));
        }
        return "bingo_" + res;
    }

    private SlimePropertyMap getWorldPropertyMap() {
        SlimePropertyMap propertyMap = new SlimePropertyMap();
        propertyMap.setValue(SlimeProperties.DIFFICULTY, "normal");
        propertyMap.setValue(SlimeProperties.DRAGON_BATTLE, true);
        propertyMap.setValue(SlimeProperties.SEED, random.nextLong());
        propertyMap.setValue(SlimeProperties.GENERATE_WORLD, true);
        propertyMap.setValue(SlimeProperties.SAVE_POI, true);
        return propertyMap;
    }

    public World setupWorld() {
        init();
        unloadWorld();
        final var namePrefix = getRandomWorldName();
        final var commonProps = getWorldPropertyMap();
        List.of("normal", "nether", "the_end").forEach(type -> {
            final var worldName = type.equals("normal") ? namePrefix : namePrefix + "_" + type;
            final var worldProps = commonProps.clone();
            worldProps.setValue(SlimeProperties.ENVIRONMENT, type);
            final var slimeWorld = asp.createEmptyWorld(worldName, true, worldProps, null);
            if (Bukkit.getWorld(worldName) != null) {
                throw new RuntimeException("World " + worldName + " is already loaded");
            }
            asp.loadWorld(slimeWorld, true);
        });
        currentWorldName = namePrefix;
        return Bukkit.getWorld(currentWorldName);
    }
}
