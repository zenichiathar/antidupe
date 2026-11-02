package net.antidupe.detect;

import net.antidupe.AntiDupe;
import net.antidupe.config.PluginConfig;
import net.antidupe.util.DupeHandler;
import net.antidupe.util.DupeType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ContainerDupeListener implements Listener {

    private final AntiDupe plugin;
    private final PluginConfig config;
    private final DupeHandler handler;
    private final Set<UUID> enderChestOpen = new HashSet<>();

    public ContainerDupeListener(AntiDupe plugin, PluginConfig config, DupeHandler handler) {
        this.plugin = plugin;
        this.config = config;
        this.handler = handler;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (config.isIgnoredEconomyView(event.getView())) {
            return;
        }
        if (!config.isEnderChestEnabled()) {
            return;
        }
        if (!config.isEnderChestView(event.getView())) {
            return;
        }
        UUID id = player.getUniqueId();
        if (enderChestOpen.contains(id)) {
            event.setCancelled(true);
            handler.trigger(DupeType.ENDER_CHEST_DOUBLE_OPEN, player, "opened a second Ender Chest view while one was open");
            return;
        }
        enderChestOpen.add(id);
    }

    public void openEnderChest(Player player) {
        enderChestOpen.add(player.getUniqueId());
    }

    public void closeEnderChest(Player player) {
        enderChestOpen.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        enderChestOpen.remove(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        enderChestOpen.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        enderChestOpen.remove(event.getPlayer().getUniqueId());
    }
}