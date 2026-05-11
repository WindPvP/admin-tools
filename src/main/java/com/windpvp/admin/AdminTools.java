package com.windpvp.admin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AdminTools extends JavaPlugin {

    private final Set<UUID> vanishedPlayers = new HashSet<>();
    private VanishPacketListener packetListener;

    @Override
    public void onEnable() {
        this.packetListener = new VanishPacketListener(this);
        getCommand("vanish").setExecutor(new VanishCommand(this));
        
        // Register events to handle join/quit logic
        getServer().getPluginManager().registerEvents(new VanishListener(this), this);
        
        getLogger().info("AdminTools enabled (1.8.8 ProtocolLib)");
    }

    public Set<UUID> getVanishedPlayers() {
        return vanishedPlayers;
    }

    public void vanishPlayer(Player player) {
        vanishedPlayers.add(player.getUniqueId());
        // Hide player from everyone currently online
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                online.hidePlayer(player);
            }
        }
        player.sendMessage("§aYou are now vanished.");
    }

    public void unvanishPlayer(Player player) {
        vanishedPlayers.remove(player.getUniqueId());
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(player);
        }
        player.sendMessage("§eYou are no longer vanished.");
    }
}