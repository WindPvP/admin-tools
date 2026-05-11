package com.windpvp.admin;

import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class MySqlVanishStorage {

    private final AdminTools plugin;
    private Connection connection;

    public MySqlVanishStorage(AdminTools plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("mysql.enabled", false)) {
            plugin.getLogger().info("MySQL vanish sync disabled in config.");
            return false;
        }

        String host = config.getString("mysql.host", "127.0.0.1");
        int port = config.getInt("mysql.port", 3306);
        String database = config.getString("mysql.database", "admintools");
        String username = config.getString("mysql.username", "root");
        String password = config.getString("mysql.password", "");
        String table = config.getString("mysql.table", "vanished_players");
        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";

        try {
            connection = DriverManager.getConnection(jdbcUrl, username, password);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS `" + table + "` (" +
                        "`player_uuid` VARCHAR(36) PRIMARY KEY, " +
                        "`vanished` TINYINT(1) NOT NULL DEFAULT 1" +
                        ")"
                );
            }
            return true;
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialize MySQL vanish storage.", ex);
            connection = null;
            return false;
        }
    }

    public Set<UUID> loadVanishedPlayers() {
        Set<UUID> vanished = new HashSet<>();
        if (!isConnected()) {
            return vanished;
        }

        String table = plugin.getConfig().getString("mysql.table", "vanished_players");
        String query = "SELECT player_uuid FROM `" + table + "` WHERE vanished = 1";
        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String uuidText = resultSet.getString("player_uuid");
                try {
                    vanished.add(UUID.fromString(uuidText));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load vanished players from MySQL.", ex);
        }

        return vanished;
    }

    public void setVanished(UUID uuid, boolean vanished) {
        if (!isConnected()) {
            return;
        }

        String table = plugin.getConfig().getString("mysql.table", "vanished_players");
        String query = "REPLACE INTO `" + table + "` (player_uuid, vanished) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            statement.setBoolean(2, vanished);
            statement.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to update vanished state for " + uuid + ".", ex);
        }
    }

    public void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to close MySQL connection.", ex);
        } finally {
            connection = null;
        }
    }

    private boolean isConnected() {
        if (connection == null) {
            return false;
        }
        try {
            return !connection.isClosed();
        } catch (SQLException ex) {
            return false;
        }
    }
}
