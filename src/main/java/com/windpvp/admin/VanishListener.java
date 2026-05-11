package com.windpvp.admin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class VanishListener implements Listener {

    private final AdminTools plugin;

    public VanishListener(AdminTools plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();

        // 1. If the person joining is vanished, hide them from everyone
        if (plugin.getVanishedPlayers().contains(joined.getUniqueId())) {
            plugin.vanishPlayer(joined);
            event.setJoinMessage(null); // Optional: hide join message
        }

        // 2. Hide already-vanished players from the person who just joined
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (plugin.getVanishedPlayers().contains(online.getUniqueId())) {
                joined.hidePlayer(online);
            }
        }
    }
}