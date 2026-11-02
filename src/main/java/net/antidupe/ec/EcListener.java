package net.antidupe.ec;

import net.antidupe.AntiDupe;
import net.antidupe.config.PluginConfig;
import net.antidupe.detect.ContainerDupeListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

public final class EcListener implements Listener {

    private final AntiDupe plugin;
    private final PluginConfig config;
    private final EnderChestStorage storage;
    private final ContainerDupeListener container;

    public EcListener(AntiDupe plugin, PluginConfig config, EnderChestStorage storage, ContainerDupeListener container) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        this.container = container;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        InventoryView view = event.getView();
        Inventory top = view.getTopInventory();
        if (storage.isPage(player, top)) {
            return;
        }
        if (config.isEnderChestEnabled() && config.isEnderChestView(view)) {
            event.setCancelled(true);
            container.closeEnderChest(player);
            storage.open(player, storage.currentPage(player));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!storage.isPage(player, top)) {
            return;
        }
        if (storage.isViewingOther(player)) {
            if (event.getClickedInventory() == top && storage.isNavSlot(event.getRawSlot())) {
                event.setCancelled(true);
                storage.navigate(player, event.getRawSlot());
                return;
            }
            event.setCancelled(true);
            return;
        }
        if (event.getClickedInventory() == top && storage.isNavSlot(event.getRawSlot())) {
            event.setCancelled(true);
            storage.navigate(player, event.getRawSlot());
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> storage.cleanupAndSave(top, player));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (!storage.isPage(player, top)) {
            return;
        }
        if (storage.isViewingOther(player)) {
            event.setCancelled(true);
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < top.getSize() && storage.isNavSlot(raw)) {
                event.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> storage.cleanupAndSave(top, player));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (storage.isPage(player, top)) {
            if (storage.isViewingOther(player)) {
                // Clear viewing state when closing
                storage.forgetViewing(player.getUniqueId());
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> storage.cleanupAndSave(top, player));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        storage.forget(event.getPlayer().getUniqueId());
    }
}