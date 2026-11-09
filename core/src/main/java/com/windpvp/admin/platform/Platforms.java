package com.windpvp.admin.platform;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

public final class Platforms {

    private static final String MODERN_CLASS = "com.windpvp.admin.platform.ModernPlatform";

    private Platforms() {
    }

    /**
     * Picks the modern (Paper 26.3) adapter when the server has the APIs it needs,
     * otherwise the 1.8.8 one. The modern adapter is Java 25 bytecode, so it is
     * only loaded after these checks and any linkage failure falls back to legacy.
     */
    public static Platform detect(Plugin plugin) {
        if (!hasModernApi()) {
            return new LegacyPlatform();
        }
        try {
            return (Platform) Class.forName(MODERN_CLASS, true, Platforms.class.getClassLoader())
                .getConstructor(Plugin.class)
                .newInstance(plugin);
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING,
                "Could not load the modern platform adapter; falling back to the legacy one.", ex);
            return new LegacyPlatform();
        }
    }

    private static boolean hasModernApi() {
        try {
            Class.forName("net.kyori.adventure.text.Component");
            Player.class.getMethod("hidePlayer", Plugin.class, Player.class);
            Server.class.getMethod("getOfflinePlayerIfCached", String.class);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException ex) {
            return false;
        }
    }
}
