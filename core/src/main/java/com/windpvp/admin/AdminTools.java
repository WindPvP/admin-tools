package com.windpvp.admin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.windpvp.admin.platform.Platform;
import com.windpvp.admin.platform.Platforms;
import com.windpvp.admin.vanish.MySqlVanishStorage;
import com.windpvp.admin.vanish.VanishCommand;
import com.windpvp.admin.vanish.VanishListener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class AdminTools extends JavaPlugin {

    private static final long MIN_SYNC_INTERVAL_TICKS = 20L;

    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean syncInFlight = new AtomicBoolean(false);
    /** Bumped on every local vanish change. Only read and written on the main thread. */
    private long localChangeCount;

    private Platform platform;
    private MySqlVanishStorage vanishStorage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.platform = Platforms.detect(this);

        this.vanishStorage = new MySqlVanishStorage(this);
        if (!vanishStorage.connect()) {
            getLogger().warning("Failed to connect to MySQL vanish storage; using in-memory vanish state only.");
            vanishStorage = null;
        } else {
            Set<UUID> initial = vanishStorage.loadVanishedPlayersNow();
            vanishedPlayers.clear();
            if (initial != null) {
                vanishedPlayers.addAll(initial);
            }
            startSyncTask();
        }

        getCommand("vanish").setExecutor(new VanishCommand(this));
        getServer().getPluginManager().registerEvents(new VanishListener(this), this);

        getLogger().info("AdminTools enabled (" + platform.name() + " platform, " + Bukkit.getBukkitVersion() + ")");
    }

    @Override
    public void onDisable() {
        if (vanishStorage != null) {
            vanishStorage.close();
            vanishStorage = null;
        }
    }

    public Platform getPlatform() {
        return platform;
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
                platform.hidePlayer(viewer, online);
            }
        }
    }

    public void vanishOfflinePlayer(UUID uuid) {
        setVanished(uuid, true);
    }

    public void unvanishOfflinePlayer(UUID uuid) {
        setVanished(uuid, false);
    }

    /** Main thread only. Updates memory immediately; the database write is queued. */
    private void setVanished(UUID uuid, boolean vanished) {
        localChangeCount++;
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
                platform.hidePlayer(online, player);
            }
        }
    }

    private void showPlayerToOthers(Player player) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                platform.showPlayer(online, player);
            }
        }
    }

    /**
     * Periodically pulls vanish changes made by other backends. The query runs on the
     * database thread, queued behind any pending writes from this server, and the result
     * is applied back on the main thread.
     */
    private void startSyncTask() {
        long intervalTicks = Math.max(MIN_SYNC_INTERVAL_TICKS, getConfig().getLong("mysql.sync-interval-ticks", 200L));
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            MySqlVanishStorage storage = vanishStorage;
            if (storage == null || !syncInFlight.compareAndSet(false, true)) {
                return;
            }
            long changeCountAtRequest = localChangeCount;
            storage.loadVanishedPlayers().whenComplete((dbVanished, error) -> {
                if (!isEnabled()) {
                    syncInFlight.set(false);
                    return;
                }
                Bukkit.getScheduler().runTask(this, () -> {
                    syncInFlight.set(false);
                    if (dbVanished != null) {
                        applyRemoteState(dbVanished, changeCountAtRequest);
                    }
                });
            });
        }, MIN_SYNC_INTERVAL_TICKS, intervalTicks);
    }

    private void applyRemoteState(Set<UUID> dbVanished, long changeCountAtRequest) {
        // A local toggle happened after the query was queued, so this snapshot may not
        // include it yet. Skip this round rather than revert it; the next sync catches up.
        if (localChangeCount != changeCountAtRequest) {
            return;
        }

        Set<UUID> newlyVanished = new HashSet<>(dbVanished);
        newlyVanished.removeAll(vanishedPlayers);

        Set<UUID> newlyUnvanished = new HashSet<>(vanishedPlayers);
        newlyUnvanished.removeAll(dbVanished);

        if (newlyVanished.isEmpty() && newlyUnvanished.isEmpty()) {
            return;
        }

        vanishedPlayers.addAll(newlyVanished);
        vanishedPlayers.removeAll(newlyUnvanished);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (newlyVanished.contains(online.getUniqueId())) {
                hidePlayerFromOthers(online);
            }
            if (newlyUnvanished.contains(online.getUniqueId())) {
                showPlayerToOthers(online);
            }
        }
    }
}
