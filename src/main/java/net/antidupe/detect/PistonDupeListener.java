package net.antidupe.detect;

import net.antidupe.AntiDupe;
import net.antidupe.config.PluginConfig;
import net.antidupe.util.DupeHandler;
import net.antidupe.util.DupeType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import java.util.Collection;
import java.util.List;

public final class PistonDupeListener implements Listener {

    private final AntiDupe plugin;
    private final PluginConfig config;
    private final DupeHandler handler;

    public PistonDupeListener(AntiDupe plugin, PluginConfig config, DupeHandler handler) {
        this.plugin = plugin;
        this.config = config;
        this.handler = handler;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (config.isPistonProtected(b.getType())) {
                event.setCancelled(true);
                if (config.isDebug()) {
                    plugin.getLogger().info("Cancelled piston extending " + b.getType() + " at " + b.getLocation());
                }
                Player nearest = nearestPlayer(b);
                if (nearest != null) {
                    handler.trigger(DupeType.PISTON_BLOCK_MOVE, nearest, "piston attempted to move " + b.getType());
                }
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (config.isPistonProtected(b.getType())) {
                event.setCancelled(true);
                if (config.isDebug()) {
                    plugin.getLogger().info("Cancelled piston retracting " + b.getType() + " at " + b.getLocation());
                }
                Player nearest = nearestPlayer(b);
                if (nearest != null) {
                    handler.trigger(DupeType.PISTON_BLOCK_MOVE_RETRACT, nearest, "piston attempted to pull " + b.getType());
                }
                return;
            }
        }
    }

    private Player nearestPlayer(Block b) {
        Collection<org.bukkit.entity.Player> nearby = b.getLocation().getNearbyPlayers(16);
        return nearby.isEmpty() ? null : nearby.iterator().next();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock falling)) {
            return;
        }
        Material type = falling.getBlockData().getMaterial();
        if (config.isFallingAllowed(type)) {
            return;
        }
        event.setCancelled(true);
        event.getEntity().remove();
        if (config.isDebug()) {
            plugin.getLogger().info("Removed non-gravity falling block " + type + " at " + falling.getLocation());
        }
        List<org.bukkit.entity.Entity> nearby = falling.getNearbyEntities(1, 1, 1);
        for (org.bukkit.entity.Entity e : nearby) {
            if (e instanceof org.bukkit.entity.Player p) {
                handler.trigger(DupeType.NON_GRAVITY_FALLING_BLOCK, p, "falling block " + type + " attempted to duplicate");
                return;
            }
        }
    }
}