package net.antidupe.cmd;

import net.antidupe.config.PluginConfig;
import net.antidupe.ec.EnderChestStorage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class EcCommand implements CommandExecutor, TabCompleter {

    private final EnderChestStorage storage;
    private final PluginConfig config;

    public EcCommand(EnderChestStorage storage, PluginConfig config) {
        this.storage = storage;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can open the Ender Chest.");
            return true;
        }
        if (config.isEnderChestView(player.getOpenInventory())) {
            player.sendMessage("Close your Ender Chest first.");
            return true;
        }
        int current = storage.currentPage(player);
        if (current < 1) {
            current = 1;
        }
        int target = current;
        if (args.length > 0) {
            String arg = args[0].toLowerCase();
            switch (arg) {
                case "next" -> target = current + 1;
                case "prev" -> target = current - 1;
                case "last" -> target = storage.pageCount();
                case "first" -> target = 1;
                default -> {
                    try {
                        target = Integer.parseInt(args[0]);
                    } catch (NumberFormatException ignored) {
                        player.sendMessage("Usage: /" + label + " [page|next|prev|last]");
                        return true;
                    }
                }
            }
        }
        if (target < 1 || target > storage.pageCount()) {
            player.sendMessage("Invalid page. Pages: 1-" + storage.pageCount());
            return true;
        }
        storage.open(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String pre = args[0].toLowerCase();
            for (String s : new String[]{"next", "prev", "first", "last", "1", "2", "3"}) {
                if (s.startsWith(pre)) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}