package com.windpvp.admin.vanish;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.windpvp.admin.AdminTools;

public class VanishCommand implements CommandExecutor {

    private final AdminTools plugin;

    public VanishCommand(AdminTools plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("admintools.vanish")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Console must specify a player: /vanish <name>");
                return true;
            }
            Player self = (Player) sender;
            if (plugin.isVanished(self)) {
                plugin.unvanishPlayer(self);
            } else {
                plugin.vanishPlayer(self);
            }
            return true;
        }

        String targetName = args[0];
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        if (onlineTarget != null) {
            if (plugin.isVanished(onlineTarget)) {
                plugin.unvanishPlayer(onlineTarget);
                sender.sendMessage("§eUnvanished " + onlineTarget.getName());
            } else {
                plugin.vanishPlayer(onlineTarget);
                sender.sendMessage("§aVanished " + onlineTarget.getName());
            }
            return true;
        }

        // Cache-only lookup: never blocks the main thread on a Mojang profile request.
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayerIfCached(targetName);
        if (offlineTarget == null || !offlineTarget.hasPlayedBefore()) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }

        if (plugin.isVanished(offlineTarget.getUniqueId())) {
            plugin.unvanishOfflinePlayer(offlineTarget.getUniqueId());
            sender.sendMessage("§eUnvanished offline player " + offlineTarget.getName());
        } else {
            plugin.vanishOfflinePlayer(offlineTarget.getUniqueId());
            sender.sendMessage("§aVanished offline player " + offlineTarget.getName());
        }

        return true;
    }
}
