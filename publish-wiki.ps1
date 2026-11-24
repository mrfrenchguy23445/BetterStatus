$ErrorActionPreference = "Stop"
$wikiUrl = "https://github.com/mrfrenchguy23445/BetterStatus.wiki.git"
$workDir = Join-Path $env:TEMP ("BetterServerStatus-Wiki-" + [guid]::NewGuid().ToString("N"))
$utf8 = New-Object System.Text.UTF8Encoding($false)

Write-Host "Cloning the BetterServerStatus GitHub Wiki..."
git clone $wikiUrl $workDir
if ($LASTEXITCODE -ne 0) {
    throw "Could not clone the Wiki. Enable Wikis in GitHub under Repository Settings > Features > Wikis, then run this script again."
}

$pages = @{
"Home.md" = @'
# BetterServerStatus

A Minecraft network monitoring plugin for proxy networks. Monitor backend availability, latency, player counts, health states, and Discord transition alerts.

## Quick links
- [[Installation]]
- [[BungeeCord-Setup]]
- [[Velocity-Setup]]
- [[Backend-Setup]]
- [[Configuration]]
- [[Commands-and-Permissions]]
- [[Placeholders]]
- [[Vanish-Integration]]
- [[Discord-Webhooks]]
- [[API]]
- [[Troubleshooting]]

## Components

| Component | Purpose |
| --- | --- |
| BungeeCord adapter | Monitoring on BungeeCord and compatible Waterfall proxies |
| Velocity adapter | Native Velocity monitoring adapter |
| Backend companion | PlaceholderAPI support and vanish-aware player counts |
| Common module | Shared models, monitoring, protocol, API, storage, and notifications |

Install exactly one proxy adapter. The backend companion is optional unless you need backend placeholders or vanish-aware counts.

## Requirements
- Java 21 or newer for the current build
- Maven 3.9+ to build from source
- BungeeCord/Waterfall or Velocity 3.x
- Paper/Spigot backend servers; the backend module currently compiles against Paper API 1.21.4
- PlaceholderAPI for in-game placeholders

The declared API version is not a guarantee that every server release has been tested.
'@

"Installation.md" = @'
# Installation

## Select the proxy adapter
- BungeeCord/Waterfall: use the BungeeCord artifact.
- Velocity: use the Velocity artifact.

Do not install both adapters on the same proxy.

## Build from source

Requirements: JDK 21+ and Maven 3.9+.

Run from the repository root:

```powershell
mvn clean package
```

Artifacts:
- proxy-bungee/target/betterstatus-bungee-2.0.0.jar
- proxy-velocity/target/betterstatus-velocity-2.0.0.jar
- backend/target/betterstatus-backend-2.0.0.jar

## Install
1. Stop the proxy.
2. Put the matching proxy JAR in its plugins folder.
3. Start once to generate configuration.
4. Configure exact backend server names.
5. Install the backend companion on each Paper/Spigot server where placeholders or vanish-aware counts are needed.
6. Restart the backend servers and proxy.
7. Check console logs for startup errors.

Backend snapshot requests use the BungeeCord plugin-message channel. A backend needs at least one connected player to request fresh snapshots.
'@

"BungeeCord-Setup.md" = @'
# BungeeCord / Waterfall setup

Install the BungeeCord artifact in the proxy plugins folder, start once, edit plugins/BetterStatus/config.yml, then restart.

Example:

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

Names under servers must exactly match the names in BungeeCord config.yml.

The adapter attempts to hook into the Maintenance plugin API when available. Install the backend companion separately on servers that need placeholders or vanish-aware counts.
'@

"Velocity-Setup.md" = @'
# Velocity setup

Install the Velocity artifact in Velocity's plugins folder and start once. Edit plugins/betterstatus/config.properties and restart.

Example:

```properties
servers=lobby,survival
update-interval-seconds=5
timeout-millis=3000
failure-threshold=3
recovery-threshold=2
warning-ping-millis=150
critical-ping-millis=300
discord-webhook-url=
```

The servers property is a comma-separated list of exact registered server names. Install the backend companion where placeholders or vanish-aware counts are required.
'@

"Backend-Setup.md" = @'
# Backend companion setup

The backend companion is an optional Paper/Spigot plugin. Install the backend JAR on every backend where you need:
- PlaceholderAPI status placeholders.
- Proxy snapshot delivery to placeholders.
- Vanish-aware online player counts.

It is not a proxy plugin.

## Optional integrations
- PlaceholderAPI is required only for placeholders.
- EssentialsX, SuperVanish, and PremiumVanish are detected when installed.
- A compatible proxy adapter must be installed on the network.

## Plugin messaging limitation

The companion requests snapshots through the BungeeCord plugin-message channel. It needs at least one connected player on that backend to send a request. If a backend is empty, cached placeholders and vanish adjustments may stop updating until a player joins.
'@

"Configuration.md" = @'
# Configuration

## BungeeCord

File: plugins/BetterStatus/config.yml

| Setting | Default | Description |
| --- | --- | --- |
| servers | lobby, survival, dev | Backend names to monitor |
| settings.update-interval-seconds | 5 | Check interval |
| settings.timeout-millis | 3000 | Ping timeout |
| settings.failure-threshold | 3 | Failures before OFFLINE |
| settings.recovery-threshold | 2 | Successful checks before recovery |
| settings.warning-ping-millis | 150 | Latency threshold for DEGRADED |
| settings.critical-ping-millis | 300 | Critical latency threshold |
| messages.online | &aOnline | Legacy online text |
| messages.offline | &cOffline | Legacy offline text |
| messages.maintenance | &6Maintenance | Maintenance text |
| storage.enabled | true | Enable SQLite history |
| discord.webhook-url | empty | Optional Discord webhook |

## Velocity

File: plugins/betterstatus/config.properties

The servers property is comma-separated. Other monitoring properties correspond to the BungeeCord settings. Velocity creates SQLite history automatically and accepts discord-webhook-url.

## Backend

The backend companion currently has no separate user-facing configuration file. Vanish detection is automatic when supported plugin metadata or APIs are available.
'@

"Commands-and-Permissions.md" = @'
# Commands and permissions

Command: /srvstatus (alias /ss on supported adapters)

| Command | Description |
| --- | --- |
| /srvstatus | List monitored servers |
| /srvstatus list | List monitored servers |
| /srvstatus info <server> | Show a server snapshot |
| /srvstatus check <server> | Queue a fresh check |
| /srvstatus check all | Queue checks for all servers |
| /srvstatus history <server> | Recent state transitions (BungeeCord adapter) |

Permissions:
- serverstatus.admin — required to execute administrative status commands.
- serverstatus.view — used by Velocity's command visibility/permission check.

Command behavior differs slightly between adapters.
'@

"Placeholders.md" = @'
# PlaceholderAPI placeholders

Install PlaceholderAPI and the BetterServerStatus backend companion on the backend server.

Replace lobby in these examples with the configured server name.

## Per-server placeholders

| Placeholder | Meaning |
| --- | --- |
| %srvstatus_lobby% | Legacy status text |
| %srvstatus_lobby_state% | State enum |
| %srvstatus_lobby_online% | Boolean online indicator |
| %srvstatus_lobby_players% | Online player count |
| %srvstatus_lobby_max% | Maximum player count |
| %srvstatus_lobby_playercount% | online/max |
| %srvstatus_lobby_ping% | Ping in milliseconds |
| %srvstatus_lobby_motd% | MOTD |
| %srvstatus_lobby_version% | Server version |
| %srvstatus_lobby_uptime% | Uptime in seconds |
| %srvstatus_lobby_downtime% | Time since last successful ping |
| %srvstatus_lobby_lastseen% | Last successful check timestamp |
| %srvstatus_lobby_health% | Health state |

## Network placeholders

| Placeholder | Meaning |
| --- | --- |
| %srvstatus_playercount% | Total online / total capacity |
| %srvstatus_total_playercount% | Alias of total player count |
| %srvstatus_total_players% | Total online players |
| %srvstatus_total_max% | Total capacity |
| %srvstatus_total_servers% | Number of monitored servers |
| %srvstatus_online_servers% | Online/degraded server count |
| %srvstatus_offline_servers% | Offline/unreachable server count |
| %srvstatus_network_status% | Aggregate network state |

Example TAB lines:

```text
Network: %srvstatus_playercount%
Lobby: %srvstatus_lobby_playercount%
Survival: %srvstatus_survival_playercount%
```

Placeholders read the backend's local cache. Unknown server names return Unknown.
'@

"Vanish-Integration.md" = @'
# Vanish integration

BetterServerStatus can exclude vanished staff from reported online player counts.

## Supported integrations
- EssentialsX vanish through the Essentials user API.
- SuperVanish.
- PremiumVanish.
- The common player metadata flag named vanished as a compatibility fallback.

## Setup
1. Install a BetterServerStatus proxy adapter.
2. Install the backend companion on every backend where vanish-aware counts are needed.
3. Install and configure your vanish plugin normally.
4. Restart the backend and proxy.
5. Test with a staff account that is actually vanished.

Vanish plugins are optional dependencies.

## Behavior and limitations
- The backend companion detects vanished players and sends a hidden-player count to the proxy.
- BungeeCord and Velocity subtract that count from monitored online totals.
- Maximum capacity is unchanged.
- BetterServerStatus does not modify the actual player list or vanish behavior.
- Proxy-only installation cannot inspect backend vanish state.
- Plugin-message updates require a player connected to that backend.
- Custom vanish plugins may need a dedicated integration.

If a supported plugin does not work with a particular version, report its exact name and version.
'@

"Discord-Webhooks.md" = @'
# Discord webhooks

The proxy adapters can send server state transition notifications to a Discord webhook.

## BungeeCord
Set discord.webhook-url in plugins/BetterStatus/config.yml.

## Velocity
Set discord-webhook-url in plugins/betterstatus/config.properties.

Leave the value empty to disable notifications. Keep webhook URLs private. If a webhook URL is exposed, regenerate it in Discord.
'@

"API.md" = @'
# Java API

The shared API is exposed by the proxy adapter through ServerStatusApi.

Available operations:
- getServer(name) — retrieve a cached server snapshot.
- getServers() — retrieve all monitored snapshots.
- checkServer(name) — request a fresh asynchronous check.
- addListener(listener) and removeListener(listener) — observe state transitions.
- setMaintenance(server, true/false) — set or clear maintenance state.

The API is part of the common module. Check repository source for exact package and signatures for the version in use.
'@

"Troubleshooting.md" = @'
# Troubleshooting

## Placeholders show Unknown
- Confirm the backend name exactly matches proxy configuration.
- Confirm the proxy adapter and backend companion are enabled.
- Ensure at least one player is connected to that backend.
- Check proxy and backend console logs.

## Player counts include vanished staff
- Confirm the backend companion is installed on that backend.
- Confirm EssentialsX, SuperVanish, or PremiumVanish is enabled.
- Test with a player who is actually vanished.
- Allow a few seconds for the next plugin-message update.
- Custom vanish plugins may need an additional API hook.

## Server marked OFFLINE
- Verify the backend is reachable from the proxy.
- Check configured address and port.
- Review timeout and failure-threshold settings.
- Monitoring uses server-list ping; it does not log into the backend.

## Discord alerts missing
- Verify the webhook URL.
- Ensure the proxy can reach Discord over HTTPS.
- Check console logs.
- Keep the URL private.

## Build errors

Build from the repository root with JDK 21+ and Maven 3.9+:

```powershell
mvn clean verify
```

Run Maven from the root so the common module is built before the adapters.
'@

"_Sidebar.md" = @'
* [[Home]]
* **Getting started**
  * [[Installation]]
  * [[BungeeCord-Setup]]
  * [[Velocity-Setup]]
  * [[Backend-Setup]]
* **Reference**
  * [[Configuration]]
  * [[Commands-and-Permissions]]
  * [[Placeholders]]
  * [[Vanish-Integration]]
  * [[Discord-Webhooks]]
  * [[API]]
* [[Troubleshooting]]
'@
}

foreach ($entry in $pages.GetEnumerator()) {
    $content = $entry.Value
    [System.IO.File]::WriteAllText((Join-Path $workDir $entry.Key), $content, $utf8)
}

Push-Location $workDir
try {
    git add -- .
    if ($LASTEXITCODE -ne 0) { throw "git add failed." }
    git commit -m "Publish BetterServerStatus documentation"
    if ($LASTEXITCODE -ne 0) { throw "git commit failed. Configure your Git author identity and retry." }
    git push origin HEAD
    if ($LASTEXITCODE -ne 0) { throw "git push failed. Check GitHub authentication and Wiki write access." }
}
finally {
    Pop-Location
}

Write-Host ""
Write-Host "Wiki published: https://github.com/mrfrenchguy23445/BetterStatus/wiki"
Write-Host "Working copy: $workDir"
