package me._furiouspotato_.bingo.service;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.world.properties.SlimeProperties;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Random;

public final class AspService {
    private final Plugin plugin;
    private final Random random = new Random();

    private AdvancedSlimePaperAPI asp;

    private String currentWorldName;

    public AspService(Plugin plugin) {
        this.plugin = plugin;
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
        final var world = Bukkit.getWorld(currentWorldName);
        if (world == null) {
            currentWorldName = null;
            return;
        }
        final var players = world.getPlayers();
        final var spawn = Bukkit.getWorlds().getFirst().getSpawnLocation();
        for (final var player : players) {
            player.teleport(spawn);
        }
        boolean unloaded = Bukkit.unloadWorld(world, false);
        if (!unloaded) {
            plugin.getLogger().warning("ASP support: Failed to unload world " + currentWorldName);
        }
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
        propertyMap.setValue(SlimeProperties.SEED, random.nextLong());
        propertyMap.setValue(SlimeProperties.GENERATE_WORLD, true);
        return propertyMap;
    }

    public World setupWorld() {
        init();
        unloadWorld();
        final var worldName = getRandomWorldName();
        final var worldProps = getWorldPropertyMap();
        final var slimeWorld = asp.createEmptyWorld(worldName, true, worldProps, null);
        if (Bukkit.getWorld(worldName) != null) {
            throw new RuntimeException("World " + worldName + " is already loaded");
        }
        final var world = asp.loadWorld(slimeWorld, true);
        currentWorldName = worldName;
        return world.getBukkitWorld();
    }
}
