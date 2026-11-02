package net.antidupe.util;

import net.antidupe.AntiDupe;
import net.antidupe.config.PluginConfig;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class DupeHandler {

    private final AntiDupe plugin;
    private final PluginConfig config;

    public DupeHandler(AntiDupe plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void trigger(DupeType type, Player player, String detail) {
        trigger(type, player, detail, true);
    }

    public void trigger(DupeType type, Player player, String detail, boolean punish) {
        if (player == null || !player.isOnline()) {
            return;
        }
        String location = player.getWorld().getName() + " " + player.getLocation().getBlockX() + "," + player.getLocation().getBlockY() + "," + player.getLocation().getBlockZ();
        String line = player.getName() + " @" + location + " | " + type + " | " + detail;

        if (config.isFileLogging()) {
            logLine(line);
        }
        plugin.getLogger().warning("Dupe detected: " + line);

        if (config.isNotifyStaff()) {
            String msg = ChatColor.RED + "[AntiDupe] " + type.toString().replace('_', ' ') + ChatColor.GRAY + " by " + ChatColor.YELLOW + player.getName() + ChatColor.GRAY + ": " + detail;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("antidupe.notify")) {
                    p.sendMessage(msg);
                }
            }
        }

        String webhook = config.getWebhookUrl();
        if (!webhook.isEmpty()) {
            sendWebhook(webhook, "[AntiDupe] " + line);
        }

        if (punish) {
            punish(type, player);
        }
    }

    private void punish(DupeType type, Player player) {
        PluginConfig.PunishMode mode = config.getPunishMode();
        if (mode == PluginConfig.PunishMode.KICK) {
            player.kickPlayer(ChatColor.RED + "AntiDupe: " + type.toString().replace('_', ' '));
        } else if (mode == PluginConfig.PunishMode.BAN) {
            long ms = config.getBanDays() * 86400000L;
            Date expiry = ms > 0 ? new Date(System.currentTimeMillis() + ms) : null;
            Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), "AntiDupe: " + type.toString().replace('_', ' '), expiry, "AntiDupe");
            player.kickPlayer(ChatColor.RED + "Banned: " + type.toString().replace('_', ' '));
        }
    }

    private void logLine(String line) {
        try {
            File dir = new File(plugin.getDataFolder(), "logs");
            if (!dir.exists() && !dir.mkdirs()) {
                return;
            }
            File f = new File(dir, "dupes-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log");
            try (FileWriter w = new FileWriter(f, true)) {
                w.write("[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "] " + line + System.lineSeparator());
            }
        } catch (IOException ignored) {
        }
    }

    private void sendWebhook(String url, String content) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setDoOutput(true);
                String json = "{\"content\":\"" + content.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
                try (OutputStream os = con.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                int code = con.getResponseCode();
                if (code < 200 || code >= 300) {
                    plugin.getLogger().warning("Webhook returned HTTP " + code);
                }
                con.disconnect();
            } catch (IOException ignored) {
            }
        });
    }
}