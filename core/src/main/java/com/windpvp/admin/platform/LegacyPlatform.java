package com.windpvp.admin.platform;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 1.8.8 implementation. These calls still exist (deprecated) on newer servers,
 * so this is also the fallback if the modern adapter cannot load.
 */
public class LegacyPlatform implements Platform {

    @Override
    public String name() {
        return "legacy";
    }

    @Override
    @SuppressWarnings("deprecation")
    public void hidePlayer(Player viewer, Player target) {
        viewer.hidePlayer(target);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void showPlayer(Player viewer, Player target) {
        viewer.showPlayer(target);
    }

    @Override
    public void clearJoinMessage(PlayerJoinEvent event) {
        event.setJoinMessage(null);
    }

    @Override
    @SuppressWarnings("deprecation")
    public OfflinePlayer findOfflinePlayer(String name) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        return player.hasPlayedBefore() ? player : null;
    }
}
