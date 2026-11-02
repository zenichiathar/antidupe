package net.antidupe;

import net.antidupe.cmd.AntiDupeCommand;
import net.antidupe.cmd.EcCommand;
import net.antidupe.config.PluginConfig;
import net.antidupe.detect.ContainerDupeListener;
import net.antidupe.detect.PistonDupeListener;
import net.antidupe.detect.ScanService;
import net.antidupe.detect.TimingDupeListener;
import net.antidupe.ec.EcListener;
import net.antidupe.ec.EnderChestStorage;
import net.antidupe.util.DupeHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AntiDupe extends JavaPlugin {

    private static AntiDupe instance;

    private PluginConfig pluginConfig;
    private DupeHandler dupeHandler;
    private ScanService scanService;
    private EnderChestStorage enderChestStorage;
    private int autoScanTask = -1;
    private int historyTask = -1;

    public static AntiDupe getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.pluginConfig = new PluginConfig();
        this.pluginConfig.load(getConfig());

        this.dupeHandler = new DupeHandler(this, pluginConfig);
        this.enderChestStorage = new EnderChestStorage(this, pluginConfig, dupeHandler);
        this.scanService = new ScanService(this, pluginConfig, dupeHandler, enderChestStorage);

        getServer().getPluginManager().registerEvents(new PistonDupeListener(this, pluginConfig, dupeHandler), this);
        ContainerDupeListener container = new ContainerDupeListener(this, pluginConfig, dupeHandler);
        getServer().getPluginManager().registerEvents(container, this);
        getServer().getPluginManager().registerEvents(scanService, this);
        getServer().getPluginManager().registerEvents(new TimingDupeListener(this, pluginConfig, dupeHandler), this);
        getServer().getPluginManager().registerEvents(new EcListener(this, pluginConfig, enderChestStorage, container), this);

        AntiDupeCommand cmd = new AntiDupeCommand(this, pluginConfig, scanService, dupeHandler);
        getCommand("antidupe").setExecutor(cmd);
        getCommand("antidupe").setTabCompleter(cmd);

        EcCommand ecCmd = new EcCommand(enderChestStorage, pluginConfig);
        getCommand("ec").setExecutor(ecCmd);
        getCommand("ec").setTabCompleter(ecCmd);

        net.antidupe.cmd.EnderSeeCommand enderSeeCmd = new net.antidupe.cmd.EnderSeeCommand(enderChestStorage, pluginConfig);
        if (getCommand("endersee") != null) {
            getCommand("endersee").setExecutor(enderSeeCmd);
            getCommand("endersee").setTabCompleter(enderSeeCmd);
        }

        if (pluginConfig.isAutoScanEnabled()) {
            long interval = pluginConfig.getAutoScanIntervalTicks();
            autoScanTask = getServer().getScheduler().runTaskTimer(this, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    scanService.sanitize(p, pluginConfig.isScanClamp(), true);
                }
            }, interval, interval).getTaskId();
        }

        // Keep a copy of all enderchests every 3 hours (for endersee history/backups)
        long threeHours = 20L * 60L * 60L * 3L;
        historyTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                enderChestStorage.backupAllHistory();
            } catch (Throwable ignored) {
            }
        }, threeHours, threeHours).getTaskId();

        getLogger().info("AntiDupe enabled. /ec = " + pluginConfig.getEcStoragePages() + " pages of " + pluginConfig.getEcStorageSize() + " slots.");
    }

    @Override
    public void onDisable() {
        if (autoScanTask != -1) {
            getServer().getScheduler().cancelTask(autoScanTask);
            autoScanTask = -1;
        }
        if (historyTask != -1) {
            getServer().getScheduler().cancelTask(historyTask);
            historyTask = -1;
        }
        try {
            enderChestStorage.backupAllHistory();
        } catch (Throwable ignored) {
        }
        enderChestStorage.saveAllPlayers();
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public DupeHandler getDupeHandler() {
        return dupeHandler;
    }

    public ScanService getScanService() {
        return scanService;
    }

    public EnderChestStorage getEnderChestStorage() {
        return enderChestStorage;
    }

    public void reload() {
        reloadConfig();
        pluginConfig.load(getConfig());
    }
}