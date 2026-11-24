package org.updraft.betterstatus.api;
import org.updraft.betterstatus.model.ServerSnapshot;
import java.util.*;
import java.util.concurrent.CompletableFuture;
public interface ServerStatusApi { Optional<ServerSnapshot> getServer(String name); Collection<ServerSnapshot> getServers(); CompletableFuture<Optional<ServerSnapshot>> checkServer(String name); void addListener(StatusListener listener); void removeListener(StatusListener listener); }