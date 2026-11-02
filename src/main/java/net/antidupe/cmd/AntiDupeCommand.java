package net.antidupe.cmd;

import net.antidupe.AntiDupe;
import net.antidupe.config.PluginConfig;
import net.antidupe.detect.ScanService;
import net.antidupe.util.DupeHandler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class AntiDupeCommand implements CommandExecutor, TabCompleter {

    private final AntiDupe plugin;
    private final PluginConfig config;
    private final ScanService scanService;
    private final DupeHandler handler;

    public AntiDupeCommand(AntiDupe plugin, PluginConfig config, ScanService scanService, DupeHandler handler) {
        this.plugin = plugin;
        this.config = config;
        this.scanService = scanService;
        this.handler = handler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "AntiDupe " + plugin.getDescription().getVersion());
            sender.sendMessage(ChatColor.GRAY + "Usage: /" + label + " reload | scan [player] | debug");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(ChatColor.GREEN + "AntiDupe config reloaded.");
                return true;
            }
            case "scan" -> {
                int affected = 0;
                int dupeStacks = 0;
                if (args.length > 1) {
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        sender.sendMessage(ChatColor.RED + "Player not found or offline: " + args[1]);
                        return true;
                    }
                    List<ScanService.DupeStack> found = scanService.sanitize(target, config.isScanClamp(), true);
                    affected = found.isEmpty() ? 0 : 1;
                    dupeStacks = found.size();
                } else {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        List<ScanService.DupeStack> found = scanService.sanitize(p, config.isScanClamp(), true);
                        if (!found.isEmpty()) {
                            affected++;
                            dupeStacks += found.size();
                        }
                    }
                }
                sender.sendMessage(ChatColor.GREEN + "Scan complete: " + affected + " player(s), " + dupeStacks + " dupe stack(s) "
                        + (config.isScanClamp() ? "clamped." : "found."));
                return true;
            }
            case "debug" -> {
                boolean next = !config.isDebug();
                plugin.getConfig().set("debug", next);
                plugin.saveConfig();
                plugin.reload();
                sender.sendMessage(ChatColor.GREEN + "Debug mode: " + (next ? "ON" : "OFF"));
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.GOLD + "Usage: /" + label + " reload | scan [player] | debug");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : new String[]{"reload", "scan", "debug"}) {
                if (s.startsWith(args[0].toLowerCase())) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("scan")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}