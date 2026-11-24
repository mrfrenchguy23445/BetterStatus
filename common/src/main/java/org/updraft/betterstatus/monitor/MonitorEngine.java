package org.updraft.betterstatus.monitor;

import org.updraft.betterstatus.api.*;
import org.updraft.betterstatus.model.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MonitorEngine implements ServerStatusApi, AutoCloseable {
    private final Map<String, PingProvider> providers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ServerSnapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> failures = new ConcurrentHashMap<>(), recoveries = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<StatusListener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(daemon("BetterStatus-Scheduler"));
    private final ExecutorService workers = new ThreadPoolExecutor(2, 8, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1024), daemon("BetterStatus-Ping"), new ThreadPoolExecutor.CallerRunsPolicy());
    private final MonitorConfig config;
    private final AtomicBoolean running = new AtomicBoolean();

    public MonitorEngine(Map<String, PingProvider> providers, MonitorConfig config) {
        providers.forEach((name, provider) -> {
            String key = normalize(name);
            this.providers.put(key, provider);
            snapshots.put(key, unknown(key));
        });
        this.config = config;
    }

    private static ThreadFactory daemon(String name) {
        return task -> { Thread thread = new Thread(task, name); thread.setDaemon(true); return thread; };
    }
    private static String normalize(String name) { return name.toLowerCase(Locale.ROOT); }
    private static ServerSnapshot unknown(String name) {
        long now = System.currentTimeMillis();
        return new ServerSnapshot(name, ServerState.UNKNOWN, 0, 0, -1, "", "", false, 0, now, now, 0, "Waiting for first check");
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            refreshAll();
            scheduler.scheduleWithFixedDelay(this::refreshAll, config.intervalMillis(), config.intervalMillis(), TimeUnit.MILLISECONDS);
        }
    }

    public void refreshAll() { providers.keySet().forEach(this::checkServer); }

    @Override
    public CompletableFuture<Optional<ServerSnapshot>> checkServer(String name) {
        String key = normalize(name);
        PingProvider provider = providers.get(key);
        if (provider == null) return CompletableFuture.completedFuture(Optional.empty());
        return CompletableFuture.supplyAsync(provider::ping, workers)
                .thenCompose(future -> future)
                .orTimeout(config.timeoutMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(error -> PingResult.failure(error.getClass().getSimpleName()))
                .thenApply(result -> Optional.of(apply(key, result)));
    }

    private ServerSnapshot apply(String name, PingResult ping) {
        long now = System.currentTimeMillis();
        ServerSnapshot old = snapshots.getOrDefault(name, unknown(name));
        ServerState state;
        long lastSuccess = old.lastSuccessMillis();
        long uptimeSince = old.uptimeSinceMillis();

        if (old.maintenance()) {\n            state = ServerState.MAINTENANCE;\n            if (ping.reachable()) lastSuccess = now;\n        } else if (ping.reachable()) {
            failures.put(name, 0);
            int recovered = recoveries.merge(name, 1, Integer::sum);
            lastSuccess = now;
            boolean recovering = old.state() == ServerState.OFFLINE || old.state() == ServerState.UNREACHABLE || old.state() == ServerState.RESTARTING;
            state = recovering && recovered < config.recoveryThreshold() ? ServerState.STARTING
                    : ping.pingMillis() >= config.warningPingMillis() ? ServerState.DEGRADED : ServerState.ONLINE;
            if (state == ServerState.ONLINE && (old.state() != ServerState.ONLINE || uptimeSince == 0)) uptimeSince = now;
        } else {
            recoveries.put(name, 0);
            state = failures.merge(name, 1, Integer::sum) >= config.failureThreshold() ? ServerState.OFFLINE : ServerState.UNREACHABLE;
            uptimeSince = 0;
        }

        long changed = state == old.state() ? old.stateChangedMillis() : now;
        ServerSnapshot current = new ServerSnapshot(name, state, ping.players(), ping.maxPlayers(), ping.pingMillis(),
                ping.motd(), ping.version(), old.maintenance(), lastSuccess, now, changed, uptimeSince, ping.failureReason());
        snapshots.put(name, current);
        if (state != old.state()) for (StatusListener listener : listeners) {
            try { listener.onStatusChange(old, current); } catch (RuntimeException ignored) { }
        }
        return current;
    }

    public void setMaintenance(String name, boolean enabled) {
        String key = normalize(name);
        snapshots.computeIfPresent(key, (ignored, old) -> {
            ServerState state = enabled ? ServerState.MAINTENANCE
                    : old.state() == ServerState.MAINTENANCE ? ServerState.ONLINE : old.state();
            ServerSnapshot current = new ServerSnapshot(old.name(), state, old.players(), old.maxPlayers(), old.pingMillis(),
                    old.motd(), old.version(), enabled, old.lastSuccessMillis(), old.lastCheckMillis(),
                    System.currentTimeMillis(), old.uptimeSinceMillis(), enabled ? "Maintenance mode" : "");
            if (state != old.state()) for (StatusListener listener : listeners) {
                try { listener.onStatusChange(old, current); } catch (RuntimeException ignoredException) { }
            }
            return current;
        });
    }

    @Override public Optional<ServerSnapshot> getServer(String name) { return Optional.ofNullable(snapshots.get(normalize(name))); }
    @Override public Collection<ServerSnapshot> getServers() { return List.copyOf(snapshots.values()); }
    @Override public void addListener(StatusListener listener) { listeners.add(Objects.requireNonNull(listener)); }
    @Override public void removeListener(StatusListener listener) { listeners.remove(listener); }
    @Override public void close() { running.set(false); scheduler.shutdownNow(); workers.shutdownNow(); }
}
