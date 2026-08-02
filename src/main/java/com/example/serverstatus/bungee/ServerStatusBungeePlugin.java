package com.example.serverstatus.bungee;

import eu.kennytv.maintenance.api.MaintenanceProvider;
import eu.kennytv.maintenance.api.proxy.MaintenanceProxy;
import eu.kennytv.maintenance.api.proxy.Server;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerStatusBungeePlugin extends Plugin implements Listener {

    private static final String CHANNEL = "BungeeCord";
    private static final String SUBCHANNEL = "srvstatus";

    private MaintenanceProxy maintenanceApi;
    private List<String> trackedServers;
    private String msgOnline;
    private String msgOffline;
    private String msgMaintenance;
    private int updateInterval;

    private final ConcurrentHashMap<String, String> statuses = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        try {
            maintenanceApi = (MaintenanceProxy) MaintenanceProvider.get();
            getLogger().info("Hooked into Maintenance!");
        } catch (Exception e) {
            getLogger().warning("Maintenance not found - maintenance status unavailable");
        }

        getProxy().getPluginManager().registerListener(this, this);

        // Initial ping and periodic refresh
        pingAllServers();
        getProxy().getScheduler().schedule(this, this::pingAllServers,
                updateInterval, updateInterval, TimeUnit.SECONDS);

        getLogger().info("ServerStatus enabled - tracking " + trackedServers.size() + " servers");
    }

    private void saveDefaultConfig() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) {
            try (InputStream in = getResourceAsStream("config.yml")) {
                if (in != null) {
                    Files.copy(in, file.toPath());
                }
            } catch (IOException e) {
                getLogger().severe("Failed to save default config.yml");
            }
        }
    }

    private void loadConfig() {
        try {
            File file = new File(getDataFolder(), "config.yml");
            Configuration config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);

            trackedServers = config.getStringList("servers");
            updateInterval = config.getInt("update-interval", 5);
            msgOnline = translate(config.getString("messages.online", "&aOnline"));
            msgOffline = translate(config.getString("messages.offline", "&cOffline"));
            msgMaintenance = translate(config.getString("messages.maintenance", "&6Maintenance"));
        } catch (IOException e) {
            e.printStackTrace();
            trackedServers = List.of("lobby");
            updateInterval = 5;
            msgOnline = "&aOnline";
            msgOffline = "&cOffline";
            msgMaintenance = "&6Maintenance";
        }
    }

    // --- Status logic ---

    private void pingAllServers() {
        AtomicInteger pending = new AtomicInteger(trackedServers.size());
        for (String serverName : trackedServers) {
            pingServer(serverName, pending);
        }
    }

    private void pingServer(String serverName, AtomicInteger pending) {
        ServerInfo info = getProxy().getServerInfo(serverName);
        if (info == null) {
            getLogger().warning("Server '" + serverName + "' not found in BungeeCord config");
            statuses.put(serverName, msgOffline);
            pending.decrementAndGet();
            return;
        }

        info.ping((ping, error) -> {
            boolean online = error == null && ping != null;

            if (isMaintenance(serverName)) {
                statuses.put(serverName, msgMaintenance);
            } else {
                statuses.put(serverName, online ? msgOnline : msgOffline);
            }

            pending.decrementAndGet();
        });
    }

    private boolean isMaintenance(String serverName) {
        if (maintenanceApi == null) return false;
        try {
            Server server = maintenanceApi.getServer(serverName);
            if (server != null) {
                return maintenanceApi.isMaintenance(server);
            }
            return maintenanceApi.isMaintenance();
        } catch (Exception e) {
            return false;
        }
    }

    // --- Utility ---

    private String translate(String text) {
        return text.replace("&", "§");
    }

    // --- Plugin messaging: respond to backend requests ---

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals(CHANNEL)) return;
        if (!(event.getSender() instanceof net.md_5.bungee.api.connection.Server)) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String subChannel = in.readUTF();

        if (!subChannel.equals(SUBCHANNEL)) return;

        net.md_5.bungee.api.connection.Server server = (net.md_5.bungee.api.connection.Server) event.getSender();
        sendStatusToServer(server);
    }

    private void sendStatusToServer(net.md_5.bungee.api.connection.Server server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(SUBCHANNEL);
        out.writeInt(trackedServers.size());
        for (String srv : trackedServers) {
            out.writeUTF(srv);
            out.writeUTF(statuses.getOrDefault(srv, msgOffline));
        }
        server.sendData(CHANNEL, out.toByteArray());
    }

    // --- Events ---

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        // Force a status refresh when a player joins
        pingAllServers();
    }
}
