package com.windpvp.admin.vanish;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.windpvp.admin.AdminTools;

public class VanishListener implements Listener {

    private final AdminTools plugin;

    public VanishListener(AdminTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();

        if (plugin.isVanished(joined)) {
            plugin.vanishPlayer(joined, false);
            event.setJoinMessage(null);
        }

        plugin.hideVanishedPlayersFrom(joined);
    }
}
