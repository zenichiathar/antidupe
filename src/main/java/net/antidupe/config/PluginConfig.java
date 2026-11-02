package net.antidupe.config;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PluginConfig {

    public enum PunishMode {
        NONE, KICK, BAN
    }

    private final Set<String> fallingAllowed = new HashSet<>();
    private final Set<String> pistonProtected = new HashSet<>();
    private final List<String> ecTitleHints = new ArrayList<>();
    private final List<String> ignoredTitles = new ArrayList<>();
    private final Map<String, Integer> unstackableMax = new HashMap<>();

    private boolean ecEnabled;
    private boolean scanClampOnJoin;
    private boolean autoScanEnabled;
    private long autoScanIntervalSeconds;
    private boolean scanClamp;
    private boolean unstackablesEnabled;
    private PunishMode punishMode;
    private int banDays;
    private boolean notifyStaff;
    private boolean fileLogging;
    private String webhookUrl;
    private boolean debug;
    private boolean timingEnabled;
    private long dropPickupRoundtripMs;
    private int dropPickupLoopTrigger;
    private long dropPickupWindowMs;
    private boolean clickSpamEnabled;
    private int clickSpamPerSecond;
    private String timingAction;
    private long timingFlagCooldownMs;
    private boolean bookEditEnabled;
    private long bookEditPasteCooldownMs;
    private long bookEditPageCooldownMs;
    private boolean ecNavBar;
    private int ecStoragePages;
    private int ecStorageSize;
    private String ecStorageTitle;

    public void load(FileConfiguration c) {
        fallingAllowed.clear();
        pistonProtected.clear();
        ecTitleHints.clear();
        ignoredTitles.clear();
        unstackableMax.clear();

        for (String s : c.getStringList("falling-blocks.allow")) {
            fallingAllowed.add(s.toLowerCase(Locale.ROOT));
        }
        for (String s : c.getStringList("piston-protected")) {
            pistonProtected.add(s.toLowerCase(Locale.ROOT));
        }
        for (String s : c.getStringList("ender-chest.menu-title-hints")) {
            ecTitleHints.add(s.toLowerCase(Locale.ROOT));
        }
        for (String s : c.getStringList("economy.ignored-titles")) {
            ignoredTitles.add(s.toLowerCase(Locale.ROOT));
        }
        var maxSection = c.getConfigurationSection("unstackables.max-stack");
        if (maxSection != null) {
            for (String key : maxSection.getKeys(false)) {
                int v = maxSection.getInt(key);
                if (v > 0) {
                    unstackableMax.put(key.toLowerCase(Locale.ROOT), v);
                }
            }
        }

        ecEnabled = c.getBoolean("ender-chest.enabled", true);
        scanClampOnJoin = c.getBoolean("scan.clamp-on-join", true);
        autoScanEnabled = c.getBoolean("scan.auto-scan", false);
        autoScanIntervalSeconds = c.getLong("scan.auto-scan-interval-seconds", 60L);
        scanClamp = c.getBoolean("scan.clamp", true);
        unstackablesEnabled = c.getBoolean("unstackables.enabled", true);
        punishMode = PunishMode.valueOf(c.getString("punish.mode", "NONE").toUpperCase(Locale.ROOT));
        banDays = c.getInt("punish.ban-days", 1);
        notifyStaff = c.getBoolean("notify.staff", true);
        fileLogging = c.getBoolean("logging.file", true);
        webhookUrl = c.getString("logging.discord-webhook", "");
        debug = c.getBoolean("debug", false);

        timingEnabled = c.getBoolean("timing.enabled", true);
        dropPickupRoundtripMs = c.getLong("timing.drop-pickup.roundtrip-ms", 200L);
        dropPickupLoopTrigger = c.getInt("timing.drop-pickup.loop-trigger", 4);
        dropPickupWindowMs = c.getLong("timing.drop-pickup.window-ms", 2000L);
        clickSpamEnabled = c.getBoolean("timing.click-spam.enabled", true);
        clickSpamPerSecond = c.getInt("timing.click-spam.per-second", 30);
        timingAction = c.getString("timing.action", "flag").toLowerCase(Locale.ROOT);
        timingFlagCooldownMs = c.getLong("timing.flag-cooldown-ms", 5000L);

        bookEditEnabled = c.getBoolean("timing.book-edit.enabled", true);
        bookEditPasteCooldownMs = c.getLong("timing.book-edit.paste-cooldown-ms", 1000L);
        bookEditPageCooldownMs = c.getLong("timing.book-edit.page-create-cooldown-ms", 2000L);

        ecNavBar = c.getBoolean("ec-storage.nav-bar", true);
        ecStoragePages = Math.max(1, c.getInt("ec-storage.pages", 3));
        int configuredSize = Math.max(9, c.getInt("ec-storage.size", 54));
        configuredSize = (configuredSize / 9) * 9;
        ecStorageSize = Math.min(54, configuredSize);
        ecStorageTitle = c.getString("ec-storage.title", "Ender Chest ({page}/{max})");
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isFallingAllowed(Material m) {
        return fallingAllowed.contains(m.name().toLowerCase(Locale.ROOT));
    }

    public boolean isPistonProtected(Material m) {
        return pistonProtected.contains(m.name().toLowerCase(Locale.ROOT));
    }

    public boolean isEnderChestView(InventoryView v) {
        if (!ecEnabled) {
            return false;
        }
        Inventory top = v.getTopInventory();
        if (top.getHolder() instanceof org.bukkit.entity.Player && top.getSize() == 27) {
            return true;
        }
        String t = normalize(v.getTitle());
        for (String s : ecTitleHints) {
            if (t.contains(s)) {
                return true;
            }
        }
        return false;
    }

    public boolean isIgnoredEconomyView(InventoryView v) {
        String t = normalize(v.getTitle());
        for (String s : ignoredTitles) {
            if (t.contains(s)) {
                return true;
            }
        }
        return false;
    }

    public int maxStackFor(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        Material m = item.getType();
        if (unstackablesEnabled) {
            Integer override = unstackableMax.get(m.name().toLowerCase(Locale.ROOT));
            if (override != null) {
                return Math.max(1, override);
            }
        }
        return Math.max(1, m.getMaxStackSize());
    }

    public boolean isEnderChestEnabled() {
        return ecEnabled;
    }

    public boolean isScanClampOnJoin() {
        return scanClampOnJoin;
    }

    public boolean isAutoScanEnabled() {
        return autoScanEnabled;
    }

    public long getAutoScanIntervalTicks() {
        return Math.max(20L, autoScanIntervalSeconds * 20L);
    }

    public boolean isScanClamp() {
        return scanClamp;
    }

    public boolean isUnstackablesEnabled() {
        return unstackablesEnabled;
    }

    public PunishMode getPunishMode() {
        return punishMode;
    }

    public int getBanDays() {
        return Math.max(0, banDays);
    }

    public boolean isNotifyStaff() {
        return notifyStaff;
    }

    public boolean isFileLogging() {
        return fileLogging;
    }

    public String getWebhookUrl() {
        return webhookUrl == null ? "" : webhookUrl.trim();
    }

    public boolean isTimingEnabled() {
        return timingEnabled;
    }

    public long getDropPickupRoundtripMs() {
        return Math.max(10L, dropPickupRoundtripMs);
    }

    public int getDropPickupLoopTrigger() {
        return Math.max(1, dropPickupLoopTrigger);
    }

    public long getDropPickupWindowMs() {
        return Math.max(100L, dropPickupWindowMs);
    }

    public boolean isClickSpamEnabled() {
        return clickSpamEnabled;
    }

    public int getClickSpamPerSecond() {
        return Math.max(3, clickSpamPerSecond);
    }

    public String getTimingAction() {
        return timingAction;
    }

    public long getTimingFlagCooldownMs() {
        return Math.max(0L, timingFlagCooldownMs);
    }

    public boolean isBookEditEnabled() {
        return bookEditEnabled;
    }

    public long getBookEditPasteCooldownMs() {
        return Math.max(250L, bookEditPasteCooldownMs);
    }

    public long getBookEditPageCooldownMs() {
        return Math.max(500L, bookEditPageCooldownMs);
    }

    public boolean isEcNavBarEnabled() {
        return ecNavBar;
    }

    public int getEcStoragePages() {
        return ecStoragePages;
    }

    public int getEcStorageSize() {
        return ecStorageSize;
    }

    public String getEcStorageTitle() {
        return ecStorageTitle;
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return ChatColor.stripColor(s).toLowerCase(Locale.ROOT);
    }
}