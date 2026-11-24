package org.updraft.betterstatus.backend;

import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

final class VanishDetector {
    private final BackendStatusPlugin plugin;

    VanishDetector(BackendStatusPlugin plugin) {
        this.plugin = plugin;
    }

    int countVanishedPlayers() {
        int count = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (isVanished(player)) count++;
        }
        return count;
    }

    private boolean isVanished(Player player) {
        if (player.hasMetadata("vanished")) {
            for (MetadataValue value : player.getMetadata("vanished")) {
                if (value.asBoolean()) return true;
            }
        }
        if (player.hasMetadata("essentials")) {
            for (MetadataValue value : player.getMetadata("essentials")) {
                if (value.asBoolean()) return true;
            }
        }
        return isEssentialsVanished(player) || isSuperVanishVanished(player);
    }

    private boolean isEssentialsVanished(Player player) {
        Plugin essentials = plugin.getServer().getPluginManager().getPlugin("Essentials");
        if (essentials == null) return false;
        try {
            Method getUser = essentials.getClass().getMethod("getUser", Player.class);
            Object user = getUser.invoke(essentials, player);
            if (user == null) return false;
            Method vanished = user.getClass().getMethod("isVanished");
            return Boolean.TRUE.equals(vanished.invoke(user));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private boolean isSuperVanishVanished(Player player) {
        for (String pluginName : new String[]{"SuperVanish", "PremiumVanish"}) {
            Plugin vanishPlugin = plugin.getServer().getPluginManager().getPlugin(pluginName);
            if (vanishPlugin == null) continue;
            for (String className : new String[]{
                    "de.myzelyam.api.vanish.VanishAPI",
                    "de.myzelyam.api.vanish.PremiumVanishAPI"
            }) {
                try {
                    Class<?> api = Class.forName(className, false, vanishPlugin.getClass().getClassLoader());
                    for (Method method : api.getMethods()) {
                        if (!method.getName().equals("isInvisible") || method.getParameterCount() != 1) continue;
                        if (method.getParameterTypes()[0].isInstance(player)) {
                            return Boolean.TRUE.equals(method.invoke(null, player));
                        }
                    }
                } catch (ReflectiveOperationException | LinkageError ignored) {
                }
            }
        }
        return false;
    }
}
