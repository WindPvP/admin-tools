package com.windpvp.admin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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

        Player target;

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Console must specify a player: /vanish <name>");
                return true;
            }
            target = (Player) sender;
        } else {
            target = Bukkit.getPlayer(args[0]);
        }

        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }

        if (plugin.getVanishedPlayers().contains(target.getUniqueId())) {
            plugin.unvanishPlayer(target);
            if (sender != target) sender.sendMessage("§eUnvanished " + target.getName());
        } else {
            plugin.vanishPlayer(target);
            if (sender != target) sender.sendMessage("§aVanished " + target.getName());
        }

        return true;
    }
}