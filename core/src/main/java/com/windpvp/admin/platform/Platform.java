package com.windpvp.admin.platform;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * The few server calls whose API differs between 1.8.8 and Paper 26.3.
 * Everything else in the plugin uses API that exists on both.
 */
public interface Platform {

    /** Short label for logs, e.g. "legacy" or "modern". */
    String name();

    void hidePlayer(Player viewer, Player target);

    void showPlayer(Player viewer, Player target);

    void clearJoinMessage(PlayerJoinEvent event);

    /**
     * Looks up a player who is not online by name.
     *
     * @return the player, or {@code null} if this server has never seen them
     */
    OfflinePlayer findOfflinePlayer(String name);
}
