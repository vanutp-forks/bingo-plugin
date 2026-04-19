package me._furiouspotato_.bingo.listener;

import me._furiouspotato_.bingo.service.GameSessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class BingoGameplayListener implements Listener {
    private final JavaPlugin plugin;
    private final GameSessionManager session;
    private final Advancement enterNetherAdvancement;
    private final Advancement enterEndAdvancement;

    public BingoGameplayListener(JavaPlugin plugin, GameSessionManager session) {
        this.plugin = plugin;
        this.session = session;
        this.enterNetherAdvancement = plugin.getServer().getAdvancement(NamespacedKey.minecraft("story/enter_the_nether"));
        if (enterNetherAdvancement == null) {
            plugin.getLogger().warning("story/enter_the_nether advancement not found");
        }
        this.enterEndAdvancement = plugin.getServer().getAdvancement(NamespacedKey.minecraft("story/enter_the_end"));
        if (enterNetherAdvancement == null) {
            plugin.getLogger().warning("story/enter_the_end advancement not found");
        }
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        session.onItemProgress(player, event.getItem().getItemStack());
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack crafted = event.getCurrentItem();
        if (crafted == null) {
            return;
        }
        session.onItemProgress(player, crafted);
    }

    @EventHandler
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        session.onItemProgress(event.getPlayer(), event.getItemType());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (session.onBoardInventoryClick(player, event.getView().getTopInventory().getHolder(), event.getRawSlot())) {
            event.setCancelled(true);
            return;
        }
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            if (event.getCurrentItem() != null) {
                session.onItemProgress(player, event.getCurrentItem());
            }
            if (event.getCursor() != null) {
                session.onItemProgress(player, event.getCursor());
            }
        }
        if (session.rules().fixBingoCard()) {
            if (session.isBingoCard(event.getCurrentItem()) || session.isBingoCard(event.getCursor())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (session.isBoardInventory(event.getView().getTopInventory().getHolder())) {
            event.setCancelled(true);
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                return;
            }
        }
        if (session.rules().fixBingoCard()) {
            if (session.isBingoCard(event.getOldCursor())) {
                event.setCancelled(true);
                return;
            }
            for (ItemStack stack : event.getNewItems().values()) {
                if (session.isBingoCard(stack)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        for (ItemStack stack : event.getNewItems().values()) {
            if (stack != null) {
                session.onItemProgress(player, stack);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean spectator = event.getPlayer().getGameMode() == org.bukkit.GameMode.SPECTATOR;
        if (spectator) {
            if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
                return;
            }
        } else if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        if (session.onCardUse(event.getPlayer(), item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        session.onPlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        session.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (session.shouldCancelCountdownDamage(player)) {
            event.setCancelled(true);
            return;
        }
        if (session.shouldCancelDripstoneDamage(player, event.getCause())) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && session.shouldCancelFallDamage(player)) {
            event.setCancelled(true);
            return;
        }
        if (!session.shouldCancelLethalDamage(player, event.getFinalDamage())) {
            return;
        }
        event.setCancelled(true);
        session.punishPlayerDeath(player);
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        session.onHeldSlotChange(event.getPlayer(), event.getNewSlot());
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!session.rules().fixBingoCard()) {
            return;
        }
        if (session.isBingoCard(event.getMainHandItem()) || session.isBingoCard(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!session.rules().fixBingoCard()) {
            return;
        }
        if (session.isBingoCard(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (session.shouldLockBlockActions(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (session.shouldLockBlockActions(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (session.shouldCancelPvp(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!session.lockMovementIfPunished(event.getPlayer(), event.getFrom(), event.getTo())) {
            return;
        }
        event.setTo(event.getFrom());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (!session.isPunished(event.getPlayer())) {
            return;
        }
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        final var baseWorldName = session.asp().currentWorldName();
        if (!session.rules().useAsp() || baseWorldName == null) {
            return;
        }
        final var player = event.getPlayer();
        if (!player.getLocation().getWorld().getName().equals(baseWorldName + "_the_end")) {
            return;
        }
        if (player.getRespawnLocation() != null && player.getRespawnLocation().getWorld().getName().equals(baseWorldName)) {
            return;
        }
        if (event.getRespawnReason() == PlayerRespawnEvent.RespawnReason.END_PORTAL) {
            event.setRespawnLocation(Objects.requireNonNull(Bukkit.getWorld(baseWorldName)).getSpawnLocation());
        }
    }

    private void awardAdvancement(Player player, Advancement advancement) {
        String criteria;
        if (advancement == enterNetherAdvancement) {
            criteria = "entered_nether";
        } else if (advancement == enterEndAdvancement) {
            criteria = "entered_end";
        } else {
            throw new RuntimeException("Invalid advancement passed: " + advancement);
        }
        if (advancement == null) {
            return;
        }
        final var progress = player.getAdvancementProgress(advancement);
        if (progress.isDone()) {
            return;
        }
        if (!progress.awardCriteria(criteria)) {
            plugin.getLogger().warning("Unable to award advancement criteria " + criteria + " to " + player.getName());
        }
    }

    private @Nullable Location handlePortalCommon(Location fromLoc, PortalType portalType, Entity entity) {
        final var baseWorldName = session.asp().currentWorldName();
        if (!session.rules().useAsp() || baseWorldName == null) {
            return null;
        }
        final var fromEnv = fromLoc.getWorld().getEnvironment();
        var toLoc = fromLoc.clone();
        if (portalType == PortalType.NETHER) {
            if (fromEnv == World.Environment.NETHER) {
                return new Location(
                    Objects.requireNonNull(Bukkit.getWorld(baseWorldName)),
                    fromLoc.getX() * 8,
                    fromLoc.getY(),
                    fromLoc.getZ() * 8
                );
            } else {
                if (entity instanceof Player player) {
                    awardAdvancement(player, enterNetherAdvancement);
                }
                return new Location(
                    Objects.requireNonNull(Objects.requireNonNull(Bukkit.getWorld(baseWorldName + "_nether"))),
                    fromLoc.getX() / 8,
                    fromLoc.getY(),
                    fromLoc.getZ() / 8
                );
            }
        } else if (portalType == PortalType.ENDER) {
            if (fromEnv == World.Environment.THE_END) {
                // PlayerPortalEvent is not actually fired in this case,
                // so this does nothing and teleportation is handled by PlayerRespawnEvent
                return Objects.requireNonNull(Bukkit.getWorld(baseWorldName)).getSpawnLocation();
            } else {
                final var toWorld = Objects.requireNonNull(Bukkit.getWorld(baseWorldName + "_the_end"));
                final var nativeWorld = ((CraftWorld) toLoc.getWorld()).getHandle();
                final var nativeEntity = ((CraftEntity) entity).getHandle();
                EndPlatformFeature.createEndPlatform(nativeWorld, new BlockPos(100, 49, 0), true, nativeEntity);
                if (entity instanceof Player player) {
                    awardAdvancement(player, enterEndAdvancement);
                }
                return new Location(toWorld, 100, 50, 0);
            }
        } else {
            return null;
        }
    }

    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        final var newLoc = handlePortalCommon(event.getFrom(), event.getPortalType(), event.getEntity());
        if (newLoc != null) {
            event.setTo(newLoc);
        }
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        PortalType portalType;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            portalType = PortalType.ENDER;
        } else if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            portalType = PortalType.NETHER;
        } else {
            return;
        }
        final var newLoc = handlePortalCommon(event.getFrom(), portalType, event.getPlayer());
        if (newLoc != null) {
            event.setTo(newLoc);
        }
    }
}
