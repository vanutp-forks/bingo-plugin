package me._furiouspotato_.bingo.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import me._furiouspotato_.bingo.service.GameSessionManager;

public final class BingoGameplayListener implements Listener {
    private final GameSessionManager session;

    public BingoGameplayListener(GameSessionManager session) {
        this.session = session;
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
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
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
}
