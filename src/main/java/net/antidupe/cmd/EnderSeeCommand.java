package net.antidupe.cmd;

import net.antidupe.config.PluginConfig;
import net.antidupe.ec.EnderChestStorage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class EnderSeeCommand implements CommandExecutor, TabCompleter {

    private final EnderChestStorage storage;
    private final PluginConfig config;

    public EnderSeeCommand(EnderChestStorage storage, PluginConfig config) {
        this.storage = storage;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("antidupe.endersee")) {
            player.sendMessage("You don't have permission to use this command.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("Usage: /" + label + " <player> [page]");
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            // Try to find offline player? EnderChestStorage loads by UUID from files, but we need UUID.
            // Try to get offline player
            org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
            if (offline == null || (!offline.hasPlayedBefore() && !offline.isOnline())) {
                // Still might not have data file; try to look up by name differently
            }
            // We can still try to open using their UUID if they have data
            java.util.UUID targetUuid = offline.getUniqueId();
            openForUuid(player, targetUuid, targetName, args);
            return true;
        }

        openForUuid(player, target.getUniqueId(), target.getName(), args);
        return true;
    }

    private void openForUuid(Player viewer, java.util.UUID targetUuid, String targetName, String[] args) {
        if (config.isEnderChestView(viewer.getOpenInventory())) {
            viewer.sendMessage("Close your Ender Chest first.");
            return;
        }
        int targetPage = 1;
        if (args.length > 1) {
            String arg = args[1].toLowerCase();
            // For simplicity, just parse number or use basic navigation relative to page 1
            switch (arg) {
                case "next" -> targetPage = 2; // will be clamped by storage
                case "prev" -> targetPage = 1;
                case "last" -> targetPage = 9999;
                case "first" -> targetPage = 1;
                default -> {
                    try {
                        targetPage = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {
                        viewer.sendMessage("Usage: /endersee <player> [page]");
                        return;
                    }
                }
            }
        }
        if (targetPage < 1) {
            targetPage = 1;
        }
        if (targetPage > storage.pageCount()) {
            targetPage = storage.pageCount();
        }
        // We need to open another player's ender chest - but storage.open uses the player's UUID from cache
        // We need to access the internal storage for targetUuid
        storage.openOther(viewer, targetUuid, targetName, targetPage);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("antidupe.endersee")) {
            return out;
        }
        if (args.length == 1) {
            String pre = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(pre)) {
                    out.add(p.getName());
                }
            }
        } else if (args.length == 2) {
            String pre = args[1].toLowerCase();
            for (String s : new String[]{"1", "2", "3", "next", "prev", "first", "last"}) {
                if (s.startsWith(pre)) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
