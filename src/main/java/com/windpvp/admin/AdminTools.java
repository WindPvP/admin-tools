package com.windpvp.admin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.windpvp.admin.vanish.MySqlVanishStorage;
import com.windpvp.admin.vanish.VanishCommand;
import com.windpvp.admin.vanish.VanishListener;
import com.windpvp.admin.vanish.VanishPacketListener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AdminTools extends JavaPlugin {

    private static final long MIN_SYNC_INTERVAL_TICKS = 20L;

    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
    private VanishPacketListener packetListener;
    private MySqlVanishStorage vanishStorage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.vanishStorage = new MySqlVanishStorage(this);
        if (!vanishStorage.connect()) {
            getLogger().warning("Failed to connect to MySQL vanish storage; using in-memory vanish state only.");
            vanishStorage = null;
        } else {
            vanishedPlayers.clear();
            vanishedPlayers.addAll(vanishStorage.loadVanishedPlayers());
            startSyncTask();
        }
        
        this.packetListener = new VanishPacketListener(this);
        getCommand("vanish").setExecutor(new VanishCommand(this));
        getServer().getPluginManager().registerEvents(new VanishListener(this), this);

        // Bedrock compat
        getLogger().info("AdminTools enabled (1.8.8 ProtocolLib)");
    }

    @Override
    public void onDisable() {
        if (vanishStorage != null) {
            vanishStorage.close();
        }
    }

    public Set<UUID> getVanishedPlayers() {
        return vanishedPlayers;
    }

    public boolean isVanished(UUID uuid) {
        return vanishedPlayers.contains(uuid);
    }

    public boolean isVanished(Player player) {
        return isVanished(player.getUniqueId());
    }

    public void vanishPlayer(Player player) {
        vanishPlayer(player, true);
    }

    public void vanishPlayer(Player player, boolean notify) {
        setVanished(player.getUniqueId(), true);
        hidePlayerFromOthers(player);
        if (notify) {
            player.sendMessage("§aYou are now vanished.");
        }
    }

    public void unvanishPlayer(Player player) {
        unvanishPlayer(player, true);
    }

    public void unvanishPlayer(Player player, boolean notify) {
        setVanished(player.getUniqueId(), false);
        showPlayerToOthers(player);
        if (notify) {
            player.sendMessage("§eYou are no longer vanished.");
        }
    }

    public void hideVanishedPlayersFrom(Player viewer) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (isVanished(online) && !online.equals(viewer)) {
                viewer.hidePlayer(online);
            }
        }
    }

    public void vanishOfflinePlayer(UUID uuid) {
        setVanished(uuid, true);
    }

    public void unvanishOfflinePlayer(UUID uuid) {
        setVanished(uuid, false);
    }

    private void setVanished(UUID uuid, boolean vanished) {
        if (vanished) {
            vanishedPlayers.add(uuid);
        } else {
            vanishedPlayers.remove(uuid);
        }
        if (vanishStorage != null) {
            vanishStorage.setVanished(uuid, vanished);
        }
    }

    private void hidePlayerFromOthers(Player player) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                online.hidePlayer(player);
            }
        }
    }

    private void showPlayerToOthers(Player player) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(player);
        }
    }

    private void startSyncTask() {
        long intervalTicks = Math.max(MIN_SYNC_INTERVAL_TICKS, getConfig().getLong("mysql.sync-interval-ticks", 200L));
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (vanishStorage == null) {
                return;
            }

            Set<UUID> dbVanished = vanishStorage.loadVanishedPlayers();
            Set<UUID> oldVanished = new HashSet<>(vanishedPlayers);
            vanishedPlayers.removeIf(uuid -> !dbVanished.contains(uuid));
            vanishedPlayers.addAll(dbVanished);

            Set<UUID> newlyVanished = new HashSet<>(dbVanished);
            newlyVanished.removeAll(oldVanished);

            Set<UUID> newlyUnvanished = new HashSet<>(oldVanished);
            newlyUnvanished.removeAll(dbVanished);

            if (newlyVanished.isEmpty() && newlyUnvanished.isEmpty()) {
                return;
            }

            Bukkit.getScheduler().runTask(this, () -> {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (newlyVanished.contains(online.getUniqueId())) {
                        hidePlayerFromOthers(online);
                    }
                    if (newlyUnvanished.contains(online.getUniqueId())) {
                        showPlayerToOthers(online);
                    }
                }
            });
        }, MIN_SYNC_INTERVAL_TICKS, intervalTicks);
    }
}
