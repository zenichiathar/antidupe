package net.antidupe.ec;

import net.antidupe.AntiDupe;
import net.antidupe.config.PluginConfig;
import net.antidupe.util.DupeHandler;
import net.antidupe.util.DupeType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EnderChestStorage {

    private final AntiDupe plugin;
    private final PluginConfig config;
    private final DupeHandler handler;
    private final Map<UUID, PlayerEnderChest> cache = new HashMap<>();
    private final Map<UUID, UUID> viewingOther = new HashMap<>(); // viewer -> target
    private final Map<UUID, String> viewingOtherNames = new HashMap<>(); // viewer -> target name

    public EnderChestStorage(AntiDupe plugin, PluginConfig config, DupeHandler handler) {
        this.plugin = plugin;
        this.config = config;
        this.handler = handler;
    }

    public int storageSize() {
        return config.getEcStorageSize();
    }

    public int pageCount() {
        return config.getEcStoragePages();
    }

    public String title(int page) {
        return config.getEcStorageTitle()
                .replace("{page}", String.valueOf(page))
                .replace("{max}", String.valueOf(pageCount()));
    }

    public boolean navBarEnabled() {
        return config.isEcNavBarEnabled() && storageSize() >= 45 && pageCount() > 1;
    }

    public boolean isNavSlot(int slot) {
        return navBarEnabled() && slot >= storageSize() - 9 && slot < storageSize();
    }

    public synchronized void open(Player player, int page) {
        int index = Math.max(0, Math.min(pageCount() - 1, page - 1));
        viewingOther.remove(player.getUniqueId());
        viewingOtherNames.remove(player.getUniqueId());
        player.openInventory(chest(player.getUniqueId()).page(index));
    }

    public synchronized void openOther(Player viewer, UUID targetId, String targetName, int page) {
        int index = Math.max(0, Math.min(pageCount() - 1, page - 1));
        viewingOther.put(viewer.getUniqueId(), targetId);
        if (targetName != null) {
            viewingOtherNames.put(viewer.getUniqueId(), targetName);
        } else {
            viewingOtherNames.remove(viewer.getUniqueId());
        }
        viewer.openInventory(chest(targetId).page(index));
    }

    public UUID getViewingTarget(Player viewer) {
        return viewingOther.get(viewer.getUniqueId());
    }

    public String getViewingTargetName(Player viewer) {
        return viewingOtherNames.get(viewer.getUniqueId());
    }

    public boolean isViewingOther(Player viewer) {
        return viewingOther.containsKey(viewer.getUniqueId());
    }

    public synchronized void navigate(Player player, int navSlot) {
        UUID viewId = viewingOther.getOrDefault(player.getUniqueId(), player.getUniqueId());
        int current = Math.max(0, chest(viewId).indexOf(player.getOpenInventory().getTopInventory()));
        int prevSlot = storageSize() - 9;
        int nextSlot = storageSize() - 1;
        int target = navSlot == prevSlot ? current - 1 : navSlot == nextSlot ? current + 1 : current;
        if (target < 0 || target >= pageCount() || target == current) {
            return;
        }
        int finalTarget = target;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (viewingOther.containsKey(player.getUniqueId())) {
                UUID tid = viewingOther.get(player.getUniqueId());
                String tname = viewingOtherNames.get(player.getUniqueId());
                openOther(player, tid, tname, finalTarget + 1);
            } else {
                open(player, finalTarget + 1);
            }
        });
    }

    public int currentPage(Player player) {
        UUID viewId = viewingOther.getOrDefault(player.getUniqueId(), player.getUniqueId());
        int index = chest(viewId).indexOf(player.getOpenInventory().getTopInventory());
        return index < 0 ? 1 : index + 1;
    }

    public boolean isPage(Player player, Inventory inventory) {
        UUID viewId = viewingOther.getOrDefault(player.getUniqueId(), player.getUniqueId());
        return chest(viewId).indexOf(inventory) >= 0;
    }

    public int clampPage(Inventory inventory, Player player) {
        int clamped = 0;
        for (int i = 0; i < storageSize(); i++) {
            if (isNavSlot(i)) {
                continue;
            }
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            int max = config.maxStackFor(item);
            if (item.getAmount() > max) {
                clamped += item.getAmount() - max;
                if (player != null) {
                    handler.trigger(DupeType.OVERSIZE_ITEM_STACK, player, item.getType() + " x" + item.getAmount() + " in Ender Chest clamped to " + max, false);
                } else {
                    plugin.getLogger().warning("Clamped " + item.getType() + " x" + item.getAmount() + " to " + max + " in Ender Chest");
                }
                item.setAmount(max);
            }
        }
        return clamped;
    }

    public void cleanupAndSave(Inventory inventory, Player player) {
        if (viewingOther.containsKey(player.getUniqueId())) {
            // Don't save when viewing others - read-only view
            clampPage(inventory, null);
            return;
        }
        clampPage(inventory, player);
        saveAll(player);
    }

    public void saveAll(Player player) {
        if (viewingOther.containsKey(player.getUniqueId())) {
            return;
        }
        chest(player.getUniqueId()).saveAll();
    }

    public void saveAllPlayers() {
        for (PlayerEnderChest chest : cache.values()) {
            chest.saveAll();
        }
    }

    public void forget(UUID id) {
        viewingOther.entrySet().removeIf(e -> e.getValue().equals(id) || e.getKey().equals(id));
        viewingOtherNames.entrySet().removeIf(e -> !viewingOther.containsKey(e.getKey()) && e.getKey().equals(id));
        viewingOther.remove(id);
        viewingOtherNames.remove(id);
        PlayerEnderChest chest = cache.remove(id);
        if (chest != null) {
            chest.saveAll();
        }
    }

    public void forgetViewing(UUID viewerId) {
        viewingOther.remove(viewerId);
        viewingOtherNames.remove(viewerId);
    }

    public void backupAllHistory() {
        File ecDir = ecDir();
        if (!ecDir.exists()) {
            return;
        }
        File[] files = ecDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            return;
        }
        File historyDir = new File(plugin.getDataFolder(), "ec-history");
        if (!historyDir.exists() && !historyDir.mkdirs()) {
            return;
        }
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File snapshotDir = new File(historyDir, stamp);
        if (!snapshotDir.exists() && !snapshotDir.mkdirs()) {
            return;
        }
        for (File f : files) {
            try {
                java.nio.file.Files.copy(f.toPath(), new File(snapshotDir, f.getName()).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable ignored) {
            }
        }
    }

    public void scanPlayer(Player player, boolean clamp) {
        PlayerEnderChest chest = chest(player.getUniqueId());
        int clamped = 0;
        for (int i = 0; i < pageCount(); i++) {
            clamped += clampPage(chest.page(i), player);
        }
        if (clamped > 0) {
            chest.saveAll();
        }
    }

    private PlayerEnderChest chest(UUID id) {
        return cache.computeIfAbsent(id, PlayerEnderChest::new);
    }

    private File ecDir() {
        return new File(plugin.getDataFolder(), "ec");
    }

    private File file(UUID id) {
        return new File(ecDir(), id + ".yml");
    }

    private void loadPage(Inventory inventory, UUID id, int index) {
        File f = file(id);
        if (!f.exists()) {
            // Try backup if original missing
            File bak = new File(ecDir(), id + ".yml.bak");
            if (bak.exists()) {
                try {
                    java.nio.file.Files.copy(bak.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Throwable ignored) {
                }
            }
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection section = data.getConfigurationSection("page-" + index);
        if (section == null) {
            // If file exists but section missing and looks wiped unexpectedly, try backup
            if (f.exists() && f.length() == 0) {
                File bak = new File(ecDir(), id + ".yml.bak");
                if (bak.exists() && bak.length() > 0) {
                    try {
                        java.nio.file.Files.copy(bak.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        data = YamlConfiguration.loadConfiguration(f);
                        section = data.getConfigurationSection("page-" + index);
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (section == null) {
                return;
            }
        }
        for (String key : section.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                ItemStack item = section.getItemStack(key);
                if (item != null && slot >= 0 && slot < storageSize() && !isNavSlot(slot) && !item.getType().isAir()) {
                    inventory.setItem(slot, item);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void savePage(UUID id, int index, Inventory inventory) {
        File dir = ecDir();
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        File file = file(id);
        // Backup existing file before writing to prevent accidental wipe on reload/plugin reload
        if (file.exists() && file.length() > 0) {
            try {
                File backup = new File(dir, id + ".yml.bak");
                java.nio.file.Files.copy(file.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable ignored) {
            }
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        data.set("page-" + index, null);
        ConfigurationSection section = data.createSection("page-" + index);
        for (int i = 0; i < storageSize(); i++) {
            if (isNavSlot(i)) {
                continue;
            }
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.getType().isAir()) {
                section.set(String.valueOf(i), item);
            }
        }
        try {
            data.save(file);
        } catch (IOException ignored) {
        }
    }

    private final class PlayerEnderChest {
        private final UUID id;
        private final Inventory[] pages = new Inventory[pageCount()];

        private PlayerEnderChest(UUID id) {
            this.id = id;
        }

        private Inventory page(int index) {
            if (pages[index] == null) {
                Inventory inventory = Bukkit.createInventory(null, storageSize(), title(index + 1));
                loadPage(inventory, id, index);
                clampPage(inventory, null);
                decorate(inventory, index);
                pages[index] = inventory;
            }
            return pages[index];
        }

        private void decorate(Inventory inventory, int pageIndex) {
            if (!navBarEnabled()) {
                return;
            }
            int prevSlot = storageSize() - 9;
            int nextSlot = storageSize() - 1;
            int center = prevSlot + 4;
            ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
            for (int s = prevSlot + 1; s < nextSlot; s++) {
                if (s == center) {
                    continue;
                }
                inventory.setItem(s, filler);
            }
            inventory.setItem(prevSlot, pageIndex > 0
                    ? named(Material.ARROW, ChatColor.GREEN + "Previous Page")
                    : named(Material.BARRIER, ChatColor.DARK_GRAY + "No Previous Page"));
            inventory.setItem(center, named(Material.PAPER, ChatColor.GOLD + "Page " + (pageIndex + 1) + "/" + pageCount()));
            inventory.setItem(nextSlot, pageIndex < pageCount() - 1
                    ? named(Material.ARROW, ChatColor.GREEN + "Next Page")
                    : named(Material.BARRIER, ChatColor.DARK_GRAY + "No Next Page"));
        }

        private ItemStack named(Material material, String name) {
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(name);
            item.setItemMeta(meta);
            return item;
        }

        private int indexOf(Inventory inventory) {
            for (int i = 0; i < pages.length; i++) {
                if (pages[i] == inventory) {
                    return i;
                }
            }
            return -1;
        }

        private void saveAll() {
            for (int i = 0; i < pages.length; i++) {
                if (pages[i] != null) {
                    savePage(id, i, pages[i]);
                }
            }
        }
    }
}