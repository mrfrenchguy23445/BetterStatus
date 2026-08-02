package com.example.serverstatus.spigot;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.util.concurrent.ConcurrentHashMap;

public class ServerStatusSpigotPlugin extends JavaPlugin implements PluginMessageListener {

    private static final String CHANNEL = "BungeeCord";
    private static final String SUBCHANNEL = "srvstatus";

    private final ConcurrentHashMap<String, String> serverStatuses = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            boolean registered = new StatusExpansion(this).register();
            if (registered) {
                getLogger().info("Hooked into PlaceholderAPI!");
            } else {
                getLogger().warning("Failed to register PlaceholderAPI expansion");
            }
        } else {
            getLogger().warning("PlaceholderAPI not found - placeholders disabled");
        }

        // Request status on enable
        Bukkit.getScheduler().runTaskLater(this, () -> requestStatus(), 40L);

        // Periodic requests
        Bukkit.getScheduler().runTaskTimer(this, () -> requestStatus(), 100L, 100L);

        getLogger().info("ServerStatus Spigot side enabled");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL);
    }

    private void requestStatus() {
        Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        if (players.length == 0) return;

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(SUBCHANNEL);

        // Send request to proxy via the first online player
        players[0].sendPluginMessage(this, CHANNEL, out.toByteArray());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(CHANNEL)) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subChannel = in.readUTF();

        if (!subChannel.equals(SUBCHANNEL)) return;

        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            String serverName = in.readUTF();
            String status = in.readUTF();
            serverStatuses.put(serverName.toLowerCase(), status);
        }

    }

    public String getStatus(String serverName) {
        String status = serverStatuses.get(serverName.toLowerCase());
        return status != null ? status : "Unknown";
    }
}
