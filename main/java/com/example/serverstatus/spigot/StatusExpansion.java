package com.example.serverstatus.spigot;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class StatusExpansion extends PlaceholderExpansion {

    private final ServerStatusSpigotPlugin plugin;

    public StatusExpansion(ServerStatusSpigotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "srvstatus";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Developer";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String getRequiredPlugin() {
        return "ServerStatus";
    }

    @Override
    public @NotNull String onPlaceholderRequest(Player player, @NotNull String params) {
        if (params.isEmpty()) return "";
        return plugin.getStatus(params);
    }
}
