# BetterStatus — ServerStatus 2.0 Roadmap

This document is the implementation plan for evolving the current ServerStatus prototype into a production-ready Minecraft network monitoring platform.

## Product goals

- Monitor backend availability, latency, player counts, capacity, version, MOTD and maintenance state.
- Support BungeeCord and Velocity through native proxy adapters.
- Expose server and network metrics through PlaceholderAPI.
- Provide administrative commands, diagnostics, history and notifications.
- Offer a public API for other plugins.
- Preserve the existing `%srvstatus_<server>%` placeholder and legacy configuration where practical.
- Keep monitoring asynchronous, bounded and safe for production servers.

## Current repository baseline

The repository currently contains an early BungeeCord implementation, a backend implementation under a separate `main/` source tree, plugin descriptors and a README. It does not yet contain the Maven parent/module structure described by the README. The first engineering task is therefore to establish a reproducible build and normalize the source layout before adding new features.

Known prototype limitations to address:
- The Bungee implementation depends directly on the Maintenance API rather than treating it as an optional integration.
- Backend status requests rely on a connected player and BungeeCord plugin messaging, so a backend with no players cannot request fresh data.
- The backend message parser does not validate packet length, count bounds or malformed input.
- Status is represented as a formatted string rather than structured state and metrics.
- The ping callback's result is not retained as structured latency/player/version data.
- The current source layout and README build instructions are inconsistent.

## Target architecture

Use a Maven multi-module build with a dependency direction that keeps platform APIs isolated.

```text
BetterStatus/
├── pom.xml
├── api/
├── common/
├── monitoring/
├── proxy-bungee/
├── proxy-velocity/
├── backend/
├── storage-api/
├── storage-sqlite/
├── storage-mysql/
├── notifications-discord/
├── tests/
├── README.md
└── ROADMAP.md
```

### Module responsibilities

| Module | Responsibility |
| --- | --- |
| api | Stable public Java API, snapshots and listener interfaces |
| common | Shared models, state machine, protocol DTOs and validation |
| monitoring | Scheduling, health evaluation, thresholds, cache and history abstractions |
| proxy-bungee | Native BungeeCord adapter, server pings, commands and transport |
| proxy-velocity | Native Velocity adapter, server pings, commands and transport |
| backend | Paper/Spigot plugin, PlaceholderAPI expansion and backend receiver |
| storage-api | Storage contracts and data models |
| storage-sqlite | Default local persistent storage |
| storage-mysql | Optional shared storage |
| notifications-discord | Asynchronous webhook delivery and alert formatting |
| tests | Unit and integration test suites |

The common, monitoring and API modules must not depend on BungeeCord, Velocity or Bukkit classes. Platform-specific code belongs only in its adapter module.

## Data model and states

Replace formatted-string-only status with structured immutable snapshots.

A server snapshot should contain:
- Canonical server name and display name.
- State and health classification.
- Online and maximum player counts when available.
- Latency in milliseconds when available.
- MOTD and Minecraft protocol/version information when available.
- Maintenance flag and source of that information.
- Last successful response and last check timestamps.
- State-change timestamp and diagnostic reason.

States:
- `UNKNOWN`: no reliable result has been received.
- `STARTING`: a server is becoming reachable.
- `ONLINE`: responding normally.
- `DEGRADED`: reachable but outside configured health thresholds.
- `MAINTENANCE`: intentionally unavailable to players.
- `RESTARTING`: a monitored outage matching restart policy.
- `UNREACHABLE`: check timed out or failed without enough evidence to declare offline.
- `OFFLINE`: confirmed unavailable after the configured failure threshold.

Do not collapse `UNKNOWN`, `UNREACHABLE` and `OFFLINE` into one value. Use consecutive failures and recovery confirmations to prevent flapping.

## Feature roadmap

### Phase 0 — Repository and build foundation

- [ ] Audit all tracked source files and remove duplicate/misplaced source trees.
- [ ] Establish a Maven parent POM and module POMs.
- [ ] Normalize Java packages and plugin entry points.
- [ ] Add dependency scopes and shade/relocation policy where required.
- [ ] Add formatting, compiler and test configuration.
- [ ] Make README installation/build instructions match the real project.
- [ ] Add CI that builds every module on supported JDKs.

**Exit criteria:** a clean checkout builds reproducibly and produces the existing proxy/backend artifacts.

### Phase 1 — Shared core

- [ ] Implement `ServerState`, `HealthState`, `ServerSnapshot` and `NetworkSnapshot`.
- [ ] Add case-insensitive server-name normalization.
- [ ] Add validated configuration models and defaults.
- [ ] Add immutable snapshot publication and thread-safe repositories.
- [ ] Add a versioned protocol model and bounded packet codec.
- [ ] Preserve the legacy status placeholder semantics.

**Exit criteria:** shared models compile without any platform API dependency and have unit tests.

### Phase 2 — Monitoring engine

- [ ] Implement asynchronous, bounded ping scheduling.
- [ ] Collect latency, player counts, maximum players, MOTD and version where supported.
- [ ] Add per-server check intervals and timeouts.
- [ ] Add consecutive failure and recovery thresholds.
- [ ] Implement state transitions, restart detection and health evaluation.
- [ ] Add cache expiry and stale-data handling.
- [ ] Prevent overlapping checks for the same server.
- [ ] Ensure no network or database operation blocks a Minecraft main thread.

Default latency thresholds should be configurable, not hardcoded. Initial suggested defaults are warning above 150 ms and critical above 300 ms.

**Exit criteria:** deterministic state transitions under success, timeout, recovery and flapping test scenarios.

### Phase 3 — Proxy adapters and communication

- [ ] Implement the BungeeCord adapter using the shared monitoring engine.
- [ ] Implement a native Velocity adapter using the same engine.
- [ ] Keep platform APIs isolated behind provider/transport interfaces.
- [ ] Version all messages and include timestamps and monotonically increasing sequence numbers.
- [ ] Reject malformed, oversized, stale and unsupported packets.
- [ ] Add heartbeat and handshake messages.
- [ ] Keep legacy BungeeCord plugin messaging as a compatibility transport.
- [ ] Add an authenticated direct transport for player-independent backend updates.
- [ ] Secure direct transport with authentication, replay protection, bounded queues, timeouts and TLS support.
- [ ] Make transport selection explicit in configuration.

Plugin messaging is player-carried in common proxy implementations. It must not be the only transport if monitoring must continue when a backend has zero players.

**Exit criteria:** BungeeCord and Velocity produce equivalent snapshots and can update a backend without requiring a player to be online when direct transport is enabled.

### Phase 4 — Backend and PlaceholderAPI

- [ ] Normalize backend source layout and plugin descriptor.
- [ ] Receive and validate status snapshots.
- [ ] Register the PlaceholderAPI expansion when PlaceholderAPI is installed.
- [ ] Preserve `%srvstatus_<server>%`.
- [ ] Add structured server placeholders:
  - `%srvstatus_<server>_state%`
  - `%srvstatus_<server>_online%`
  - `%srvstatus_<server>_max%`
  - `%srvstatus_<server>_players%`
  - `%srvstatus_<server>_ping%`
  - `%srvstatus_<server>_motd%`
  - `%srvstatus_<server>_version%`
  - `%srvstatus_<server>_uptime%`
  - `%srvstatus_<server>_maintenance%`
  - `%srvstatus_<server>_health%`
- [ ] Add network placeholders for total players, capacity, server counts and network state.
- [ ] Add configurable display formats and unknown-value fallbacks.
- [ ] Ensure placeholder requests read cached snapshots only.

**Exit criteria:** placeholders work in TAB, scoreboards, holograms and other PlaceholderAPI consumers without doing I/O.

### Phase 5 — Administration

- [ ] Add `/srvstatus` network overview.
- [ ] Add `/srvstatus list` and `/srvstatus info <server>`.
- [ ] Add `/srvstatus check <server|all>`.
- [ ] Add `/srvstatus reload` with validation and safe configuration replacement.
- [ ] Add `/srvstatus history <server>`.
- [ ] Add `/srvstatus debug` and diagnostics output.
- [ ] Add tab completion and consistent permission checks.
- [ ] Add configurable messages and formatting.

Initial permissions:
- `serverstatus.view`
- `serverstatus.admin`
- `serverstatus.reload`
- `serverstatus.check`
- `serverstatus.history`
- `serverstatus.debug`

**Exit criteria:** all commands behave consistently on BungeeCord and Velocity and provide actionable error messages.

### Phase 6 — Persistence and analytics

- [ ] Define a storage provider interface.
- [ ] Implement SQLite as the default storage provider.
- [ ] Implement optional MySQL storage.
- [ ] Add a no-persistence mode.
- [ ] Track uptime, downtime, outage count and recovery duration.
- [ ] Track average, maximum and aggregated latency.
- [ ] Add retention policies and scheduled aggregation.
- [ ] Bound memory and database write frequency.

Do not persist every ping as an individual row indefinitely. Aggregate samples and make retention configurable.

**Exit criteria:** history survives restarts and storage failures do not stop live monitoring.

### Phase 7 — Alerts and Discord

- [ ] Add an event-driven alert manager.
- [ ] Add asynchronous Discord webhook delivery.
- [ ] Support offline, online/recovery, maintenance and degraded events.
- [ ] Add per-server alert configuration.
- [ ] Add cooldowns, deduplication and optional role mentions.
- [ ] Redact webhook URLs from logs and diagnostics.
- [ ] Ensure notification failures never interrupt monitoring.

**Exit criteria:** notifications are deduplicated, configurable and fully isolated from monitoring threads.

### Phase 8 — Public API and integrations

- [ ] Publish stable server/network snapshot interfaces.
- [ ] Add status-change listener/event APIs.
- [ ] Add safe asynchronous check requests.
- [ ] Document API compatibility and lifecycle.
- [ ] Add examples for server selectors, menus and network plugins.

**Exit criteria:** third-party plugins can consume status without parsing placeholders or internal implementation classes.

### Phase 9 — Release hardening

- [ ] Unit-test state transitions, protocol codecs and configuration validation.
- [ ] Integration-test BungeeCord, Velocity and Paper/Spigot.
- [ ] Test empty backend, proxy restart, backend restart and packet loss scenarios.
- [ ] Load-test large configured server counts.
- [ ] Test shutdown, reload and worker cleanup.
- [ ] Verify permissions and malformed-packet handling.
- [ ] Document supported software versions and migration steps.
- [ ] Publish reproducible release artifacts and checksums.

**Exit criteria:** all supported adapters pass the same conformance suite and the release documentation matches the shipped artifacts.

## Placeholder compatibility contract

The existing placeholder `%srvstatus_<server>%` is a compatibility contract. Its configured online/offline/maintenance display strings must continue to work after the internal state model is introduced.

New placeholders should return documented values with consistent casing and safe fallbacks. Unknown server names must not throw exceptions.

## Configuration direction

Keep proxy configuration separate from backend configuration. The proxy owns the tracked server list, monitoring intervals, thresholds, storage and alert configuration. Backend configuration owns transport authentication/connection settings and PlaceholderAPI formatting.

Example target proxy configuration:

```yaml
settings:
  update-interval: 5
  ping-timeout: 3000
  failure-threshold: 3
  recovery-threshold: 2

servers:
  lobby:
    enabled: true
    display-name: "Lobby"
    maintenance-detection: true
    thresholds:
      warning-ping: 150
      critical-ping: 300

transport:
  mode: plugin-messaging
  direct:
    enabled: false
    bind: "127.0.0.1:8765"
    authentication:
      token: "CHANGE-ME"

storage:
  type: sqlite
  retention-days: 30

discord:
  enabled: false
  webhook-url: ""
  notify:
    offline: true
    recovery: true
    maintenance: true
    degraded: true
```

The example direct transport is disabled by default. Never expose an unauthenticated listener publicly. Secrets should be stored outside version control and redacted from logs.

## Compatibility and runtime policy

- Target modern Minecraft server software and Java 21+ initially.
- Support BungeeCord and Velocity through separate native adapters.
- Support Paper/Purpur/Spigot backend APIs through a dedicated backend module.
- Treat PlaceholderAPI and Maintenance as optional integrations.
- Keep API-specific dependencies out of shared modules.
- Maintain a documented support matrix rather than claiming compatibility with every version.

Exact Minecraft and proxy API versions must be selected and tested when the build foundation is implemented.

## Release milestones

| Milestone | Scope |
| --- | --- |
| 0.1 | Reproducible multi-module build and normalized source |
| 0.2 | Shared models, state machine and monitoring engine |
| 0.3 | BungeeCord and Velocity adapters |
| 0.4 | Backend receiver and expanded PlaceholderAPI |
| 0.5 | Admin commands and diagnostics |
| 0.6 | SQLite/MySQL history and analytics |
| 0.7 | Discord alerts |
| 0.8 | Public API and integrations |
| 1.0 | Hardened production release |

## Engineering rules

1. Never perform network, disk or database I/O on a Minecraft main thread.
2. Use bounded executors, bounded queues and explicit timeouts.
3. Publish immutable snapshots for concurrent readers.
4. Do not infer OFFLINE from one failed check.
5. Do not let optional integrations prevent plugin startup.
6. Validate every configuration value and inbound packet.
7. Keep secrets out of source control, logs and diagnostic output.
8. Test both proxy adapters against the same shared behavior suite.
9. Preserve the legacy placeholder and provide a migration path for configuration changes.
10. Update this roadmap as milestones are implemented; do not mark features complete until tested.
