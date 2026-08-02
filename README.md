# ServerStatus

A Minecraft plugin that tracks the online/maintenance state of your backend servers and exposes it through [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) placeholders.

- **BungeeCord side** (`serverstatus-bungee`): pings tracked servers, checks maintenance status via the [Maintenance](https://www.spigotmc.org/resources/maintenance.61799/) plugin API, and broadcasts the results to backend servers over the `BungeeCord` plugin messaging channel.
- **Spigot side** (`serverstatus-spigot`): receives the statuses and registers a PlaceholderAPI expansion so you can use the status in any PlaceholderAPI-compatible plugin (scoreboards, tablists, chat, etc.).

## Placeholders

| Placeholder | Description |
| --- | --- |
| `%srvstatus_<server>%` | Returns the status of the given server, e.g. `%srvstatus_lobby%` |

Placeholder values are the status messages defined in the Bungee config (caseinsensitive server names):

- `&aOnline` (green "Online")
- `&cOffline` (red "Offline")
- `&6Maintenance` (gold "Maintenance")
- `Unknown` (returned by the Spigot side when no status has been received yet)

## Requirements

- **Proxy**: BungeeCord (or a fork such as Waterfall) with the [Maintenance](https://www.spigotmc.org/resources/maintenance.61799/) plugin installed
- **Backend**: Spigot/Paper 1.20.4+ with [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) installed
- **Java**: 17+

## Installation

1. Build the project with `mvn clean package` (the parent POM builds both modules).
2. Copy `bungee/target/serverstatus-bungee.jar` into the proxy's `plugins` folder.
3. Copy `spigot/target/serverstatus-spigot.jar` into the `plugins` folder of **every** backend server that should use the placeholders.
4. Restart the proxy and the backend servers.

## Configuration

### Proxy (`plugins/ServerStatus/config.yml`)

```yaml
# List of servers to track (must match your BungeeCord server names exactly)
servers:
  - lobby
  - survival
  - dev

# How often to ping servers and broadcast status (in seconds)
update-interval: 5

# Text returned by %srvstatus_<name>% placeholder
messages:
  online: "&aOnline"
  offline: "&cOffline"
  maintenance: "&6Maintenance"
```

Servers are pinged immediately on enable, on a fixed `update-interval` schedule, and whenever a player joins the proxy. Maintenance status is taken from the Maintenance plugin if it is present; otherwise the server falls back to online/offline detection only.

### PlaceholderAPI (`plugins/PlaceholderAPI/config.yml`)

`softdepend: PlaceholderAPI` is already set. After both plugins are running, the `srvstatus` expansion registers automatically; no manual `papi ecloud download` is needed.

## Usage examples

In a PlaceholderAPI-compatible plugin (e.g. a scoreboard or tab plugin):

```
%srvstatus_lobby%
%srvstatus_survival%
```

## Building

```
mvn clean package
```

Requires Maven 3.6+ and JDK 17+.

## Authors

Wontonstew and Frenchguy
