package org.updraft.betterstatus.backend;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.updraft.betterstatus.protocol.StatusProtocol;
import org.updraft.betterstatus.protocol.StatusProtocol.WireStatus;
import org.updraft.betterstatus.model.ServerState;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BackendStatusPlugin extends JavaPlugin implements PluginMessageListener {
    private static final String CHANNEL = "BungeeCord";
    private final Map<String, WireStatus> cache = new ConcurrentHashMap<>();

    @Override public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new StatusExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        } else getLogger().warning("PlaceholderAPI not found; placeholders disabled.");
        getServer().getScheduler().runTaskTimer(this, this::requestStatus, 40L, 100L);
    }

    private void requestStatus() {
        Player player = getServer().getOnlinePlayers().stream().findFirst().orElse(null);
        if (player == null) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeUTF(StatusProtocol.SUBCHANNEL);
            player.sendPluginMessage(this, CHANNEL, bytes.toByteArray());
        } catch (IOException ignored) { }
    }

    @Override public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!CHANNEL.equals(channel)) return;
        try {
            for (WireStatus status : StatusProtocol.decode(data)) cache.put(status.name().toLowerCase(Locale.ROOT), status);
        } catch (IOException ex) {
            getLogger().warning("Ignored malformed status packet.");
        }
    }

    public String resolve(String params) {
        if (params == null || params.isBlank()) return "";
        String key = params.toLowerCase(Locale.ROOT);
        switch (key) {
            case "total_players": return Integer.toString(cache.values().stream().mapToInt(WireStatus::players).sum());
            case "total_max": return Integer.toString(cache.values().stream().mapToInt(WireStatus::maxPlayers).sum());
            case "playercount":
            case "total_playercount":
                return playerCount(cache.values().stream().mapToInt(WireStatus::players).sum(),
                        cache.values().stream().mapToInt(WireStatus::maxPlayers).sum());
            case "total_servers": return Integer.toString(cache.size());
            case "online_servers": return Long.toString(cache.values().stream().filter(s -> s.state() == ServerState.ONLINE || s.state() == ServerState.DEGRADED).count());
            case "offline_servers": return Long.toString(cache.values().stream().filter(s -> s.state() == ServerState.OFFLINE || s.state() == ServerState.UNREACHABLE).count());
            case "network_status":
                if (cache.isEmpty()) return "UNKNOWN";
                if (cache.values().stream().anyMatch(s -> s.state() == ServerState.OFFLINE)) return "OFFLINE";
                if (cache.values().stream().anyMatch(s -> s.state() == ServerState.DEGRADED || s.state() == ServerState.UNREACHABLE)) return "DEGRADED";
                return "ONLINE";
        }

        String field = "status";
        int split = key.lastIndexOf('_');
        if (split > 0 && Set.of("state", "players", "max", "playercount", "ping", "motd", "version", "uptime", "downtime", "lastseen", "online", "health", "maintenance").contains(key.substring(split + 1))) {
            field = key.substring(split + 1);
            key = key.substring(0, split);
        }
        WireStatus status = cache.get(key);
        if (status == null) return "Unknown";
        return switch (field) {
            case "state", "health" -> status.state().name();
            case "players" -> Integer.toString(status.players());
            case "max" -> Integer.toString(status.maxPlayers());
            case "playercount" -> playerCount(status.players(), status.maxPlayers());
            case "ping" -> status.pingMillis() < 0 ? "Unknown" : Long.toString(status.pingMillis());
            case "motd" -> status.motd();
            case "version" -> status.version();
            case "uptime" -> Long.toString(status.uptimeSeconds());
            case "downtime" -> Long.toString(status.downtimeSeconds());
            case "lastseen" -> status.lastSuccessMillis() <= 0 ? "Never" : Long.toString(status.lastSuccessMillis());
            case "online" -> Boolean.toString(status.state() == ServerState.ONLINE || status.state() == ServerState.DEGRADED);
            case "maintenance" -> Boolean.toString(status.state() == ServerState.MAINTENANCE);
            default -> status.legacyStatus();
        };
    }

    private String playerCount(int online, int max) {
        return online + "/" + max;
    }

    @Override public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL);
    }
}
