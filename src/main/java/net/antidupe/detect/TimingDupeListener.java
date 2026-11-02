package net.antidupe.detect;

import net.antidupe.AntiDupe;
import net.antidupe.config.PluginConfig;
import net.antidupe.util.DupeHandler;
import net.antidupe.util.DupeType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TimingDupeListener implements Listener {

    private final AntiDupe plugin;
    private final PluginConfig config;
    private final DupeHandler handler;

    private final Map<UUID, Long> lastDrop = new HashMap<>();
    private final Map<UUID, Integer> loopCount = new HashMap<>();
    private final Map<UUID, Long> loopWindowStart = new HashMap<>();
    private final Map<UUID, ArrayDeque<Long>> clickTimes = new HashMap<>();
    private final Map<UUID, Long> lastFlag = new HashMap<>();
    private final Map<UUID, Long> lastBookEdit = new HashMap<>();
    private final Map<UUID, Integer> lastBookPages = new HashMap<>();

    public TimingDupeListener(AntiDupe plugin, PluginConfig config, DupeHandler handler) {
        this.plugin = plugin;
        this.config = config;
        this.handler = handler;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onDrop(PlayerDropItemEvent event) {
        if (!config.isTimingEnabled()) {
            return;
        }
        lastDrop.put(event.getPlayer().getUniqueId(), now());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onPickup(PlayerPickupItemEvent event) {
        if (!config.isTimingEnabled()) {
            return;
        }
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        Long droppedAt = lastDrop.get(id);
        if (droppedAt == null) {
            return;
        }
        long time = now();
        long windowStart = loopWindowStart.getOrDefault(id, time);
        if (time - windowStart > config.getDropPickupWindowMs()) {
            loopCount.remove(id);
            loopWindowStart.remove(id);
            lastDrop.remove(id);
            return;
        }
        if (time - droppedAt <= config.getDropPickupRoundtripMs()) {
            int count = loopCount.merge(id, 1, Integer::sum);
            if (count == 1) {
                loopWindowStart.put(id, time);
            }
            if (count >= config.getDropPickupLoopTrigger()) {
                if (handleFlag(p, DupeType.RAPID_DROP_PICKUP_LOOP, "drop+pickup loop x" + count, event)) {
                    event.setCancelled(true);
                }
                loopCount.remove(id);
                loopWindowStart.remove(id);
                lastDrop.remove(id);
            }
        } else {
            loopCount.remove(id);
            loopWindowStart.remove(id);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!config.isTimingEnabled() || !config.isClickSpamEnabled()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (config.isIgnoredEconomyView(event.getView())) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof Player) {
            return;
        }
        UUID id = p.getUniqueId();
        long time = now();
        ArrayDeque<Long> queue = clickTimes.computeIfAbsent(id, k -> new ArrayDeque<>());
        while (!queue.isEmpty() && time - queue.peekFirst() > 1000) {
            queue.pollFirst();
        }
        if (queue.size() >= config.getClickSpamPerSecond()) {
            if (handleFlag(p, DupeType.INVENTORY_CLICK_SPAM, "click spam " + queue.size() + "/sec", event)) {
                event.setCancelled(true);
            }
            queue.clear();
        } else {
            queue.addLast(time);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBookEdit(PlayerEditBookEvent event) {
        if (!config.isTimingEnabled() || !config.isBookEditEnabled()) {
            return;
        }
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        long time = now();
        int pages = event.getNewBookMeta().getPageCount();
        Long last = lastBookEdit.get(id);
        if (last != null) {
            long since = time - last;
            if (since < config.getBookEditPasteCooldownMs()) {
                handleFlag(p, DupeType.RAPID_BOOK_EDIT, "book edit/paste " + since + "ms after last (limit " + config.getBookEditPasteCooldownMs() + "ms)", event);
                event.setCancelled(true);
                return;
            }
            Integer lastPages = lastBookPages.get(id);
            if (lastPages != null && pages > lastPages && since < config.getBookEditPageCooldownMs()) {
                handleFlag(p, DupeType.RAPID_BOOK_PAGE_CREATE, "added " + (pages - lastPages) + " page(s) within " + since + "ms (limit " + config.getBookEditPageCooldownMs() + "ms)", event);
                event.setCancelled(true);
                return;
            }
        }
        lastBookEdit.put(id, time);
        lastBookPages.put(id, pages);
    }

    private boolean handleFlag(Player p, DupeType type, String detail, org.bukkit.event.Cancellable event) {
        UUID id = p.getUniqueId();
        long time = now();
        if (time - lastFlag.getOrDefault(id, 0L) < config.getTimingFlagCooldownMs()) {
            return false;
        }
        lastFlag.put(id, time);
        boolean kick = config.getTimingAction().equals("kick");
        handler.trigger(type, p, detail, kick);
        return !config.getTimingAction().equals("flag");
    }

    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }
}