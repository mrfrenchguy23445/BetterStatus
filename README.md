# BetterServerStatus

A Minecraft network monitoring plugin with separate BungeeCord and Velocity proxy adapters and a Paper/Spigot PlaceholderAPI backend.

## Modules

- `common`: immutable status snapshots, asynchronous monitoring/state transitions, versioned bounded plugin-message protocol, public API, SQLite history and Discord webhook support.
- `proxy-bungee`: BungeeCord/Waterfall adapter, monitoring, `/srvstatus` commands, history and webhook alerts.
- `proxy-velocity`: Velocity-native adapter with the same monitoring core and command.
- `backend`: Bukkit/Paper plugin that receives proxy snapshots and registers PlaceholderAPI placeholders.

## Requirements

- JDK 21+
- Maven 3.9+
- BungeeCord/Waterfall or Velocity 3.x (install only one proxy adapter)
- Paper/Spigot 1.20+ on backend servers
- PlaceholderAPI for placeholders

## Build

From the repository root:

```sh
mvn clean verify
```

Artifacts:
- `proxy-bungee/target/betterstatus-bungee-2.0.0.jar`
- `proxy-velocity/target/betterstatus-velocity-2.0.0.jar`
- `backend/target/betterstatus-backend-2.0.0.jar`

Install one proxy adapter on the proxy and the backend jar on every server where placeholders are needed. Do not install both proxy adapters on one proxy.

## BungeeCord configuration

On first startup, `plugins/BetterStatus/config.yml` is created:

```yaml
servers:
  - lobby
  - survival
settings:
  update-interval-seconds: 5
  timeout-millis: 3000
  failure-threshold: 3
  recovery-threshold: 2
  warning-ping-millis: 150
  critical-ping-millis: 300
messages:
  online: "&aOnline"
  offline: "&cOffline"
  maintenance: "&6Maintenance"
storage:
  enabled: true
discord:
  webhook-url: ""
```

Velocity creates `plugins/betterstatus/config.properties` with equivalent monitoring settings. Configure `servers` as a comma-separated list of exact proxy server names. SQLite history is stored in the plugin data directory. Discord notifications remain disabled until a webhook URL is configured.

## Placeholders

The existing placeholder is retained:

- `%srvstatus_lobby%` — configured legacy status text.

Per-server placeholders:

| Placeholder | Value |
| --- | --- |
| `%srvstatus_lobby_state%` | State enum |
| `%srvstatus_lobby_online%` | Boolean online indicator |
| `%srvstatus_lobby_players%` | Current players |
| `%srvstatus_lobby_max%` | Maximum players |
| `%srvstatus_lobby_playercount%` | Combined count, for example `12/100` |
| `%srvstatus_lobby_ping%` | Ping in milliseconds |
| `%srvstatus_lobby_motd%` | MOTD |
| `%srvstatus_lobby_version%` | Server version |
| `%srvstatus_lobby_uptime%` | Uptime in seconds |
| `%srvstatus_lobby_downtime%` | Time since last successful ping |
| `%srvstatus_lobby_lastseen%` | Last successful check timestamp |
| `%srvstatus_lobby_health%` | Health state |

Network-wide placeholders:

| Placeholder | Value |
| --- | --- |
| `%srvstatus_playercount%` | Total online players / total capacity, e.g. `24/500` |
| `%srvstatus_total_playercount%` | Alias of the network player count |
| `%srvstatus_total_players%` | Total online players only |
| `%srvstatus_total_max%` | Total player capacity |
| `%srvstatus_total_servers%` | Number of monitored servers |
| `%srvstatus_online_servers%` | Online/degraded server count |
| `%srvstatus_offline_servers%` | Offline/unreachable server count |
| `%srvstatus_network_status%` | Aggregate network state |

Example TAB/scoreboard line:

```
Players: %srvstatus_playercount%
Lobby: %srvstatus_lobby_playercount%
```

Placeholder requests only read the backend cache. Unknown server names return `Unknown`.

## Commands

- `/srvstatus` or `/srvstatus list` — list monitored servers.
- `/srvstatus info <server>` — show a server snapshot.
- `/srvstatus check <server|all>` — queue a fresh check.
- `/srvstatus history <server>` — show recent transitions (BungeeCord).

Permission: `serverstatus.admin`. Velocity additionally checks `serverstatus.view` for command visibility.

## Vanish integration

When the BetterServerStatus backend companion is installed on a monitored Paper/Spigot server, it detects vanished players and reports a hidden-player adjustment to the proxy. The BungeeCord and Velocity adapters subtract that adjustment from the monitored online count, so staff in vanish do not inflate server or network player counts.

Supported integrations:
- EssentialsX vanish through its User API.
- SuperVanish and PremiumVanish through their API when available.
- The common `vanished` player metadata flag as a compatibility fallback.

Install the backend companion on each server where vanish-aware counts are required. The proxy-only adapters cannot inspect backend vanish state by themselves. This feature changes reported online counts; it does not change the server's actual player list or hide players in-game.

## States

`UNKNOWN`, `STARTING`, `ONLINE`, `DEGRADED`, `MAINTENANCE`, `RESTARTING`, `UNREACHABLE`, and `OFFLINE`. Configurable failure/recovery thresholds reduce flapping. Ping thresholds are configurable.

## Transport limitations

The proxy monitoring engine pings backends independently of player count. Backend status delivery currently uses the BungeeCord plugin-message channel, so a backend needs at least one connected player to request fresh snapshots. Player-independent authenticated TCP/TLS transport is not implemented in this version.

The BungeeCord adapter automatically attempts to hook the Maintenance plugin API when it is installed. Both proxy adapters expose the shared `ServerStatusApi`; integrations can call `setMaintenance(server, true/false)` to control a maintenance state. MySQL, network-wide aggregate analytics, automatic hot reload and a player-independent transport are also not included yet.

## Security

Inbound plugin messages have packet-size and server-count bounds and malformed packets are rejected. Monitoring and webhook delivery are asynchronous. Keep webhook URLs private and never commit real credentials.
