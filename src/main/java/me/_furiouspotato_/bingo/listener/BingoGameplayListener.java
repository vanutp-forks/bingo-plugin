package me._furiouspotato_.bingo.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
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
        session.onItemProgress(player, event.getItem().getItemStack().getType());
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
        session.onItemProgress(player, crafted.getType());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        session.onPlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!session.shouldCancelLethalDamage(player, event.getFinalDamage())) {
            return;
        }
        event.setCancelled(true);
        session.punishPlayerDeath(player);
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
