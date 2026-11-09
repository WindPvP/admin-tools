package com.windpvp.admin.vanish;

import org.bukkit.configuration.file.FileConfiguration;

import com.windpvp.admin.AdminTools;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * MySQL-backed vanish state.
 *
 * <p>All database work after startup runs on one dedicated thread, so the main
 * server thread never waits on MySQL, and writes and reads run in the order they
 * were submitted (a read always sees every write queued before it). The JDBC
 * connection is only ever touched from that thread.
 */
public class MySqlVanishStorage {

    private static final String DEFAULT_TABLE = "vanished_players";
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10L;

    private final AdminTools plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AdminTools-MySQL");
        thread.setDaemon(true);
        return thread;
    });

    private Connection connection;
    private String jdbcUrl;
    private String username;
    private String password;
    private String tableName = DEFAULT_TABLE;

    public MySqlVanishStorage(AdminTools plugin) {
        this.plugin = plugin;
    }

    /**
     * Reads the config and opens the first connection. Runs on the calling thread;
     * only call it from {@code onEnable}.
     */
    public boolean connect() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("mysql.enabled", false)) {
            plugin.getLogger().info("MySQL vanish sync disabled in config.");
            return false;
        }

        String host = config.getString("mysql.host", "127.0.0.1");
        int port = config.getInt("mysql.port", 3306);
        String database = config.getString("mysql.database", "admintools");
        username = config.getString("mysql.username", "root");
        password = config.getString("mysql.password", "");
        String configuredTable = config.getString("mysql.table", DEFAULT_TABLE);
        if (configuredTable != null && configuredTable.matches("^[A-Za-z0-9_]+$")) {
            tableName = configuredTable;
        } else {
            plugin.getLogger().warning("Invalid MySQL table name configured; falling back to " + DEFAULT_TABLE + ".");
            tableName = DEFAULT_TABLE;
        }
        boolean useSsl = config.getBoolean("mysql.use-ssl", false);
        jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSsl;

        try {
            openConnection();
            return true;
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialize MySQL vanish storage.", ex);
            connection = null;
            return false;
        }
    }

    /**
     * Blocking load for startup, before any player has joined.
     *
     * @return the vanished players, or {@code null} if the query failed
     */
    public Set<UUID> loadVanishedPlayersNow() {
        return queryVanishedPlayers();
    }

    /**
     * Loads the vanished players on the database thread. The future completes with
     * {@code null} if the query failed, so callers can tell "nobody is vanished"
     * apart from "the database is unreachable".
     */
    public CompletableFuture<Set<UUID>> loadVanishedPlayers() {
        try {
            return CompletableFuture.supplyAsync(this::queryVanishedPlayers, executor);
        } catch (RejectedExecutionException ex) {
            return CompletableFuture.completedFuture(null);
        }
    }

    /** Queues a write on the database thread and returns immediately. */
    public void setVanished(UUID uuid, boolean vanished) {
        try {
            executor.execute(() -> writeVanished(uuid, vanished));
        } catch (RejectedExecutionException ex) {
            plugin.getLogger().warning("MySQL storage is shut down; vanish state for " + uuid + " was not saved.");
        }
    }

    /** Finishes queued writes (up to a timeout), then closes the connection. */
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Timed out waiting for queued MySQL vanish writes to finish.");
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        closeConnection();
    }

    private Set<UUID> queryVanishedPlayers() {
        if (!ensureConnected()) {
            return null;
        }

        Set<UUID> vanished = new HashSet<>();
        String query = "SELECT player_uuid FROM `" + tableName + "` WHERE vanished = 1";
        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String uuidText = resultSet.getString("player_uuid");
                try {
                    vanished.add(UUID.fromString(uuidText));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return vanished;
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to load vanished players from MySQL.", ex);
            return null;
        }
    }

    private void writeVanished(UUID uuid, boolean vanished) {
        if (!ensureConnected()) {
            plugin.getLogger().warning("MySQL unavailable; vanish state for " + uuid + " was not saved.");
            return;
        }

        String query = "REPLACE INTO `" + tableName + "` (player_uuid, vanished) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            statement.setBoolean(2, vanished);
            statement.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to update vanished state for " + uuid + ".", ex);
        }
    }

    /** Reconnects if the connection was dropped (e.g. MySQL's wait_timeout). */
    private boolean ensureConnected() {
        try {
            if (connection != null && connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                return true;
            }
        } catch (SQLException ignored) {
        }

        closeConnection();
        try {
            openConnection();
            plugin.getLogger().info("Reconnected to MySQL vanish storage.");
            return true;
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not reconnect to MySQL vanish storage.", ex);
            connection = null;
            return false;
        }
    }

    private void openConnection() throws SQLException {
        connection = DriverManager.getConnection(jdbcUrl, username, password);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS `" + tableName + "` (" +
                    "`player_uuid` VARCHAR(36) PRIMARY KEY, " +
                    "`vanished` TINYINT(1) NOT NULL" +
                    ")"
            );
        }
    }

    private void closeConnection() {
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
}
