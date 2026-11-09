package com.windpvp.admin.platform;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/**
 * Paper 26.3 implementation. Loaded reflectively by {@link Platforms}.
 */
public class ModernPlatform implements Platform {

    private final Plugin plugin;

    public ModernPlatform(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "modern";
    }

    @Override
    public void hidePlayer(Player viewer, Player target) {
        viewer.hidePlayer(plugin, target);
    }

    @Override
    public void showPlayer(Player viewer, Player target) {
        viewer.showPlayer(plugin, target);
    }

    @Override
    public void clearJoinMessage(PlayerJoinEvent event) {
        event.joinMessage(null);
    }

    @Override
    public OfflinePlayer findOfflinePlayer(String name) {
        // Cache-only: never blocks the main thread on a Mojang profile lookup.
        OfflinePlayer player = Bukkit.getOfflinePlayerIfCached(name);
        return player != null && player.hasPlayedBefore() ? player : null;
    }
}
