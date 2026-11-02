package net.antidupe.detect;

import net.antidupe.AntiDupe;
import net.antidupe.config.PluginConfig;
import net.antidupe.ec.EnderChestStorage;
import net.antidupe.util.DupeHandler;
import net.antidupe.util.DupeType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ScanService implements Listener {

    public static final class DupeStack {
        public final String slot;
        public final String material;
        public final int amount;
        public final int max;

        DupeStack(String slot, String material, int amount, int max) {
            this.slot = slot;
            this.material = material;
            this.amount = amount;
            this.max = max;
        }
    }

    private final AntiDupe plugin;
    private final PluginConfig config;
    private final DupeHandler handler;
    private final EnderChestStorage storage;

    public ScanService(AntiDupe plugin, PluginConfig config, DupeHandler handler, EnderChestStorage storage) {
        this.plugin = plugin;
        this.config = config;
        this.handler = handler;
        this.storage = storage;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (config.isScanClampOnJoin()) {
            sanitize(event.getPlayer(), config.isScanClamp(), false);
        }
    }

    public List<DupeStack> sanitize(Player player, boolean clamp, boolean announce) {
        List<DupeStack> found = new ArrayList<>();
        check(player.getInventory().getStorageContents(), player, found, clamp);
        check(player.getInventory().getExtraContents(), player, found, clamp);
        check(player.getEnderChest().getContents(), player, found, clamp);
        check(player.getInventory().getArmorContents(), player, found, clamp);
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && !off.getType().isAir()) {
            check(new ItemStack[]{off}, player, found, clamp);
        }
        storage.scanPlayer(player, clamp);
        if (announce && !found.isEmpty()) {
            for (DupeStack d : found) {
                plugin.getLogger().warning("Sanitized " + player.getName() + ": " + d.material + " x" + d.amount + " (slot " + d.slot + ") clamped to " + d.max);
            }
        }
        return found;
    }

    private void check(ItemStack[] items, Player player, List<DupeStack> found, boolean clamp) {
        if (items == null) {
            return;
        }
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            int max = config.maxStackFor(item);
            if (max <= 0 || item.getAmount() > max) {
                found.add(new DupeStack("slot-" + i, item.getType().name(), item.getAmount(), max));
                handler.trigger(DupeType.OVERSIZE_ITEM_STACK, player, item.getType() + " x" + item.getAmount() + " exceeds max " + max, false);
                if (clamp) {
                    item.setAmount(max);
                }
            }
        }
    }
}